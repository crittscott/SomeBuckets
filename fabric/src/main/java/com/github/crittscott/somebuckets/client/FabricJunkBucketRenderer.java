package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Draws the Junk Bucket vessel with stable, visible icons for its stored stack entries. */
final class FabricJunkBucketRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation VESSEL_MODEL =
            new ResourceLocation(SomeBuckets.MODID, "item/junk_bucket_vessel");

    static void registerModel() {
        ModelLoadingPlugin.register(context -> context.addModels(VESSEL_MODEL));
    }

    @Override
    public void render(ItemStack bucket, ItemDisplayContext context, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        BakedModel vessel = minecraft.getModelManager().getModel(VESSEL_MODEL);
        renderModel(renderer, bucket, vessel, pose, buffers, light, overlay);

        List<ItemStack> contents = NBTUtil.getStoredItems(bucket);
        for (int index = contents.size() - 1; index >= 0; index--) {
            RandomSource random = RandomSource.create(seed(contents.get(index), index));
            float size = 0.27F + random.nextFloat() * 0.09F;
            float x = 0.31F + random.nextFloat() * 0.38F;
            float y = 0.48F + random.nextFloat() * 0.16F;
            float angle = (random.nextFloat() * 2.0F - 1.0F) * 0.44F;
            float z = 0.04F + (contents.size() - index) * 0.0015F;

            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.ZP.rotation(angle));
            pose.scale(size, size, 1.0F / 256.0F);
            renderer.renderStatic(contents.get(index), ItemDisplayContext.GUI, light, overlay,
                    pose, buffers, minecraft.level, index);
            pose.popPose();
        }

        if (!contents.isEmpty()) {
            renderModel(renderer, bucket, vessel, pose, buffers, light, overlay);
        }
    }

    private static void renderModel(ItemRenderer renderer, ItemStack stack, BakedModel model,
                                    PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }

    private static long seed(ItemStack stack, int index) {
        return Item.getId(stack.getItem()) * 31L + index;
    }
}
