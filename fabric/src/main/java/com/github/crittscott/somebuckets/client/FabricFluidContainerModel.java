package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
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
import net.minecraft.client.renderer.block.model.BakedOverrides;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Replaces the Fabric fluid-mask layer with the stored fluid's live atlas sprite. */
final class FabricFluidContainerModel implements BakedModel, FabricBakedModel {
    private static final int FLUID_TINT_INDEX = 1;
    private static final int CACHE_LIMIT = 256;
    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    private static final Set<String> FLUID_MODEL_PATHS = Set.of(
            "item/" + BucketDefinitions.BIG_BUCKET_ID.getPath() + "_fluid",
            "item/" + BucketDefinitions.HUGE_BUCKET_ID.getPath() + "_fluid",
            "item/" + BucketDefinitions.SOURCE_BUCKET_ID.getPath() + "_fluid");

    private final BakedModel vessel;

    private final Map<TextureAtlasSprite, Mesh> fluidLayers = new ConcurrentHashMap<>();

    private FabricFluidContainerModel(BakedModel vessel) {
        this.vessel = vessel;
    }

    static void registerModels() {
        ModelLoadingPlugin.register(context -> {
            FluidMaskGeometry.clear();
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
        List<FluidMaskGeometry.Face> faces = FluidMaskGeometry.faces();
        if (stored.isEmpty() || faces.isEmpty()) {
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
        Mesh fluidLayer = fluidLayers.computeIfAbsent(sprite, key -> buildFluidLayer(key, faces));
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
    public BakedOverrides overrides() {
        return vessel.overrides();
    }

    /*
     * Builds the content-mask slab textured with the fluid sprite, assembled through the Fabric
     * renderer's {@link QuadEmitter} so the vertex format is owned by the renderer rather than
     * packed by hand.
     */
    private static Mesh buildFluidLayer(TextureAtlasSprite sprite, List<FluidMaskGeometry.Face> faces) {
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException("Fabric renderer is unavailable");
        }
        RenderMaterial material = renderer.materialFinder().blendMode(BlendMode.SOLID).find();
        MeshBuilder builder = renderer.meshBuilder();
        QuadEmitter emitter = builder.getEmitter();
        for (FluidMaskGeometry.Face face : faces) {
            emitter.material(material);
            vertex(emitter, sprite, 0, face.first());
            vertex(emitter, sprite, 1, face.second());
            vertex(emitter, sprite, 2, face.third());
            vertex(emitter, sprite, 3, face.fourth());
            emitter.nominalFace(face.direction());
            emitter.colorIndex(FLUID_TINT_INDEX);
            emitter.emit();
        }
        return builder.build();
    }

    private static void vertex(QuadEmitter emitter, TextureAtlasSprite sprite, int index,
                               FluidMaskGeometry.Vertex point) {
        emitter.pos(index, point.x(), point.y(), point.z());
        emitter.color(index, VERTEX_COLOR);
        emitter.uv(index,
                lerp(sprite.getU0(), sprite.getU1(), point.x()),
                lerp(sprite.getV1(), sprite.getV0(), point.y()));
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
