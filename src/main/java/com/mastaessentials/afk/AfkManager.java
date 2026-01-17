package com.mastaessentials.afk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mod.EventBusSubscriber
public class AfkManager {

    private static final Map<UUID, Long> lastActive = new HashMap<>();
    private static final Set<UUID> afkPlayers = new HashSet<>();
    private static final Map<UUID, Position> playerPositions = new HashMap<>();

    private static boolean ENABLED = true;
    private static int KICK_TIME_MINUTES = 15;
    private static final Map<String, String> MESSAGES = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("AfkConfig.json");

    // -------------------- Command --------------------
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("afk")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean nowAfk = toggleAfk(player);

                            String msg = getMessage(nowAfk ? "afkEnabled" : "afkDisabled")
                                    .replace("%player%", player.getName().getString());
                            ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                            return 1;
                        })
        );
    }

    // -------------------- AFK Logic --------------------
    public static boolean toggleAfk(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (afkPlayers.contains(uuid)) {
            afkPlayers.remove(uuid);
            lastActive.put(uuid, System.currentTimeMillis());
            return false;
        } else {
            afkPlayers.add(uuid);
            return true;
        }
    }

    public static void playerActivity(ServerPlayer player) {
        UUID uuid = player.getUUID();
        lastActive.put(uuid, System.currentTimeMillis());
        if (afkPlayers.remove(uuid)) {
            // Send "no longer AFK" message
            String msg = getMessage("afkDisabled").replace("%player%", player.getName().getString());
            player.sendSystemMessage(Component.literal(msg));
        }
    }

    // -------------------- Tick Events --------------------
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!(event.player instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.player; // classic cast

        Position lastPos = playerPositions.getOrDefault(player.getUUID(),
                new Position(player.getX(), player.getY(), player.getZ()));

        if (player.getX() != lastPos.x || player.getY() != lastPos.y || player.getZ() != lastPos.z) {
            playerActivity(player);
        }

        playerPositions.put(player.getUUID(), new Position(player.getX(), player.getY(), player.getZ()));
    }
    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        playerActivity(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ENABLED) return;

        long now = System.currentTimeMillis();
        long kickMillis = KICK_TIME_MINUTES * 60L * 1000L;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();

            // ensure player has lastActive initialized
            lastActive.putIfAbsent(uuid, now);

            long last = lastActive.get(uuid);

            if (afkPlayers.contains(uuid) && now - last >= kickMillis) {
                // Kick player and remove them from afk tracking
                player.connection.disconnect(Component.literal(getMessage("kick")));
                afkPlayers.remove(uuid);
                lastActive.remove(uuid);
                playerPositions.remove(uuid);
            }
        }
    }


    // -------------------- Config --------------------
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        loadConfig();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        afkPlayers.clear();
        lastActive.clear();
        playerPositions.clear();
    }

    public static void loadConfig() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            if (!Files.exists(CONFIG_FILE)) saveDefaultConfig();

            try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> cfg = GSON.fromJson(r, type);

                ENABLED = (Boolean) cfg.getOrDefault("enabled", true);
                KICK_TIME_MINUTES = ((Double) cfg.getOrDefault("kickTimeMinutes", 15.0)).intValue();

                Map<String, String> messages = (Map<String, String>) cfg.getOrDefault("messages", new HashMap<>());
                MESSAGES.clear();
                MESSAGES.putAll(messages);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveDefaultConfig() {
        try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", true);
            cfg.put("kickTimeMinutes", 15);

            Map<String, String> messages = new LinkedHashMap<>();
            messages.put("afkEnabled", "§7%player% is now AFK.");
            messages.put("afkDisabled", "§7%player% is no longer AFK.");
            messages.put("kick", "§cYou were kicked for being AFK too long. Hope to see you on again sometime soon!");

            cfg.put("messages", messages);
            GSON.toJson(cfg, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getMessage(String key) {
        return MESSAGES.getOrDefault(key, key);
    }

    // -------------------- Helper Classes --------------------
    private static class Position {
        double x, y, z;
        Position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }
}
