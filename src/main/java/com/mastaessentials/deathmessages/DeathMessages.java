package com.mastaessentials.deathmessages;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber
public class DeathMessages {

    private static final Random RANDOM = new Random();

    // ========= PVP =========
    private static final List<String> PVP_WEAPONS = List.of(
            "v was erased by k's w.",
            "k emotionally ruined v using w.",
            "k speedran v's life.",
            "v lost their entire career to k."
    );

    private static final List<String> PVP_FISTS = List.of(
            "v got deleted by k's hands.",
            "k chose violence against v.",
            "v caught hands from k."
    );

    // ========= MOB =========
    private static final List<String> MOB_KILLS = List.of(
            "v was claimed by m.",
            "m ended v.",
            "v underestimated m.",
            "m introduced v to death."
    );

    // ========= ENVIRONMENT =========
    private static final List<String> FALL = List.of(
            "v challenged gravity and lost.",
            "v fell with confidence.",
            "v invented gravity testing.",
            "v lost the ground.",
            "v believed in flight too much."
    );

    private static final List<String> FIRE = List.of(
            "v developed a toxic relationship with fire.",
            "v became ambience.",
            "v burned with purpose."
    );

    private static final List<String> LAVA = List.of(
            "v tried to swim in soup.",
            "v became lava décor.",
            "v flavored the magma."
    );

    private static final List<String> EXPLODE = List.of(
            "v was violently rearranged.",
            "v became abstract.",
            "v stood too close to emotion."
    );

    private static final List<String> DROWN = List.of(
            "v forgot breathing.",
            "v tried liquid oxygen."
    );

    private static final List<String> VOID = List.of(
            "v left reality.",
            "v wandered too far."
    );

    private static final List<String> MAGIC = List.of(
            "v trusted magic incorrectly.",
            "v exploded spiritually."
    );

    private static final List<String> SUFFOCATE = List.of(
            "v hugged a wall too hard.",
            "v clipped into regret."
    );

    private static final List<String> GENERIC = List.of(
            "v experienced consequences.",
            "v made a bad choice.",
            "v lost to physics."
    );

    // ========= EVENT =========
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DamageSource source = event.getSource();
        String victim = player.getName().getString();
        String message;

        Entity attacker = source.getEntity();

        // ---- PVP ----
        if (attacker instanceof Player killer) {
            String k = killer.getName().getString();
            String weapon = killer.getMainHandItem().isEmpty()
                    ? "fists"
                    : killer.getMainHandItem().getHoverName().getString();

            List<String> pool = weapon.equals("fists") ? PVP_FISTS : PVP_WEAPONS;
            message = pick(pool)
                    .replace("v", victim)
                    .replace("k", k)
                    .replace("w", weapon);

            // ---- MOB ----
        } else if (attacker instanceof LivingEntity mob) {
            String m = mob.getName().getString();
            message = pick(MOB_KILLS)
                    .replace("v", victim)
                    .replace("m", m);

            // ---- ENVIRONMENT ----
            // FALL
        } else if (source == DamageSource.FALL) {
            message = pick(FALL).replace("v", victim);

// FIRE
        } else if (source == DamageSource.ON_FIRE || source == DamageSource.IN_FIRE) {
            message = pick(FIRE).replace("v", victim);

// LAVA
        } else if (source == DamageSource.LAVA) {
            message = pick(LAVA).replace("v", victim);

// EXPLOSION
        } else if (source.isExplosion()) { // isExplosion() still exists
            message = pick(EXPLODE).replace("v", victim);

// DROWN
        } else if (source == DamageSource.DROWN) {
            message = pick(DROWN).replace("v", victim);

// VOID
        } else if (source == DamageSource.OUT_OF_WORLD) {
            message = pick(VOID).replace("v", victim);

// MAGIC
        } else if (source == DamageSource.MAGIC) {
            message = pick(MAGIC).replace("v", victim);

// SUFFOCATE / IN_WALL
        } else if (source == DamageSource.IN_WALL) {
            message = pick(SUFFOCATE).replace("v", victim);
        }

        // Override vanilla death message
        event.setCanceled(true);
        player.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal(message), false);
    }

    private static String pick(List<String> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }
}
