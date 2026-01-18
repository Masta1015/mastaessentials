package com.mastaessentials.events;

import com.mastaessentials.commands.HomeCommand;
import com.mastaessentials.commands.ReloadCommand;
import com.mastaessentials.rankup.RankCommand;
import com.mastaessentials.afk.AfkManager;
import com.mastaessentials.tpa.TpaManager; // <--- import TpaManager

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mastaessentials.chat.ChatManager;
import net.minecraftforge.event.server.ServerStartedEvent;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

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
        AfkManager.register(event.getDispatcher());
        TpaManager.registerCommands(event.getDispatcher());
        ChatManager.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        HomeCommand.loadHomes(event.getServer()); // server exists here
        RankCommand.loadConfig(event.getServer());
        AfkManager.loadConfig();
        TpaManager.loadConfig();
        ChatManager.loadConfig();
    }

}
