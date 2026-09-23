package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Renders the stored fluid of Big, Huge, and Source Buckets without runtime model baking. */
@OnlyIn(Dist.CLIENT)
public final class FluidBucketRenderer extends BlockEntityWithoutLevelRenderer {
    private static FluidBucketRenderer instance;
    private static volatile BakedModel bigVessel;
    private static volatile BakedModel hugeVessel;
    private static volatile BakedModel sourceVessel;

    private FluidBucketRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** NeoForge expects the item extension to return a shared renderer instance. */
    public static IClientItemExtensions createItemExtensions() {
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new FluidBucketRenderer();
                return instance;
            }
        };
    }

    /** Supplies the ordinary baked vessel models after each resource reload. */
    static void setVesselModels(BakedModel big, BakedModel huge, BakedModel source) {
        bigVessel = big;
        hugeVessel = huge;
        sourceVessel = source;
        FluidMaskGeometry.clear();
    }

    @Override
    public void renderByItem(ItemStack bucket, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight,
                             int combinedOverlay) {
        BakedModel vessel = vesselFor(bucket);
        if (vessel == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        renderModel(minecraft.getItemRenderer(), bucket, vessel, poseStack, bufferSource,
                combinedLight, combinedOverlay);

        FluidStack contents = NeoForgeFluidStacks.get(bucket);
        if (contents.isEmpty()) return;

        IClientFluidTypeExtensions extensions =
                IClientFluidTypeExtensions.of(contents.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(contents);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTexture);
        int tint = extensions.getTintColor(contents);
        int light = contents.getFluid().getFluidType().getLightLevel() > 0
                ? LightTexture.FULL_BRIGHT : combinedLight;
        VertexConsumer consumer = bufferSource.getBuffer(Sheets.translucentItemSheet());
        renderFluid(sprite, tint, light, combinedOverlay, poseStack, consumer);
    }

    private static BakedModel vesselFor(ItemStack stack) {
        if (stack.is(ModItems.BIG_BUCKET_64.get())) return hugeVessel;
        if (stack.is(ModItems.SOURCE_BUCKET.get())) return sourceVessel;
        return bigVessel;
    }

    /*
     * The outer item renderer has already applied the selected display transform and translated
     * model coordinates by -0.5. The nested render performs its own -0.5 translation, so +0.5
     * keeps the retained vessel aligned with the directly submitted fluid vertices.
     */
    private static void renderModel(ItemRenderer itemRenderer, ItemStack stack, BakedModel model,
                                    PoseStack poseStack, MultiBufferSource bufferSource,
                                    int combinedLight, int combinedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, bufferSource,
                combinedLight, combinedOverlay, model);
        poseStack.popPose();
    }

    private static void renderFluid(TextureAtlasSprite sprite, int tint, int light, int overlay,
                                    PoseStack poseStack, VertexConsumer consumer) {
        PoseStack.Pose pose = poseStack.last();
        for (FluidMaskGeometry.Face face : FluidMaskGeometry.faces()) {
            Direction direction = face.direction();
            vertex(consumer, pose, sprite, face.first(), direction, tint, light, overlay);
            vertex(consumer, pose, sprite, face.second(), direction, tint, light, overlay);
            vertex(consumer, pose, sprite, face.third(), direction, tint, light, overlay);
            vertex(consumer, pose, sprite, face.fourth(), direction, tint, light, overlay);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               TextureAtlasSprite sprite, FluidMaskGeometry.Vertex point,
                               Direction normal, int tint, int light, int overlay) {
        consumer.addVertex(pose, point.x(), point.y(), point.z())
                .setColor(tint)
                .setUv(lerp(sprite.getU0(), sprite.getU1(), point.x()),
                        lerp(sprite.getV1(), sprite.getV0(), point.y()))
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
