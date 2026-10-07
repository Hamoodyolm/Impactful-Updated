package dev.sirblambo.impactful.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class ModConfig {
    public List<FrameConfig> frames = new ArrayList<>(List.of(
            new FrameConfig(25, "FFFFFF"),
            new FrameConfig(25, "FFFFFF"),
            new FrameConfig(25, "000000"),
            new FrameConfig(25, "000000")));

    public boolean triggerOnAny      = false;
    public boolean triggerOnHostile  = false;
    public boolean triggerOnPlayers  = true;

    public boolean triggerOnTotemPop  = false;
    public boolean triggerOnAnyHit    = false;
    public boolean triggerOnCriticals = false;
    public boolean triggerOnMaceSmash = true;
    public boolean triggerOnKill      = true;

    public boolean pvpMusic            = false;
    public boolean pauseMusicDuck      = false;
    public boolean showStatsAfterFight = false;
    public float   musicMasterVolume   = 1.0f;
    public int     musicProximityRadius = 50;
    public boolean showTierHud         = true;
    public boolean pvpMusicFinishTrack = false;
    public boolean showImpactfulUsers  = true;

    public float[] tierVolumes = { 0.55f, 0.55f, 0.55f, 0.55f, 0.55f };
    public float[] tierCrossfades = { 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f };
    public boolean[] tierEnabled = { true, true, true, true, true };

    public List<TierConfig> tierConfigs = new ArrayList<>();

    public boolean killSoundOnDeath      = false;
    public boolean renderHudDuringFrames = false;
    public boolean killSound             = false;
    public int     killSoundDelayMs      = 0;
    public boolean freezeCamera          = true;
    public boolean stretchToFill         = true;
    public boolean enabled               = true;
    public boolean impactFramesEnabled   = true;
    public boolean flashHitEntity        = false;
    public boolean debugBattleScore      = false;
    public int     scoringDifficulty     = 1;

    public boolean deathFramesEnabled = false;
    public List<FrameConfig> deathFrames = new ArrayList<>(List.of(
            new FrameConfig(35, "FFFFFF"),
            new FrameConfig(35, "FFFFFF"),
            new FrameConfig(35, "FFFFFF"),
            new FrameConfig(45, "FFFFFF"),
            new FrameConfig(35, "FFFFFF"),
            new FrameConfig(35, "FFFFFF")));

    private static final Gson GSON         = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH  = FabricLoader.getInstance().getConfigDir().resolve("Impactful.json");
    private static ModConfig  INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH, new LinkOption[0])) {
            try (BufferedReader r = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                System.err.println("[Impactful] Config load failed, using defaults: " + e.getMessage());
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
        }

        if (INSTANCE.frames == null || INSTANCE.frames.isEmpty()) {
            INSTANCE.frames = new ArrayList<>(List.of(
                    new FrameConfig(150, "FFFFFF"),
                    new FrameConfig(150, "000000")));
        }

        if (INSTANCE.tierConfigs == null) INSTANCE.tierConfigs = new ArrayList<>();

        if (INSTANCE.tierVolumes == null || INSTANCE.tierVolumes.length < 5) {
            INSTANCE.tierVolumes = new float[] { 0.55f, 0.55f, 0.55f, 0.55f, 0.55f };
        }
        if (INSTANCE.tierCrossfades == null || INSTANCE.tierCrossfades.length < 8) {
            INSTANCE.tierCrossfades = new float[] { 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f };
        }
        for (int i = 0; i < INSTANCE.tierCrossfades.length; i++) {
            INSTANCE.tierCrossfades[i] = Math.max(1.0f, Math.min(5.0f, INSTANCE.tierCrossfades[i]));
        }
        if (INSTANCE.tierEnabled == null || INSTANCE.tierEnabled.length < 5) {
            INSTANCE.tierEnabled = new boolean[] { true, true, true, true, true };
        }

        migrateLegacyBackgrounds(INSTANCE.frames);
        migrateLegacyBackgrounds(INSTANCE.deathFrames);

        save();
    }

    private static void migrateLegacyBackgrounds(List<FrameConfig> list) {
        if (list == null) return;
        for (FrameConfig f : list) {
            if ("-".equals(f.backgroundColor)) {
                f.bgOpacity = 0;
                f.frameOpacity = 0;
                f.backgroundColor = "000000";
            } else if ("=".equals(f.backgroundColor)) {
                f.bgOpacity = 100;
                f.frameOpacity = 0;
                f.backgroundColor = "000000";
            }
        }
    }

    public static void save() {
        try (BufferedWriter w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            System.err.println("[Impactful] Config save failed: " + e.getMessage());
        }
    }

    private static final int[]   DEFAULT_TIER_HITS  = { 1, 1, 2, 2, 2 };
    private static final float[] DEFAULT_TIER_DECAY = { 15f, 15f, 20f, 15f, 20f };

    public TierConfig getTierConfig(int index) {
        while (tierConfigs.size() <= index) {
            int slot = tierConfigs.size();
            TierConfig tier = new TierConfig();
            if (slot < DEFAULT_TIER_HITS.length) {
                tier.hitsToAdvance = DEFAULT_TIER_HITS[slot];
                tier.decaySecs     = DEFAULT_TIER_DECAY[slot];
            }
            tierConfigs.add(tier);
        }
        return tierConfigs.get(index);
    }

    public FrameConfig getFrame(int index) {
        return index >= 0 && index < this.frames.size()
                ? this.frames.get(index)
                : new FrameConfig(150, "FFFFFF");
    }

    public void ensureFrameCount(int size) {
        while (this.frames.size() < size) {
            this.frames.add(new FrameConfig(25, "FFFFFF"));
        }
    }

    public void ensureDeathFrameCount(int count) {
        while (this.deathFrames.size() < count) {
            this.deathFrames.add(new FrameConfig(25, "FFFFFF"));
        }
    }

    public FrameConfig getDeathFrame(int index) {
        return index >= 0 && index < this.deathFrames.size()
                ? this.deathFrames.get(index)
                : new FrameConfig(25, "FFFFFF");
    }
}