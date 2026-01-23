package com.mastaessentials.schedular;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeScheduler {

    private static MinecraftServer server;
    private String lastExecuted = "";

    /** Called once from ServerStartingEvent */
    public static void setServer(MinecraftServer srv) {
        server = srv;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (server == null) return;
        if (!SchedulerConfig.enabled()) return;

        // SERVER LOCAL TIME (not world time)
        String nowStr = LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        // Prevent double execution within same minute
        if (nowStr.equals(lastExecuted)) return;

        for (String task : SchedulerConfig.tasks()) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            String time = parts[0].trim();
            String command = parts[1].trim();

            if (nowStr.equals(time)) {
                executeCommand(command);
                lastExecuted = nowStr;
                break;
            }
        }
    }

    private void executeCommand(String command) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4);

        try {
            server.getCommands().performPrefixedCommand(source, command);
        } catch (Exception e) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§c[Scheduler] Failed: " + command),
                    false
            );
            e.printStackTrace();
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§a[Scheduler] Executed: " + command),
                false
        );
    }
}
