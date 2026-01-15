package com.mastaessentials.commands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class HomeData {

    private static final Map<String, BlockPos> homes = new HashMap<>();

    public static void setHome(ServerPlayer player, BlockPos pos) {
        homes.put(player.getUUID().toString(), pos);
    }

    public static boolean hasHome(ServerPlayer player) {
        return homes.containsKey(player.getUUID().toString());
    }

    public static BlockPos getHome(ServerPlayer player) {
        return homes.get(player.getUUID().toString());
    }
}
