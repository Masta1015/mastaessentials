package com.mastaessentials.deathmessages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod.EventBusSubscriber
public class DeathMessages {

    private static final Random RANDOM = new Random();
    private static Map<String, List<String>> messages;

    // Path to JSON config
    private static final Path CONFIG_PATH = Path.of("config", "MastaConfig", "DeathMessages.json");

    /**
     * Load the JSON config. Auto-creates file with default messages if missing.
     */
    public static void loadConfig() {
        try {
            File file = CONFIG_PATH.toFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs(); // Create MastaConfig folder if missing
                try (FileWriter writer = new FileWriter(file)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    gson.toJson(getDefaultMessages(), writer); // Write default messages
                }
            }
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
                messages = new Gson().fromJson(reader, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Default death messages. Used if JSON config is missing.
     * ## Note: Placeholders you can use:
     * {player} - the victim
     * {killer} - the player killing
     * {weapon} - weapon used by killer or mob
     * {mob} - mob that killed the player
     */
    private static Map<String, List<String>> getDefaultMessages() {
        Map<String, List<String>> defaults = new HashMap<>();
        defaults.put("pvpWeapons", List.of(
                "§b{player} was erased by {killer}'s {weapon}.",
                "§b{killer} emotionally ruined {player} using {weapon}.",
                "§b{killer} speedran {player}'s life.",
                "§b{player} lost their entire career to {killer}."
        ));
        defaults.put("pvpFists", List.of(
                "§b{player} got deleted by {killer}'s hands.",
                "§b{killer} chose violence against {player}.",
                "§b{player} caught hands from {killer}."
        ));
        defaults.put("mobKills", List.of(
                "§b{player} was claimed by {mob}'s {weapon}.",
                "§b{mob} ended {player} using {weapon}.",
                "§b{player} underestimated {mob}'s {weapon}.",
                "§b{mob} introduced {player} to death with {weapon}."
        ));
        defaults.put("fall", List.of(
                "§b{player} challenged gravity and lost.",
                "§b{player} fell with confidence.",
                "§b{player} invented gravity testing.",
                "§b{player} lost the ground.",
                "§b{player} believed in flight too much."
        ));
        defaults.put("fire", List.of(
                "§b{player} developed a toxic relationship with fire.",
                "§b{player} became ambience.",
                "§b{player} burned with purpose."
        ));
        defaults.put("lava", List.of(
                "§b{player} tried to swim in soup.",
                "§b{player} became lava décor.",
                "§b{player} flavored the magma."
        ));
        defaults.put("explode", List.of(
                "§b{player} was violently rearranged.",
                "§b{player} became abstract.",
                "§b{player} stood too close to emotion."
        ));
        defaults.put("drown", List.of(
                "§b{player} forgot breathing.",
                "§b{player} tried liquid oxygen."
        ));
        defaults.put("void", List.of(
                "§b{player} left reality.",
                "§b{player} wandered too far."
        ));
        defaults.put("magic", List.of(
                "§b{player} trusted magic incorrectly.",
                "§b{player} exploded spiritually."
        ));
        defaults.put("suffocate", List.of(
                "§b{player} hugged a wall too hard.",
                "§b{player} clipped into regret."
        ));
        defaults.put("generic", List.of(
                "§b{player} experienced consequences.",
                "§b{player} made a bad choice.",
                "§b{player} lost to physics."
        ));
        return defaults;
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (messages == null) loadConfig();
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DamageSource source = event.getSource();
        String victim = player.getName().getString();
        String message;

        Entity attacker = source.getEntity();

        if (attacker instanceof Player killer) {
            String k = killer.getName().getString();
            ItemStack weaponStack = killer.getMainHandItem();

            if (weaponStack.isEmpty() || weaponStack.getItem() == Items.AIR) {
                message = pick(messages.get("pvpFists"))
                        .replace("{player}", victim)
                        .replace("{killer}", k);
            } else {
                message = pick(messages.get("pvpWeapons"))
                        .replace("{player}", victim)
                        .replace("{killer}", k)
                        .replace("{weapon}", weaponStack.getHoverName().getString());
            }

        } else if (attacker instanceof LivingEntity mob) {
            String weapon = mob.getMainHandItem().isEmpty()
                    ? "claws"
                    : mob.getMainHandItem().getHoverName().getString();

            message = pick(messages.get("mobKills"))
                    .replace("{player}", victim)
                    .replace("{mob}", mob.getName().getString())
                    .replace("{weapon}", weapon);
        } else {
            message = pick(messages.getOrDefault(source.getMsgId(), messages.get("generic")))
                    .replace("{player}", victim);
        }

        // Override vanilla death message WITHOUT cancelling death
        player.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);


    }

    private static String pick(List<String> list) {
        if (list == null || list.isEmpty()) return "{player} met an untimely fate.";
        return list.get(RANDOM.nextInt(list.size()));
    }
}
