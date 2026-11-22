package com.dttdrt.addons.core.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Resolves the active mod config directory (config/dttdrtaddons) and performs
 * a one-time non-destructive migration from legacy pre-rename configs if present.
 */
public final class ConfigPaths {

    public static final String CONFIG_DIR_NAME = "dttdrtaddons";
    private static final String LEGACY_DIR_NAME = "accarc" + "addons";
    private static final AtomicBoolean MIGRATED = new AtomicBoolean(false);

    private ConfigPaths() {
    }

    public static Path getConfigPath() {
        Path root = FabricLoader.getInstance().getConfigDir();
        Path target = root.resolve(CONFIG_DIR_NAME);
        migrateLegacyDirectoryIfNeeded(root, target);
        return target;
    }

    public static File getConfigDir() {
        return getConfigPath().toFile();
    }

    public static File getConfigFile(String relativePath) {
        return getConfigPath().resolve(relativePath).toFile();
    }

    private static void migrateLegacyDirectoryIfNeeded(Path configRoot, Path targetDir) {
        if (!MIGRATED.compareAndSet(false, true)) {
            return;
        }
        try {
            Path legacyDir = configRoot.resolve(LEGACY_DIR_NAME);
            if (!Files.isDirectory(legacyDir)) {
                return;
            }
            Files.createDirectories(targetDir);
            copyMissingRecursively(legacyDir, targetDir);
        } catch (Throwable ignored) {
        }
    }

    private static void copyMissingRecursively(Path sourceRoot, Path targetRoot) throws IOException {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            for (var it = walk.iterator(); it.hasNext(); ) {
                Path src = it.next();
                Path rel = sourceRoot.relativize(src);
                if (rel.toString().isEmpty()) {
                    continue;
                }
                Path dst = targetRoot.resolve(rel);
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else if (Files.isRegularFile(src) && !Files.exists(dst)) {
                    Path parent = dst.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(src, dst);
                }
            }
        }
    }
}
