package com.mastaessentials.schedular;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SchedulerConfig handles reading/writing scheduled tasks from a TOML file.
 * Supports both real-world time and Minecraft world time tasks.
 */
public class SchedulerConfig {

    private static final File CONFIG_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(),
            "MastaConfig/Scheduler.toml");

    private static CommentedFileConfig config;

    // Scheduler enabled flag
    private static boolean enabled = true;

    // Lists for tasks
    private static List<String> tasksReal = new ArrayList<>();
    private static List<String> tasksMinecraft = new ArrayList<>();

    /**
     * Loads the config file. Creates it if missing with default values.
     */
    public static void load() {
        // Ensure the folder exists
        CONFIG_FILE.getParentFile().mkdirs();

        config = CommentedFileConfig.builder(CONFIG_FILE)
                .writingMode(WritingMode.REPLACE)
                .build();
        config.load();

        // -------------------------
        // Enable flag
        // -------------------------
        if (!config.contains("enabled")) config.set("enabled", true);
        enabled = config.getOrElse("enabled", true);

        // -------------------------
        // Real-world tasks
        // -------------------------
        if (!config.contains("tasks_real")) {
            config.set("tasks_real", new ArrayList<String>() {{
                add("12:00|say Hello World");
                add("13:00|say Another command");
            }});
        }
        tasksReal = config.getOrElse("tasks_real", new ArrayList<>());

        // -------------------------
        // Minecraft world time tasks
        // -------------------------
        if (!config.contains("tasks_minecraft")) {
            config.set("tasks_minecraft", new ArrayList<String>() {{
                add("6000|say Good morning Minecraft");
                add("12000|say Sunset time!");
                add("18000|say Midnight in Minecraft");
            }});
        }
        tasksMinecraft = config.getOrElse("tasks_minecraft", new ArrayList<>());

        // Save changes back to file (adds spacing, comments)
        config.setComment("enabled", "Enable or disable the scheduler (true/false)");
        config.setComment("tasks_real", "Real-world time tasks (HH:mm|command), 24-hour format");
        config.setComment("tasks_minecraft", "Minecraft world time tasks (ticks|command), 0-23999");

        config.save();
    }

    /** Returns true if the scheduler is enabled */
    public static boolean enabled() {
        return enabled;
    }

    /** Returns a copy of real-world tasks */
    public static List<String> tasksReal() {
        return new ArrayList<>(tasksReal);
    }

    /** Returns a copy of Minecraft world time tasks */
    public static List<String> tasksMinecraft() {
        return new ArrayList<>(tasksMinecraft);
    }
}
