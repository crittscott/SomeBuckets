package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.TwoColorPalette;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.function.Consumer;


/** Owns a reusable GUI-item target and one asynchronous readback; all calls run on the render thread. */
@Environment(EnvType.CLIENT)
final class EggPaletteSampler implements AutoCloseable {
    static final int IMAGE_SIZE = 32;
    private static final int BUFFER_CAPACITY = 65536;
    private static final int CHANNELS = 4;
    private static final float ITEM_SIZE = IMAGE_SIZE - 4;
    // Keep the whole item inside the clip volume on both OpenGL and zero-to-one backends.
    private static final float ITEM_Z = -750;
    private static final float PROJECTION_FAR = 1000;

    private final ByteBufferBuilder vertices = new ByteBufferBuilder(BUFFER_CAPACITY);
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(vertices);
    private final SubmitNodeStorage submits = new SubmitNodeStorage();
    private final FeatureRenderDispatcher features = createFeatures();
    private final FogRenderer fog = new FogRenderer();
    private final GpuBuffer projection = createProjection();
    private final GpuBuffer readback = RenderSystem.getDevice().createBuffer(() -> "SomeBuckets egg pixels",
            GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, IMAGE_SIZE * IMAGE_SIZE * CHANNELS);
    private final TextureTarget target = new TextureTarget("SomeBuckets egg palette", IMAGE_SIZE, IMAGE_SIZE, true);
    private boolean busy;
    private boolean closed;
    private boolean retired;

    void capture(Item egg, Consumer<MobEggColors.Sample> complete) {
        RenderSystem.assertOnRenderThread();
        if (busy || retired) throw new IllegalStateException("Egg palette sampler is unavailable");
        render(egg);
        busy = true;
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target.getColorTexture(),
                    readback, 0, () -> finish(complete), 0);
        } catch (RuntimeException | LinkageError exception) {
            busy = false;
            throw exception;
        }
    }

    /** Closes immediately when idle, or after the outstanding GPU readback has completed. */
    @Override
    public void close() {
        retired = true;
        if (busy || closed) return;
        closed = true;
        target.destroyBuffers();
        readback.close();
        projection.close();
        fog.close();
        features.close();
        vertices.close();
    }

    private FeatureRenderDispatcher createFeatures() {
        Minecraft minecraft = Minecraft.getInstance();
        return new FeatureRenderDispatcher(submits, minecraft.getBlockRenderer(), buffers,
                minecraft.getAtlasManager(), minecraft.renderBuffers().outlineBufferSource(), buffers,
                minecraft.font);
    }

    private static GpuBuffer createProjection() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
            new Matrix4f().setOrtho(0, IMAGE_SIZE, IMAGE_SIZE, 0, 0, PROJECTION_FAR).get(data);
            return RenderSystem.getDevice().createBuffer(() -> "SomeBuckets egg projection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
        }
    }

    private void finish(Consumer<MobEggColors.Sample> complete) {
        if (retired) {
            busy = false;
            close();
            return;
        }
        MobEggColors.Sample sample;
        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(readback, true, false)) {
            int[] pixels = pixels(view.data());
            sample = TwoColorPalette.extract(pixels)
                    .map(colors -> new MobEggColors.Sample(colors, pixels, null))
                    .orElseGet(() -> new MobEggColors.Sample(null, pixels, "rendered egg is entirely transparent"));
        } catch (RuntimeException | LinkageError exception) {
            sample = new MobEggColors.Sample(null, new int[0], exception.toString());
        } finally {
            busy = false;
            if (retired) close();
        }
        if (!retired) complete.accept(sample);
    }

    private static int[] pixels(ByteBuffer data) {
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                // GPU rows start at the bottom; transparent-target blending premultiplies RGB.
                int offset = ((IMAGE_SIZE - 1 - y) * IMAGE_SIZE + x) * CHANNELS;
                int alpha = Byte.toUnsignedInt(data.get(offset + 3));
                if (alpha == 0) continue;
                int red = Math.min(255, Byte.toUnsignedInt(data.get(offset)) * 255 / alpha);
                int green = Math.min(255, Byte.toUnsignedInt(data.get(offset + 1)) * 255 / alpha);
                int blue = Math.min(255, Byte.toUnsignedInt(data.get(offset + 2)) * 255 / alpha);
                pixels[y * IMAGE_SIZE + x] = alpha << 24 | red << 16 | green << 8 | blue;
            }
        }
        return pixels;
    }

    private void render(Item egg) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStackRenderState item = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(item, egg.getDefaultInstance(),
                ItemDisplayContext.GUI, minecraft.level, null, 0);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1);

        GpuTextureView color = RenderSystem.outputColorTextureOverride;
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice oldProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType projectionType = RenderSystem.getProjectionType();
        GpuBufferSlice lights = RenderSystem.getShaderLights();
        GpuBufferSlice oldFog = RenderSystem.getShaderFog();
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        boolean scissored = scissor.enabled();
        int sx = scissor.x(), sy = scissor.y(), sw = scissor.width(), sh = scissor.height();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            RenderSystem.setProjectionMatrix(projection.slice(), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.setShaderFog(fog.getBuffer(FogRenderer.FogMode.NONE));
            minecraft.gameRenderer.getLighting().setupFor(item.usesBlockLight()
                    ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            PoseStack pose = new PoseStack();
            pose.translate(IMAGE_SIZE / 2.0F, IMAGE_SIZE / 2.0F, ITEM_Z);
            pose.scale(ITEM_SIZE, -ITEM_SIZE, ITEM_SIZE);
            item.submit(pose, submits, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            features.renderAllFeatures();
            buffers.endBatch();
            features.endFrame();
        } finally {
            RenderSystem.outputColorTextureOverride = color;
            RenderSystem.outputDepthTextureOverride = depth;
            RenderSystem.setProjectionMatrix(oldProjection, projectionType);
            RenderSystem.setShaderLights(lights);
            RenderSystem.setShaderFog(oldFog);
            if (scissored) RenderSystem.enableScissorForRenderTypeDraws(sx, sy, sw, sh);
            else RenderSystem.disableScissorForRenderTypeDraws();
            modelView.popMatrix();
        }
    }
}
