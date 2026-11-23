package com.dttdrt.addons;

import com.dttdrt.addons.api.ItemDatabase;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.FeatureManager;
import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.core.update.UpdateChecker;
import com.dttdrt.addons.features.combat.DamageTrackerFeature;
import com.dttdrt.addons.features.custom.CustomNameFeature;
import com.dttdrt.addons.features.freecam.FreeCamFeature;
import com.dttdrt.addons.features.market.MarketMonitorFeature;
import com.dttdrt.addons.features.mining.CorpseEspFeature;
import com.dttdrt.addons.features.mining.CrystalHollowsFeature;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker;
import com.dttdrt.addons.features.mining.GemstoneEspFeature;
import com.dttdrt.addons.features.mining.LittleFootEspFeature;
import com.dttdrt.addons.features.mining.MiningProfitFeature;
import com.dttdrt.addons.features.mining.NucleusProfitFeature;
import com.dttdrt.addons.features.mining.ScathaProfitFeature;
import com.dttdrt.addons.features.mining.TempleWaypointFeature;
import com.dttdrt.addons.features.mining.profit.ProfitDataStore;
import com.dttdrt.addons.features.nametag.NametagFeature;
import com.dttdrt.addons.features.party.PartyVoteFeature;
import com.dttdrt.addons.features.storage.StorageData;
import com.dttdrt.addons.gui.DttdrtMainScreen;
import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.dttdrt.addons.util.NotificationUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Method;

public class DttdrtAddons implements ClientModInitializer {
    public static final String MOD_ID = "dttdrtaddons";
    private static KeyMapping openMenuKeyMapping;
    private static KeyMapping quickVoteKeyMapping;
    private static KeyMapping quickVoteYesKeyMapping;
    private static KeyMapping quickVoteNoKeyMapping;
    private static KeyMapping freecamKeyMapping;
    private static KeyMapping gemstoneEspKeyMapping;
    private static boolean keybindsSyncedOnStart = false;
    private static long lastQuickVoteMs = 0L;
    private static long lastQuickVoteResponseMs = 0L;
    private static long lastFreecamRawToggleNs = 0L;
    private static long lastGemstoneEspRawToggleNs = 0L;
    private static boolean isKeyDownLookupDone = false;
    private static Method isKeyDownMethod = null;
    private static int isKeyDownArgCount = 0;

    @Override
    public void onInitializeClient() {
        // 1. Initialize configuration, whitelist check, update check & sync full Skyblock item catalog in background
        ModConfig.load();
        ModLogManager.info("Init", "DttdrtAddons initializing (freecamKey=" + ModConfig.getInstance().freecamKey + ")");
        WhitelistManager.checkAsync();
        UpdateChecker.checkAsync();
        ItemDatabase.syncFullCatalogAsync();

        // 2. Register keybindings (Open Menu default: 'M', Quick !vote & votes default: unbound)
        openMenuKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.open_menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_M,
                KeyMapping.Category.MISC
        ));
        quickVoteKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.quick_vote",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC
        ));
        quickVoteYesKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.quick_vote_yes",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC
        ));
        quickVoteNoKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.quick_vote_no",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC
        ));
        freecamKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.freecam",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC
        ));
        gemstoneEspKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.dttdrtaddons.gemstone_esp",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC
        ));

        // 3. Register modular features
        FeatureManager.register(new MarketMonitorFeature());
        FeatureManager.register(new CrystalHollowsFeature());
        FeatureManager.register(new GemstoneEspFeature());
        FeatureManager.register(new CorpseEspFeature());
        FeatureManager.register(new LittleFootEspFeature());
        FeatureManager.register(new MiningProfitFeature());
        FeatureManager.register(new ScathaProfitFeature());
        FeatureManager.register(new NucleusProfitFeature());
        FeatureManager.register(new TempleWaypointFeature());
        FeatureManager.register(new PartyVoteFeature());
        FeatureManager.register(new NametagFeature());
        FeatureManager.register(new CustomNameFeature());
        FeatureManager.register(new FreeCamFeature());
        FeatureManager.register(new DamageTrackerFeature());

        // 4. Register client commands (/da, /daapply, /daretry) - always available even when unauthorized
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("da")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (canOpenMenu(mc)) {
                            mc.schedule(() -> mc.setScreen(new DttdrtMainScreen()));
                        }
                        return 1;
                    })
            );
            dispatcher.register(ClientCommands.literal("daretry")
                    .executes(context -> {
                        executeWhitelistRetryCommand(Minecraft.getInstance());
                        return 1;
                    })
            );
            dispatcher.register(ClientCommands.literal("daapply")
                    .executes(context -> {
                        executeWhitelistApplyCommand(Minecraft.getInstance(), "");
                        return 1;
                    })
                    .then(ClientCommands.argument("note", StringArgumentType.greedyString())
                            .executes(context -> {
                                String note = StringArgumentType.getString(context, "note");
                                executeWhitelistApplyCommand(Minecraft.getInstance(), note);
                                return 1;
                            }))
            );
        });

        // 5. Register client tick loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChromaRenderState.onClientTick();
            if (!keybindsSyncedOnStart && client.options != null) {
                keybindsSyncedOnStart = true;
                syncKeybindsFromConfig();
            }

            // Handle Open Settings Menu keybind press (only after entering a world/server)
            while (openMenuKeyMapping != null && openMenuKeyMapping.consumeClick()) {
                if (client.screen == null && canOpenMenu(client)) {
                    client.setScreen(new DttdrtMainScreen());
                }
            }

            // Handle Quick !vote keybind press (respects whitelist and Party !vote toggle)
            while (quickVoteKeyMapping != null && quickVoteKeyMapping.consumeClick()) {
                if (canSendPartyHotkey(client)) {
                    long now = System.currentTimeMillis();
                    if (now - lastQuickVoteMs >= 1500L) {
                        lastQuickVoteMs = now;
                        client.getConnection().sendCommand("pc !vote");
                    }
                }
            }

            // Handle Quick Vote Yes ('y') and No ('n') keybind presses
            while (quickVoteYesKeyMapping != null && quickVoteYesKeyMapping.consumeClick()) {
                if (canSendPartyHotkey(client)) {
                    long now = System.currentTimeMillis();
                    if (now - lastQuickVoteResponseMs >= 1500L) {
                        lastQuickVoteResponseMs = now;
                        client.getConnection().sendCommand("pc y");
                    }
                }
            }
            while (quickVoteNoKeyMapping != null && quickVoteNoKeyMapping.consumeClick()) {
                if (canSendPartyHotkey(client)) {
                    long now = System.currentTimeMillis();
                    if (now - lastQuickVoteResponseMs >= 1500L) {
                        lastQuickVoteResponseMs = now;
                        client.getConnection().sendCommand("pc n");
                    }
                }
            }
            while (freecamKeyMapping != null && freecamKeyMapping.consumeClick()) {
                if (System.nanoTime() - lastFreecamRawToggleNs < 250_000_000L) {
                    continue;
                }
                if (client.screen == null) {
                    FreeCamFeature.toggleIfPossible(client, "keymapping_tick");
                }
            }
            while (gemstoneEspKeyMapping != null && gemstoneEspKeyMapping.consumeClick()) {
                if (System.nanoTime() - lastGemstoneEspRawToggleNs < 250_000_000L) {
                    continue;
                }
                if (client.screen == null) {
                    GemstoneEspFeature.toggleByHotkey(client);
                }
            }

            UpdateChecker.tickNotifyIfReady(client);

            // Always track zone/mineshaft state and tick active features when whitelisted
            if (WhitelistManager.isAuthorized()) {
                CrystalHollowsTracker.tick(client);
                FeatureManager.tickAll(client);
            }
        });

        // 6. Shutdown hook to persist configurations, storage pages, profit trackers, and launch staged auto-update
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ModConfig.save();
            StorageData.getInstance().saveSync();
            ProfitDataStore.getInstance().saveSync();
            UpdateChecker.launchStagedUpdateIfReady();
        }));
    }

    public static boolean canOpenMenu(Minecraft client) {
        return client != null && client.level != null && client.player != null;
    }

    private static boolean canSendPartyHotkey(Minecraft client) {
        return client.screen == null
                && WhitelistManager.isAuthorized()
                && ModConfig.getInstance().partyVoteEnabled
                && client.getConnection() != null;
    }

    private static InputConstants.Key parseKeyOrFallback(String keyName, InputConstants.Key fallback) {
        if (keyName == null || keyName.isEmpty() || "key.keyboard.unknown".equals(keyName)) {
            return InputConstants.UNKNOWN;
        }
        try {
            return InputConstants.getKey(keyName);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void syncKeybindsFromConfig() {
        ModConfig cfg = ModConfig.getInstance();
        boolean changed = false;
        if (openMenuKeyMapping != null) {
            openMenuKeyMapping.setKey(parseKeyOrFallback(cfg.openMenuKey, openMenuKeyMapping.getDefaultKey()));
            changed = true;
        }
        if (quickVoteKeyMapping != null) {
            quickVoteKeyMapping.setKey(parseKeyOrFallback(cfg.quickVoteKey, InputConstants.UNKNOWN));
            changed = true;
        }
        if (quickVoteYesKeyMapping != null) {
            quickVoteYesKeyMapping.setKey(parseKeyOrFallback(cfg.quickVoteYesKey, InputConstants.UNKNOWN));
            changed = true;
        }
        if (quickVoteNoKeyMapping != null) {
            quickVoteNoKeyMapping.setKey(parseKeyOrFallback(cfg.quickVoteNoKey, InputConstants.UNKNOWN));
            changed = true;
        }
        if (freecamKeyMapping != null) {
            freecamKeyMapping.setKey(parseKeyOrFallback(cfg.freecamKey, InputConstants.UNKNOWN));
            changed = true;
        }
        if (gemstoneEspKeyMapping != null) {
            gemstoneEspKeyMapping.setKey(parseKeyOrFallback(cfg.gemstoneEspKey, InputConstants.UNKNOWN));
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            ModLogManager.info("Keybind", "Synced keybinds (freecam=" + cfg.freecamKey
                    + ", gemstoneEsp=" + cfg.gemstoneEspKey + ", openMenu=" + cfg.openMenuKey + ")");
        }
    }

    public static void handleInGameKeyPress(KeyEvent input) {
        if (input == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (isKeyDownCompat(mc, InputConstants.KEY_F3)) {
                return;
            }
            InputConstants.Key pressed = InputConstants.getKey(input);
            if (pressed == null) {
                return;
            }
            String pressedName = pressed.getName();
            ModConfig cfg = ModConfig.getInstance();

            String freecamBound = cfg.freecamKey;
            if (freecamBound != null && !freecamBound.isEmpty()
                    && !"key.keyboard.unknown".equals(freecamBound) && freecamBound.equals(pressedName)) {
                lastFreecamRawToggleNs = System.nanoTime();
                while (freecamKeyMapping != null && freecamKeyMapping.consumeClick()) {
                    // drain matching KeyMapping click so tick loop does not double-toggle
                }
                FreeCamFeature.toggleIfPossible(mc, "raw_key:" + pressedName);
            }

            String gemstoneBound = cfg.gemstoneEspKey;
            if (gemstoneBound != null && !gemstoneBound.isEmpty()
                    && !"key.keyboard.unknown".equals(gemstoneBound) && gemstoneBound.equals(pressedName)) {
                lastGemstoneEspRawToggleNs = System.nanoTime();
                while (gemstoneEspKeyMapping != null && gemstoneEspKeyMapping.consumeClick()) {
                    // drain matching KeyMapping click so tick loop does not double-toggle
                }
                GemstoneEspFeature.toggleByHotkey(mc);
            }
        } catch (Throwable t) {
            ModLogManager.error("Keybind", "Error in handleInGameKeyPress", t);
        }
    }

    private static boolean isKeyDownCompat(Minecraft mc, int keyCode) {
        if (!isKeyDownLookupDone) {
            isKeyDownLookupDone = true;
            for (Method m : InputConstants.class.getMethods()) {
                if ("isKeyDown".equals(m.getName())) {
                    int count = m.getParameterCount();
                    if (count == 1 || count == 2) {
                        isKeyDownMethod = m;
                        isKeyDownArgCount = count;
                        break;
                    }
                }
            }
        }
        if (isKeyDownMethod != null) {
            try {
                if (isKeyDownArgCount == 1) {
                    return (boolean) isKeyDownMethod.invoke(null, keyCode);
                } else if (mc != null && mc.getWindow() != null) {
                    return (boolean) isKeyDownMethod.invoke(null, mc.getWindow(), keyCode);
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void bindKey(KeyMapping mapping, InputConstants.Key key, java.util.function.Consumer<String> configSetter) {
        InputConstants.Key target = key != null ? key : InputConstants.UNKNOWN;
        if (mapping != null) {
            mapping.setKey(target);
            KeyMapping.resetMapping();
        }
        configSetter.accept(target.getName());
        ModConfig.save();
        saveVanillaOptionsSafely();
    }

    public static void setOpenMenuKey(InputConstants.Key key) {
        bindKey(openMenuKeyMapping, key, name -> ModConfig.getInstance().openMenuKey = name);
    }

    public static void setQuickVoteKey(InputConstants.Key key) {
        bindKey(quickVoteKeyMapping, key, name -> ModConfig.getInstance().quickVoteKey = name);
    }

    public static void setQuickVoteYesKey(InputConstants.Key key) {
        bindKey(quickVoteYesKeyMapping, key, name -> ModConfig.getInstance().quickVoteYesKey = name);
    }

    public static void setQuickVoteNoKey(InputConstants.Key key) {
        bindKey(quickVoteNoKeyMapping, key, name -> ModConfig.getInstance().quickVoteNoKey = name);
    }

    public static void setFreecamKey(InputConstants.Key key) {
        bindKey(freecamKeyMapping, key, name -> ModConfig.getInstance().freecamKey = name);
        ModLogManager.info("Keybind", "FreeCam key bound to " + ModConfig.getInstance().freecamKey);
    }

    private static void saveVanillaOptionsSafely() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                mc.options.save();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String formatKeyLabel(KeyMapping mapping, String fallback) {
        if (mapping == null) return fallback;
        return mapping.isUnbound() ? "None" : mapping.getTranslatedKeyMessage().getString();
    }

    public static String getOpenMenuKeyLabel() {
        return formatKeyLabel(openMenuKeyMapping, ModConfig.getInstance().openMenuKey);
    }

    public static String getQuickVoteKeyLabel() {
        return formatKeyLabel(quickVoteKeyMapping, "None");
    }

    public static String getQuickVoteYesKeyLabel() {
        return formatKeyLabel(quickVoteYesKeyMapping, "None");
    }

    public static String getQuickVoteNoKeyLabel() {
        return formatKeyLabel(quickVoteNoKeyMapping, "None");
    }

    public static boolean isOpenMenuKeyDefault() {
        return openMenuKeyMapping == null || openMenuKeyMapping.isDefault();
    }

    public static boolean isQuickVoteKeyUnbound() {
        return quickVoteKeyMapping == null || quickVoteKeyMapping.isUnbound();
    }

    public static boolean isQuickVoteYesKeyUnbound() {
        return quickVoteYesKeyMapping == null || quickVoteYesKeyMapping.isUnbound();
    }

    public static boolean isQuickVoteNoKeyUnbound() {
        return quickVoteNoKeyMapping == null || quickVoteNoKeyMapping.isUnbound();
    }

    public static String getFreecamKeyLabel() {
        return formatKeyLabel(freecamKeyMapping, "None");
    }

    public static boolean isFreecamKeyUnbound() {
        return freecamKeyMapping == null || freecamKeyMapping.isUnbound();
    }

    public static void setGemstoneEspKey(InputConstants.Key key) {
        bindKey(gemstoneEspKeyMapping, key, name -> ModConfig.getInstance().gemstoneEspKey = name);
        ModLogManager.info("Keybind", "Gemstone ESP key bound to " + ModConfig.getInstance().gemstoneEspKey);
    }

    public static String getGemstoneEspKeyLabel() {
        return formatKeyLabel(gemstoneEspKeyMapping, "None");
    }

    public static boolean isGemstoneEspKeyUnbound() {
        return gemstoneEspKeyMapping == null || gemstoneEspKeyMapping.isUnbound();
    }

    public static void executeWhitelistRetryCommand(Minecraft mc) {
        if (mc == null) {
            mc = Minecraft.getInstance();
        }
        final Minecraft client = mc;
        WhitelistManager.checkAsync(result -> {
            if (client == null) return;
            client.schedule(() -> {
                ChatFormatting color = WhitelistManager.isAuthorized()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW;
                NotificationUtils.sendClientChatMessage(Component.literal(result).withStyle(color));
            });
        });
    }

    public static void executeWhitelistApplyCommand(Minecraft mc, String note) {
        if (mc == null) {
            mc = Minecraft.getInstance();
        }
        final Minecraft client = mc;
        WhitelistManager.requestAsync(note, result -> {
            if (client == null) return;
            client.schedule(() -> {
                ChatFormatting color = WhitelistManager.isAuthorized()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.AQUA;
                NotificationUtils.sendClientChatMessage(Component.literal(result).withStyle(color));
            });
        });
    }
}
