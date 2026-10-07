package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public class FrameManager {
    private static final Path FRAMES_DIR = FabricLoader.getInstance().getGameDir().resolve("Impactful").resolve("frames");
    private static final String[] BAD_EXTENSIONS = new String[]{"jpg", "jpeg", "bmp", "gif", "webp", "tga"};
    private static final List<Identifier> loadedTextures = new ArrayList<>();
    private static final List<Identifier> loadedDeathTextures = new ArrayList<>();
    private static final List<int[]> frameSizes = new ArrayList<>();
    private static final List<int[]> deathFrameSizes = new ArrayList<>();
    private static final List<String> errors = new ArrayList<>();
    private static boolean showGhostSlot = false;

    public static void initialize() {
        Path oldDir = FabricLoader.getInstance().getGameDir().resolve("impactful");
        Path newDir = FabricLoader.getInstance().getGameDir().resolve("Impactful");
        if (Files.exists(oldDir) && !Files.exists(newDir)) {
            try {
                Files.move(oldDir, newDir);
                System.out.println("[Impactful] Migrated impactful folder to Impactful.");
            } catch (Exception e) {
                System.err.println("[Impactful] Could not migrate folder: " + e.getMessage());
            }
        }

        try {
            Files.createDirectories(FRAMES_DIR);
        } catch (Exception e) {
            System.err.println("[Impactful] Could not create frames dir: " + e.getMessage());
        }

        copyDefaultsIfEmpty();
        reload();
    }

    private static void copyDefaultsIfEmpty() {
        boolean hasKillFrames = false;
        try {
            hasKillFrames = Files.list(FRAMES_DIR)
                    .anyMatch(p -> p.getFileName().toString().matches("frame\\d+\\.png"));
        } catch (Exception ignored) {}

        if (!hasKillFrames) {
            for (int i = 1; i <= 4; ++i) {
                String resourcePath = "/assets/impactful/defaults/frame" + i + ".png";
                Path dest = FRAMES_DIR.resolve("frame" + i + ".png");
                try (InputStream is = FrameManager.class.getResourceAsStream(resourcePath)) {
                    if (is != null) Files.copy(is, dest, new CopyOption[0]);
                } catch (Exception e) {
                    System.err.println("[Impactful] Could not copy default frame " + i + ": " + e.getMessage());
                }
            }
            try {
                String guide = "HOW TO ADD IMPACT FRAMES\n========================\n\nFrames are loaded from this folder in order: frame1.png, frame2.png, frame3.png ...\nThe mod stops loading at the first gap, so don't skip numbers.\n\nREQUIREMENTS\n  - Files MUST be .png (RGBA, transparent background)\n  - Recommended resolution: 1920x1080\n  - Name them exactly: frame1.png, frame2.png, frame3.png, etc.\n\nFRAME DESIGN TIPS\n  - The mod fills the screen with the frame's background color first.\n  - Your PNG is drawn ON TOP of that color fill.\n  - Press U in-game to open the config screen at any time.\n  - Hit \"Reload Frames\" after adding, replacing, or editing files.\n";
                Files.writeString(FRAMES_DIR.resolve("HOW_TO_ADD_FRAMES.txt"), guide, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[Impactful] Could not write guide: " + e.getMessage());
            }
        }

        for (int i = 1; i <= 6; ++i) {
            Path dest = FRAMES_DIR.resolve("deathframe" + i + ".png");
            if (Files.exists(dest)) continue;
            String resourcePath = "/assets/impactful/defaults/deathframe" + i + ".png";
            try (InputStream is = FrameManager.class.getResourceAsStream(resourcePath)) {
                if (is != null) Files.copy(is, dest, new CopyOption[0]);
            } catch (Exception e) {
                System.err.println("[Impactful] Could not copy default death frame " + i + ": " + e.getMessage());
            }
        }
    }

    public static void reload() {
        MinecraftClient client = MinecraftClient.getInstance();

        for (Identifier id : loadedTextures) {
            client.getTextureManager().destroyTexture(id);
        }

        loadedTextures.clear();
        frameSizes.clear();
        errors.clear();
        showGhostSlot = false;
        int i = 1;

        while (true) {
            for (String ext : BAD_EXTENSIONS) {
                Path bad = FRAMES_DIR.resolve("frame" + i + "." + ext);
                if (Files.exists(bad, new LinkOption[0])) {
                    errors.add("frame" + i + "." + ext + "  ←  must be .png");
                }
            }

            Path path = FRAMES_DIR.resolve("frame" + i + ".png");
            if (!Files.exists(path, new LinkOption[0])) {
                break;
            }

            final int frameIndex = i;

            try (InputStream is = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(is);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "impactful:frame" + frameIndex, image);
                Identifier texId = Identifier.of("impactful", "dynamic/frame" + frameIndex);
                client.getTextureManager().registerTexture(texId, tex);
                loadedTextures.add(texId);
                frameSizes.add(new int[]{image.getWidth(), image.getHeight()});
            } catch (Exception e) {
                errors.add("frame" + i + ".png  ←  failed to load: " + e.getMessage());
                break;
            }

            ++i;
        }

        ModConfig modConfig = ModConfig.get();
        modConfig.ensureFrameCount(loadedTextures.size());
        ModConfig.save();

        reloadDeathFrames();
    }

    private static void reloadDeathFrames() {
        MinecraftClient client = MinecraftClient.getInstance();

        for (Identifier id : loadedDeathTextures) {
            client.getTextureManager().destroyTexture(id);
        }
        loadedDeathTextures.clear();
        deathFrameSizes.clear();

        int i = 1;
        while (true) {
            Path path = FRAMES_DIR.resolve("deathframe" + i + ".png");
            if (!Files.exists(path, new LinkOption[0])) break;

            final int frameIndex = i;
            try (InputStream is = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(is);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(
                        () -> "impactful:deathframe" + frameIndex, image);
                Identifier texId = Identifier.of("impactful", "dynamic/deathframe" + frameIndex);
                client.getTextureManager().registerTexture(texId, tex);
                loadedDeathTextures.add(texId);
                deathFrameSizes.add(new int[]{image.getWidth(), image.getHeight()});
            } catch (Exception e) {
                errors.add("deathframe" + i + ".png  ←  failed to load: " + e.getMessage());
                break;
            }
            i++;
        }

        ModConfig.get().ensureDeathFrameCount(loadedDeathTextures.size());
        ModConfig.save();
    }

    public static void addNextFrame() {
        int nextIndex = loadedTextures.size() + 1;

        for (String ext : BAD_EXTENSIONS) {
            Path bad = FRAMES_DIR.resolve("frame" + nextIndex + "." + ext);
            if (Files.exists(bad, new LinkOption[0])) {
                errors.add("frame" + nextIndex + "." + ext + "  ←  must be .png");
            }
        }

        Path path = FRAMES_DIR.resolve("frame" + nextIndex + ".png");
        if (!Files.exists(path, new LinkOption[0])) {
            showGhostSlot = true;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        final int frameIndex = nextIndex;

        try (InputStream is = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(is);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "impactful:frame" + frameIndex, image);
            Identifier texId = Identifier.of("impactful", "dynamic/frame" + frameIndex);
            client.getTextureManager().registerTexture(texId, tex);
            loadedTextures.add(texId);
            frameSizes.add(new int[]{image.getWidth(), image.getHeight()});
            showGhostSlot = false;
        } catch (Exception e) {
            errors.add("frame" + nextIndex + ".png  ←  failed to load: " + e.getMessage());
            showGhostSlot = true;
        }

        ModConfig modConfig = ModConfig.get();
        modConfig.ensureFrameCount(loadedTextures.size());
        ModConfig.save();
    }

    public static boolean hasGhostSlot() { return showGhostSlot; }
    public static int ghostSlotIndex() { return loadedTextures.size() + 1; }
    public static int getFrameCount() { return loadedTextures.size(); }
    public static List<String> getErrors() { return errors; }
    public static boolean hasErrors() { return !errors.isEmpty(); }
    public static boolean hasTexture(int i) { return i >= 0 && i < loadedTextures.size(); }
    public static Identifier getTexture(int i) { return loadedTextures.get(i); }
    public static Path getFramesDir() { return FRAMES_DIR; }

    public static int getTextureWidth(int i)  { return i >= 0 && i < frameSizes.size() ? frameSizes.get(i)[0] : 0; }
    public static int getTextureHeight(int i) { return i >= 0 && i < frameSizes.size() ? frameSizes.get(i)[1] : 0; }

    public static int getDeathFrameCount() { return loadedDeathTextures.size(); }
    public static boolean hasDeathTexture(int i) { return i >= 0 && i < loadedDeathTextures.size(); }
    public static Identifier getDeathTexture(int i) { return loadedDeathTextures.get(i); }

    public static int getDeathTextureWidth(int i)  { return i >= 0 && i < deathFrameSizes.size() ? deathFrameSizes.get(i)[0] : 0; }
    public static int getDeathTextureHeight(int i) { return i >= 0 && i < deathFrameSizes.size() ? deathFrameSizes.get(i)[1] : 0; }

    public static int getDominantColor(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= loadedTextures.size()) return 0xFFFFFFFF;

        Path path = FRAMES_DIR.resolve("frame" + (frameIndex + 1) + ".png");
        try (InputStream is = Files.newInputStream(path)) {
            NativeImage img = NativeImage.read(is);
            long r = 0, g = 0, b = 0, count = 0;
            int step = Math.max(1, img.getWidth() / 64);
            for (int x = 0; x < img.getWidth(); x += step) {
                for (int y = 0; y < img.getHeight(); y += step) {
                    int color = img.getColorArgb(x, y);
                    int alpha = (color >> 24) & 0xFF;
                    if (alpha > 10) {
                        r += (color >> 16) & 0xFF;
                        g += (color >> 8) & 0xFF;
                        b += color & 0xFF;
                        count++;
                    }
                }
            }
            img.close();
            if (count == 0) return 0xFFFFFFFF;
            int ar = (int)(r / count);
            int ag = (int)(g / count);
            int ab = (int)(b / count);
            return 0xFF000000 | ((255 - ar) << 16) | ((255 - ag) << 8) | (255 - ab);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }
}