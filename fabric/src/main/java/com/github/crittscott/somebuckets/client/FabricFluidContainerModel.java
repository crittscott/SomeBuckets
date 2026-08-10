package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Replaces the Fabric fluid-mask layer with the stored fluid's live atlas sprite. */
final class FabricFluidContainerModel implements BakedModel, FabricBakedModel {
    private static final int FLUID_TINT_INDEX = 1;
    private static final int CACHE_LIMIT = 256;
    private static final float MODEL_SIZE = 16.0F;
    private static final float BACK_DEPTH = 7.49F;
    private static final float FRONT_DEPTH = 8.51F;

    private static final int VERTEX_STRIDE = 8;
    private static final int POSITION = 0;
    private static final int COLOR = 3;
    private static final int UV = 4;
    private static final int NORMAL = 7;
    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    private static final ResourceLocation MASK =
            new ResourceLocation(SomeBuckets.MODID, "textures/item/big_bucket_full.png");
    private static final Set<String> FLUID_MODEL_PATHS = Set.of(
            "item/big_bucket_8_fluid",
            "item/big_bucket_64_fluid",
            "item/source_bucket_fluid");

    private static volatile FluidMask mask;

    private final BakedModel vessel;
    private static volatile RenderMaterial fluidMaterial;

    private final Map<TextureAtlasSprite, FluidLayerModel> fluidLayers = new ConcurrentHashMap<>();

    private FabricFluidContainerModel(BakedModel vessel) {
        this.vessel = vessel;
    }

    static void registerModels() {
        ModelLoadingPlugin.register(context -> {
            mask = null;
            FabricClientFluidColors.clearCache();
            context.modifyModelAfterBake().register((model, modelContext) -> {
                ResourceLocation id = modelContext.id();
                if (model == null || !SomeBuckets.MODID.equals(id.getNamespace())
                        || !FLUID_MODEL_PATHS.contains(id.getPath())) {
                    return model;
                }
                return new FabricFluidContainerModel(model);
            });
        });
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier,
                              RenderContext context) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        FluidMask currentMask = getMask();
        if (stored.isEmpty() || currentMask.isEmpty()) {
            emitVessel(stack, randomSupplier, context, false);
            return;
        }

        FluidVariant variant = FluidVariant.of(stored.fluid(), stored.variantTag());
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        if (sprite == null) {
            emitVessel(stack, randomSupplier, context, false);
            return;
        }

        emitVessel(stack, randomSupplier, context, true);
        if (fluidLayers.size() >= CACHE_LIMIT) fluidLayers.clear();
        FluidLayerModel fluidLayer = fluidLayers.computeIfAbsent(sprite,
                key -> new FluidLayerModel(vessel, key, currentMask));
        emitFluidLayer(fluidLayer, context);
    }

    private void emitVessel(ItemStack stack, Supplier<RandomSource> randomSupplier,
                            RenderContext context, boolean removeMask) {
        if (removeMask) {
            context.pushTransform(quad -> quad.colorIndex() != FLUID_TINT_INDEX);
        }
        try {
            ((FabricBakedModel) vessel).emitItemQuads(stack, randomSupplier, context);
        } finally {
            if (removeMask) context.popTransform();
        }
    }

    private static void emitFluidLayer(FluidLayerModel fluidLayer, RenderContext context) {
        RenderMaterial material = getFluidMaterial();
        QuadEmitter emitter = context.getEmitter();
        for (BakedQuad quad : fluidLayer.quads) {
            emitter.fromVanilla(quad, material, null).emit();
        }
    }

    private static RenderMaterial getFluidMaterial() {
        RenderMaterial cached = fluidMaterial;
        if (cached == null) {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) {
                throw new IllegalStateException("Fabric renderer is unavailable");
            }
            cached = renderer.materialFinder().blendMode(BlendMode.SOLID).find();
            fluidMaterial = cached;
        }
        return cached;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random) {
        return vessel.getQuads(state, side, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return vessel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return vessel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return vessel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return vessel.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return vessel.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return vessel.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return vessel.getOverrides();
    }

    private static FluidMask getMask() {
        FluidMask cached = mask;
        if (cached == null) {
            cached = readMask();
            mask = cached;
        }
        return cached;
    }

    private static FluidMask readMask() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MASK);
        if (resource.isEmpty()) return FluidMask.EMPTY;

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            boolean[][] opaque = new boolean[image.getHeight()][image.getWidth()];
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    opaque[row][column] = (image.getPixelRGBA(column, row) >>> 24) != 0;
                }
            }
            return new FluidMask(image.getWidth(), image.getHeight(), opaque);
        } catch (IOException ignored) {
            return FluidMask.EMPTY;
        }
    }

    private record FluidMask(int width, int height, boolean[][] opaque) {
        private static final FluidMask EMPTY = new FluidMask(0, 0, new boolean[0][0]);

        private boolean isEmpty() {
            return width == 0 || height == 0;
        }

        private boolean isOpaque(int column, int row) {
            return row >= 0 && row < height && column >= 0 && column < width
                    && opaque[row][column];
        }
    }

    /** A generated-item-thickness layer clipped to the opaque pixels of the content mask. */
    private static final class FluidLayerModel implements BakedModel {
        private final BakedModel vessel;
        private final TextureAtlasSprite sprite;
        private final List<BakedQuad> quads;

        private FluidLayerModel(BakedModel vessel, TextureAtlasSprite sprite, FluidMask mask) {
            this.vessel = vessel;
            this.sprite = sprite;
            this.quads = buildQuads(sprite, mask);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return vessel.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return vessel.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return vessel.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return sprite;
        }

        @Override
        public ItemTransforms getTransforms() {
            return vessel.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    private static List<BakedQuad> buildQuads(TextureAtlasSprite sprite, FluidMask mask) {
        List<BakedQuad> out = new ArrayList<>();
        float cellWidth = MODEL_SIZE / mask.width();
        float cellHeight = MODEL_SIZE / mask.height();

        for (int row = 0; row < mask.height(); row++) {
            for (int column = 0; column < mask.width(); column++) {
                if (!mask.isOpaque(column, row)) continue;

                float minX = column * cellWidth;
                float maxX = (column + 1) * cellWidth;
                float minY = MODEL_SIZE - (row + 1) * cellHeight;
                float maxY = MODEL_SIZE - row * cellHeight;

                out.add(face(sprite, Direction.SOUTH,
                        point(minX, maxY, FRONT_DEPTH), point(minX, minY, FRONT_DEPTH),
                        point(maxX, minY, FRONT_DEPTH), point(maxX, maxY, FRONT_DEPTH)));
                out.add(face(sprite, Direction.NORTH,
                        point(maxX, maxY, BACK_DEPTH), point(maxX, minY, BACK_DEPTH),
                        point(minX, minY, BACK_DEPTH), point(minX, maxY, BACK_DEPTH)));

                if (!mask.isOpaque(column - 1, row)) {
                    out.add(face(sprite, Direction.WEST,
                            point(minX, maxY, BACK_DEPTH), point(minX, minY, BACK_DEPTH),
                            point(minX, minY, FRONT_DEPTH), point(minX, maxY, FRONT_DEPTH)));
                }
                if (!mask.isOpaque(column + 1, row)) {
                    out.add(face(sprite, Direction.EAST,
                            point(maxX, maxY, FRONT_DEPTH), point(maxX, minY, FRONT_DEPTH),
                            point(maxX, minY, BACK_DEPTH), point(maxX, maxY, BACK_DEPTH)));
                }
                if (!mask.isOpaque(column, row - 1)) {
                    out.add(face(sprite, Direction.UP,
                            point(minX, maxY, BACK_DEPTH), point(minX, maxY, FRONT_DEPTH),
                            point(maxX, maxY, FRONT_DEPTH), point(maxX, maxY, BACK_DEPTH)));
                }
                if (!mask.isOpaque(column, row + 1)) {
                    out.add(face(sprite, Direction.DOWN,
                            point(minX, minY, FRONT_DEPTH), point(minX, minY, BACK_DEPTH),
                            point(maxX, minY, BACK_DEPTH), point(maxX, minY, FRONT_DEPTH)));
                }
            }
        }
        return List.copyOf(out);
    }

    private static float[] point(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    private static BakedQuad face(TextureAtlasSprite sprite, Direction direction,
                                  float[] first, float[] second, float[] third, float[] fourth) {
        float[][] points = {first, second, third, fourth};
        int[] vertices = new int[VERTEX_STRIDE * 4];
        int normal = packedNormal(direction);

        for (int vertex = 0; vertex < 4; vertex++) {
            float x = points[vertex][0];
            float y = points[vertex][1];
            float z = points[vertex][2];
            int base = vertex * VERTEX_STRIDE;
            vertices[base + POSITION] = Float.floatToRawIntBits(x / MODEL_SIZE);
            vertices[base + POSITION + 1] = Float.floatToRawIntBits(y / MODEL_SIZE);
            vertices[base + POSITION + 2] = Float.floatToRawIntBits(z / MODEL_SIZE);
            vertices[base + COLOR] = VERTEX_COLOR;
            vertices[base + UV] = Float.floatToRawIntBits(
                    lerp(sprite.getU0(), sprite.getU1(), x / MODEL_SIZE));
            vertices[base + UV + 1] = Float.floatToRawIntBits(
                    lerp(sprite.getV1(), sprite.getV0(), y / MODEL_SIZE));
            vertices[base + NORMAL] = normal;
        }
        return new BakedQuad(vertices, FLUID_TINT_INDEX, direction, sprite, true);
    }

    private static int packedNormal(Direction direction) {
        return direction.getStepX() * 127 & 0xFF
                | (direction.getStepY() * 127 & 0xFF) << 8
                | (direction.getStepZ() * 127 & 0xFF) << 16;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
