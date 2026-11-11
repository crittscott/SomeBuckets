package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/** Cross-loader recipe and furnace scenarios. */
final class RecipeScenarios {
    private static final BlockPos FURNACE_POS = new BlockPos(4, 2, 6);
    private static final int FUEL_SLOT = 1;
    private static final int UNIT = FluidBucketItem.BUCKET_VOLUME_MB;

    private RecipeScenarios() {}
    /**
     * Automation-only: resolves every shipped recipe id from the server recipe manager and requires each
     * one to exist.
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
     * Manual: smelt cobblestone with a Big or Huge Bucket holding two lava units. Each furnace
     * lights and returns the same bucket with one unit left.
     */
    static void furnace_burns_one_lava_unit_and_returns_the_big_bucket(GameTestHelper helper) {
        assertFiniteRemainder(helper, FURNACE_POS, GameTestSupport.big8(), 2);
        assertFiniteRemainder(helper, FURNACE_POS.east(), GameTestSupport.big64(), 2);
        helper.succeed();
    }

    /** Manual: feed a Big Bucket with one lava unit into the side of a furnace; smelting returns an empty Big Bucket. */
    static void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        assertFiniteRemainder(helper, FURNACE_POS, GameTestSupport.big8(), 1);
        helper.succeed();
    }

    /** Manual: try an empty, water, milk, or partly filled lava bucket in a furnace; none supplies fuel. */
    static void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        FurnaceBlockEntity furnace = placeFurnace(helper, FURNACE_POS);
        for (ItemStack stack : List.of(GameTestSupport.big8(),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, UNIT - 1),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, UNIT),
                GameTestSupport.milk(GameTestSupport.big8(), UNIT))) {
            assertRejectsFuel(helper, furnace, stack);
        }
        helper.succeed();
    }

    /** Manual: smelt with an allowed lava Source Bucket; the furnace lights and the lava assignment stays. */
    static void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        FurnaceBlockEntity furnace = placeFurnace(helper, FURNACE_POS);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, UNIT);
        GameTestSupport.check(acceptsSidedFuel(furnace, source), "Furnace rejected allowed lava Source Bucket");
        ItemStack expected = source.copy();
        GameTestSupport.check(ignite(helper, furnace, source), "Lava Source Bucket did not ignite the furnace");
        GameTestSupport.assertSameStack(expected, furnace.getItem(FUEL_SLOT), "Source assignment changed after ignition");
        helper.succeed();
    }

    /** Furnace assertions reused by the loaders' Source Bucket policy tests. */
    static final class FurnaceAssertions {
        private FurnaceAssertions() {}

        static void assertSourceDeniedAsFuel(GameTestHelper helper, ItemStack stack) {
            assertRejectsFuel(helper, placeFurnace(helper, FURNACE_POS), stack);
        }

        static void assertLavaBurns(GameTestHelper helper, ItemStack stack) {
            FurnaceBlockEntity furnace = placeFurnace(helper, FURNACE_POS.east());
            GameTestSupport.check(acceptsSidedFuel(furnace, stack) && ignite(helper, furnace, stack),
                    "Source policy disabled finite lava fuel");
        }
    }

    private static void assertRejectsFuel(GameTestHelper helper, FurnaceBlockEntity furnace, ItemStack stack) {
        GameTestSupport.check(!acceptsSidedFuel(furnace, stack), "Furnace admitted ineligible fuel: " + stack);
        ItemStack expected = stack.copy();
        GameTestSupport.check(!ignite(helper, furnace, stack), "Ineligible fuel ignited the furnace: " + stack);
        GameTestSupport.assertSameStack(expected, furnace.getItem(FUEL_SLOT), "Rejected fuel changed");
    }

    private static void assertFiniteRemainder(GameTestHelper helper, BlockPos pos, ItemStack bucket, int units) {
        FurnaceBlockEntity furnace = placeFurnace(helper, pos);
        ItemStack fuel = GameTestSupport.fluid(bucket, Fluids.LAVA, units * UNIT);
        GameTestSupport.check(acceptsSidedFuel(furnace, fuel), "Furnace rejected eligible lava fuel");
        GameTestSupport.check(ignite(helper, furnace, fuel), "Lava bucket did not ignite the furnace");
        ItemStack remainder = furnace.getItem(FUEL_SLOT);
        GameTestSupport.check(remainder.getItem() == bucket.getItem() && remainder.getCount() == 1,
                "Fuel slot did not return the bucket: " + remainder);
        if (units == 1) {
            GameTestSupport.check(BucketState.isEmptyBucket(remainder), "Last lava unit did not leave an empty bucket");
        } else {
            GameTestSupport.assertFluid(remainder, Fluids.LAVA, (units - 1) * UNIT);
        }
    }

    private static FurnaceBlockEntity placeFurnace(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = helper.getBlockEntity(pos, FurnaceBlockEntity.class);
        GameTestSupport.check(furnace != null, "Furnace block entity was not created");
        furnace.setItem(0, new ItemStack(Items.COBBLESTONE));
        return furnace;
    }

    private static boolean acceptsSidedFuel(FurnaceBlockEntity furnace, ItemStack stack) {
        return furnace.canPlaceItemThroughFace(FUEL_SLOT, stack, Direction.NORTH);
    }

    private static boolean ignite(GameTestHelper helper, FurnaceBlockEntity furnace, ItemStack stack) {
        // Bypass slot admission so this also checks policy on a stack already in the furnace.
        furnace.setItem(FUEL_SLOT, stack);
        var level = helper.getLevel();
        BlockPos pos = furnace.getBlockPos();
        AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        return level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT);
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
     * Manual: craft the Huge Bucket ring with empty Big Buckets, then repeat with one filled bucket; only
     * the empty ring matches.
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
     * Manual: shapeless-craft an empty Source Bucket with a spawn egg; a Mob Bucket is produced, but a
     * filled source is rejected.
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
     * Manual: shapeless-craft the Trash Bucket ingredients with an empty and then nonempty Junk Bucket;
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
     * Manual: shapeless-craft the Source Bucket ingredients with an empty and then nonempty Trash Bucket;
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


