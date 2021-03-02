package me.blue.il.anti.cheat.fabric.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.lib.gson.JsonReader;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Config {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("AntiCheatFabric.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, ModDependency> WHITELISTED_MODS = new LinkedHashMap<>();

    private Config() {
    }

    static void read() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                JsonReader reader = new JsonReader(Files.newBufferedReader(CONFIG_FILE));

                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();

                    if ("whitelisted".equals(key)) {
                        DependencyUtil.readDependenciesContainer(reader, WHITELISTED_MODS);
                    } else {
                        reader.skipValue();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            write();
        }
    }

    static void write() {
        try {
            JsonObject object = new JsonObject();

            object.add("whitelisted", DependencyUtil.toJsonObject(WHITELISTED_MODS));

            BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE);
            GSON.toJson(object, writer);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    static void whitelist(String modId, ModDependency dependency) {
        WHITELISTED_MODS.put(modId, dependency);
        write();
    }

    static @Nullable ModDependency getWhitelistedVersion(String modId) {
        return WHITELISTED_MODS.get(modId);
    }

    static Collection<ModDependency> getWhitelistedMods() {
        return new ArrayList<>(WHITELISTED_MODS.values());
    }

    public static ModDependency unWhitelist(String modId) {
        return WHITELISTED_MODS.remove(modId);
    }
}
