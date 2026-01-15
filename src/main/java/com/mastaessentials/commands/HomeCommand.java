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

public class HomeCommand {

    // In-memory storage of player homes: UUID -> home name -> position
    private static final Map<String, Map<String, HomePosition>> homes = new HashMap<>();

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
                                            Map<String, HomePosition> playerHomes = homes.computeIfAbsent(player.getUUID().toString(), k -> new HashMap<>());

                                            if (playerHomes.size() >= maxHomes && !playerHomes.containsKey(homeName)) {
                                                player.sendSystemMessage(Component.literal("You have reached your max homes (" + maxHomes + ")."));
                                                return 0;
                                            }

                                            playerHomes.put(homeName, new HomePosition(player.getBlockX(), player.getBlockY(), player.getBlockZ()));
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
                                            Map<String, HomePosition> playerHomes = homes.get(player.getUUID().toString());

                                            if (playerHomes != null && playerHomes.containsKey(homeName)) {
                                                HomePosition pos = playerHomes.get(homeName);
                                                player.teleportTo((ServerLevel) player.getCommandSenderWorld(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
                                                player.sendSystemMessage(Component.literal("Teleported to home '" + homeName + "'!"));
                                            } else {
                                                player.sendSystemMessage(Component.literal("Home '" + homeName + "' does not exist."));
                                            }
                                            return 1;
                                        })
                                )
                        )

                        // /home list
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Map<String, HomePosition> playerHomes = homes.get(player.getUUID().toString());

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

    private static int getMaxHomes(ServerPlayer player) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUUID());
            if (user == null) return 1; // fallback

            if (user.getCachedData().getPermissionData().checkPermission("home.6").asBoolean()) return 6;
            if (user.getCachedData().getPermissionData().checkPermission("home.3").asBoolean()) return 3;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 1; // default
    }

    // Simple class to store a position
    private static class HomePosition {
        final double x, y, z;

        HomePosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
