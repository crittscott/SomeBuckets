package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Adds one bucket stack after its data-driven target and chance conditions pass. */
public final class AddBucketLootModifier extends LootModifier {
    public static final MapCodec<AddBucketLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item")
                            .forGetter(modifier -> modifier.item),
                    Codec.intRange(0, BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS)
                            .optionalFieldOf("powder_units", 0)
                            .forGetter(modifier -> modifier.powderUnits)
            )).apply(instance, AddBucketLootModifier::new));

    private final Item item;
    private final int powderUnits;

    private AddBucketLootModifier(LootItemCondition[] conditions, Item item, int powderUnits) {
        super(conditions);
        this.item = item;
        this.powderUnits = powderUnits;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        ItemStack stack = new ItemStack(item);
        if (powderUnits > 0) NBTUtil.setPowderUnits(stack, powderUnits);
        generatedLoot.add(stack);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
