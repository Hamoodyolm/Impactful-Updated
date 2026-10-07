package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class GeneralSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget difficultyButton;

    public GeneralSettingsScreen(Screen parent) {
        super(Text.literal("General Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.get();
        int midX = this.width / 2;
        int colW = (this.width - 60) / 2;
        int leftX = 20;
        int rightX = midX + 10;
        int y = this.height / 2 - 60;

        this.addDrawableChild(ButtonWidget.builder(toggleText("Freeze Camera", config.freezeCamera), btn -> {
            config.freezeCamera = !config.freezeCamera;
            btn.setMessage(toggleText("Freeze Camera", config.freezeCamera));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Stretch To Fill", config.stretchToFill), btn -> {
            config.stretchToFill = !config.stretchToFill;
            btn.setMessage(toggleText("Stretch To Fill", config.stretchToFill));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Kill Sound", config.killSound), btn -> {
            config.killSound = !config.killSound;
            btn.setMessage(toggleText("Kill Sound", config.killSound));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Render HUD During Frames", config.renderHudDuringFrames), btn -> {
            config.renderHudDuringFrames = !config.renderHudDuringFrames;
            btn.setMessage(toggleText("Render HUD During Frames", config.renderHudDuringFrames));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(new SliderWidget(leftX, y, colW, 20,
                Text.literal("Kill Sound Delay (ms): " + config.killSoundDelayMs),
                config.killSoundDelayMs / 2500.0) {
            @Override
            protected void updateMessage() {
                int val = (int)(this.value * 2500);
                this.setMessage(Text.literal("Kill Sound Delay (ms): " + val));
            }
            @Override
            protected void applyValue() {
                config.killSoundDelayMs = (int)(this.value * 2500);
                ModConfig.save();
            }
        });

        this.addDrawableChild(ButtonWidget.builder(toggleText("Kill Sound On YOUR Death", config.killSoundOnDeath), btn -> {
            config.killSoundOnDeath = !config.killSoundOnDeath;
            btn.setMessage(toggleText("Kill Sound On YOUR Death", config.killSoundOnDeath));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Stats After Fight", config.showStatsAfterFight), btn -> {
            config.showStatsAfterFight = !config.showStatsAfterFight;
            btn.setMessage(toggleText("Stats After Fight", config.showStatsAfterFight));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Duck Music On Pause", config.pauseMusicDuck), btn -> {
            config.pauseMusicDuck = !config.pauseMusicDuck;
            btn.setMessage(toggleText("Duck Music On Pause", config.pauseMusicDuck));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Battle Score", config.debugBattleScore), btn -> {
            config.debugBattleScore = !config.debugBattleScore;
            btn.setMessage(toggleText("Battle Score", config.debugBattleScore));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        difficultyButton = this.addDrawableChild(ButtonWidget.builder(difficultyText(MatchGrader.currentDifficulty()), btn -> {
            if (BattleTracker.isInBattle()) return;
            MatchGrader.Difficulty next = MatchGrader.currentDifficulty().next();
            config.scoringDifficulty = next.ordinal();
            btn.setMessage(difficultyText(next));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());
        refreshDifficultyButton();

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Impactful User Icons", config.showImpactfulUsers), btn -> {
            config.showImpactfulUsers = !config.showImpactfulUsers;
            btn.setMessage(toggleText("Impactful User Icons", config.showImpactfulUsers));
            if (!config.showImpactfulUsers) PresenceClient.reset();
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§lGeneral Settings"), this.width / 2, 10, -1);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        refreshDifficultyButton();
    }

    private void refreshDifficultyButton() {
        if (difficultyButton == null) return;
        boolean locked = BattleTracker.isInBattle();
        difficultyButton.active = !locked;
        difficultyButton.setMessage(locked
                ? Text.literal("Scoring Difficulty: " + MatchGrader.currentDifficulty().label + " (In Fight)")
                : difficultyText(MatchGrader.currentDifficulty()));
    }

    private Text difficultyText(MatchGrader.Difficulty difficulty) {
        return Text.literal("Scoring Difficulty: " + difficulty.color + difficulty.label);
    }

    private Text toggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "§aON" : "§cOFF"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}