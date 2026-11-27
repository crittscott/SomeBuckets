package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.diagnostic.DiagnosticsSupport;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.MobEggColors;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * The item tint rules of the bucket models, registered by each loader through its own item color
 * registry. Fluid buckets tint their content mask with the stored fluid's loader-resolved tint, Mob
 * Buckets tint their two overlays from the stored mob, and the Trash Bucket tints its mask black;
 * every other layer is untinted. Milk models have no content layer, so only fluid mode reaches a
 * tinted fluid-bucket quad.
 */
@Environment(EnvType.CLIENT)
public final class ItemTints {
    /**
     * The tint index of the bucket item models' {@code layer1} content mask: a fluid bucket's content
     * and the Trash Bucket's void.
     */
    public static final int CONTENT_TINT_INDEX = 1;

    private static final int MISSING_EGG_COLOR = 0xFF808080;
    // The Mob Bucket model's first overlay layer; later layers take later spawn-egg colors.
    private static final int MOB_BUCKET_FIRST_OVERLAY_INDEX = 1;
    private static final int NO_TINT = -1;
    private static final int TRASH_VOID_TINT = 0xFF000000;

    private ItemTints() {}

    private static int fluidBucketTint(ItemStack stack, int tintIndex, ToIntFunction<StoredFluid> fluidTint) {
        if (tintIndex != CONTENT_TINT_INDEX || BucketState.getMode(stack) != BucketState.Mode.FLUID) return NO_TINT;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        return stored.isEmpty() ? NO_TINT : fluidTint.applyAsInt(stored);
    }

    /*
     * The two overlays take the override table's or the loader-resolved spawn egg's colors, the same
     * lookup /sb eggs reports; a mob with neither is gray.
     */
    private static int mobBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex < MOB_BUCKET_FIRST_OVERLAY_INDEX) return NO_TINT;
        EntityType<?> type = BucketState.getCurrentEntityType(stack);
        if (type == null) return MISSING_EGG_COLOR;
        int[] colors = MobEggColors.resolve(type, DiagnosticsSupport.get().spawnEggFor(type));
        return colors == null ? MISSING_EGG_COLOR : colors[tintIndex - MOB_BUCKET_FIRST_OVERLAY_INDEX];
    }

    /**
     * Registers the bucket item tints, looking the items up by their {@link BucketDefinitions} ids.
     *
     * @param registrar the loader's item color registration for one item
     * @param fluidTint the loader's stack-aware ARGB tint for a non-empty stored fluid
     */
    public static void register(BiConsumer<ItemColor, Item> registrar, ToIntFunction<StoredFluid> fluidTint) {
        ItemColor fluidBuckets = (stack, tintIndex) -> fluidBucketTint(stack, tintIndex, fluidTint);
        for (ResourceLocation id : List.of(BucketDefinitions.BIG_BUCKET_ID, BucketDefinitions.HUGE_BUCKET_ID,
                BucketDefinitions.SOURCE_BUCKET_ID)) {
            registrar.accept(fluidBuckets, BuiltInRegistries.ITEM.get(id));
        }
        registrar.accept(ItemTints::mobBucketTint, BuiltInRegistries.ITEM.get(BucketDefinitions.MOB_BUCKET_ID));
        registrar.accept(ItemTints::trashBucketTint, BuiltInRegistries.ITEM.get(BucketDefinitions.TRASH_BUCKET_ID));
    }

    private static int trashBucketTint(ItemStack stack, int tintIndex) {
        return tintIndex == CONTENT_TINT_INDEX ? TRASH_VOID_TINT : NO_TINT;
    }
}
