package com.dttdrt.addons.gui;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.lang.ModLang;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Manages custom local background image selection (TinyFileDialogs when present,
 * native Windows OpenFileDialog fallback on LWJGL-SDL builds like MC 26.3)
 * and DynamicTexture registration for DttdrtMainScreen.
 */
public class BackgroundTextureManager {
    private static final Identifier BG_TEXTURE_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "custom_menu_background");

    private static volatile boolean hasTexture = false;
    private static volatile String loadedPath = "";
    private static volatile int texWidth = 1;
    private static volatile int texHeight = 1;
    private static volatile String statusKey = "dttdrtaddons.custom.bg_none";
    private static volatile Object[] statusArgs = new Object[0];
    private static volatile boolean dialogOpen = false;
    private static DynamicTexture activeTexture;

    public static Identifier getTextureId() {
        return BG_TEXTURE_ID;
    }

    public static boolean hasTexture() {
        ensureLoadedFromConfig();
        return hasTexture;
    }

    public static int getTexWidth() {
        return texWidth;
    }

    public static int getTexHeight() {
        return texHeight;
    }

    public static String getStatusText() {
        return ModLang.tr(statusKey, statusArgs);
    }

    public static void ensureLoadedFromConfig() {
        String path = ModConfig.getInstance().customBackgroundPath;
        if (path == null) path = "";
        if (!path.equals(loadedPath)) {
            loadFromPath(path);
        }
    }

    public static void clearBackground() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.customBackgroundPath = "";
        ModConfig.save();
        loadedPath = "";
        hasTexture = false;
        statusKey = "dttdrtaddons.custom.bg_none";
        statusArgs = new Object[0];
        if (activeTexture != null) {
            try {
                activeTexture.close();
            } catch (Throwable ignored) {}
            activeTexture = null;
        }
    }

    public static void openFileExplorerAsync() {
        if (dialogOpen) return;
        dialogOpen = true;
        statusKey = "dttdrtaddons.custom.bg_waiting";
        statusArgs = new Object[0];

        CompletableFuture.runAsync(() -> {
            try {
                String defaultDir = ModConfig.getInstance().customBackgroundPath;
                if (defaultDir == null || defaultDir.isEmpty()) {
                    defaultDir = System.getProperty("user.home");
                }

                String selected = tryOpenTinyFd(defaultDir);
                if (selected == null && !hasTinyFd()) {
                    selected = openWindowsExplorerDialog(defaultDir);
                }

                if (selected != null && !selected.trim().isEmpty()) {
                    ModConfig cfg = ModConfig.getInstance();
                    cfg.customBackgroundPath = selected.trim();
                    ModConfig.save();
                    Minecraft.getInstance().execute(() -> loadFromPath(cfg.customBackgroundPath));
                } else if (hasTexture) {
                    statusKey = "dttdrtaddons.custom.bg_loaded";
                    statusArgs = new Object[]{new File(loadedPath).getName(), texWidth, texHeight};
                } else {
                    statusKey = "dttdrtaddons.custom.bg_none";
                    statusArgs = new Object[0];
                }
            } catch (Throwable t) {
                statusKey = "dttdrtaddons.custom.bg_failed_dialog";
                statusArgs = new Object[0];
            } finally {
                dialogOpen = false;
            }
        });
    }

    private static boolean hasTinyFd() {
        try {
            Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String tryOpenTinyFd(String defaultDir) {
        try {
            Class<?> cls = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Method m = cls.getMethod("tinyfd_openFileDialog",
                    CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.put(stack.UTF8("*.bmp"));
                filters.flip();
                Object res = m.invoke(null,
                        "Select Menu Background Image",
                        defaultDir,
                        filters,
                        "Image Files (*.png, *.jpg, *.jpeg, *.bmp)",
                        false);
                return res instanceof String s ? s : null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String openWindowsExplorerDialog(String defaultDir) {
        try {
            String initDir = new File(defaultDir).isFile()
                    ? new File(defaultDir).getParent()
                    : defaultDir;
            String escapedDir = initDir != null ? initDir.replace("'", "''") : "";
            String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
                    + "Add-Type -AssemblyName System.Windows.Forms; "
                    + "$d = New-Object System.Windows.Forms.OpenFileDialog; "
                    + "$d.Title = 'Select Menu Background Image'; "
                    + "$d.Filter = 'Image Files (*.png;*.jpg;*.jpeg;*.bmp)|*.png;*.jpg;*.jpeg;*.bmp'; "
                    + (escapedDir.isEmpty() ? "" : "$d.InitialDirectory = '" + escapedDir + "'; ")
                    + "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $d.FileName }";
            Process proc = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-STA", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) {
                        out.append(line.trim());
                    }
                }
            }
            proc.waitFor();
            return out.length() > 0 ? out.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void loadFromPath(String path) {
        loadedPath = path != null ? path : "";
        if (loadedPath.isEmpty()) {
            hasTexture = false;
            statusKey = "dttdrtaddons.custom.bg_none";
            statusArgs = new Object[0];
            return;
        }

        File file = new File(loadedPath);
        if (!file.exists() || !file.isFile()) {
            hasTexture = false;
            statusKey = "dttdrtaddons.custom.bg_not_found";
            statusArgs = new Object[]{file.getName()};
            return;
        }

        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                hasTexture = false;
                statusKey = "dttdrtaddons.custom.bg_unsupported";
                statusArgs = new Object[0];
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            NativeImage nativeImg = NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));

            if (activeTexture != null) {
                activeTexture.close();
            }
            activeTexture = new DynamicTexture(() -> "dttdrt_menu_bg", nativeImg) {
                {
                    this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                }
            };
            Minecraft.getInstance().getTextureManager().register(BG_TEXTURE_ID, activeTexture);

            texWidth = Math.max(1, nativeImg.getWidth());
            texHeight = Math.max(1, nativeImg.getHeight());
            hasTexture = true;
            statusKey = "dttdrtaddons.custom.bg_loaded";
            statusArgs = new Object[]{file.getName(), texWidth, texHeight};
        } catch (Throwable t) {
            hasTexture = false;
            statusKey = "dttdrtaddons.custom.bg_error";
            statusArgs = new Object[]{file.getName()};
        }
    }
}
