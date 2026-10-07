package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private static final int BTN_W = 200;
    private static final int BTN_H = 20;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BTN_W / 2;
        int y = this.height / 2 - 64;

        this.addDrawableChild(ButtonWidget.builder(masterText(), btn -> {
            ModConfig config = ModConfig.get();
            config.enabled = !config.enabled;
            btn.setMessage(masterText());
            ModConfig.save();
        }).dimensions(x, y, BTN_W, BTN_H).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Impact Frames.."), btn ->
                this.client.setScreen(new FrameEditorScreen(this))
        ).dimensions(x, y, BTN_W, BTN_H).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("General Settings..."), btn ->
                this.client.setScreen(new GeneralSettingsScreen(this))
        ).dimensions(x, y, BTN_W, BTN_H).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("PvP Music..."), btn ->
                this.client.setScreen(new PvpMusicScreen(this))
        ).dimensions(x, y, BTN_W, BTN_H).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn ->
                this.client.setScreen(this.parent)
        ).dimensions(x, this.height - 30, BTN_W, BTN_H).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, -1);
    }

    private Text masterText() {
        return Text.literal("Mod is: " + (ModConfig.get().enabled ? "§aEnabled" : "§cDisabled"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
