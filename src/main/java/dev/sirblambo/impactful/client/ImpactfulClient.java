package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class ImpactfulClient implements ClientModInitializer {
    public static KeyBinding openConfigKey;

    private static final Identifier[] VANILLA_HUD_ELEMENTS = {
            VanillaHudElements.MISC_OVERLAYS,
            VanillaHudElements.CROSSHAIR,
            VanillaHudElements.SPECTATOR_MENU,
            VanillaHudElements.HOTBAR,
            VanillaHudElements.ARMOR_BAR,
            VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR,
            VanillaHudElements.AIR_BAR,
            VanillaHudElements.MOUNT_HEALTH,
            VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL,
            VanillaHudElements.HELD_ITEM_TOOLTIP,
            VanillaHudElements.SPECTATOR_TOOLTIP,
            VanillaHudElements.STATUS_EFFECTS,
            VanillaHudElements.BOSS_BAR,
            VanillaHudElements.SLEEP,
            VanillaHudElements.DEMO_TIMER,
            VanillaHudElements.SCOREBOARD,
            VanillaHudElements.OVERLAY_MESSAGE,
            VanillaHudElements.TITLE_AND_SUBTITLE,
            VanillaHudElements.CHAT,
            VanillaHudElements.PLAYER_LIST,
            VanillaHudElements.SUBTITLES
    };

    private static final KeyBinding.Category IMPACTFUL_CATEGORY =
            KeyBinding.Category.create(Identifier.of("impactful", "main"));

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            StatsManager.initialize();
            FrameManager.initialize();
            SoundManager.initialize();
            MusicManager.initialize();
            PauseMusicDucker.initialize();

        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicManager.shutdown();
            PauseMusicDucker.shutdown();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MusicManager.stopAll();
            MusicTracker.reset();
            BattleTracker.reset();
            KillDetector.reset();
            IncomingDamageEstimator.reset();
            DebugBattleStats.clear();
            PresenceClient.reset();
        });

        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!BattleTracker.isInBattle()) return;
            String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String root = parts[0].substring(parts[0].indexOf(':') + 1);
            if (root.equals("leave") || root.equals("lobby")) {
                StatsManager.triggerQuit(false);
            } else if (root.equals("gamemode") && parts.length > 1
                    && (parts[1].equals("creative") || parts[1].equals("spectator"))) {
                StatsManager.triggerQuit(true);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) KillDetector.onGameMessage(message);
        });

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.impactful.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                IMPACTFUL_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null) {
                    if (SoundManager.shouldWarnMultiple() && client.player != null) {
                        client.player.sendMessage(Text.literal(
                                "[\u00A75Impactful\u00A7f] Hey! You seem to have \u00A76more than one sound file \u00A7f" +
                                        "in your \u00A72\"death_soundz\" \u00A7ffolder in your instance! Please choose one and " +
                                        "stick with it, else it'll break.. idk just do it for the sake of the mod working correctly ok? " +
                                        "\u00A77Ty twin \u00A7f:3"), false);
                    }
                    client.setScreen(new ConfigScreen(null));
                }
            }

            if (FlashController.isActive() && ModConfig.get().freezeCamera && client.player != null) {
                client.player.setYaw(FlashController.getFrozenYaw());
                client.player.setPitch(FlashController.getFrozenPitch());
            }

            KillDetector.tick();
            IncomingDamageEstimator.tick();
            MusicTracker.tick();
            BattleTracker.tick();
            PresenceClient.tick(client);
        });

        HudElementRegistry.addLast(Identifier.of("impactful", "tier_hud"), (context, tickCounter) -> {
            TierHudRenderer.render(context, tickCounter);
        });

        HudElementRegistry.addFirst(Identifier.of("impactful", "death_flash_under_hud"), (context, tickCounter) -> {
            if (!ModConfig.get().renderHudDuringFrames) return;
            DeathFlashController.update();
            DeathFlashRenderer.render(context, tickCounter);
        });

        HudElementRegistry.addFirst(Identifier.of("impactful", "flash_under_hud"), (context, tickCounter) -> {
            if (!ModConfig.get().renderHudDuringFrames) return;
            FlashController.update();
            FlashRenderer.render(context, tickCounter);
        });

        HudElementRegistry.addLast(Identifier.of("impactful", "flash"), (context, tickCounter) -> {
            if (ModConfig.get().renderHudDuringFrames) return;
            FlashController.update();
            FlashRenderer.render(context, tickCounter);
        });

        HudElementRegistry.addLast(Identifier.of("impactful", "death_flash"), (context, tickCounter) -> {
            if (ModConfig.get().renderHudDuringFrames) return;
            DeathFlashController.update();
            DeathFlashRenderer.render(context, tickCounter);
        });

        for (Identifier id : VANILLA_HUD_ELEMENTS) {
            HudElementRegistry.replaceElement(id, original -> (context, tickCounter) -> {
                if (!ModConfig.get().renderHudDuringFrames
                        && (FlashController.isActive() || DeathFlashController.isActive())) return;
                original.render(context, tickCounter);
            });
        }

        HudElementRegistry.addLast(Identifier.of("impactful", "debug_stats"), (context, tickCounter) -> {
            DebugStatsRenderer.render(context, tickCounter);
        });
    }
}