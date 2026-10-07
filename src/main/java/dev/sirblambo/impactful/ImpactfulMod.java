package dev.sirblambo.impactful;

import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.api.ModInitializer;

public class ImpactfulMod implements ModInitializer {
    public static final String MOD_ID = "impactful";

    public void onInitialize() {
        ModConfig.load();
    }
}
