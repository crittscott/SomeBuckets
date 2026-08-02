package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.interaction.FuelHandler;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(SomeBuckets.MODID)
public class SomeBuckets {
    public static final String MODID = "somebuckets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SomeBuckets() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register all mod content
        ModItems.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);
        bus.addListener(this::registerItemColors);

        FuelHandler.register(MinecraftForge.EVENT_BUS);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Ingredient type used by recipes that consume a bucket as material
            EmptyBucketIngredient.register();

            // Dispenser behaviors
            Dispensers behavior = new Dispensers();
            DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_8.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_64.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.SOURCE_BUCKET.get(), behavior);
            DispenserBlock.registerBehavior(ModItems.MOB_BUCKET.get(), behavior);

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

    private void registerItemColors(final RegisterColorHandlersEvent.Item event) {
        ItemColors itemColors = event.getItemColors();

        itemColors.register((stack, tintIndex) -> {
            if (tintIndex == 0) return -1; // No tint for base layer

            EntityType<?> entityType = NBTUtil.getCurrentEntityType(stack);
            if (entityType == null) return 0x808080; // Gray fallback

            // Find spawn egg for this entity type
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            if (entityId == null) return 0x808080;

            String eggName = entityId.getPath() + "_spawn_egg";
            ResourceLocation eggId = new ResourceLocation(entityId.getNamespace(), eggName);
            Item eggItem = ForgeRegistries.ITEMS.getValue(eggId);

            if (!(eggItem instanceof SpawnEggItem spawnEgg)) return 0x808080;

            return tintIndex == 1 ? spawnEgg.getColor(0) : spawnEgg.getColor(1);
        }, ModItems.MOB_BUCKET.get());
    }
}
