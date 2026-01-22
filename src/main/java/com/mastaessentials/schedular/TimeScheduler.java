package com.mastaessentials.schedular;

import com.mastaessentials.schedular.SchedulerConfig;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeScheduler {

    private String lastExecuted = "";

    public TimeScheduler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!SchedulerConfig.enabled()) return;

        LocalTime now = LocalTime.now();
        String nowStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));

        if (nowStr.equals(lastExecuted)) return;

        for (String task : SchedulerConfig.tasks()) {
            String[] parts = task.split("\\|", 2);
            if (parts.length != 2) continue;

            String time = parts[0];
            String command = parts[1];

            if (nowStr.equals(time)) {
                executeCommand(command);
                lastExecuted = nowStr;
                break;
            }
        }
    }

    private void executeCommand(String command) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);

        try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Scheduler] Failed to execute command: " + command),
                    false
            );
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Scheduler] Executed: " + command),
                false
        );
    }
}
