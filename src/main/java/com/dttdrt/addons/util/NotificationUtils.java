package com.dttdrt.addons.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * 100% Safe client-side notification utility.
 * NEVER sends any packets to the server. Operates exclusively on the local client.
 */
public class NotificationUtils {
    private static final String PREFIX = "§8[§bDA§8] §r";
    private static volatile TrayIcon trayIcon;
    private static volatile boolean trayShutdownHookRegistered = false;

    /**
     * Sends a client-only message to the local chat bar.
     */
    public static void sendClientChatMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            MutableComponent fullMsg = Component.literal(PREFIX).append(message);
            mc.player.sendSystemMessage(fullMsg);
        }
    }

    /**
     * Sends an AH alert with clickable /viewauction command copy.
     */
    public static void sendAhPriceAlert(String itemName, long price, String condition, double threshold, String auctionUuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MutableComponent alert = Component.literal("§e" + itemName + " §fLowest BIN: §6" + NumberFormatUtils.formatWithCommas(price)
                + " Coins §7(" + condition + " target: §a" + NumberFormatUtils.formatExact(threshold) + "§7) ");

        if (auctionUuid != null && !auctionUuid.isEmpty()) {
            MutableComponent clickLink = Component.literal("§b§n[Copy Command]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard("/viewauction " + auctionUuid))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to copy: §e/viewauction " + auctionUuid))));
            alert.append(clickLink);
        }

        sendClientChatMessage(alert);
    }

    /**
     * Sends a Bazaar price alert.
     */
    public static void sendBazaarPriceAlert(String itemName, double currentPrice, String condition, double threshold) {
        MutableComponent alert = Component.literal("§a[Bazaar] §e" + itemName + " §fInstant Buy: §6"
                + NumberFormatUtils.formatExact(currentPrice) + " Coins §7(" + condition + " target: §a"
                + NumberFormatUtils.formatExact(threshold) + "§7)");
        sendClientChatMessage(alert);
    }

    /**
     * Plays a crisp client-side notification sound.
     */
    public static void playAlertSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            try {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Displays an in-game actionbar overlay AND a Windows bottom-right desktop toast notification.
     */
    public static void showOverlayToast(String title, String subtitle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("§6[DA] §e" + title + " §f" + subtitle));
        } else if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal("§6[DA] §e" + title + " §f" + subtitle), false);
        }

        sendWindowsToastAsync("DA: " + title, subtitle);
    }

    /**
     * Triggers an in-process Windows bottom-right tray notification via java.awt.SystemTray.
     * Spawns zero external processes (no powershell.exe / cmd.exe), avoiding antivirus HIPS alerts.
     */
    public static void sendWindowsToastAsync(String title, String body) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                System.setProperty("java.awt.headless", "false");
                if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                    return;
                }

                TrayIcon icon = getOrCreateTrayIcon();
                if (icon != null) {
                    icon.displayMessage(
                            title != null ? title : "DA",
                            body != null ? body : "",
                            TrayIcon.MessageType.INFO
                    );
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static synchronized TrayIcon getOrCreateTrayIcon() {
        if (trayIcon != null) {
            return trayIcon;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x3B, 0x82, 0xF6));
            g.fillRoundRect(1, 1, 14, 14, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString("A", 4, 12);
            g.dispose();

            TrayIcon icon = new TrayIcon(img, "DA");
            icon.setImageAutoSize(true);
            tray.add(icon);
            trayIcon = icon;

            if (!trayShutdownHookRegistered) {
                trayShutdownHookRegistered = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (trayIcon != null) {
                            SystemTray.getSystemTray().remove(trayIcon);
                            trayIcon = null;
                        }
                    } catch (Throwable ignored) {}
                }));
            }
            return trayIcon;
        } catch (Throwable t) {
            return null;
        }
    }
}
