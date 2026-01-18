package com.mastaessentials.commands;

import com.mastaessentials.MastaEssentialsMod;
import com.mastaessentials.commands.HomeCommand;
import com.mastaessentials.rankup.RankCommand;
import com.mastaessentials.afk.AfkManager;
import com.mastaessentials.JoinandLeave.JoinLeaveMessages;
import com.mastaessentials.deathmessages.DeathMessages;
import com.mastaessentials.tpa.TpaManager; // <-- ADD THIS
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import com.mastaessentials.chat.ChatManager;

public class ReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadmod")
                .requires(cs -> cs.hasPermission(2))
                .executes(context -> reload(context.getSource())));
    }

    private static int reload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        if (server != null) {
            // Reload Home config
            HomeCommand.loadHomes(server);

            // Reload Rank config
            RankCommand.loadConfig(server);

            // Reload AFK config
            AfkManager.loadConfig();

            // 🔥 Reload Join / Leave messages
            JoinLeaveMessages.reloadConfig();

            // ⚡ Reload Death messages
            DeathMessages.loadConfig();

            // 🌀 Reload TPA config
            TpaManager.loadConfig(); // <-- This will reload Tpa.json
            ChatManager.loadConfig();
        }

        MastaEssentialsMod.reloadConfigs();

        source.sendSuccess(
                () -> Component.literal("MastaEssentials configs reloaded successfully!"),
                true
        );
        return 1;
    }
}
