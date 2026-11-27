package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** The Junk Bucket's custom item renderer: hands the baked vessel to {@link JunkBucketRenderer}. */
@OnlyIn(Dist.CLIENT)
public final class JBRenderer extends BlockEntityWithoutLevelRenderer {
    private static JBRenderer instance;
    private static volatile BakedModel vesselModel;

    private JBRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** NeoForge expects one BEWLR instance for the mod rather than one per rendered stack. */
    public static JBRenderer getInstance() {
        if (instance == null) instance = new JBRenderer();
        return instance;
    }

    /** Client item extensions that route Junk Bucket rendering through the shared BEWLR. */
    public static IClientItemExtensions createItemExtensions() {
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return getInstance();
            }
        };
    }

    /** Supplies the newly baked vessel on each model bake. */
    static void setVesselModel(BakedModel vessel) {
        vesselModel = vessel;
    }

    @Override
    public void renderByItem(ItemStack bucket, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight,
                             int combinedOverlay) {
        JunkBucketRenderer.render(vesselModel, bucket, context, poseStack, bufferSource,
                combinedLight, combinedOverlay);
    }
}
