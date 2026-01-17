package com.mastaessentials.rankup;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RankCommand {

    private static final Path CONFIG_DIR = Path.of("config", "MastaConfig");
    private static final Path RANK_FILE = CONFIG_DIR.resolve("Ranks.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static boolean RankupFeatureEnabled = true; // renamed
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

            if (!Files.exists(RANK_FILE)) {
                createDefaultConfig();
            }

            try (Reader reader = Files.newBufferedReader(RANK_FILE)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                // use new property name
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
                    if (r.has("notes")) {
                        r.getAsJsonArray("notes").forEach(e -> notes.add(e.getAsString()));
                    }

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
            root.addProperty("RankupFeatureEnabled", true); // renamed in JSON

            JsonObject ranks = new JsonObject();

            ranks.add("Initiate", rankObj(0, 0,
                    List.of(),
                    List.of(
                            "• 2 /sethome",
                            "• /rtp",
                            "• /spawn",
                            "• /warp event",
                            "• /warp resourceworld",
                            "• /back",
                            "• 16 chunks"
                    )
            ));

            ranks.add("Tinkermage", rankObj(6, 0,
                    List.of("say %player% has ranked up to Tinkermage!"),
                    List.of(
                            "• 1 /sethome",
                            "• 4 chunks",
                            "• /trash",
                            "• 1 Donor Crate Key on get"
                    )
            ));

            ranks.add("Runesmith", rankObj(25, 0,
                    List.of("give %player% minecraft:diamond 2"),
                    List.of(
                            "• 1 /sethome",
                            "• 4 chunks",
                            "• 1 Donor Crate Key on get"
                    )
            ));

            ranks.add("Arcanengineer", rankObj(61, 0,
                    List.of(),
                    List.of(
                            "• 1 /pwarp create",
                            "• 1 /sethome",
                            "• 4 chunks",
                            "• /nick",
                            "• 1 Donor Crate Key on get"
                    )
            ));

            ranks.add("Mystic Mechanist", rankObj(100, 0,
                    List.of(),
                    List.of(
                            "• 1 /pwarp create",
                            "• 1 /sethome",
                            "• 4 chunks",
                            "• 1 Donor Crate Key on get"
                    )
            ));

            root.add("ranks", ranks);
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject rankObj(int hours, int minutes, List<String> commands, List<String> notes) {
        JsonObject obj = new JsonObject();
        obj.addProperty("hours", hours);
        obj.addProperty("minutes", minutes);

        JsonArray arr = new JsonArray();
        for (String c : commands) arr.add(c);
        obj.add("commands", arr);

        JsonArray notesArr = new JsonArray();
        for (String n : notes) notesArr.add(n);
        obj.add("notes", notesArr);

        return obj;
    }

    /* ================= COMMAND LOGIC ================= */
    private static void showRank(ServerPlayer player) {
        if (!RankupFeatureEnabled) {
            player.sendSystemMessage(Component.literal("§cRank system is disabled."));
            return;
        }

        long ticksPlayed = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long secondsPlayed = ticksPlayed / 20;

        Rank current = null;
        Rank next = null;

        for (Rank rank : RANKS) {
            if (secondsPlayed >= rank.seconds) {
                current = rank;
            } else {
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

        // Show notes/perks
        if (current != null && current.notes != null) {
            player.sendSystemMessage(Component.literal("§6§lPerks:"));
            for (String note : current.notes) {
                player.sendSystemMessage(Component.literal("§7" + note));
            }
        }
    }


    /* ================= UTIL ================= */
    private static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    private record Rank(String name, long seconds, List<String> commands, List<String> notes) {}
}
