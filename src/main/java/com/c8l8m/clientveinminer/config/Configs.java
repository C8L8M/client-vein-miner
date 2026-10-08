/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.c8l8m.clientveinminer.Reference;

import fi.dy.masa.malilib.MaLiLib;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    public static class Generic
    {
        public static final ConfigBoolean    MAIN_TOGGLE           = new ConfigBoolean("mainToggle", true, "client_vein_miner.config.comment.main_toggle").translatedName("client_vein_miner.config.name.main_toggle");
        /** The chain mining feature toggle: it is listed on both the General and the Chain Mining tab. */
        public static final ConfigBoolean    CHAIN_MINING          = new ConfigBoolean("chainMining", true, "client_vein_miner.config.comment.chain_mining").translatedName("client_vein_miner.config.name.chain_mining");
        public static final ConfigInteger    MAX_BLOCKS            = new ConfigInteger("maxBlocks", 128, 1, 1024, "client_vein_miner.config.comment.max_blocks").translatedName("client_vein_miner.config.name.max_blocks");
        public static final ConfigBoolean    MATCH_EXACT_STATE     = new ConfigBoolean("matchExactState", false, "client_vein_miner.config.comment.match_exact_state").translatedName("client_vein_miner.config.name.match_exact_state");
        public static final ConfigBoolean    IGNORE_TILE_ENTITIES  = new ConfigBoolean("ignoreTileEntities", true, "client_vein_miner.config.comment.ignore_tile_entities").translatedName("client_vein_miner.config.name.ignore_tile_entities");
        /**
         * Break progress (in percent) at which our own stop packet is sent. The server only accepts it
         * once its own progress reached 0.7, so 70 is the earliest legal value and about 30% faster
         * than vanilla; 100 waits for the full break and is exactly vanilla timing.
         */
        public static final ConfigInteger    MINING_PROGRESS_THRESHOLD = new ConfigInteger("miningProgressThreshold", 100, 70, 100, "client_vein_miner.config.comment.mining_progress_threshold").translatedName("client_vein_miner.config.name.mining_progress_threshold");
        public static final ConfigInteger    MINING_INTERVAL       = new ConfigInteger("miningInterval", 2, 0, 40, "client_vein_miner.config.comment.mining_interval").translatedName("client_vein_miner.config.name.mining_interval");
        public static final ConfigInteger    AUTO_STOP_SECONDS     = new ConfigInteger("autoStopSeconds", 5, 0, 600, "client_vein_miner.config.comment.auto_stop_seconds").translatedName("client_vein_miner.config.name.auto_stop_seconds");
        public static final ConfigDouble     REACH_DISTANCE        = new ConfigDouble("reachDistance", 0.0, 0.0, 32.0, "client_vein_miner.config.comment.reach_distance").translatedName("client_vein_miner.config.name.reach_distance");
        public static final ConfigOptionList CREATIVE_REACH_MODE   = new ConfigOptionList("creativeReachMode", CreativeReachMode.SINGLE_PLAYER, "client_vein_miner.config.comment.creative_reach_mode").translatedName("client_vein_miner.config.name.creative_reach_mode");
        public static final ConfigBoolean    REQUIRE_LINE_OF_SIGHT = new ConfigBoolean("requireLineOfSight", false, "client_vein_miner.config.comment.require_line_of_sight").translatedName("client_vein_miner.config.name.require_line_of_sight");
        public static final ConfigBoolean    STOP_ON_KEY_RELEASE   = new ConfigBoolean("stopOnKeyRelease", false, "client_vein_miner.config.comment.stop_on_key_release").translatedName("client_vein_miner.config.name.stop_on_key_release");
        public static final ConfigBoolean    RENDER_OUTLINE        = new ConfigBoolean("renderOutline", false, "client_vein_miner.config.comment.render_outline").translatedName("client_vein_miner.config.name.render_outline");
        public static final ConfigBoolean    SHOW_COUNT            = new ConfigBoolean("showCount", true, "client_vein_miner.config.comment.show_count").translatedName("client_vein_miner.config.name.show_count");
        public static final ConfigDouble     LINE_WIDTH            = new ConfigDouble("lineWidth", 2.0, 1.0, 8.0, "client_vein_miner.config.comment.line_width").translatedName("client_vein_miner.config.name.line_width");
        /** Opacity in percent, applied to the red/green target fills only. */
        public static final ConfigInteger    HIGHLIGHT_OPACITY     = new ConfigInteger("highlightOpacity", 20, 0, 100, "client_vein_miner.config.comment.highlight_opacity").translatedName("client_vein_miner.config.name.highlight_opacity");
        // Plain RGB colours without any alpha channel (ConfigRgbColor strips it): the preview outline
        // is always drawn opaque, and the target fills use the opacity option above.
        public static final ConfigColor      PREVIEW_COLOR         = new ConfigRgbColor("previewColor", "#FFFFFF", "client_vein_miner.config.comment.preview_color").translatedName("client_vein_miner.config.name.preview_color");
        public static final ConfigColor      REACHABLE_COLOR       = new ConfigRgbColor("reachableColor", "#00FF00", "client_vein_miner.config.comment.reachable_color").translatedName("client_vein_miner.config.name.reachable_color");
        public static final ConfigColor      UNREACHABLE_COLOR     = new ConfigRgbColor("unreachableColor", "#FF0000", "client_vein_miner.config.comment.unreachable_color").translatedName("client_vein_miner.config.name.unreachable_color");
        public static final ConfigStringList BLOCK_BLACKLIST       = new ConfigStringList("blockBlacklist", ImmutableList.of(), "client_vein_miner.config.comment.block_blacklist").translatedName("client_vein_miner.config.name.block_blacklist");

        /** The "General" category: only the two feature toggles, no settings of the feature itself. */
        public static final ImmutableList<IConfigBase> GENERAL_OPTIONS = ImmutableList.of(
                MAIN_TOGGLE,
                CHAIN_MINING
        );

        /** The "Chain Mining" category: its own toggle followed by every chain mining setting. */
        public static final ImmutableList<IConfigBase> CHAIN_MINING_OPTIONS = ImmutableList.of(
                CHAIN_MINING,
                MAX_BLOCKS,
                MATCH_EXACT_STATE,
                IGNORE_TILE_ENTITIES,
                MINING_PROGRESS_THRESHOLD,
                MINING_INTERVAL,
                AUTO_STOP_SECONDS,
                REACH_DISTANCE,
                CREATIVE_REACH_MODE,
                REQUIRE_LINE_OF_SIGHT,
                STOP_ON_KEY_RELEASE,
                RENDER_OUTLINE,
                SHOW_COUNT,
                LINE_WIDTH,
                HIGHLIGHT_OPACITY,
                PREVIEW_COLOR,
                REACHABLE_COLOR,
                UNREACHABLE_COLOR,
                BLOCK_BLACKLIST
        );

        /** Every option of the mod exactly once, for reading and writing the config file. */
        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.<IConfigBase>builder()
                .add(MAIN_TOGGLE)
                .addAll(CHAIN_MINING_OPTIONS)
                .build();
    }

    public static class Hotkeys
    {
        /** Held while pointing at a block to preview the group; allowExtraKeys so other keys may be held too. */
        public static final ConfigHotkey ACTIVATE    = new ConfigHotkey("activate", "GRAVE_ACCENT", KeybindSettings.MODIFIER_INGAME, "client_vein_miner.config.comment.hotkey_activate").translatedName("client_vein_miner.config.name.hotkey_activate");
        /** Aborts a running chain; allowExtraKeys so it also fires while other keys are held. */
        public static final ConfigHotkey FORCE_STOP  = new ConfigHotkey("forceStop", "X", KeybindSettings.PRESS_ALLOWEXTRA, "client_vein_miner.config.comment.hotkey_force_stop").translatedName("client_vein_miner.config.name.hotkey_force_stop");
        public static final ConfigHotkey OPEN_CONFIG = new ConfigHotkey("openConfig", "GRAVE_ACCENT,C", "client_vein_miner.config.comment.hotkey_open_config").translatedName("client_vein_miner.config.name.hotkey_open_config");

        public static final ImmutableList<IHotkey> HOTKEYS = ImmutableList.of(
                ACTIVATE,
                FORCE_STOP,
                OPEN_CONFIG
        );

        /** The hotkeys listed on the Chain Mining tab; the config GUI hotkey lives on the General tab. */
        public static final ImmutableList<IHotkey> CHAIN_MINING_HOTKEYS = ImmutableList.of(
                ACTIVATE,
                FORCE_STOP
        );
    }

    /** The configured creative reach mode, falling back to OFF for an unknown stored value. */
    public static CreativeReachMode getCreativeReachMode()
    {
        final IConfigOptionListEntry value = Generic.CREATIVE_REACH_MODE.getOptionListValue();
        return value instanceof CreativeReachMode mode ? mode : CreativeReachMode.OFF;
    }

    /** True when the mod itself and the chain mining feature are both switched on. */
    public static boolean isChainMiningEnabled()
    {
        return Generic.MAIN_TOGGLE.getBooleanValue() && Generic.CHAIN_MINING.getBooleanValue();
    }

    /** The "General" category: the feature toggles and the hotkey that opens this screen. */
    public static List<? extends IConfigBase> getGeneralConfigs()
    {
        List<IConfigBase> list = new ArrayList<>(Generic.GENERAL_OPTIONS);
        list.add(Hotkeys.OPEN_CONFIG);
        return list;
    }

    /** The "Chain Mining" category: the settings and hotkeys belonging to the chain mining feature. */
    public static List<? extends IConfigBase> getChainMiningConfigs()
    {
        List<IConfigBase> list = new ArrayList<>(Generic.CHAIN_MINING_OPTIONS);
        list.addAll(Hotkeys.CHAIN_MINING_HOTKEYS);
        return list;
    }

    /**
     * Everything the mod offers, across every category. Features added later contribute their own
     * category list (and are appended here), so each tab of the config screen stays independent.
     */
    public static List<? extends IConfigBase> getAllConfigs()
    {
        return getConfigList();
    }

    private static List<IConfigBase> getConfigList()
    {
        List<IConfigBase> list = new ArrayList<>();
        list.addAll(Generic.OPTIONS);
        list.addAll(Hotkeys.HOTKEYS);
        return list;
    }

    public static void loadFromFile()
    {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.isRegularFile(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                JsonObject root = element.getAsJsonObject();

                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readHotkeys(root, "Hotkeys", Hotkeys.HOTKEYS);
            }
        }
    }

    public static void saveToFile()
    {
        Path dir = FileUtils.getConfigDirectory();

        try
        {
            Files.createDirectories(dir);
        }
        catch (IOException e)
        {
            MaLiLib.LOGGER.error("[{}] Failed to create the config directory {}", Reference.MOD_ID, dir, e);
            return;
        }

        JsonObject root = new JsonObject();

        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        ConfigUtils.writeHotkeys(root, "Hotkeys", Hotkeys.HOTKEYS);

        JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }
}
