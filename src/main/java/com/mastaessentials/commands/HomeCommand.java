package com.mastaessentials.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

@Mod.EventBusSubscriber
public class HomeCommand {

    // -------------------- Data --------------------
    private static final Map<UUID, Map<String, HomePosition>> homes = new HashMap<>();
    private static final Map<UUID, Long> lastTeleport = new HashMap<>();
    private static final Map<UUID, ScheduledTeleport> scheduledTeleports = new HashMap<>();
    private static final long WARMUP = 10_000;
    private static final long COOLDOWN = 10_000;
    private static final Gson GSON = new Gson();
    private static final File HOMES_FILE = new File("config/mastaessentials_homes.json");

    // -------------------- Command Registration --------------------
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // /sethome <name>
        dispatcher.register(
                Commands.literal("sethome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String inputName = StringArgumentType.getString(context, "name");
                                    String homeName = capitalize(inputName); // Capitalize first letter

                                    int maxHomes = getMaxHomes(player);
                                    Map<String, HomePosition> playerHomes = homes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

                                    if (playerHomes.size() >= maxHomes && !playerHomes.containsKey(homeName)) {
                                        player.sendSystemMessage(Component.literal("You have reached your max homes (" + maxHomes + ")."));
                                        return 0;
                                    }

                                    playerHomes.put(homeName, new HomePosition(player, homeName));
                                    saveHomes();
                                    player.sendSystemMessage(Component.literal("Home '" + homeName + "' set!"));
                                    return 1;
                                })
                        )
        );

        // /homes or /listhomes
        dispatcher.register(
                Commands.literal("homes")
                        .executes(context -> listHomes(context.getSource().getPlayerOrException()))
        );
        dispatcher.register(
                Commands.literal("listhomes")
                        .executes(context -> listHomes(context.getSource().getPlayerOrException()))
        );

        // /delhome <name>
        dispatcher.register(
                Commands.literal("delhome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) {
                                        return builder.buildFuture();
                                    }

                                    Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
                                    if (playerHomes != null) {
                                        for (String homeName : playerHomes.keySet()) {
                                            builder.suggest(homeName);
                                        }
                                    }

                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String inputName = StringArgumentType.getString(context, "name");
                                    String homeName = findHomeIgnoreCase(player, inputName);

                                    if (homeName == null) {
                                        player.sendSystemMessage(
                                                Component.literal("Home '" + inputName + "' does not exist.")
                                        );
                                        return 0;
                                    }

                                    homes.get(player.getUUID()).remove(homeName);
                                    saveHomes();

                                    player.sendSystemMessage(
                                            Component.literal("Home '" + homeName + "' deleted!")
                                    );
                                    return 1;
                                })
                        )
        );

        // /home <name>
        dispatcher.register(
                Commands.literal("home")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) {
                                        return builder.buildFuture();
                                    }

                                    Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
                                    if (playerHomes != null) {
                                        for (String homeName : playerHomes.keySet()) {
                                            builder.suggest(homeName);
                                        }
                                    }

                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String inputName = StringArgumentType.getString(context, "name");

                                    // Case-insensitive lookup
                                    String homeName = findHomeIgnoreCase(player, inputName);
                                    if (homeName == null) {
                                        player.sendSystemMessage(
                                                Component.literal("Home '" + inputName + "' does not exist.")
                                        );
                                        return 0;
                                    }

                                    HomePosition pos = homes.get(player.getUUID()).get(homeName);

                                    long now = System.currentTimeMillis();
                                    long last = lastTeleport.getOrDefault(player.getUUID(), 0L);
                                    if (now - last < COOLDOWN) {
                                        player.sendSystemMessage(
                                                Component.literal("You must wait before teleporting again.")
                                        );
                                        return 0;
                                    }

                                    player.sendSystemMessage(
                                            Component.literal("Teleporting to home '" + homeName + "' in 10 seconds... Do not move.")
                                    );

                                    scheduledTeleports.put(
                                            player.getUUID(),
                                            new ScheduledTeleport(player, pos, 200)
                                    );

                                    return 1;
                                })
                        )
        );
    }

    // -------------------- Helper Methods --------------------
    private static int listHomes(ServerPlayer player) {
        Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
        if (playerHomes == null || playerHomes.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no homes set."));
        } else {
            player.sendSystemMessage(Component.literal("Your homes: " + String.join(", ", playerHomes.keySet())));
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

    private static String findHomeIgnoreCase(ServerPlayer player, String inputName) {
        Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
        if (playerHomes == null) return null;
        for (String name : playerHomes.keySet()) {
            if (name.equalsIgnoreCase(inputName)) return name;
        }
        return null;
    }

    // -------------------- Tick & Teleport --------------------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Map.Entry<UUID, ScheduledTeleport>> iterator = scheduledTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ScheduledTeleport> entry = iterator.next();
            ScheduledTeleport st = entry.getValue();

            if (!st.player.isAlive()) {
                iterator.remove();
                continue;
            }

            if (st.player.distanceToSqr(st.startX, st.startY, st.startZ) > 0.01) {
                st.player.sendSystemMessage(Component.literal("Teleport cancelled because you moved."));
                iterator.remove();
                continue;
            }

            st.ticksLeft--;
            if (st.ticksLeft <= 0) {
                st.player.teleportTo((ServerLevel) st.pos.level, st.pos.x, st.pos.y, st.pos.z, st.pos.yRot, st.pos.xRot);
                st.player.sendSystemMessage(Component.literal("Teleported to home '" + st.pos.name + "'!"));
                lastTeleport.put(st.player.getUUID(), System.currentTimeMillis());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        saveHomes();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        loadHomes(event.getServer());
    }

    // -------------------- JSON Save/Load --------------------
    public static void saveHomes() {
        try (FileWriter writer = new FileWriter(HOMES_FILE)) {
            Map<UUID, Map<String, SerializableHome>> toSave = new HashMap<>();
            for (Map.Entry<UUID, Map<String, HomePosition>> e : homes.entrySet()) {
                Map<String, SerializableHome> map = new HashMap<>();
                for (Map.Entry<String, HomePosition> h : e.getValue().entrySet()) {
                    map.put(h.getKey(), SerializableHome.from(h.getValue()));
                }
                toSave.put(e.getKey(), map);
            }
            GSON.toJson(toSave, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadHomes(MinecraftServer server) {
        if (!HOMES_FILE.exists()) return;
        try (FileReader reader = new FileReader(HOMES_FILE)) {
            Type type = new TypeToken<Map<UUID, Map<String, SerializableHome>>>() {}.getType();
            Map<UUID, Map<String, SerializableHome>> data = GSON.fromJson(reader, type);

            for (Map.Entry<UUID, Map<String, SerializableHome>> entry : data.entrySet()) {
                UUID playerId = entry.getKey();
                Map<String, SerializableHome> shMap = entry.getValue();
                Map<String, HomePosition> loaded = new HashMap<>();

                for (Map.Entry<String, SerializableHome> shEntry : shMap.entrySet()) {
                    SerializableHome sh = shEntry.getValue();
                    ServerLevel level = getLevelByName(sh.levelName, server);
                    if (level != null) {
                        String homeName = capitalize(shEntry.getKey());
                        loaded.put(homeName, new HomePosition(sh.x, sh.y, sh.z, level, homeName));
                    }
                }
                homes.put(playerId, loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServerLevel getLevelByName(String name, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(name)) return level;
        }
        return null;
    }

    // -------------------- Classes --------------------
    private static class HomePosition {
        final double x, y, z;
        final ServerLevel level;
        final float yRot, xRot;
        final String name;

        HomePosition(ServerPlayer player, String name) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.level = (ServerLevel) player.level();
            this.yRot = player.getYRot();
            this.xRot = player.getXRot();
            this.name = name;
        }

        HomePosition(double x, double y, double z, ServerLevel level, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.yRot = 0f;
            this.xRot = 0f;
            this.name = name;
        }
    }

    private static class ScheduledTeleport {
        ServerPlayer player;
        HomePosition pos;
        double startX, startY, startZ;
        int ticksLeft;

        ScheduledTeleport(ServerPlayer player, HomePosition pos, int ticks) {
            this.player = player;
            this.pos = pos;
            this.startX = player.getX();
            this.startY = player.getY();
            this.startZ = player.getZ();
            this.ticksLeft = ticks;
        }
    }

    private static class SerializableHome {
        double x, y, z;
        String levelName;

        SerializableHome(double x, double y, double z, String levelName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.levelName = levelName;
        }

        static SerializableHome from(HomePosition pos) {
            return new SerializableHome(pos.x, pos.y, pos.z, pos.level.dimension().location().toString());
        }
    }
}