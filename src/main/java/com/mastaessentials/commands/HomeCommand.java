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
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Path HOMES_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("MastaConfig")
            .resolve("HomeStorage.json");

    // -------------------- Command Registration --------------------
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("sethome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String homeName = capitalize(
                                            StringArgumentType.getString(context, "name")
                                    );

                                    int maxHomes = getMaxHomes(player);
                                    Map<String, HomePosition> playerHomes =
                                            homes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

                                    if (playerHomes.size() >= maxHomes && !playerHomes.containsKey(homeName)) {
                                        player.sendSystemMessage(Component.literal(
                                                "You have reached your max homes (" + maxHomes + ")."
                                        ));
                                        return 0;
                                    }

                                    playerHomes.put(homeName, new HomePosition(player, homeName));
                                    saveHomes();
                                    player.sendSystemMessage(Component.literal("Home '" + homeName + "' set!"));
                                    return 1;
                                })
                        )
        );

        dispatcher.register(
                Commands.literal("homes")
                        .executes(ctx -> listHomes(ctx.getSource().getPlayerOrException()))
        );

        dispatcher.register(
                Commands.literal("delhome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null && homes.containsKey(p.getUUID())) {
                                        homes.get(p.getUUID()).keySet().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String input = StringArgumentType.getString(context, "name");
                                    String homeName = findHomeIgnoreCase(player, input);

                                    if (homeName == null) {
                                        player.sendSystemMessage(Component.literal(
                                                "Home '" + input + "' does not exist."
                                        ));
                                        return 0;
                                    }

                                    homes.get(player.getUUID()).remove(homeName);
                                    saveHomes();
                                    player.sendSystemMessage(Component.literal(
                                            "Home '" + homeName + "' deleted!"
                                    ));
                                    return 1;
                                })
                        )
        );

        dispatcher.register(
                Commands.literal("home")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    if (p != null && homes.containsKey(p.getUUID())) {
                                        homes.get(p.getUUID()).keySet().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String input = StringArgumentType.getString(context, "name");
                                    String homeName = findHomeIgnoreCase(player, input);

                                    if (homeName == null) {
                                        player.sendSystemMessage(Component.literal(
                                                "Home '" + input + "' does not exist."
                                        ));
                                        return 0;
                                    }

                                    HomePosition pos = homes.get(player.getUUID()).get(homeName);

                                    long now = System.currentTimeMillis();
                                    long last = lastTeleport.getOrDefault(player.getUUID(), 0L);
                                    if (now - last < COOLDOWN) {
                                        player.sendSystemMessage(Component.literal(
                                                "You must wait before teleporting again."
                                        ));
                                        return 0;
                                    }

                                    player.sendSystemMessage(Component.literal(
                                            "Teleporting to home '" + homeName + "' in 10 seconds..."
                                    ));

                                    scheduledTeleports.put(
                                            player.getUUID(),
                                            new ScheduledTeleport(player, pos, 200)
                                    );
                                    return 1;
                                })
                        )
        );
        dispatcher.register(
                Commands.literal("listhomes")
                        .executes(context ->
                                listHomes(context.getSource().getPlayerOrException())
                        )
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        Map<String, HomePosition> playerHomes = homes.get(player.getUUID());
                                        if (playerHomes != null) {
                                            playerHomes.keySet().forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                // Argument is ignored, still just lists homes
                                .executes(context ->
                                        listHomes(context.getSource().getPlayerOrException())
                                )
                        )
        );
    }

    // -------------------- Helpers --------------------
    private static int listHomes(ServerPlayer player) {
        Map<String, HomePosition> map = homes.get(player.getUUID());
        if (map == null || map.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no homes set."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Your homes: " + String.join(", ", map.keySet())
            ));
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
        return s.substring(0,1).toUpperCase(Locale.ROOT)
                + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String findHomeIgnoreCase(ServerPlayer player, String input) {
        Map<String, HomePosition> map = homes.get(player.getUUID());
        if (map == null) return null;
        for (String name : map.keySet()) {
            if (name.equalsIgnoreCase(input)) return name;
        }
        return null;
    }

    // -------------------- Events --------------------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<ScheduledTeleport> it = scheduledTeleports.values().iterator();
        while (it.hasNext()) {
            ScheduledTeleport st = it.next();

            if (st.player.distanceToSqr(st.startX, st.startY, st.startZ) > 0.01) {
                st.player.sendSystemMessage(Component.literal(
                        "Teleport cancelled because you moved."
                ));
                it.remove();
                continue;
            }

            if (--st.ticksLeft <= 0) {
                st.player.teleportTo(
                        st.pos.level, st.pos.x, st.pos.y, st.pos.z,
                        st.pos.yRot, st.pos.xRot
                );
                lastTeleport.put(st.player.getUUID(), System.currentTimeMillis());
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        saveHomes();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        loadHomes(e.getServer());
    }

    // -------------------- JSON --------------------
    private static void ensureHomeDir() {
        try {
            Files.createDirectories(HOMES_FILE.getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveHomes() {
        ensureHomeDir();
        try (Writer w = Files.newBufferedWriter(HOMES_FILE)) {
            Map<UUID, Map<String, SerializableHome>> out = new HashMap<>();
            for (var e : homes.entrySet()) {
                Map<String, SerializableHome> map = new HashMap<>();
                e.getValue().forEach((k,v) -> map.put(k, SerializableHome.from(v)));
                out.put(e.getKey(), map);
            }
            GSON.toJson(out, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadHomes(MinecraftServer server) {
        ensureHomeDir();
        if (!Files.exists(HOMES_FILE)) return;

        try (Reader r = Files.newBufferedReader(HOMES_FILE)) {
            Type type = new TypeToken<Map<UUID, Map<String, SerializableHome>>>(){}.getType();
            Map<UUID, Map<String, SerializableHome>> data = GSON.fromJson(r, type);
            homes.clear();

            for (var e : data.entrySet()) {
                Map<String, HomePosition> loaded = new HashMap<>();
                for (var h : e.getValue().entrySet()) {
                    ServerLevel level = getLevelByName(h.getValue().levelName, server);
                    if (level != null) {
                        String name = capitalize(h.getKey());
                        loaded.put(name, new HomePosition(
                                h.getValue().x, h.getValue().y, h.getValue().z, level, name
                        ));
                    }
                }
                homes.put(e.getKey(), loaded);
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

    // -------------------- Inner Classes --------------------
    private static class HomePosition {
        double x,y,z;
        ServerLevel level;
        float yRot,xRot;
        String name;

        HomePosition(ServerPlayer p, String name) {
            this(p.getX(), p.getY(), p.getZ(), (ServerLevel)p.level(), name);
            this.yRot = p.getYRot();
            this.xRot = p.getXRot();
        }

        HomePosition(double x, double y, double z, ServerLevel level, String name) {
            this.x=x; this.y=y; this.z=z;
            this.level=level; this.name=name;
        }
    }

    private static class ScheduledTeleport {
        ServerPlayer player;
        HomePosition pos;
        double startX,startY,startZ;
        int ticksLeft;

        ScheduledTeleport(ServerPlayer p, HomePosition pos, int ticks) {
            this.player=p; this.pos=pos; this.ticksLeft=ticks;
            this.startX=p.getX(); this.startY=p.getY(); this.startZ=p.getZ();
        }
    }

    private static class SerializableHome {
        double x,y,z;
        String levelName;

        SerializableHome(double x,double y,double z,String lvl) {
            this.x=x; this.y=y; this.z=z; this.levelName=lvl;
        }

        static SerializableHome from(HomePosition p) {
            return new SerializableHome(
                    p.x,p.y,p.z,
                    p.level.dimension().location().toString()
            );
        }
    }
}
