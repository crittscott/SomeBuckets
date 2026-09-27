package com.github.crittscott.somebuckets.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Rolls another loot table into the generated loot after this modifier's conditions pass. */
public final class AddTableLootModifier extends LootModifier {
    /** Codec for loot conditions and the loot table to roll. */
    public static final MapCodec<AddTableLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(
                    ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("table")
                            .forGetter(modifier -> modifier.table)
            ).apply(instance, AddTableLootModifier::new));

    private final ResourceKey<LootTable> table;

    private AddTableLootModifier(LootItemCondition[] conditions, ResourceKey<LootTable> table) {
        super(conditions);
        this.table = table;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(LootTable targetTable, ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        context.getResolver().lookup(Registries.LOOT_TABLE)
                .flatMap(tables -> tables.get(table))
                .ifPresent(extra -> extra.value().getRandomItemsRaw(context,
                        LootTable.createStackSplitter(context.getLevel(), generatedLoot::add)));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
