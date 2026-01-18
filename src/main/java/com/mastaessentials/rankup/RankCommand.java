package com.mastaessentials.rankup;

import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.Commands;

@Mod.EventBusSubscriber
public class RankCommand {

    private static final Path CONFIG_DIR = Path.of("config", "MastaConfig");
    private static final Path RANK_FILE = CONFIG_DIR.resolve("Ranks.json");
    private static final Path RANK_STORAGE = CONFIG_DIR.resolve("RankStorage");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static boolean RankupFeatureEnabled = true;
    private static final List<Rank> RANKS = new ArrayList<>();

    /* ================= REGISTER COMMAND ================= */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rank")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            showRank(player);
                            return 1;
                        })
        );
    }

    /* ================= CONFIG ================= */
    public static void loadConfig(MinecraftServer server) {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(RANK_STORAGE);

            if (!Files.exists(RANK_FILE)) createDefaultConfig();

            try (Reader reader = Files.newBufferedReader(RANK_FILE)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                RankupFeatureEnabled = root.get("RankupFeatureEnabled").getAsBoolean();
                RANKS.clear();

                JsonObject ranks = root.getAsJsonObject("ranks");
                for (String key : ranks.keySet()) {
                    JsonObject r = ranks.getAsJsonObject(key);
                    int hours = r.get("hours").getAsInt();
                    int minutes = r.get("minutes").getAsInt();
                    long seconds = hours * 3600L + minutes * 60L;

                    List<String> commands = new ArrayList<>();
                    r.getAsJsonArray("commands").forEach(e -> commands.add(e.getAsString()));

                    List<String> notes = new ArrayList<>();
                    if (r.has("notes")) r.getAsJsonArray("notes").forEach(e -> notes.add(e.getAsString()));

                    RANKS.add(new Rank(key, seconds, commands, notes));
                }

                RANKS.sort(Comparator.comparingLong(a -> a.seconds));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefaultConfig() throws Exception {
        try (Writer writer = Files.newBufferedWriter(RANK_FILE)) {
            JsonObject root = new JsonObject();
            root.addProperty("RankupFeatureEnabled", true);

            JsonObject ranks = new JsonObject();
            ranks.add("Initiate", rankObj(0, 0, List.of(), List.of(
                    "• 2 /sethome",
                    "• /rtp",
                    "• /spawn",
                    "• /warp event",
                    "• /warp resourceworld",
                    "• /back",
                    "• 16 chunks"
            )));
            ranks.add("Tinkermage", rankObj(6, 0, List.of("say %player% has ranked up to Tinkermage!"), List.of(
                    "• 1 /sethome",
                    "• 4 chunks",
                    "• /trash",
                    "• 1 Donor Crate Key on get"
            )));
            ranks.add("Runesmith", rankObj(25, 0, List.of("give %player% minecraft:diamond 2"), List.of(
                    "• 1 /sethome",
                    "• 4 chunks",
                    "• 1 Donor Crate Key on get"
            )));
            ranks.add("Arcanengineer", rankObj(61, 0, List.of(), List.of(
                    "• 1 /pwarp create",
                    "• 1 /sethome",
                    "• 4 chunks",
                    "• /nick",
                    "• 1 Donor Crate Key on get"
            )));
            ranks.add("Mystic Mechanist", rankObj(100, 0, List.of(), List.of(
                    "• 1 /pwarp create",
                    "• 1 /sethome",
                    "• 4 chunks",
                    "• 1 Donor Crate Key on get"
            )));

            root.add("ranks", ranks);
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject rankObj(int hours, int minutes, List<String> commands, List<String> notes) {
        JsonObject obj = new JsonObject();
        obj.addProperty("hours", hours);
        obj.addProperty("minutes", minutes);

        JsonArray arr = new JsonArray();
        commands.forEach(arr::add);
        obj.add("commands", arr);

        JsonArray notesArr = new JsonArray();
        notes.forEach(notesArr::add);
        obj.add("notes", notesArr);

        return obj;
    }

    /* ================= SHOW RANK ================= */
    private static void showRank(ServerPlayer player) {
        if (!RankupFeatureEnabled) {
            player.sendSystemMessage(Component.literal("§cRank system is disabled."));
            return;
        }

        long secondsPlayed = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20;

        Rank current = null;
        Rank next = null;

        for (Rank rank : RANKS) {
            if (secondsPlayed >= rank.seconds) current = rank;
            else {
                next = rank;
                break;
            }
        }

        long hours = secondsPlayed / 3600;
        long minutes = (secondsPlayed % 3600) / 60;

        player.sendSystemMessage(Component.literal("§6§lYour Rank"));
        player.sendSystemMessage(Component.literal("§7Current: §a" + (current != null ? current.name : "None")));
        player.sendSystemMessage(Component.literal("§7Playtime: §b" + hours + "h " + minutes + "m"));

        if (next != null && current != null) {
            double percent = ((double)(secondsPlayed - current.seconds) / (next.seconds - current.seconds)) * 100;
            player.sendSystemMessage(Component.literal("§7Next Rank: §e" + next.name));
            player.sendSystemMessage(Component.literal(String.format("§7Progress: §a%.1f%%", percent)));
        } else {
            player.sendSystemMessage(Component.literal("§aYou are at the highest rank!"));
        }

        if (current != null && current.notes != null) {
            player.sendSystemMessage(Component.literal("§6§lPerks:"));
            for (String note : current.notes) {
                player.sendSystemMessage(Component.literal("§7" + note));
            }
        }
    }

    /* ================= AUTOMATIC RANKUP CHECK ================= */
    public static void checkRankup(ServerPlayer player) {
        if (!RankupFeatureEnabled) return;

        long secondsPlayed = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20;

        Rank current = null;
        for (Rank rank : RANKS) {
            if (secondsPlayed >= rank.seconds) current = rank;
            else break;
        }
        if (current == null) return;

        String lastRank = getLastRankName(player);
        if (!current.name.equals(lastRank)) {
            // Player ranked up
            runCommands(player, current.commands);
            saveLastRankName(player, current.name);
            player.sendSystemMessage(Component.literal("§aYou have ranked up to §e" + current.name + "§a!"));
        }
    }

    private static void runCommands(ServerPlayer player, List<String> commands) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        for (String cmd : commands) {
            if (cmd.contains("%player%")) cmd = cmd.replace("%player%", player.getName().getString());
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    /* ================= PLAYER RANK STORAGE ================= */
    private static String getLastRankName(ServerPlayer player) {
        try {
            Files.createDirectories(RANK_STORAGE);
            Path playerFile = RANK_STORAGE.resolve(player.getUUID() + ".json");
            if (!Files.exists(playerFile)) return "None";

            try (Reader reader = Files.newBufferedReader(playerFile)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                return obj.get("lastRank").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "None";
        }
    }

    private static void saveLastRankName(ServerPlayer player, String rankName) {
        try {
            Files.createDirectories(RANK_STORAGE);
            Path playerFile = RANK_STORAGE.resolve(player.getUUID() + ".json");

            JsonObject obj = new JsonObject();
            obj.addProperty("lastRank", rankName);

            try (Writer writer = Files.newBufferedWriter(playerFile)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ================= UTIL ================= */
    private static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    private record Rank(String name, long seconds, List<String> commands, List<String> notes) {}

    /* ================= SERVER TICK EVENT FOR AUTO-RANK ================= */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer player && !player.level().isClientSide) {
            checkRankup(player);
        }
    }
}
