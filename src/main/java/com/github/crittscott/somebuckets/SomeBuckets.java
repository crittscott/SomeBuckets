package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.interaction.StorageBucketDispenser;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SomeBuckets.MODID)
public class SomeBuckets {
    public static final String MODID = "somebuckets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SomeBuckets() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        // Register all mod content
        ModItems.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom ingredient types used by bucket recipes
            EmptyBucketIngredient.register();
            SpawnEggIngredient.register();
            ClaimProtections.initialize();

            // Dispenser behaviors
            Dispensers behavior = new Dispensers();
            DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_8.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_64.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.SOURCE_BUCKET.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.MOB_BUCKET.get(), behavior);
            StorageBucketDispenser storageBehavior = new StorageBucketDispenser();
            DispenserBlock.registerBehavior(ModItems.JUNK_BUCKET.get(), storageBehavior);
            DispenserBlock.registerBehavior(ModItems.TRASH_BUCKET.get(), storageBehavior);

            // Cauldron interactions (BB specific; SB handles via its own item logic + dispenser)
            Cauldrons.register();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Reuse BB property mapping for SB
            ResourceLocation prop = new ResourceLocation(MODID, "bb_content");

            ItemProperties.register(ModItems.BIG_BUCKET_8.get(), prop,
                    (stack, level, entity, seed) -> BBItem.getContentProperty(stack));
            ItemProperties.register(ModItems.BIG_BUCKET_64.get(), prop,
                    (stack, level, entity, seed) -> BBItem.getContentProperty(stack));
            ItemProperties.register(ModItems.SOURCE_BUCKET.get(), prop,
                    (stack, level, entity, seed) -> SBItem.getContentProperty(stack));

            // Mob bucket filled property
            ResourceLocation filledProp = new ResourceLocation(MODID, "filled");
            ItemProperties.register(ModItems.MOB_BUCKET.get(), filledProp,
                    (stack, level, entity, seed) -> NBTUtil.getEntityCount(stack) > 0 ? 1.0f : 0.0f);
        });
    }

}
