package com.mastaessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeCommand {

    // Player UUID -> home name -> position
    private static final Map<UUID, Map<String, HomePosition>> homes = new HashMap<>();

    // Player UUID -> last teleport timestamp (ms)
    private static final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Warmup/cooldown in milliseconds
    private static final long WARMUP = 10_000;   // 10 seconds
    private static final long COOLDOWN = 10_000; // 10 seconds

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("home")
                        // /home set <name>
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String homeName = StringArgumentType.getString(context, "name");
                                            int maxHomes = getMaxHomes(player);
                                            Map<String, HomePosition> playerHomes = homes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

                                            if (playerHomes.size() >= maxHomes && !playerHomes.containsKey(homeName)) {
                                                player.sendSystemMessage(Component.literal("You have reached your max homes (" + maxHomes + ")."));
                                                return 0;
                                            }

                                            playerHomes.put(homeName, new HomePosition(player.getX(), player.getY(), player.getZ(), (ServerLevel) player.level()));
                                            player.sendSystemMessage(Component.literal("Home '" + homeName + "' set!"));
                                            return 1;
                                        })
                                )
                        )

                        // /home go <name>
                        .then(Commands.literal("go")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String homeName = StringArgumentType.getString(context, "name");
                                            Map<String, HomePosition> playerHomes = homes.get(player.getUUID());

                                            if (playerHomes == null || !playerHomes.containsKey(homeName)) {
                                                player.sendSystemMessage(Component.literal("Home '" + homeName + "' does not exist."));
                                                return 0;
                                            }

                                            long now = System.currentTimeMillis();
                                            Long last = lastTeleport.getOrDefault(player.getUUID(), 0L);
                                            if (now - last < COOLDOWN) {
                                                player.sendSystemMessage(Component.literal("You must wait before teleporting again."));
                                                return 0;
                                            }

                                            HomePosition pos = playerHomes.get(homeName);
                                            player.sendSystemMessage(Component.literal("Teleporting to home '" + homeName + "' in 10 seconds... Do not move."));

                                            // Schedule teleport after warmup
                                            player.getServer().execute(() -> {
                                                long start = System.currentTimeMillis();
                                                double startX = player.getX();
                                                double startY = player.getY();
                                                double startZ = player.getZ();

                                                // Simple blocking warmup (could be replaced with scheduler)
                                                try { Thread.sleep(WARMUP); } catch (InterruptedException ignored) {}

                                                // Cancel if moved
                                                if (player.distanceToSqr(startX, startY, startZ) > 0.01) {
                                                    player.sendSystemMessage(Component.literal("Teleport cancelled because you moved."));
                                                    return;
                                                }

                                                // Teleport
                                                player.teleportTo((ServerLevel) pos.level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
                                                player.sendSystemMessage(Component.literal("Teleported to home '" + homeName + "'!"));
                                                lastTeleport.put(player.getUUID(), System.currentTimeMillis());
                                            });

                                            return 1;
                                        })
                                )
                        )

                        // /home list
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Map<String, HomePosition> playerHomes = homes.get(player.getUUID());

                                    if (playerHomes == null || playerHomes.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("You have no homes set."));
                                    } else {
                                        player.sendSystemMessage(Component.literal("Your homes: " + String.join(", ", playerHomes.keySet())));
                                    }
                                    return 1;
                                })
                        )

                        // /home (show max homes)
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int maxHomes = getMaxHomes(player);
                            player.sendSystemMessage(Component.literal("You can have up to " + maxHomes + " homes."));
                            return 1;
                        })
        );
    }

    // Get max homes based on LuckPerms permissions
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
        } catch (Throwable ignored) {
            // LP not present or not loaded
        }

        return 1;
    }



    // Simple class to store a position
    private static class HomePosition {
        final double x, y, z;
        final ServerLevel level;
        final float yRot, xRot;

        HomePosition(double x, double y, double z, ServerLevel level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.yRot = 0f;
            this.xRot = 0f;
        }
    }
}