package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/** Cross-loader recipe and furnace-fuel scenarios; each loader wraps every method as a GameTest. */
final class RecipeScenarios {
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    /** Ticks allowed for a furnace block entity to take its fuel and light. */
    private static final int FURNACE_LIGHT_TICKS = 5;

    private RecipeScenarios() {}

    /**
     * In game: craft a Big, Huge, Junk, Trash, Source, and Mob Bucket from their recipes; each recipe is
     * available and yields an empty bucket.
     */
    static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        checkRecipeResult(helper, "big_bucket_8", GameTestSupport.big8());
        checkRecipeResult(helper, "big_bucket_64", GameTestSupport.big64());
        checkRecipeResult(helper, "junk_bucket", GameTestSupport.junk());
        checkRecipeResult(helper, "trash_bucket", GameTestSupport.trash());
        checkRecipeResult(helper, "source_bucket", GameTestSupport.source());
        checkRecipeResult(helper, "mob_bucket", GameTestSupport.mob());
        helper.succeed();
    }

    /**
     * In game: put cobblestone in a furnace and a Big Bucket holding two units of lava in the fuel slot;
     * the furnace lights and the fuel slot keeps the Big Bucket with one unit left. With one unit of
     * lava, the furnace lights and the fuel slot ends holding an empty Big Bucket.
     */
    static void furnace_burns_one_lava_unit_and_returns_the_big_bucket(GameTestHelper helper) {
        BlockPos twoUnits = new BlockPos(2, 2, 4);
        BlockPos oneUnit = new BlockPos(6, 2, 4);
        Item bigBucket = GameTestSupport.big8().getItem();
        AbstractFurnaceBlockEntity twoUnitFurnace = furnace(helper, twoUnits,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 2000));
        AbstractFurnaceBlockEntity oneUnitFurnace = furnace(helper, oneUnit,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000));

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            assertLit(helper, twoUnits, true);
            assertLit(helper, oneUnit, true);
            ItemStack twoUnitFuel = twoUnitFurnace.getItem(FUEL_SLOT);
            GameTestSupport.check(twoUnitFuel.is(bigBucket) && twoUnitFuel.getCount() == 1,
                    "Fuel slot did not keep one Big Bucket: " + twoUnitFuel);
            GameTestSupport.assertFluid(twoUnitFuel, Fluids.LAVA, 1000);
            ItemStack oneUnitFuel = oneUnitFurnace.getItem(FUEL_SLOT);
            GameTestSupport.check(oneUnitFuel.is(bigBucket) && oneUnitFuel.getCount() == 1,
                    "Fuel slot did not keep an empty Big Bucket: " + oneUnitFuel);
            GameTestSupport.assertEmpty(oneUnitFuel);
            helper.succeed();
        });
    }

    /**
     * In game: put cobblestone in a furnace and a lava Source Bucket in the fuel slot; the furnace
     * lights and the fuel slot keeps the same lava Source Bucket.
     */
    static void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        AbstractFurnaceBlockEntity furnace = furnace(helper, pos, source.copy());

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            assertLit(helper, pos, true);
            GameTestSupport.assertSameStack(source, furnace.getItem(FUEL_SLOT),
                    "Furnace consumed or changed the lava Source Bucket");
            helper.succeed();
        });
    }

    /**
     * In game: put cobblestone in three furnaces, with a Big Bucket holding less than a unit of lava, a
     * full Big Bucket of water, and a full Big Bucket of milk in the fuel slots; none lights and each
     * fuel slot keeps its bucket unchanged.
     */
    static void non_lava_buckets_do_not_fuel_a_furnace(GameTestHelper helper) {
        List<BlockPos> positions = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(6, 2, 4));
        List<ItemStack> fuels = List.of(GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000),
                GameTestSupport.milk(GameTestSupport.big8(), 8000));
        List<AbstractFurnaceBlockEntity> furnaces = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            furnaces.add(furnace(helper, positions.get(i), fuels.get(i).copy()));
        }

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            for (int i = 0; i < positions.size(); i++) {
                assertLit(helper, positions.get(i), false);
                GameTestSupport.assertSameStack(fuels.get(i), furnaces.get(i).getItem(FUEL_SLOT),
                        "Furnace consumed a bucket that is not fuel");
            }
            helper.succeed();
        });
    }

    /** A furnace at {@code pos} smelting cobblestone with {@code fuel} in its fuel slot. */
    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper, BlockPos pos, ItemStack fuel) {
        helper.setBlock(pos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(pos, AbstractFurnaceBlockEntity.class);
        GameTestSupport.check(furnace != null, "Furnace block entity was not created");
        furnace.setItem(INPUT_SLOT, new ItemStack(Items.COBBLESTONE));
        furnace.setItem(FUEL_SLOT, fuel);
        return furnace;
    }

    private static void assertLit(GameTestHelper helper, BlockPos pos, boolean expected) {
        GameTestSupport.check(helper.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT) == expected,
                "Furnace at " + pos + (expected ? " did not light" : " lit"));
    }

    private static void checkRecipeResult(GameTestHelper helper, String path, ItemStack expected) {
        CraftingRecipe recipe = recipe(helper, path);
        ItemStack result = recipe.assemble(CraftingInput.of(1, 1, List.of(ItemStack.EMPTY)));
        GameTestSupport.check(result.getItem() == expected.getItem(),
                "Recipe somebuckets:" + path + " produced " + result);
        GameTestSupport.check(BucketState.isEmptyBucket(result),
                "Recipe somebuckets:" + path + " produced a non-empty bucket");
    }
    /**
     * In game: lay out the Huge Bucket recipe with empty Big Buckets; it yields a Huge Bucket. Replace
     * one with a Big Bucket holding water; the recipe no longer matches.
     */
    static void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "big_bucket_64");
        ItemStack empty = GameTestSupport.big8();
        ItemStack filled = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Huge Bucket recipe did not accept empty Big Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Huge Bucket recipe accepted filled Big Bucket");
        helper.succeed();
    }
    /**
     * In game: shapeless-craft an empty Source Bucket with a pig spawn egg; a Mob Bucket results. A
     * water-assigned Source Bucket in place of the empty one, or a porkchop in place of the egg, gives
     * no result.
     */
    static void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "mob_bucket");
        ItemStack emptySource = GameTestSupport.source();
        ItemStack filledSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack spawnEgg = new ItemStack(Items.PIG_SPAWN_EGG);
        ItemStack ordinaryItem = new ItemStack(Items.PORKCHOP);

        GameTestSupport.check(anyIngredientMatches(recipe, emptySource),
                "Mob Bucket recipe did not accept empty Source Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filledSource),
                "Mob Bucket recipe accepted assigned Source Bucket");
        GameTestSupport.check(anyIngredientMatches(recipe, spawnEgg),
                "Mob Bucket recipe did not accept a standard spawn egg");
        GameTestSupport.check(!anyIngredientMatches(recipe, ordinaryItem),
                "Mob Bucket recipe accepted a non-spawn-egg item");
        helper.succeed();
    }

    /**
     * In game: shapeless-craft the Trash Bucket ingredients with an empty and then nonempty Junk Bucket;
     * only the empty one matches.
     */
    static void trash_bucket_recipe_accepts_only_empty_junk_buckets(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "trash_bucket");
        ItemStack empty = GameTestSupport.junk();
        ItemStack filled = GameTestSupport.junk();
        BucketState.setStoredItems(filled, List.of(new ItemStack(Items.APPLE)));

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Trash Bucket recipe did not accept an empty Junk Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Trash Bucket recipe accepted a non-empty Junk Bucket");
        helper.succeed();
    }
    /**
     * In game: shapeless-craft the Source Bucket ingredients with an empty and then nonempty Trash Bucket;
     * only the empty one matches.
     */
    static void source_bucket_recipe_accepts_only_empty_trash_buckets(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "source_bucket");
        ItemStack empty = GameTestSupport.trash();
        ItemStack filled = GameTestSupport.trash();
        BucketState.setStoredItems(filled, List.of(new ItemStack(Items.APPLE)));

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Source Bucket recipe did not accept an empty Trash Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Source Bucket recipe accepted a non-empty Trash Bucket");
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String path) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(SomeBuckets.MODID, path));
        return (CraftingRecipe) helper.getLevel().recipeAccess()
                .byKey(key)
                .orElseThrow(() -> helper.assertionException(Component.literal("Missing recipe somebuckets:" + path)))
                .value();
    }

    private static boolean anyIngredientMatches(CraftingRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.test(stack)) return true;
        }
        return false;
    }
}
