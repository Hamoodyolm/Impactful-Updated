package dev.sirblambo.impactful.config;

public class FrameConfig {
    public int durationMs = 150;
    public String backgroundColor = "FFFFFF";
    public int bgOpacity = 100;
    public int frameOpacity = 100;

    public FrameConfig() {
    }

    public FrameConfig(int durationMs, String backgroundColor) {
        this.durationMs = durationMs;
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundArgb() {
        if ("-".equals(this.backgroundColor)) return 0x00000000;
        try {
            return -16777216 | Integer.parseInt(sanitizeHex(this.backgroundColor), 16);
        } catch (NumberFormatException var2) {
            return -1;
        }
    }

    public int getBackgroundArgbWithOpacity() {
        int rgb = getBackgroundArgb() & 0xFFFFFF;
        int a = Math.round(clampOpacity(bgOpacity) * 255.0F / 100.0F);
        return (a << 24) | rgb;
    }

    public int getFrameTint() {
        int a = Math.round(clampOpacity(frameOpacity) * 255.0F / 100.0F);
        return (a << 24) | 0xFFFFFF;
    }

    public static int clampOpacity(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public float[] getContrastColor() {
        int argb = this.getBackgroundArgb();
        float r = (float)(argb >> 16 & 255) / 255.0F;
        float g = (float)(argb >> 8 & 255) / 255.0F;
        float b = (float)(argb & 255) / 255.0F;
        return new float[]{1.0F - r, 1.0F - g, 1.0F - b};
    }

    public static String sanitizeHex(String raw) {
        if ("-".equals(raw)) return "-";
        if (raw == null) return "FFFFFF";
        String clean = raw.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.length() > 6) clean = clean.substring(0, 6);
        while (clean.length() < 6) clean = "0" + clean;
        return clean.toUpperCase();
    }
}
