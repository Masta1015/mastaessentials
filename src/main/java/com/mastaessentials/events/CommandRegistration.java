package com.mastaessentials.events;

import com.mastaessentials.commands.HomeCommand;
import com.mastaessentials.rankup.RankCommand;
import com.mastaessentials.commands.ReloadCommand;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "mastaessentials",
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class CommandRegistration {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HomeCommand.register(event.getDispatcher());
        RankCommand.register(event.getDispatcher());
        ReloadCommand.register(event.getDispatcher());
    }
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        HomeCommand.loadHomes(event.getServer());
        RankCommand.loadConfig(event.getServer()); // load or create JSON
    }

}

