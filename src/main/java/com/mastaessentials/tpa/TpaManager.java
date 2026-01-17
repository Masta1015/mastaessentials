package com.mastaessentials.tpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.arguments.EntityArgument;

import com.mojang.brigadier.CommandDispatcher;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class TpaManager {

    private static final File CONFIG_FILE = new File("config/MastaConfig/Tpa.json");
    private static TpaConfig config;

    private static final Map<UUID, TpaRequest> pendingRequests = new HashMap<>();
    private static final Map<UUID, Long> lastTpaTime = new HashMap<>();

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) {
                if (!CONFIG_FILE.getParentFile().exists()) CONFIG_FILE.getParentFile().mkdirs();
                config = new TpaConfig();
                saveConfig();
            } else {
                Type type = new TypeToken<TpaConfig>() {}.getType();
                config = gson.fromJson(new FileReader(CONFIG_FILE), type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            config = new TpaConfig();
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        loadConfig();

        dispatcher.register(
                Commands.literal("tpa")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> requestTpa(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"), false)))
        );

        dispatcher.register(
                Commands.literal("tpahere")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> requestTpa(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"), true)))
        );

        dispatcher.register(
                Commands.literal("tpaccept")
                        .executes(ctx -> acceptTpa(ctx.getSource()))
        );

        dispatcher.register(
                Commands.literal("tpadeny")
                        .executes(ctx -> denyTpa(ctx.getSource()))
        );
    }

    private static int requestTpa(CommandSourceStack source, ServerPlayer target, boolean here) {
        if (!config.enabled) {
            source.sendSuccess(() -> Component.literal(config.messageDisabled), false);
            return 0;
        }

        ServerPlayer sender;
        try {
            sender = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        UUID senderId = sender.getUUID();
        long now = System.currentTimeMillis() / 1000;

        if (lastTpaTime.containsKey(senderId)) {
            long diff = now - lastTpaTime.get(senderId);
            if (diff < config.cooldown) {
                source.sendSuccess(() -> Component.literal(
                        config.messageCooldown.replace("%time%", String.valueOf(config.cooldown - diff))
                ), false);
                return 0;
            }
        }

        lastTpaTime.put(senderId, now);
        pendingRequests.put(target.getUUID(), new TpaRequest(sender, here, now, target.getX(), target.getY(), target.getZ()));

        target.displayClientMessage(Component.literal(
                config.messageRequest
                        .replace("%player%", sender.getName().getString())
                        .replace("%here%", here ? "you to them" : "to them")
        ), false);

        sender.displayClientMessage(Component.literal(
                config.messageSent.replace("%target%", target.getName().getString())
        ), false);

        return 1;
    }

    private static int acceptTpa(CommandSourceStack source) {
        ServerPlayer receiver;
        try {
            receiver = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        TpaRequest request = pendingRequests.remove(receiver.getUUID());
        if (request == null) {
            source.sendSuccess(() -> Component.literal(config.messageNoRequest), false);
            return 0;
        }

        ServerPlayer sender = request.sender;

        // Timer for warmup + countdown
        Timer countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            int secondsLeft = config.warmup;

            @Override
            public void run() {
                // Cancel immediately if receiver moved
                if (receiver.getX() != request.startX || receiver.getY() != request.startY || receiver.getZ() != request.startZ) {
                    receiver.displayClientMessage(Component.literal("§cTeleport canceled because you moved."), false);
                    countdownTimer.cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    countdownTimer.cancel();
                    return;
                }

                receiver.displayClientMessage(Component.literal("§eTeleporting in " + secondsLeft + "s... Do not move!"), true);
                secondsLeft--;
            }
        }, 0, 1000L);

        // Perform teleport after warmup
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // Cancel if receiver moved
                if (receiver.getX() != request.startX || receiver.getY() != request.startY || receiver.getZ() != request.startZ) {
                    countdownTimer.cancel(); // stop countdown
                    return; // only notify receiver, sender stays silent
                }

                ServerLevel senderWorld = (ServerLevel) sender.getCommandSenderWorld();
                ServerLevel recvWorld  = (ServerLevel) receiver.getCommandSenderWorld();

                if (request.here) {
                    sender.teleportTo(recvWorld, receiver.getX(), receiver.getY(), receiver.getZ(), sender.getYRot(), sender.getXRot());
                } else {
                    receiver.teleportTo(senderWorld, sender.getX(), sender.getY(), sender.getZ(), receiver.getYRot(), receiver.getXRot());
                }

                sender.displayClientMessage(Component.literal(
                        config.messageAcceptSender.replace("%target%", receiver.getName().getString())
                ), false);

                receiver.displayClientMessage(Component.literal(
                        config.messageAcceptReceiver.replace("%player%", sender.getName().getString())
                ), false);
            }
        }, config.warmup * 1000L);

        return 1;
    }

    private static int denyTpa(CommandSourceStack source) {
        ServerPlayer receiver;
        try {
            receiver = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        TpaRequest request = pendingRequests.remove(receiver.getUUID());
        if (request == null) {
            source.sendSuccess(() -> Component.literal(config.messageNoRequest), false);
            return 0;
        }

        request.sender.displayClientMessage(Component.literal(
                config.messageDenySender.replace("%target%", receiver.getName().getString())
        ), false);

        receiver.displayClientMessage(Component.literal(
                config.messageDenyReceiver.replace("%player%", request.sender.getName().getString())
        ), false);

        return 1;
    }

    private static class TpaRequest {
        ServerPlayer sender;
        boolean here;
        long timestamp;
        double startX, startY, startZ;

        TpaRequest(ServerPlayer sender, boolean here, long timestamp, double startX, double startY, double startZ) {
            this.sender = sender;
            this.here = here;
            this.timestamp = timestamp;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
        }
    }

    private static class TpaConfig {
        boolean enabled = true;
        int warmup = 10;
        int cooldown = 10;

        String messageRequest = "§a%player% has requested to teleport %here%. Use /tpaccept or /tpadeny.";
        String messageSent = "§aTeleport request sent to %target%.";
        String messageAcceptSender = "§a%target% accepted your teleport request.";
        String messageAcceptReceiver = "§aYou accepted the teleport request from %player%.";
        String messageDenySender = "§c%target% denied your teleport request.";
        String messageDenyReceiver = "§cYou denied the teleport request from %player%.";
        String messageNoRequest = "§cYou have no pending TPA requests.";
        String messageDisabled = "§cTPA is currently disabled.";
        String messageCooldown = "§cYou must wait %time%s before sending another TPA.";

        TpaConfig() {}
    }
}
