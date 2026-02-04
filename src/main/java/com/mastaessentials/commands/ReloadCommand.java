package com.mastaessentials.commands;

import com.mastaessentials.rankup.RankCommand;
import com.mastaessentials.JoinandLeave.JoinLeaveMessages;
import com.mastaessentials.deathmessages.DeathMessages;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import com.mastaessentials.chat.ChatManager;

public class ReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadconfig")
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

            // 🔥 Reload Join / Leave messages
            JoinLeaveMessages.reloadConfig();

            // ⚡ Reload Death messages
            DeathMessages.loadConfig();

            // 🌀 Reload TPA config
            ChatManager.loadConfig();
        }

        source.sendSuccess(
                () -> Component.literal("MastaEssentials configs reloaded successfully!"),
                true
        );
        return 1;
    }
}
