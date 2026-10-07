package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FrameTriggersScreen extends Screen {
    private final Screen parent;

    public FrameTriggersScreen(Screen parent) {
        super(Text.literal("Frame Triggers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.get();
        int midX = this.width / 2;
        int colW = (this.width - 60) / 2;
        int leftX = 20;
        int rightX = midX + 10;

        int y = 40;

        this.addDrawableChild(ButtonWidget.builder(toggleText("Any", config.triggerOnAny), btn -> {
            config.triggerOnAny = !config.triggerOnAny;
            btn.setMessage(toggleText("Any", config.triggerOnAny));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Hostile", config.triggerOnHostile), btn -> {
            config.triggerOnHostile = !config.triggerOnHostile;
            btn.setMessage(toggleText("Hostile", config.triggerOnHostile));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Players", config.triggerOnPlayers), btn -> {
            config.triggerOnPlayers = !config.triggerOnPlayers;
            btn.setMessage(toggleText("Players", config.triggerOnPlayers));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 44;

        this.addDrawableChild(ButtonWidget.builder(toggleText("Any Hit", config.triggerOnAnyHit), btn -> {
            config.triggerOnAnyHit = !config.triggerOnAnyHit;
            btn.setMessage(toggleText("Any Hit", config.triggerOnAnyHit));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Criticals", config.triggerOnCriticals), btn -> {
            config.triggerOnCriticals = !config.triggerOnCriticals;
            btn.setMessage(toggleText("Criticals", config.triggerOnCriticals));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("Only Mace Smash", config.triggerOnMaceSmash), btn -> {
            config.triggerOnMaceSmash = !config.triggerOnMaceSmash;
            btn.setMessage(toggleText("Only Mace Smash", config.triggerOnMaceSmash));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(toggleText("Trigger On Kill", config.triggerOnKill), btn -> {
            config.triggerOnKill = !config.triggerOnKill;
            btn.setMessage(toggleText("Trigger On Kill", config.triggerOnKill));
            ModConfig.save();
        }).dimensions(rightX, y, colW, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(toggleText("On Totem Pop", config.triggerOnTotemPop), btn -> {
            config.triggerOnTotemPop = !config.triggerOnTotemPop;
            btn.setMessage(toggleText("On Totem Pop", config.triggerOnTotemPop));
            ModConfig.save();
        }).dimensions(leftX, y, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> {
            if (hasConflict()) return;
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    private boolean hasConflict() {
        ModConfig config = ModConfig.get();
        if (config.triggerOnAnyHit && (config.triggerOnKill || config.triggerOnCriticals || config.triggerOnMaceSmash)) return true;
        return false;
    }

    private String conflictMessage() {
        ModConfig config = ModConfig.get();
        if (config.triggerOnAnyHit && config.triggerOnKill) return "Any Hit and Trigger On Kill";
        if (config.triggerOnAnyHit && config.triggerOnCriticals) return "Any Hit and Criticals";
        if (config.triggerOnAnyHit && config.triggerOnMaceSmash) return "Any Hit and Only Mace Smash";
        return "";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int midX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§lFrame Triggers"), midX, 10, -1);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§7Entity Type"), 20, 28, -1);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§7Triggers"), 20, 92, -1);

        if (hasConflict()) {
            String msg = "§c§lConflicting settings! Please turn off either " + conflictMessage() + "!";
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(msg), midX, this.height - 50, -1);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private Text toggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "§aON" : "§cOFF"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}