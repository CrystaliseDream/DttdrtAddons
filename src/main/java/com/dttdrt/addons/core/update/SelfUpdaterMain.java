package com.dttdrt.addons.core.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Detached pure-JDK helper process executed from the newly downloaded jar after the
 * Minecraft JVM exits, safely replacing the old mod jar once Windows file locks release.
 */
public final class SelfUpdaterMain {

    private SelfUpdaterMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length < 5) {
            return;
        }
        long parentPid;
        try {
            parentPid = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Path oldJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path stagedJar = Path.of(args[2]).toAbsolutePath().normalize();
        Path targetJar = Path.of(args[3]).toAbsolutePath().normalize();
        Path logFile = Path.of(args[4]).toAbsolutePath().normalize();

        StringBuilder log = new StringBuilder();
        log.append("[").append(Instant.now()).append("] SelfUpdaterMain started for PID ")
                .append(parentPid).append('\n')
                .append("  oldJar=").append(oldJar).append('\n')
                .append("  stagedJar=").append(stagedJar).append('\n')
                .append("  targetJar=").append(targetJar).append('\n');

        try {
            waitForParentExit(parentPid);
            Thread.sleep(400L);

            if (!Files.isRegularFile(stagedJar) || Files.size(stagedJar) < 1024L) {
                log.append("ABORT: staged jar missing or invalid.\n");
                writeLog(logFile, log.toString());
                return;
            }

            Path modsDir = targetJar.getParent();
            if (modsDir != null) {
                Files.createDirectories(modsDir);
            }

            // 1. Copy staged jar to temporary file inside mods/ and atomically promote to targetJar
            Path tempTarget = targetJar.resolveSibling(targetJar.getFileName().toString() + ".tmp");
            copyWithRetry(stagedJar, tempTarget, 10, 300L);
            if (Files.size(tempTarget) != Files.size(stagedJar)) {
                Files.deleteIfExists(tempTarget);
                log.append("ABORT: copied temp size mismatch.\n");
                writeLog(logFile, log.toString());
                return;
            }
            moveWithRetry(tempTarget, targetJar, 15, 300L);
            log.append("Installed new jar: ").append(targetJar).append('\n');

            // 2. Remove old jar (or relocate out of mods/ if deletion briefly stays locked)
            if (!oldJar.equals(targetJar) && Files.exists(oldJar)) {
                boolean deleted = deleteWithRetry(oldJar, 20, 300L);
                if (deleted) {
                    log.append("Removed old jar: ").append(oldJar).append('\n');
                } else {
                    Path backupDir = stagedJar.getParent().resolve("backup");
                    Files.createDirectories(backupDir);
                    Path backupTarget = backupDir.resolve(oldJar.getFileName().toString());
                    moveWithRetry(oldJar, backupTarget, 10, 300L);
                    log.append("Moved locked old jar to backup: ").append(backupTarget).append('\n');
                }
            }

            // 3. Clean up staged jar after target is safely in mods/
            if (Files.isRegularFile(targetJar)) {
                Files.deleteIfExists(stagedJar);
                log.append("Cleaned up staged jar.\n");
            }
            log.append("SUCCESS\n");
        } catch (Throwable t) {
            log.append("ERROR: ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        }
        writeLog(logFile, log.toString());
    }

    private static void waitForParentExit(long pid) {
        try {
            Optional<ProcessHandle> opt = ProcessHandle.of(pid);
            if (opt.isPresent()) {
                ProcessHandle ph = opt.get();
                try {
                    ph.onExit().get(90L, TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                    long deadline = System.currentTimeMillis() + 90_000L;
                    while (ph.isAlive() && System.currentTimeMillis() < deadline) {
                        Thread.sleep(250L);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyWithRetry(Path src, Path dst, int attempts, long delayMs) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(delayMs);
            }
        }
        throw last != null ? last : new IOException("Copy failed");
    }

    private static void moveWithRetry(Path src, Path dst, int attempts, long delayMs) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                try {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(delayMs);
            }
        }
        throw last != null ? last : new IOException("Move failed");
    }

    private static boolean deleteWithRetry(Path target, int attempts, long delayMs) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            try {
                if (Files.deleteIfExists(target) || !Files.exists(target)) {
                    return true;
                }
            } catch (IOException ignored) {
            }
            Thread.sleep(delayMs);
        }
        return !Files.exists(target);
    }

    private static void writeLog(Path logFile, String content) {
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logFile, content, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }
}
