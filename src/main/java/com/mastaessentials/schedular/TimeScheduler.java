package com.mastaessentials.schedular;

import com.mastaessentials.schedular.SchedulerConfig;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TimeScheduler
 *
 * Executes commands at scheduled times based on:
 * 1. Real-world time (system/server clock)
 * 2. Minecraft world time (ticks)
 *
 * Config example in Scheduler.toml:
 *
 * enabled = true
 * tasks_real = ["08:20|say Hello Real World", "12:00|say Noon Real World"]
 * tasks_minecraft = ["6000|say Good morning Minecraft", "18000|say Good evening Minecraft"]
 */
public class TimeScheduler {

    private String lastExecutedReal = "";
    private long lastExecutedMinecraft = -1;

    public TimeScheduler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!SchedulerConfig.enabled()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        checkRealWorldTime(server);
        checkMinecraftWorldTime(server);
    }

    // ---------------------------
    // Real-world time scheduler
    // ---------------------------
    private void checkRealWorldTime(MinecraftServer server) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime now = LocalTime.now();
        String nowStr = now.format(formatter);

        List<String> tasks = SchedulerConfig.tasksReal();
        if (tasks == null) return;

        for (String task : tasks) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            String timeStr = parts[0];
            String command = parts[1];

            // Parse task time
            LocalTime taskTime;
            try {
                taskTime = LocalTime.parse(timeStr, formatter);
            } catch (Exception e) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[Scheduler] Invalid time format in task: " + task),
                        false
                );
                continue;
            }

            // Execute if within the minute and not already executed
            if (!lastExecutedReal.equals(timeStr) &&
                    !now.isBefore(taskTime) &&
                    now.isBefore(taskTime.plusMinutes(1))) {

                executeCommand(server, command, "[Scheduler][RealTime]");
                lastExecutedReal = timeStr;
            }
        }
    }

    // ---------------------------
    // Minecraft world time scheduler
    // ---------------------------
    private void checkMinecraftWorldTime(MinecraftServer server) {
        long worldTime = server.overworld().getDayTime() % 24000; // Minecraft ticks
        List<String> tasks = SchedulerConfig.tasksMinecraft();
        if (tasks == null) return;

        for (String task : tasks) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            long targetTick;
            try {
                targetTick = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[Scheduler] Invalid Minecraft tick in task: " + task),
                        false
                );
                continue;
            }

            String command = parts[1];

            // Execute if we just reached this tick and haven't executed yet
            if (worldTime == targetTick && lastExecutedMinecraft != targetTick) {
                executeCommand(server, command, "[Scheduler][MCWorld]");
                lastExecutedMinecraft = targetTick;
            }
        }
    }

    // ---------------------------
    // Command execution helper
    // ---------------------------
    private void executeCommand(MinecraftServer server, String command, String prefix) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);

        try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(prefix + " Failed to execute command: " + command),
                    false
            );
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(prefix + " Executed: " + command),
                false
        );
    }
}
