package com.jobhuntcopilot.config;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the JSON config files into their record types. Field names in the JSON match the record's field names exactly, so Gson needs no extra mapping configuration. */
public class ConfigLoader {

    public static final Path DEFAULT_ROLES_CONFIG_PATH = Path.of("config", "roles.json");
    public static final Path DEFAULT_BLOCKLIST_CONFIG_PATH = Path.of("config", "blocklist.json");

    private static final Gson GSON = new Gson();

    public static RolesConfig loadRolesConfig() {
        return loadRolesConfig(DEFAULT_ROLES_CONFIG_PATH);
    }

    public static RolesConfig loadRolesConfig(Path path) {
        return load(path, RolesConfig.class);
    }

    public static BlocklistConfig loadBlocklistConfig() {
        return loadBlocklistConfig(DEFAULT_BLOCKLIST_CONFIG_PATH);
    }

    public static BlocklistConfig loadBlocklistConfig(Path path) {
        return load(path, BlocklistConfig.class);
    }

    private static <T> T load(Path path, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(path)) {
            T config = GSON.fromJson(reader, type);
            if (config == null) {
                throw new IllegalStateException("Config file is empty: " + path);
            }
            return config;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read config file: " + path, e);
        }
    }
}
