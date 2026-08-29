package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.ForgeBucketOperations;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModLootModifiers;
import com.github.crittscott.somebuckets.register.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge mod entry point. The constructor installs shared runtime services, registers the server
 * config and mod content on the mod event bus, and listens for config (re)load. {@link #commonSetup}
 * then registers dispenser behaviors and cauldron interactions once the mod bus reaches the
 * common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public class SomeBucketsForge {

    private static final DeferredRegister<IIngredientSerializer<?>> INGREDIENT_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, SomeBuckets.MODID);

    static {
        INGREDIENT_SERIALIZERS.register(EmptyBucketIngredient.ID.getPath(), () -> EmptyBucketIngredient.SERIALIZER);
        INGREDIENT_SERIALIZERS.register(SpawnEggIngredient.ID.getPath(), () -> SpawnEggIngredient.SERIALIZER);
    }

    public SomeBucketsForge(FMLJavaModLoadingContext context) {
        BucketOperations.install(new ForgeBucketOperations());
        MinecraftForge.EVENT_BUS.addGenericListener(
                net.minecraft.world.item.ItemStack.class, FluidProvider::attach);
        IEventBus bus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);

        // Register all mod content
        ModItems.register(bus);
        ModLootModifiers.register(bus);
        ModSounds.register(bus);
        ModCreativeTabs.register(bus);
        INGREDIENT_SERIALIZERS.register(bus);

        bus.addListener(this::commonSetup);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private static void refreshSourceBucketPolicy(ModConfig config) {
        if (config.getSpec() == ServerConfig.SPEC) {
            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), config.getFileName());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Dispensers.register();

            // Register BB cauldron-map adapters; shared transitions also serve SB and dispensers.
            Cauldrons.register();
        });
    }

}
