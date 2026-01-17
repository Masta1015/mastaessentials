package com.mastaessentials.JoinandLeave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber
public class JoinLeaveMessages {

    private static final Logger LOGGER = LogManager.getLogger("MastaEssentials");

    private static final Path CONFIG_DIR =
            FMLPaths.CONFIGDIR.get().resolve("MastaConfig");
    private static final File CONFIG_FILE =
            CONFIG_DIR.resolve("join_leave_messages.json").toFile();

    private static ConfigData configData;
    private static final Random RANDOM = new Random();

    static {
        loadConfig();
    }

    /* =========================
       EVENTS
       ========================= */

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!configData.enableJoinMessages) return;
        if (configData.joinMessages.isEmpty()) return;

        String message = formatMessage(
                getRandomMessage(configData.joinMessages),
                player
        );

        player.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);

        LOGGER.info(message); // ✅ Console output
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!configData.enableLeaveMessages) return;
        if (configData.leaveMessages.isEmpty()) return;

        String message = formatMessage(
                getRandomMessage(configData.leaveMessages),
                player
        );

        player.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);

        LOGGER.info(message); // ✅ Console output
    }

    /* =========================
       CONFIG
       ========================= */

    public static void reloadConfig() {
        loadConfig();
    }

    public static void loadConfig() {
        try {
            if (!CONFIG_DIR.toFile().exists())
                CONFIG_DIR.toFile().mkdirs();

            Gson gson = new Gson();
            Type type = new TypeToken<ConfigData>() {}.getType();

            if (!CONFIG_FILE.exists()) {
                configData = new ConfigData();
                saveConfig();
            } else {
                FileReader reader = new FileReader(CONFIG_FILE);
                configData = gson.fromJson(reader, type);
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveConfig() {
        try {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            FileWriter writer = new FileWriter(CONFIG_FILE);
            gson.toJson(configData, writer);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private static String getRandomMessage(List<String> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    private static String formatMessage(String message, ServerPlayer player) {
        return message.replace("{player}", player.getName().getString());
    }

    /* =========================
       DATA CLASS
       ========================= */

    private static class ConfigData {

        boolean enableJoinMessages = true;
        boolean enableLeaveMessages = true;

        List<String> joinMessages = List.of(
                "§eWelcome {player}!",
                "§e{player} joined the server!",
                "§eEveryone say hi to {player}!"
        );

        List<String> leaveMessages = List.of(
                "§e{player} left the server.",
                "§eGoodbye {player}!",
                "§e{player} has logged out."
        );
    }
}
