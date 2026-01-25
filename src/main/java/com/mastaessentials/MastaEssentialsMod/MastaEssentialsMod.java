package com.mastaessentials.MastaEssentialsMod;

import com.mastaessentials.commands.HomeCommand;
import com.mastaessentials.commands.ReloadCommand;
import com.mastaessentials.rankup.RankCommand;
import com.mastaessentials.chat.ChatManager;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.mastaessentials.schedular.SchedulerConfig;
import com.mastaessentials.schedular.TimeScheduler;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import org.slf4j.Logger;

@Mod(MastaEssentialsMod.MODID)
public class MastaEssentialsMod {
    public static final String MODID = "mastaessentials";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> EXAMPLE_BLOCK =
            BLOCKS.register("example_block",
                    () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));

    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM =
            ITEMS.register("example_block",
                    () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> EXAMPLE_ITEM =
            ITEMS.register("example_item",
                    () -> new Item(new Item.Properties().food(
                            new FoodProperties.Builder()
                                    .alwaysEat()
                                    .nutrition(1)
                                    .saturationMod(2f)
                                    .build())));

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB =
            CREATIVE_MODE_TABS.register("example_tab",
                    () -> CreativeModeTab.builder()
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) ->
                                    output.accept(EXAMPLE_ITEM.get()))
                            .build());

    public MastaEssentialsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);

        modBus.addListener(this::setup);

        // 🔥 THIS IS THE IMPORTANT LINE 🔥
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("MastaEssentials is now starting up. Good Work!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("MastaEssentials server starting — loading configs & scheduler");

        HomeCommand.loadHomes(event.getServer());
        RankCommand.loadConfig(event.getServer());
        ChatManager.loadConfig();

        // ✅ NOW this will actually run
        SchedulerConfig.load();

        // ✅ Scheduler will tick
        MinecraftForge.EVENT_BUS.register(new TimeScheduler());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HomeCommand.register(event.getDispatcher());
        RankCommand.register(event.getDispatcher());
        ReloadCommand.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(
            modid = MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT
    )
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
