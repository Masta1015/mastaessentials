package com.mastaessentials.pwarp;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Mod.EventBusSubscriber
public class WarpCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, TeleportWarmup> ACTIVE_WARMUPS = new ConcurrentHashMap<>();

    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("WarpConfig.toml");
    private static final Path STORAGE_FILE = FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("PwarpStorage").resolve("PlayerWarps.json");

    private static boolean ENABLE_WARPS = true;
    private static int TELEPORT_DELAY = 5; // seconds
    private static int TELEPORT_COOLDOWN = 5; // seconds

    private static final String PWARP_NODE = "mastaessentials.pwarp";

    // =========================
    // REGISTER
    // =========================
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(Commands.literal("pwarp")
                .then(Commands.literal("list")
                        .executes(c -> { list(c.getSource()); return 1; }))

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    create(c.getSource(), StringArgumentType.getString(c, "name"));
                                    return 1;
                                })))

                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, c.getSource().getPlayer()))
                                .executes(c -> {
                                    teleport(c.getSource(), StringArgumentType.getString(c, "name"));
                                    return 1;
                                })))

                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, c.getSource().getPlayer()))
                                .executes(c -> {
                                    remove(c.getSource(), StringArgumentType.getString(c, "name"));
                                    return 1;
                                })))

                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            loadConfig();
                            c.getSource().sendSuccess(() -> Component.literal("§aWarp config reloaded!"), true);
                            return 1;
                        }))

                .then(Commands.literal("give")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(c -> {
                                            give(c.getSource(),
                                                    StringArgumentType.getString(c, "player"),
                                                    IntegerArgumentType.getInteger(c, "amount"));
                                            return 1;
                                        })))));
    }

    // =========================
    // CREATE
    // =========================
    private static void create(CommandSourceStack src, String name) {
        try {
            if (!ENABLE_WARPS) return;

            ServerPlayer p = src.getPlayerOrException();
            JsonObject warps = loadWarps();

            int limit = getWarpLimit(p);

            long owned = warps.entrySet().stream()
                    .filter(e -> e.getValue().getAsJsonObject()
                            .get("owner").getAsString().equals(p.getName().getString()))
                    .count();

            if (owned >= limit) {
                p.sendSystemMessage(Component.literal("§cWarp limit reached."));
                return;
            }

            JsonObject w = new JsonObject();
            w.addProperty("x", p.getX());
            w.addProperty("y", p.getY());
            w.addProperty("z", p.getZ());
            w.addProperty("yaw", p.getYRot());
            w.addProperty("pitch", p.getXRot());
            w.addProperty("dim", p.level().dimension().location().toString());
            w.addProperty("owner", p.getName().getString());

            warps.add(name.toLowerCase(), w);
            saveWarps(warps);

            p.sendSystemMessage(Component.literal("§aWarp created."));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // LIST WARPS
    // =========================
    private static void list(CommandSourceStack src) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            JsonObject warps = loadWarps();

            if (warps.size() == 0) {
                p.sendSystemMessage(Component.literal("§7No player warps."));
                return;
            }

            p.sendSystemMessage(Component.literal("§ePlayer Warps:"));
            warps.keySet().forEach(k -> {
                String owner = warps.getAsJsonObject(k).get("owner").getAsString();
                p.sendSystemMessage(Component.literal(" §8- §f" + k + " §7(" + owner + ")"));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // REMOVE WARP
    // =========================
    private static void remove(CommandSourceStack src, String name) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            JsonObject warps = loadWarps();
            String key = name.toLowerCase();

            if (!warps.has(key)) {
                p.sendSystemMessage(Component.literal("§cWarp not found."));
                return;
            }

            String owner = warps.getAsJsonObject(key).get("owner").getAsString();
            if (!owner.equals(p.getName().getString()) && !p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal("§cYou cannot remove this warp."));
                return;
            }

            warps.remove(key);
            saveWarps(warps);
            p.sendSystemMessage(Component.literal("§aWarp removed."));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // GIVE LIMIT (ADMIN)
    // =========================
    private static void give(CommandSourceStack src, String player, int amount) {
        try {
            JsonObject limits = loadLimits();
            int current = limits.has(player) ? limits.get(player).getAsInt() : 1;
            limits.addProperty(player, current + amount);
            saveLimits(limits);

            src.sendSuccess(() -> Component.literal("§aUpdated warp limit for " + player), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // TELEPORT
    // =========================
    private static void teleport(CommandSourceStack src, String name) {
        try {
            if (!ENABLE_WARPS) return;

            ServerPlayer p = src.getPlayerOrException();
            JsonObject warps = loadWarps();
            String key = name.toLowerCase();

            if (!warps.has(key)) {
                p.sendSystemMessage(Component.literal("§cWarp not found."));
                return;
            }

            if (ACTIVE_WARMUPS.containsKey(p.getUUID())) {
                p.sendSystemMessage(Component.literal("§cTeleport already in progress."));
                return;
            }

            p.sendSystemMessage(Component.literal("§eTeleporting in " + TELEPORT_DELAY + " seconds..."));
            ACTIVE_WARMUPS.put(p.getUUID(), new TeleportWarmup(p, warps.getAsJsonObject(key), TELEPORT_DELAY * 20));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        ACTIVE_WARMUPS.values().removeIf(w -> {
            ServerPlayer p = w.player;

            double dx = p.getX() - w.startX;
            double dy = p.getY() - w.startY;
            double dz = p.getZ() - w.startZ;

            if (dx*dx + dy*dy + dz*dz > 0.01) {
                p.sendSystemMessage(Component.literal("§cTeleport cancelled (moved)."));
                return true;
            }

            if (--w.ticksRemaining <= 0) {
                teleportTo(p, w.warp);
                return true;
            }
            return false;
        });
    }

    private static void teleportTo(ServerPlayer p, JsonObject w) {
        ServerLevel level = p.server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(w.get("dim").getAsString())
        ));
        if (level == null) return;

        double x = w.get("x").getAsDouble();
        double y = w.get("y").getAsDouble();
        double z = w.get("z").getAsDouble();
        float yaw = w.get("yaw").getAsFloat();
        float pitch = w.get("pitch").getAsFloat();

        level.getChunkAt(new BlockPos((int)x,(int)y,(int)z));

        if (p.level() != level) {
            p.changeDimension(level, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel cur, ServerLevel dest, float yRot, Function<Boolean, Entity> reposition) {
                    entity.moveTo(x, y, z, yaw, pitch);
                    return entity;
                }
            });
        } else {
            p.teleportTo(level, x, y, z, yaw, pitch);
        }
    }

    // =========================
    // UTIL
    // =========================
    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder b, ServerPlayer player) {
        try {
            loadWarps().keySet().forEach(b::suggest);
        } catch (Exception ignored) {}
        return b.buildFuture();
    }

    private static JsonObject loadWarps() throws Exception {
        File f = STORAGE_FILE.toFile();
        if (!f.exists()) return new JsonObject();
        return GSON.fromJson(new FileReader(f), JsonObject.class);
    }

    private static void saveWarps(JsonObject obj) throws Exception {
        File f = STORAGE_FILE.toFile();
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(obj, w);
        }
    }

    private static JsonObject loadLimits() throws Exception {
        File f = new File(FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("PlayerWarpLimits.json").toString());
        if (!f.exists()) return new JsonObject();
        return GSON.fromJson(new FileReader(f), JsonObject.class);
    }

    private static void saveLimits(JsonObject obj) throws Exception {
        File f = new File(FMLPaths.CONFIGDIR.get().resolve("MastaConfig").resolve("PlayerWarpLimits.json").toString());
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(obj, w);
        }
    }

    // =========================
    // CONFIG
    // =========================
    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                saveDefaultConfig(); // <- this creates the file
            }

            List<String> lines = Files.readAllLines(CONFIG_FILE);
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;

                if (line.startsWith("enabled")) ENABLE_WARPS = Boolean.parseBoolean(line.split("=")[1].trim());
                if (line.startsWith("teleport_delay")) TELEPORT_DELAY = Integer.parseInt(line.split("=")[1].trim());
                if (line.startsWith("teleport_cooldown")) TELEPORT_COOLDOWN = Integer.parseInt(line.split("=")[1].trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void saveDefaultConfig() {
        try {
            // Ensure parent directories exist
            Files.createDirectories(CONFIG_FILE.getParent());

            // Write default TOML
            List<String> lines = new ArrayList<>();
            lines.add("# WarpConfig.toml - MastaEssentials");
            lines.add("# Enable or disable pwarps globally");
            lines.add("enabled = true");
            lines.add("# Warp teleport delay in seconds");
            lines.add("teleport_delay = 5");
            lines.add("# Cooldown between warps in seconds");
            lines.add("teleport_cooldown = 5");

            Files.write(CONFIG_FILE, lines);
            System.out.println("[MastaEssentials] Created default WarpConfig.toml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // =========================
    // WARP LIMIT via LuckPerms
    // =========================
    private static int getWarpLimit(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                var perms = user.getCachedData().getPermissionData();
                for (int i = 10; i >= 1; i--) { // check highest first
                    if (perms.checkPermission(PWARP_NODE + "." + i).asBoolean()) {
                        return i;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    // =========================
    // TELEPORT WARMUP
    // =========================
    private static class TeleportWarmup {
        final ServerPlayer player;
        final JsonObject warp;
        final double startX, startY, startZ;
        int ticksRemaining;

        TeleportWarmup(ServerPlayer p, JsonObject w, int ticks) {
            player = p;
            warp = w;
            startX = p.getX();
            startY = p.getY();
            startZ = p.getZ();
            ticksRemaining = ticks;
        }
    }
}
