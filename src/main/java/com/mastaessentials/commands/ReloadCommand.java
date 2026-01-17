package com.mastaessentials.commands;

import com.mastaessentials.MastaEssentialsMod;
import com.mastaessentials.commands.HomeCommand;
import com.mastaessentials.rankup.RankCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class ReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reloadmod")
                .requires(cs -> cs.hasPermission(2)) // OP only
                .executes(context -> reload(context.getSource())));
    }

    private static int reload(CommandSourceStack source) {
        MinecraftServer server = source.getServer(); // get server instance

        // Reload mod configs / data
        if (server != null) {
            HomeCommand.loadHomes(server);   // reload homes
            RankCommand.loadConfig(server);  // reload rank config
        }

        MastaEssentialsMod.reloadConfigs(); // optional additional reload logic

        // Notify the player/admin
        source.sendSuccess(() -> Component.literal("MastaEssentials configs reloaded successfully!"), true);
        return 1;
    }
}
