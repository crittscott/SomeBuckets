package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Draws a fluid bucket model's content mask with the stored fluid's live atlas sprite. The vessel
 * model already holds the mask as generated-item geometry on its content tint index; this model
 * keeps that geometry and moves its texture coordinates from the mask sprite to the fluid sprite.
 */
final class FabricFluidContainerModel extends ForwardingBakedModel {
    /*
     * The fluid is drawn with a solid material, in a different pass from the vessel's own layers, so
     * its front and back faces are pushed this far (in block units) outside the vessel's coincident
     * faces to win the depth test.
     */
    private static final float FACE_OFFSET = 0.01F / BucketMouth.ITEM_MODEL_SIZE;
    private static final Set<String> FLUID_MODEL_PATHS = Set.of(
            "item/big_bucket_8_fluid",
            "item/big_bucket_64_fluid",
            "item/source_bucket_fluid");

    private static volatile RenderMaterial fluidMaterial;

    @Nullable
    private final TextureAtlasSprite mask;

    private FabricFluidContainerModel(BakedModel vessel) {
        this.wrapped = vessel;
        this.mask = maskSprite(vessel);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        TextureAtlasSprite fluid = mask == null ? null : fluidSprite(stack);
        if (fluid == null) {
            super.emitItemQuads(stack, randomSupplier, context);
            return;
        }

        RenderMaterial material = getFluidMaterial();
        TextureAtlasSprite from = mask;
        context.pushTransform(quad -> {
            if (quad.colorIndex() == ClientPlatform.CONTENT_TINT_INDEX) retexture(quad, from, fluid, material);
            return true;
        });
        try {
            super.emitItemQuads(stack, randomSupplier, context);
        } finally {
            context.popTransform();
        }
    }

    @Nullable
    private static TextureAtlasSprite fluidSprite(ItemStack stack) {
        StoredFluid stored = BucketState.getStoredFluid(stack);
        return stored.isEmpty() ? null : FluidVariantRendering.getSprite(FabricFluidVariants.toVariant(stored));
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
    public boolean isVanillaAdapter() {
        return false;
    }

    /** The content mask's sprite: the one on the vessel's content-tinted quads. */
    @Nullable
    private static TextureAtlasSprite maskSprite(BakedModel vessel) {
        for (BakedQuad quad : vessel.getQuads(null, null, RandomSource.create(0L))) {
            if (quad.getTintIndex() == ClientPlatform.CONTENT_TINT_INDEX) return quad.getSprite();
        }
        return null;
    }

    static void registerModels() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, modelContext) -> {
            ResourceLocation id = modelContext.id();
            if (model == null || !SomeBuckets.MODID.equals(id.getNamespace())
                    || !FLUID_MODEL_PATHS.contains(id.getPath())) {
                return model;
            }
            return new FabricFluidContainerModel(model);
        }));
    }

    // Map each vertex's position in the mask sprite's atlas region to the same position in the fluid's.
    private static void retexture(MutableQuadView quad, TextureAtlasSprite mask, TextureAtlasSprite fluid,
                                  RenderMaterial material) {
        float uScale = (fluid.getU1() - fluid.getU0()) / (mask.getU1() - mask.getU0());
        float vScale = (fluid.getV1() - fluid.getV0()) / (mask.getV1() - mask.getV0());
        Direction face = quad.nominalFace();
        float offset = face == Direction.SOUTH ? FACE_OFFSET : face == Direction.NORTH ? -FACE_OFFSET : 0.0F;
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.uv(vertex, fluid.getU0() + (quad.u(vertex) - mask.getU0()) * uScale,
                    fluid.getV0() + (quad.v(vertex) - mask.getV0()) * vScale);
            if (offset != 0.0F) quad.pos(vertex, quad.x(vertex), quad.y(vertex), quad.z(vertex) + offset);
        }
        quad.material(material);
    }
}
