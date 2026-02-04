package com.mastaessentials.schedular;

import com.mastaessentials.schedular.SchedulerConfig;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeScheduler {

    private String lastMinecraftTask = "";
    private LocalTime lastRealTimeTask = null;

    public TimeScheduler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        handleMinecraftTasks(server);
        handleRealWorldTasks(server);
    }

    private void handleMinecraftTasks(MinecraftServer server) {
        if (!SchedulerConfig.enabled()) return;

        for (String task : SchedulerConfig.tasksMinecraft()) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            String tickStr = parts[0];
            String command = parts[1];

            try {
                int tick = Integer.parseInt(tickStr);
                int worldTime = (int) (server.overworld().getDayTime() % 24000);

                if (worldTime == tick && !lastMinecraftTask.equals(task)) {
                    executeCommand(server, command);
                    lastMinecraftTask = task;
                }
            } catch (NumberFormatException e) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[Scheduler] Invalid Minecraft tick: " + tickStr),
                        false
                );
            }
        }
    }

    private void handleRealWorldTasks(MinecraftServer server) {
        if (!SchedulerConfig.enabled()) return;

        // Use CST explicitly
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Chicago"));

        for (String task : SchedulerConfig.tasksReal()) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            String timeStr = parts[0]; // "HH:mm"
            String command = parts[1];

            try {
                LocalTime taskTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));

                // Execute if it's this minute and hasn't executed yet
                if ((lastRealTimeTask == null || !lastRealTimeTask.equals(taskTime))
                        && now.getHour() == taskTime.getHour()
                        && now.getMinute() == taskTime.getMinute()) {
                    executeCommand(server, command);
                    lastRealTimeTask = taskTime;
                }
            } catch (Exception e) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[Scheduler] Invalid real-world time: " + timeStr),
                        false
                );
            }
        }
    }

    private void executeCommand(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);

        try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            e.printStackTrace(); // optional: prints error to console
            // removed broadcast of failure message
        }

        // removed broadcast of success message
    }
}
