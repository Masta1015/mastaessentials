package com.mastaessentials.schedular;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.file.FileConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SchedulerConfig {

    private static final File CONFIG_FILE = new File("config/MastaConfig/Scheduler.toml");
    private static CommentedFileConfig config;

    public static void load() {
        // Create folder if it doesn't exist
        CONFIG_FILE.getParentFile().mkdirs();

        config = CommentedFileConfig.builder(CONFIG_FILE)
                .sync()
                .autosave()
                .build();

        if (!CONFIG_FILE.exists()) {
            config.set("enabled", true);
            config.set("tasks", new ArrayList<String>() {{
                add("12:00|say Hello World");
                add("13:00|say Another command");
            }});
            config.save();
        } else {
            config.load();
        }
    }

    public static boolean enabled() {
        if (config == null) return false;
        return config.getOrElse("enabled", true);
    }

    public static List<String> tasks() {
        if (config == null) return new ArrayList<>();
        List<String> raw = config.getOrElse("tasks", new ArrayList<String>());
        List<String> cleaned = new ArrayList<>();
        for (Object o : raw) {
            cleaned.add(o.toString());
        }
        return cleaned;
    }

    public static void save() {
        if (config != null) config.save();
    }
}
