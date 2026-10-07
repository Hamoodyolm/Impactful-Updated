package dev.sirblambo.impactful.config;

public class TierConfig {
    public boolean enabled        = true;
    public float   volume         = 0.55f;
    public float   crossfadeSecs  = 1.5f;
    public float   decaySecs      = 15.0f;
    public int     hitsToAdvance  = 2;

    public TierConfig() {}

    public TierConfig(boolean enabled, float volume, float crossfadeSecs, float decaySecs, int hitsToAdvance) {
        this.enabled       = enabled;
        this.volume        = volume;
        this.crossfadeSecs = crossfadeSecs;
        this.decaySecs     = decaySecs;
        this.hitsToAdvance = hitsToAdvance;
    }
}