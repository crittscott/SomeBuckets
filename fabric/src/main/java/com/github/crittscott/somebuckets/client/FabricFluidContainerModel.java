package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
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
    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    private static final ResourceLocation MASK =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "textures/item/big_bucket_full.png");
    private static final Set<String> FLUID_MODEL_PATHS = Set.of(
            "item/" + BucketDefinitions.BIG_BUCKET_ID.getPath() + "_fluid",
            "item/" + BucketDefinitions.HUGE_BUCKET_ID.getPath() + "_fluid",
            "item/" + BucketDefinitions.SOURCE_BUCKET_ID.getPath() + "_fluid");

    private static volatile FluidMask mask;

    private final BakedModel vessel;

    private final Map<TextureAtlasSprite, Mesh> fluidLayers = new ConcurrentHashMap<>();

    private FabricFluidContainerModel(BakedModel vessel) {
        this.vessel = vessel;
    }

    static void registerModels() {
        ModelLoadingPlugin.register(context -> {
            mask = null;
            FabricClientFluidColors.clearCache();
            context.modifyModelAfterBake().register((model, modelContext) -> {
                ResourceLocation id = modelContext.resourceId();
                if (model == null || id == null || !SomeBuckets.MODID.equals(id.getNamespace())
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
        StoredFluid stored = BucketState.getStoredFluid(stack);
        FluidMask currentMask = getMask();
        if (stored.isEmpty() || currentMask.isEmpty()) {
            emitVessel(stack, randomSupplier, context, false);
            return;
        }

        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        if (sprite == null) {
            emitVessel(stack, randomSupplier, context, false);
            return;
        }

        emitVessel(stack, randomSupplier, context, true);
        if (fluidLayers.size() >= CACHE_LIMIT) fluidLayers.clear();
        Mesh fluidLayer = fluidLayers.computeIfAbsent(sprite, key -> buildFluidLayer(key, currentMask));
        fluidLayer.outputTo(context.getEmitter());
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

    /**
     * Voxelizes the opaque cells of the content mask into a generated-item-thickness slab textured
     * with the fluid sprite, assembled through the Fabric renderer's {@link QuadEmitter} so the
     * vertex format is owned by the renderer rather than packed by hand.
     */
    private static Mesh buildFluidLayer(TextureAtlasSprite sprite, FluidMask mask) {
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException("Fabric renderer is unavailable");
        }
        RenderMaterial material = renderer.materialFinder().blendMode(BlendMode.SOLID).find();
        MeshBuilder builder = renderer.meshBuilder();
        QuadEmitter emitter = builder.getEmitter();

        float cellWidth = MODEL_SIZE / mask.width();
        float cellHeight = MODEL_SIZE / mask.height();

        for (int row = 0; row < mask.height(); row++) {
            for (int column = 0; column < mask.width(); column++) {
                if (!mask.isOpaque(column, row)) continue;

                float minX = column * cellWidth;
                float maxX = (column + 1) * cellWidth;
                float minY = MODEL_SIZE - (row + 1) * cellHeight;
                float maxY = MODEL_SIZE - row * cellHeight;

                face(emitter, material, sprite, Direction.SOUTH,
                        point(minX, maxY, FRONT_DEPTH), point(minX, minY, FRONT_DEPTH),
                        point(maxX, minY, FRONT_DEPTH), point(maxX, maxY, FRONT_DEPTH));
                face(emitter, material, sprite, Direction.NORTH,
                        point(maxX, maxY, BACK_DEPTH), point(maxX, minY, BACK_DEPTH),
                        point(minX, minY, BACK_DEPTH), point(minX, maxY, BACK_DEPTH));

                if (!mask.isOpaque(column - 1, row)) {
                    face(emitter, material, sprite, Direction.WEST,
                            point(minX, maxY, BACK_DEPTH), point(minX, minY, BACK_DEPTH),
                            point(minX, minY, FRONT_DEPTH), point(minX, maxY, FRONT_DEPTH));
                }
                if (!mask.isOpaque(column + 1, row)) {
                    face(emitter, material, sprite, Direction.EAST,
                            point(maxX, maxY, FRONT_DEPTH), point(maxX, minY, FRONT_DEPTH),
                            point(maxX, minY, BACK_DEPTH), point(maxX, maxY, BACK_DEPTH));
                }
                if (!mask.isOpaque(column, row - 1)) {
                    face(emitter, material, sprite, Direction.UP,
                            point(minX, maxY, BACK_DEPTH), point(minX, maxY, FRONT_DEPTH),
                            point(maxX, maxY, FRONT_DEPTH), point(maxX, maxY, BACK_DEPTH));
                }
                if (!mask.isOpaque(column, row + 1)) {
                    face(emitter, material, sprite, Direction.DOWN,
                            point(minX, minY, FRONT_DEPTH), point(minX, minY, BACK_DEPTH),
                            point(maxX, minY, BACK_DEPTH), point(maxX, minY, FRONT_DEPTH));
                }
            }
        }
        return builder.build();
    }

    private static float[] point(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    private static void face(QuadEmitter emitter, RenderMaterial material, TextureAtlasSprite sprite,
                             Direction direction, float[] first, float[] second, float[] third,
                             float[] fourth) {
        float[][] points = {first, second, third, fourth};
        emitter.material(material);
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = points[vertex][0];
            float y = points[vertex][1];
            float z = points[vertex][2];
            emitter.pos(vertex, x / MODEL_SIZE, y / MODEL_SIZE, z / MODEL_SIZE);
            emitter.color(vertex, VERTEX_COLOR);
            emitter.uv(vertex,
                    lerp(sprite.getU0(), sprite.getU1(), x / MODEL_SIZE),
                    lerp(sprite.getV1(), sprite.getV0(), y / MODEL_SIZE));
        }
        emitter.nominalFace(direction);
        emitter.colorIndex(FLUID_TINT_INDEX);
        emitter.emit();
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
