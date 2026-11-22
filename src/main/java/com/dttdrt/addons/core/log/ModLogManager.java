package com.dttdrt.addons.core.log;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight in-memory diagnostic logger for DttdrtAddons.
 * Powers the Debug -> Mod Logs viewer and one-click clipboard export.
 */
public final class ModLogManager {

    private static final int MAX_ENTRIES = 400;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Deque<String> ENTRIES = new ArrayDeque<>(MAX_ENTRIES + 8);
    private static volatile long versionCounter = 0L;

    private ModLogManager() {
    }

    public static void info(String tag, String msg) {
        append("INFO", tag, msg);
    }

    public static void warn(String tag, String msg) {
        append("WARN", tag, msg);
    }

    public static void error(String tag, String msg) {
        append("ERROR", tag, msg);
    }

    public static void error(String tag, String msg, Throwable t) {
        String detail = msg;
        if (t != null) {
            StackTraceElement[] st = t.getStackTrace();
            String top = (st != null && st.length > 0) ? (" @ " + st[0]) : "";
            detail = msg + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + top + ")";
        }
        append("ERROR", tag, detail);
    }

    private static synchronized void append(String level, String tag, String msg) {
        String ts = LocalTime.now().format(TIME_FMT);
        String line = "[" + ts + "] [" + level + "/" + tag + "] " + (msg == null ? "" : msg);
        if (ENTRIES.size() >= MAX_ENTRIES) {
            ENTRIES.pollFirst();
        }
        ENTRIES.addLast(line);
        versionCounter++;
    }

    public static synchronized List<String> getEntriesSnapshot() {
        return new ArrayList<>(ENTRIES);
    }

    public static long getVersionCounter() {
        return versionCounter;
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        versionCounter++;
        info("Debug", "Mod logs cleared.");
    }

    public static String buildDiagnosticReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DttdrtAddons Debug Report ===\n");
        try {
            String mcVer = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            sb.append("Minecraft: ").append(mcVer)
              .append(" | Java: ").append(System.getProperty("java.version"))
              .append(" | OS: ").append(System.getProperty("os.name")).append('\n');
        } catch (Throwable ignored) {
        }

        ModConfig cfg = ModConfig.getInstance();
        sb.append("Keybinds: openMenu=").append(cfg.openMenuKey)
          .append(" (label=").append(DttdrtAddons.getOpenMenuKeyLabel()).append(")")
          .append(", freecam=").append(cfg.freecamKey)
          .append(" (label=").append(DttdrtAddons.getFreecamKeyLabel()).append(")\n");

        FreeCamFeature fc = FreeCamFeature.getInstance();
        Minecraft mc = Minecraft.getInstance();
        sb.append("FreeCam: active=").append(FreeCamFeature.isActive())
          .append(", speed=").append(String.format(Locale.ROOT, "%.2fx", cfg.freecamSpeed))
          .append(", showHud=").append(cfg.freecamShowHud);
        if (fc != null) {
            sb.append(", cam=(")
              .append(String.format(Locale.ROOT, "%.1f, %.1f, %.1f", fc.getCamX(), fc.getCamY(), fc.getCamZ()))
              .append(")");
        }
        if (mc != null) {
            sb.append(", hasWorld=").append(mc.level != null)
              .append(", hasPlayer=").append(mc.player != null)
              .append(", smartCull=").append(mc.smartCull);
        }
        sb.append("\n\n--- Mod Event Log ---\n");

        List<String> snapshot = getEntriesSnapshot();
        if (snapshot.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            for (String line : snapshot) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public static String buildLatestMcLogSnippet() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MC latest.log (Filtered Errors/Warnings/Dttdrt + Tail) ===\n");
        try {
            Path logPath = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
            if (!Files.exists(logPath)) {
                return sb.append("Log file not found: ").append(logPath).append('\n').toString();
            }
            List<String> all = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>();
            for (String line : all) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("dttdrt") || lower.contains("freecam")
                        || lower.contains("mixin") || lower.contains("/error") || lower.contains("exception")
                        || (lower.contains("/warn") && !lower.contains("missing translation key"))) {
                    filtered.add(line);
                }
            }
            sb.append("--- Relevant Lines (last 80 of ").append(filtered.size()).append(") ---\n");
            int startFiltered = Math.max(0, filtered.size() - 80);
            for (int i = startFiltered; i < filtered.size(); i++) {
                sb.append(filtered.get(i)).append('\n');
            }
            sb.append("\n--- Tail (last 40 lines) ---\n");
            int startTail = Math.max(0, all.size() - 40);
            for (int i = startTail; i < all.size(); i++) {
                sb.append(all.get(i)).append('\n');
            }
        } catch (Throwable t) {
            sb.append("Failed to read latest.log: ").append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
