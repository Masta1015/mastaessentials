package com.mastaessentials.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import com.mojang.brigadier.context.CommandContext;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber
public class HomeCommand {

    // -------------------- Data --------------------
    private static final Map<UUID, Map<String, HomePosition>> homes = new HashMap<>();
    private static final Map<UUID, Long> lastTeleport = new HashMap<>();
    private static final Map<UUID, ScheduledTeleport> scheduledTeleports = new HashMap<>();

    private static long WARMUP = 10_000;
    private static long COOLDOWN = 10_000;
    private static boolean HOMES_FEATURE_ENABLED = true;
    private static Map<String, String> MESSAGES = new HashMap<>();

    private static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().create();
    private static final Path HOMES_FILE = FMLPaths.CONFIGDIR
            .get()
            .resolve("MastaConfig")
            .resolve("HomeStorage") // folder
            .resolve("HomeStorage.json"); // file
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("HomeConfig.json");

    // -------------------- Command Registration --------------------
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // SET HOME
        dispatcher.register(
                Commands.literal("sethome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String homeName = capitalize(StringArgumentType.getString(ctx, "name"));
                                    int maxHomes = getMaxHomes(player);
                                    Map<String, HomePosition> playerHomes = homes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

                                    if (playerHomes.size() >= maxHomes && !playerHomes.containsKey(homeName)) {
                                        player.sendSystemMessage(Component.literal(
                                                MESSAGES.getOrDefault("maxhomes", "You have reached your max homes (%max%).")
                                                        .replace("%max%", String.valueOf(maxHomes))
                                        ));
                                        return 0;
                                    }

                                    playerHomes.put(homeName, new HomePosition(player, homeName));
                                    saveHomes(player);
                                    player.sendSystemMessage(Component.literal(
                                            MESSAGES.getOrDefault("set", "Home '%home%' set!").replace("%home%", homeName)
                                    ));
                                    return 1;
                                })
                        )
        );

        // LIST HOMES
        dispatcher.register(
                Commands.literal("homes")
                        .executes(ctx -> listHomes(ctx.getSource().getPlayerOrException()))
        );

        // DELETE HOME
        dispatcher.register(
                Commands.literal("delhome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(HomeCommand::suggestHomes)
                                .executes(ctx -> deleteHome(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))
                        )
        );

        // TELEPORT HOME
        dispatcher.register(
                Commands.literal("home")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(HomeCommand::suggestHomes)
                                .executes(ctx -> teleportHome(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))
                        )
        );

        // LIST HOMES ALIAS
        dispatcher.register(
                Commands.literal("listhomes")
                        .executes(ctx -> listHomes(ctx.getSource().getPlayerOrException()))
        );
    }

    // -------------------- Helpers --------------------
    private static int listHomes(ServerPlayer player) {
        Map<String, HomePosition> map = homes.get(player.getUUID());
        if (map == null || map.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    MESSAGES.getOrDefault("nohomes", "You have no homes set.")
            ));
        } else {
            player.sendSystemMessage(Component.literal("Your homes: " + String.join(", ", map.keySet())));
        }
        return 1;
    }

    private static int getMaxHomes(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                var perms = user.getCachedData().getPermissionData();
                if (perms.checkPermission("mastahomes.5").asBoolean()) return 5;
                if (perms.checkPermission("mastahomes.4").asBoolean()) return 4;
                if (perms.checkPermission("mastahomes.3").asBoolean()) return 3;
                if (perms.checkPermission("mastahomes.2").asBoolean()) return 2;
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String findHomeIgnoreCase(ServerPlayer player, String input) {
        Map<String, HomePosition> map = homes.get(player.getUUID());
        if (map == null) return null;
        for (String name : map.keySet()) {
            if (name.equalsIgnoreCase(input)) return name;
        }
        return null;
    }

    // -------------------- Teleport --------------------
    private static int teleportHome(ServerPlayer player, String input) {
        String homeName = findHomeIgnoreCase(player, input);
        if (homeName == null) {
            player.sendSystemMessage(Component.literal(
                    MESSAGES.getOrDefault("notexist", "Home '%home%' does not exist!").replace("%home%", input)
            ));
            return 0;
        }

        HomePosition pos = homes.get(player.getUUID()).get(homeName);

        if (!HOMES_FEATURE_ENABLED) {
            player.sendSystemMessage(Component.literal(MESSAGES.getOrDefault("disabled", "Homes are currently disabled.")));
            return 0;
        }

        long now = System.currentTimeMillis();
        long last = lastTeleport.getOrDefault(player.getUUID(), 0L);
        if (now - last < COOLDOWN) {
            player.sendSystemMessage(Component.literal(MESSAGES.getOrDefault("cooldown", "You must wait before teleporting again.")));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                MESSAGES.getOrDefault("teleporting", "Teleporting to home '%home%' in %time% seconds...")
                        .replace("%home%", homeName)
                        .replace("%time%", String.valueOf(WARMUP / 1000))
        ));

        scheduledTeleports.put(player.getUUID(), new ScheduledTeleport(player, pos, (int)(WARMUP / 50)));
        return 1;
    }

    private static int deleteHome(ServerPlayer player, String input) {
        String homeName = findHomeIgnoreCase(player, input);
        if (homeName == null) {
            player.sendSystemMessage(Component.literal(
                    MESSAGES.getOrDefault("notexist", "Home '%home%' does not exist!").replace("%home%", input)
            ));
            return 0;
        }

        homes.get(player.getUUID()).remove(homeName);
        saveHomes(player);
        player.sendSystemMessage(Component.literal(
                MESSAGES.getOrDefault("deleted", "Home '%home%' deleted!").replace("%home%", homeName)
        ));
        return 1;
    }

    // -------------------- Tab Completion --------------------
    private static CompletableFuture<Suggestions> suggestHomes(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }

        Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
        if (playerHomes == null) return builder.buildFuture();

        String remaining = builder.getRemainingLowerCase();

        for (String name : playerHomes.keySet()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }

    // -------------------- Events --------------------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<ScheduledTeleport> it = scheduledTeleports.values().iterator();
        while (it.hasNext()) {
            ScheduledTeleport st = it.next();
            if (st.player.distanceToSqr(st.startX, st.startY, st.startZ) > 0.01) {
                st.player.sendSystemMessage(Component.literal(MESSAGES.getOrDefault("moved", "Teleport cancelled because you moved.")));
                it.remove();
                continue;
            }
            if (--st.ticksLeft <= 0) {
                st.player.teleportTo(st.pos.level, st.pos.x, st.pos.y, st.pos.z, st.pos.yRot, st.pos.xRot);
                lastTeleport.put(st.player.getUUID(), System.currentTimeMillis());
                it.remove();
            }
        }
    }
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        saveHomes(null);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        loadConfig();
        loadHomes(e.getServer());
    }

    // -------------------- JSON --------------------
    private static void ensureDir(Path file) {
        try { Files.createDirectories(file.getParent()); } catch (IOException ignored) {}
    }

    public static void saveHomes(ServerPlayer player) {
        ensureDir(HOMES_FILE);
        try (Writer w = Files.newBufferedWriter(HOMES_FILE)) {
            Map<UUID, List<SerializableHome>> out = new HashMap<>();
            for (var entry : homes.entrySet()) {
                UUID uuid = entry.getKey();
                String playerName = player != null ? player.getName().getString() : "Unknown";
                List<SerializableHome> homesList = new ArrayList<>();
                for (var home : entry.getValue().values()) {
                    homesList.add(SerializableHome.from(home, playerName));
                }
                out.put(uuid, homesList);
            }
            GSON.toJson(out, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void loadHomes(MinecraftServer server) {
        ensureDir(HOMES_FILE);
        if (!Files.exists(HOMES_FILE)) return;

        try (Reader r = Files.newBufferedReader(HOMES_FILE)) {
            Type type = new TypeToken<Map<UUID, List<SerializableHome>>>(){}.getType();
            Map<UUID, List<SerializableHome>> data = GSON.fromJson(r, type);
            homes.clear();

            for (var entry : data.entrySet()) {
                Map<String, HomePosition> playerHomes = new HashMap<>();
                for (SerializableHome sh : entry.getValue()) {
                    ServerLevel level = getLevelByName(sh.level, server);
                    if (level != null) {
                        playerHomes.put(sh.homeName, new HomePosition(
                                sh.x, sh.y, sh.z, level, 0f, 0f, sh.homeName
                        ));

                    }
                }
                homes.put(entry.getKey(), playerHomes);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private static ServerLevel getLevelByName(String name, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(name)) return level;
        }
        return null;
    }

    // -------------------- Inner Classes --------------------
    private static class HomePosition {
        double x, y, z;
        ServerLevel level;
        float yRot, xRot;
        String name;

        HomePosition(ServerPlayer p, String name) {
            this(p.getX(), p.getY(), p.getZ(), (ServerLevel)p.level(), p.getYRot(), p.getXRot(), name);
        }

        HomePosition(double x, double y, double z, ServerLevel level, float yRot, float xRot, String name) {
            this.x=x; this.y=y; this.z=z; this.level=level; this.yRot=yRot; this.xRot=xRot; this.name=name;
        }
    }

    private static class ScheduledTeleport {
        ServerPlayer player;
        HomePosition pos;
        double startX, startY, startZ;
        int ticksLeft;

        ScheduledTeleport(ServerPlayer p, HomePosition pos, int ticks) {
            this.player = p;
            this.pos = pos;
            this.ticksLeft = ticks;
            this.startX = p.getX(); this.startY = p.getY(); this.startZ = p.getZ();
        }
    }

    private static class SerializableHome {
        String playerName;
        String homeName;
        double x, y, z;
        String level;

        SerializableHome(String playerName, String homeName, double x, double y, double z, String level) {
            this.playerName = playerName;
            this.homeName = homeName;
            this.x = x; this.y = y; this.z = z; this.level = level;
        }

        static SerializableHome from(HomePosition p, String playerName) {
            return new SerializableHome(playerName, p.name, p.x, p.y, p.z, p.level.dimension().location().toString());
        }
    }

    // -------------------- Config --------------------
    public static void loadConfig() {
        ensureDir(CONFIG_FILE);
        if (!Files.exists(CONFIG_FILE)) saveDefaultConfig();

        try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
            Map<String, Object> cfg = GSON.fromJson(r, Map.class);
            HOMES_FEATURE_ENABLED = (Boolean) cfg.getOrDefault("enabled", true);
            WARMUP = (long)(((Double) cfg.getOrDefault("warmup", 10.0)) * 1000);
            COOLDOWN = (long)(((Double) cfg.getOrDefault("cooldown", 10.0)) * 1000);

            Map<String, String> msgs = (Map<String, String>) cfg.get("messages");
            if (msgs != null) MESSAGES = msgs;

        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void saveDefaultConfig() {
        ensureDir(CONFIG_FILE);
        try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("enabled", true);
            cfg.put("warmup", 10);
            cfg.put("cooldown", 10);

            Map<String, String> messages = new LinkedHashMap<>();
            messages.put("teleporting", "§aTeleporting to home '%home%' in %time% seconds...");
            messages.put("moved", "§cTeleport cancelled because you moved.");
            messages.put("maxhomes", "§7You have reached your max homes (%max%).");
            messages.put("deleted", "§7Home '%home%' deleted!");
            messages.put("set", "§7Home '%home%' set!");
            messages.put("notexist", "§cHome '%home%' does not exist!");
            messages.put("cooldown", "§cYou must wait before teleporting again.");
            messages.put("disabled", "§cHomes are currently disabled.");
            messages.put("nohomes", "§cYou have no homes set.");
            cfg.put("messages", messages);

            GSON.toJson(cfg, w);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
