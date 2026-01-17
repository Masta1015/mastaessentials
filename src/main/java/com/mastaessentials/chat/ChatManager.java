package com.mastaessentials.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collection;

public class ChatManager {

    private static final File CONFIG_FILE = new File("config/MastaConfig/chatConfig.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static ChatConfig config;

    /** Load or create default config */
    public static void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) {
                if (!CONFIG_FILE.getParentFile().exists()) CONFIG_FILE.getParentFile().mkdirs();
                config = new ChatConfig(); // default
                saveConfig();              // writes chatConfig.json
            } else {
                Type type = new TypeToken<ChatConfig>() {}.getType();
                config = gson.fromJson(new FileReader(CONFIG_FILE), type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            config = new ChatConfig();
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        loadConfig();

        if (!config.enabled) return; // commands disabled

        // /me
        dispatcher.register(
                Commands.literal("me")
                        .then(Commands.argument("action", MessageArgument.message())
                                .executes(ctx -> meAction(ctx.getSource(), MessageArgument.getMessage(ctx, "action").getString()))
                        )
        );
    }

    private static int meAction(CommandSourceStack source, String actionText) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) { return 0; }

        String message = config.meFormat
                .replace("%player%", player.getName().getString())
                .replace("%action%", actionText);

        Collection<ServerPlayer> players = player.getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            p.sendSystemMessage(Component.literal(message));
        }

        return 1;
    }

    private static class ChatConfig {
        boolean enabled = true;
        String meFormat = "§a* %player% %action% *";
        ChatConfig() {}
    }
}
