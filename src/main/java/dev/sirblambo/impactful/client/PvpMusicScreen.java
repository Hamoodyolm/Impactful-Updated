package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.ModConfig;
import dev.sirblambo.impactful.config.TierConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class PvpMusicScreen extends Screen {

    private final Screen parent;

    private static final String[] TIER_LABELS = {
            "Tier 1", "Tier 2", "Tier 3", "Tier 4", "Tier 5"
    };

    private static final double FADE_MAX_SECS = 5.0;

    private static final String TUTORIAL_TEXT = "Click Here to open Tier audio creation tutorial";
    private static final String TUTORIAL_URL = "https://youtu.be/GfDSatn8gQo";

    private static final int[][] TIER_CF_SLOTS = {
            {0},
            {1, 2},
            {3, 4},
            {5, 6},
            {7}
    };

    private int selectedTier = 2;

    private final List<SliderWidget> sliders = new ArrayList<>();
    private final List<ButtonWidget> dynamicButtons = new ArrayList<>();

    private ButtonWidget previewButton;

    private int tierNavX;
    private int tierNavY;
    private int tierNavW;
    private int tierNavH;
    private int tierNavGap;

    public PvpMusicScreen(Screen parent) {
        super(Text.literal("PvP Music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        sliders.forEach(this::remove);
        dynamicButtons.forEach(this::remove);

        sliders.clear();
        dynamicButtons.clear();

        buildGlobal();
        buildTierNavigation();
        buildEditor();
    }

    private int sidebarWidth() {
        return clamp((int) (this.width * 0.16), 136, 160);
    }

    private int dividerX() {
        return sidebarWidth();
    }

    private int contentX() {
        return sidebarWidth() + 18;
    }

    private int contentRight() {
        return this.width - 12;
    }

    private int contentWidth() {
        return Math.max(300, contentRight() - contentX());
    }

    private int sidebarX() {
        return 10;
    }

    private int sidebarControlWidth() {
        return Math.min(128, Math.max(108, sidebarWidth() - 18));
    }

    private int sy(int referenceY) {
        double scale = Math.max(0.82, Math.min(1.12, this.height / 400.0));
        return (int) Math.round(referenceY * scale);
    }

    private int tierNavBottom() {
        return sy(156) + 4 * (16 + 2) + 16;
    }

    private void buildGlobal() {
        ModConfig cfg = ModConfig.get();

        int x = sidebarX();
        int w = sidebarControlWidth();

        addButton(ButtonWidget.builder(
                toggleText("PvP Music", cfg.pvpMusic),
                btn -> {
                    cfg.pvpMusic = !cfg.pvpMusic;
                    btn.setMessage(toggleText("PvP Music", cfg.pvpMusic));
                    ModConfig.save();
                }
        ).dimensions(x, sy(22), w, 20).build());

        addSlider(new SliderWidget(
                x,
                sy(70),
                w,
                18,
                Text.literal(pct(cfg.musicMasterVolume)),
                clamp01(cfg.musicMasterVolume)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(pct((float) value)));
            }

            @Override
            protected void applyValue() {
                cfg.musicMasterVolume = (float) value;
                MusicManager.updateActiveVolumes();
                ModConfig.save();
            }
        });

        addSlider(new SliderWidget(
                x,
                sy(112),
                w,
                18,
                Text.literal(cfg.musicProximityRadius + "b"),
                clamp01(cfg.musicProximityRadius / 200.0)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal((int) (value * 200) + "b"));
            }

            @Override
            protected void applyValue() {
                cfg.musicProximityRadius = Math.max(1, (int) (value * 200));
                ModConfig.save();
            }
        });

        int flowY = tierNavBottom() + 10;

        addButton(ButtonWidget.builder(
                toggleText("Tier HUD", cfg.showTierHud),
                btn -> {
                    cfg.showTierHud = !cfg.showTierHud;
                    btn.setMessage(toggleText("Tier HUD", cfg.showTierHud));
                    if (cfg.showTierHud) TierHudRenderer.onTierChanged(MusicManager.getCurrentTier());
                    ModConfig.save();
                }
        ).dimensions(x, flowY, w, 20).build());

        addButton(ButtonWidget.builder(
                toggleText("Finish Track", cfg.pvpMusicFinishTrack),
                btn -> {
                    cfg.pvpMusicFinishTrack = !cfg.pvpMusicFinishTrack;
                    btn.setMessage(toggleText("Finish Track", cfg.pvpMusicFinishTrack));
                    ModConfig.save();
                }
        ).dimensions(x, flowY + 24, w, 20).build());

        int reloadY = Math.max(sy(332), flowY + 52);
        addButton(ButtonWidget.builder(
                Text.literal("Reload Audio(s)"),
                btn -> MusicManager.reload()
        ).dimensions(x, reloadY, w, 20).build());

        int backW = 80;
        int backX = x + (w - backW) / 2;
        addButton(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.client.setScreen(parent)
        ).dimensions(backX, Math.min(this.height - 28, Math.max(sy(360), reloadY + 24)), backW, 20).build());
    }

    private void buildTierNavigation() {
        int fullW = sidebarControlWidth();
        tierNavW = 84;
        tierNavX = sidebarX() + (fullW - tierNavW) / 2;
        tierNavY = sy(156);
        tierNavH = 16;
        tierNavGap = 2;

        for (int i = 0; i < 5; i++) {
            final int tier = i;

            String label = TIER_LABELS[i];
            if (tier == selectedTier) {
                label = "§e§l" + label;
            }

            ButtonWidget button = ButtonWidget.builder(
                    Text.literal(label),
                    btn -> {
                        selectedTier = tier;
                        rebuild();
                    }
            ).dimensions(
                    tierNavX,
                    tierNavY + i * (tierNavH + tierNavGap),
                    tierNavW,
                    tierNavH
            ).build();

            addButton(button);
        }
    }

    private void buildEditor() {
        ModConfig cfg = ModConfig.get();
        TierConfig tierCfg = cfg.getTierConfig(selectedTier);

        int x = contentX();
        int w = contentWidth();

        int columnGap = Math.max(16, (int) (w * 0.06));
        int columnW = Math.max(110, (w - columnGap) / 2);
        int rightColumnX = x + columnW + columnGap;

        addButton(ButtonWidget.builder(
                toggleText(TIER_LABELS[selectedTier], cfg.tierEnabled[selectedTier]),
                btn -> {
                    cfg.tierEnabled[selectedTier] = !cfg.tierEnabled[selectedTier];
                    btn.setMessage(toggleText(
                            TIER_LABELS[selectedTier],
                            cfg.tierEnabled[selectedTier]
                    ));
                    ModConfig.save();
                }
        ).dimensions(x, sy(22), Math.min(120, columnW), 20).build());

        addSlider(new SliderWidget(
                x,
                sy(70),
                columnW,
                18,
                Text.literal(pct(cfg.tierVolumes[selectedTier])),
                clamp01(cfg.tierVolumes[selectedTier])
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(pct((float) value)));
            }

            @Override
            protected void applyValue() {
                cfg.tierVolumes[selectedTier] = (float) value;
                MusicManager.updateActiveVolumes();
                ModConfig.save();
            }
        });

        if (selectedTier < 4) {
            addSlider(new SliderWidget(
                    x,
                    sy(132),
                    columnW,
                    18,
                    Text.literal(String.valueOf(tierCfg.hitsToAdvance)),
                    clamp01((tierCfg.hitsToAdvance - 1) / 9.0)
            ) {
                @Override
                protected void updateMessage() {
                    setMessage(Text.literal(
                            String.valueOf(1 + (int) Math.round(value * 9))
                    ));
                }

                @Override
                protected void applyValue() {
                    tierCfg.hitsToAdvance = 1 + (int) Math.round(value * 9);
                    ModConfig.save();
                }
            });
        }

        if (selectedTier > 0) {
            addSlider(new SliderWidget(
                    x,
                    sy(180),
                    columnW,
                    18,
                    Text.literal(Math.round(tierCfg.decaySecs) + "s"),
                    clamp01((tierCfg.decaySecs - 1) / 59.0)
            ) {
                @Override
                protected void updateMessage() {
                    setMessage(Text.literal(
                            Math.round(1 + value * 59) + "s"
                    ));
                }

                @Override
                protected void applyValue() {
                    tierCfg.decaySecs = 1f + (float) Math.round(value * 59);
                    ModConfig.save();
                }
            });
        }

        int[] slots = TIER_CF_SLOTS[selectedTier];

        if (selectedTier > 0) {
            int slot = slots[0];

            addSlider(new SliderWidget(
                    rightColumnX,
                    sy(132),
                    columnW,
                    18,
                    Text.literal(fadeLabel(cfg.tierCrossfades[slot])),
                    clamp01((cfg.tierCrossfades[slot] - 1.0) / (FADE_MAX_SECS - 1.0))
            ) {
                @Override
                protected void updateMessage() {
                    setMessage(Text.literal(
                            fadeLabel((float) (1.0 + value * (FADE_MAX_SECS - 1.0)))
                    ));
                }

                @Override
                protected void applyValue() {
                    cfg.tierCrossfades[slot] =
                            (float) (1.0 + value * (FADE_MAX_SECS - 1.0));
                    ModConfig.save();
                }
            });
        }

        if (selectedTier < 4) {
            int slot = slots.length > 1 ? slots[1] : slots[0];

            addSlider(new SliderWidget(
                    rightColumnX,
                    sy(180),
                    columnW,
                    18,
                    Text.literal(fadeLabel(cfg.tierCrossfades[slot])),
                    clamp01((cfg.tierCrossfades[slot] - 1.0) / (FADE_MAX_SECS - 1.0))
            ) {
                @Override
                protected void updateMessage() {
                    setMessage(Text.literal(
                            fadeLabel((float) (1.0 + value * (FADE_MAX_SECS - 1.0)))
                    ));
                }

                @Override
                protected void applyValue() {
                    cfg.tierCrossfades[slot] =
                            (float) (1.0 + value * (FADE_MAX_SECS - 1.0));
                    ModConfig.save();
                }
            });
        }

        int testingY = sy(248);
        int btnH = 22;
        int gap = 8;
        int totalBtnW = w;
        int previewW = Math.max(100, (int) (totalBtnW * 0.30));
        int testW = Math.max(90, (totalBtnW - previewW - gap * 2) / 2);

        int previewX = x;
        int testUpX = previewX + previewW + gap;
        int testDownX = testUpX + testW + gap;

        previewButton = ButtonWidget.builder(
                previewText(),
                btn -> {
                    if (MusicManager.isPreviewing()) {
                        MusicManager.stopPreview();
                    } else {
                        MusicManager.startPreview(Tier.values()[selectedTier]);
                    }
                }
        ).dimensions(previewX, testingY, previewW, btnH).build();
        previewButton.active = MusicManager.hasClip(Tier.values()[selectedTier]);
        addButton(previewButton);

        String upLabel = selectedTier < 4
                ? "Test Up to " + TIER_LABELS[selectedTier + 1]
                : "Test Up";
        ButtonWidget testUp = ButtonWidget.builder(
                Text.literal(upLabel),
                btn -> MusicManager.runTierTest(
                        Tier.values()[selectedTier],
                        Tier.values()[selectedTier + 1]
                )
        ).dimensions(testUpX, testingY, testW, btnH).build();
        testUp.active = selectedTier < 4
                && MusicManager.hasClip(Tier.values()[selectedTier])
                && MusicManager.hasClip(Tier.values()[selectedTier + 1]);
        addButton(testUp);

        String downLabel = selectedTier > 0
                ? "Test Down to " + TIER_LABELS[selectedTier - 1]
                : "Test Down";
        ButtonWidget testDown = ButtonWidget.builder(
                Text.literal(downLabel),
                btn -> MusicManager.runTierTest(
                        Tier.values()[selectedTier],
                        Tier.values()[selectedTier - 1]
                )
        ).dimensions(testDownX, testingY, testW, btnH).build();
        testDown.active = selectedTier > 0
                && MusicManager.hasClip(Tier.values()[selectedTier])
                && MusicManager.hasClip(Tier.values()[selectedTier - 1]);
        addButton(testDown);
    }

    private void addButton(ButtonWidget button) {
        dynamicButtons.add(button);
        this.addDrawableChild(button);
    }

    private void addSlider(SliderWidget slider) {
        sliders.add(slider);
        this.addDrawableChild(slider);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int divider = dividerX();
        int contentX = contentX();

        context.fill(0, 0, this.width, this.height, 0x99000000);

        context.fill(
                divider,
                0,
                divider + 1,
                this.height,
                0xFFAAAAAA
        );

        drawLabel(context, "Master Volume", sidebarX(), sy(54));
        drawLabel(
                context,
                "Detection Radius: " + ModConfig.get().musicProximityRadius + "b",
                sidebarX(),
                sy(96)
        );
        drawCenteredLabel(
                context,
                "Music Tiers",
                sidebarX() + sidebarControlWidth() / 2,
                sy(140)
        );

        if (tierNavW > 0) {
            int selY = tierNavY + selectedTier * (tierNavH + tierNavGap);
            context.fill(tierNavX - 1, selY - 1, tierNavX + tierNavW + 1, selY + tierNavH + 1, 0xFFFFFFFF);
            context.fill(tierNavX, selY, tierNavX + tierNavW, selY + tierNavH, 0xFF2A2A2A);
        }

        int w = contentWidth();
        int columnGap = Math.max(16, (int) (w * 0.06));
        int columnW = Math.max(110, (w - columnGap) / 2);
        int rightColumnX = contentX + columnW + columnGap;

        drawLabel(context, "Volume", contentX, sy(54));

        if (selectedTier < 4) {
            drawLabel(context, "Hits Till Escalate", contentX, sy(114));
        }

        if (selectedTier > 0) {
            drawLabel(context, "Time Till De-escalate", contentX, sy(162));
        }

        if (selectedTier > 0) {
            drawLabel(
                    context,
                    "Fade Length to " + TIER_LABELS[selectedTier - 1],
                    rightColumnX,
                    sy(114)
            );
        }

        if (selectedTier < 4) {
            drawLabel(
                    context,
                    "Fade Length to " + TIER_LABELS[selectedTier + 1],
                    rightColumnX,
                    sy(162)
            );
        }

        drawLabel(context, "Preview:", contentX, sy(218));
        drawLabel(context, TIER_LABELS[selectedTier], contentX + 52, sy(218));

        if (previewButton != null) previewButton.setMessage(previewText());

        super.render(context, mouseX, mouseY, delta);

        boolean hovered = isOverTutorialLink(mouseX, mouseY);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal((hovered ? "§e" : "§b") + "§n" + TUTORIAL_TEXT),
                tutorialX(),
                tutorialY(),
                0xFFFFFFFF
        );
    }

    private int tutorialX() {
        return this.width - this.textRenderer.getWidth(TUTORIAL_TEXT) - 8;
    }

    private int tutorialY() {
        return this.height - this.textRenderer.fontHeight - 8;
    }

    private boolean isOverTutorialLink(double mouseX, double mouseY) {
        int x = tutorialX();
        int y = tutorialY();
        return mouseX >= x && mouseX < x + this.textRenderer.getWidth(TUTORIAL_TEXT)
                && mouseY >= y - 1 && mouseY < y + this.textRenderer.fontHeight + 1;
    }

    private void drawLabel(
            DrawContext context,
            String text,
            int x,
            int y
    ) {
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(text),
                x,
                y,
                0xFFFFFFFF
        );
    }

    private void drawCenteredLabel(
            DrawContext context,
            String text,
            int centerX,
            int y
    ) {
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(text),
                centerX,
                y,
                0xFFFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (isOverTutorialLink(click.x(), click.y())) {
            ConfirmLinkScreen.open(this, TUTORIAL_URL);
            return true;
        }
        if (MusicManager.isPreviewing()
                && (previewButton == null || !previewButton.isMouseOver(click.x(), click.y()))) {
            MusicManager.stopPreview();
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void removed() {
        MusicManager.stopPreview();
        super.removed();
    }

    private Text previewText() {
        return Text.literal(MusicManager.isPreviewing() ? "■ Stop" : "♪ Preview");
    }

    private Text toggleText(String label, boolean value) {
        return Text.literal(
                label + ": " + (value ? "§aOn" : "§cOff")
        );
    }

    private String pct(float value) {
        return (int) (value * 100) + "%";
    }

    private String fadeLabel(float value) {
        return String.format("%.2fs", value);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}