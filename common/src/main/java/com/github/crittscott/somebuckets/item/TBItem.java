package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One-entry storage bucket whose intake merges only when the complete incoming stack fits.
 * Every other accepted input destroys the old entry and replaces it with one legal stack, leaving
 * any oversized incoming remainder at its source. A use processes one nearby item entity.
 */
public class TBItem extends JBItem {
    private static final double PICKUP_RADIUS = 2.25D;

    /**
     * Creates a one-entry Trash Bucket that processes one item entity per use.
     *
     * @param properties base item properties
     */
    public TBItem(Item.Properties properties) {
        super(properties.rarity(Rarity.RARE), BucketDefinitions.TRASH_BUCKET_CAPACITY_STACKS, PICKUP_RADIUS, 1);
    }

    /**
     * Merges only when the complete incoming stack fits the stored entry; otherwise destroys the old
     * entry and replaces it with one legal stack of the incoming item, leaving any excess.
     */
    @Override
    protected int intake(List<ItemStack> stored, ItemStack incoming) {
        ItemStack current = stored.isEmpty() ? ItemStack.EMPTY : stored.get(0);
        if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, incoming)
                && current.getCount() + incoming.getCount() <= current.getMaxStackSize()) {
            current.grow(incoming.getCount());
            return incoming.getCount();
        }

        int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
        stored.clear();
        stored.add(incoming.copyWithCount(moved));
        return moved;
    }

    /** Every storable candidate can replace the current entry, even when that entry is full. */
    @Override
    protected boolean canIntakeCandidate(List<ItemStack> stored, ItemStack incoming) {
        return true;
    }

    /** A Trash Bucket always has room in the sense that its current entry can be replaced. */
    @Override
    protected boolean canIntakeAnything(List<ItemStack> stored) {
        return true;
    }

    /** The vanilla water-evaporating-in-the-nether sound, reused for Trash Bucket intake. */
    @Override
    protected void playIntakeSound(Level level, Player player) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                FluidTransactions.hissPitch(level.random));
    }

    /** That same evaporation sound, reversed, for the destructive-replacement ejection it mirrors. */
    @Override
    protected void playEjectSound(Level level, Player player, Vec3 pos) {
        level.playSound(player, pos.x, pos.y, pos.z,
                BuiltInRegistries.SOUND_EVENT.getValue(BucketDefinitions.TB_EJECT_SOUND_ID),
                SoundSource.BLOCKS, 0.5F, FluidTransactions.hissPitch(level.random));
    }
}
