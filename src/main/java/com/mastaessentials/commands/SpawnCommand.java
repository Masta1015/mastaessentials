package com.mastaessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class SpawnCommand {

    /* ============================
       Config
       ============================ */

    private static final int WARMUP_TICKS = 20 * 5;     // 5 seconds
    private static final int COOLDOWN_TICKS = 20 * 30; // 30 seconds

    /* ============================
       State
       ============================ */

    private static final Map<UUID, WarmupData> WARMUPS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

    private static float spawnYaw = 0.0F;
    private static float spawnPitch = 0.0F;

    /* ============================
       Command Registration
       ============================ */

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Remove existing /spawn command (FTB Essentials, etc.)
        CommandNode<CommandSourceStack> existingSpawn = dispatcher.getRoot().getChild("spawn");
        if (existingSpawn != null) {
            dispatcher.getRoot().getChildren().remove(existingSpawn);
        }

        // /setspawn
        dispatcher.register(
                Commands.literal("setspawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();

                            level.setDefaultSpawnPos(player.blockPosition(), player.getYRot());

                            spawnYaw = player.getYRot();
                            spawnPitch = player.getXRot();

                            player.sendSystemMessage(
                                    Component.literal("§aSpawn set with your facing direction.")
                            );
                            return 1;
                        })
        );

        // /spawn
        dispatcher.register(
                Commands.literal("spawn")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                            // OPs teleport instantly
                            if (player.hasPermissions(2)) {
                                teleportToSpawn(player);
                                return 1;
                            }

                            // Cooldown check
                            int cooldown = COOLDOWNS.getOrDefault(player.getUUID(), 0);
                            if (cooldown > 0) {
                                player.sendSystemMessage(
                                        Component.literal(
                                                "§cYou must wait " + (cooldown / 20) +
                                                        " seconds before using /spawn again."
                                        )
                                );
                                return 0;
                            }

                            // Already warming up
                            if (WARMUPS.containsKey(player.getUUID())) {
                                player.sendSystemMessage(
                                        Component.literal("§cYou are already teleporting.")
                                );
                                return 0;
                            }

                            // Start warmup
                            WARMUPS.put(
                                    player.getUUID(),
                                    new WarmupData(player, player.blockPosition())
                            );

                            player.sendSystemMessage(
                                    Component.literal("§eTeleporting to spawn in 5 seconds... Do not move.")
                            );
                            return 1;
                        })
        );
    }

    /* ============================
       Server Tick Handling
       ============================ */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Handle warmups
        Iterator<Map.Entry<UUID, WarmupData>> iterator = WARMUPS.entrySet().iterator();
        while (iterator.hasNext()) {
            WarmupData data = iterator.next().getValue();
            ServerPlayer player = data.player;

            // Cancel if player moved
            if (!player.blockPosition().equals(data.startPos)) {
                player.sendSystemMessage(
                        Component.literal("§cTeleport cancelled (you moved).")
                );
                iterator.remove();
                continue;
            }

            data.ticks++;

            if (data.ticks >= WARMUP_TICKS) {
                teleportToSpawn(player);
                COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
                iterator.remove();
            }
        }

        // Tick down cooldowns
        COOLDOWNS.replaceAll((uuid, ticks) -> ticks > 0 ? ticks - 1 : 0);
    }

    /* ============================
       Teleport Logic
       ============================ */

    private static void teleportToSpawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = level.getSharedSpawnPos();

        player.teleportTo(
                level,
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                spawnYaw,
                spawnPitch
        );

        player.sendSystemMessage(
                Component.literal("§aTeleported to spawn.")
        );
    }

    /* ============================
       Warmup Data
       ============================ */

    private static class WarmupData {
        final ServerPlayer player;
        final BlockPos startPos;
        int ticks;

        WarmupData(ServerPlayer player, BlockPos startPos) {
            this.player = player;
            this.startPos = startPos;
            this.ticks = 0;
        }
    }
}
