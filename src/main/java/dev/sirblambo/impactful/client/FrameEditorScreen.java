package dev.sirblambo.impactful.client;

import dev.sirblambo.impactful.config.FrameConfig;
import dev.sirblambo.impactful.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class FrameEditorScreen extends Screen {
    private final Screen parent;

    private boolean deathMode = false;
    private int selected = -1;
    private int listScroll = 0;

    private TextFieldWidget bgField;
    private TextFieldWidget frameField;
    private TextFieldWidget durField;
    private TextFieldWidget colField;
    private ButtonWidget topToggle;
    private final List<ButtonWidget> frameButtons = new ArrayList<>();

    private static final int LIST_X = 6;
    private static final int LIST_W = 104;
    private static final int ROW_H = 20;
    private static final int FIELD_W = 70;
    private static final int CONTROL_GAP = 8;

    private static final int DIVIDER_X = LIST_X + LIST_W + 6;

    public FrameEditorScreen(Screen parent) {
        super(Text.literal("Impact Frames"));
        this.parent = parent;
    }

    private static final int BTN_PAD = 12;

    private int listTop()    { return 44; }
    private int listBottom() { return this.height - 34; }

    private int controlCenterX() {
        int previewLeft = this.width / 2 + 11;
        return (DIVIDER_X + previewLeft) / 2;
    }

    private int centeredX(int width) {
        return controlCenterX() - width / 2;
    }

    private int controlFieldX() {
        int labelW = Math.max(
                this.textRenderer.getWidth("BG Opacity"),
                Math.max(
                        this.textRenderer.getWidth("Frame Opacity"),
                        this.textRenderer.getWidth("Duration (ms)")
                )
        );
        int groupW = labelW + CONTROL_GAP + FIELD_W;
        return controlCenterX() - groupW / 2 + labelW + CONTROL_GAP;
    }

    private int controlLabelRight() {
        return controlFieldX() - CONTROL_GAP;
    }

    private int ctrlW()      { return (this.width / 2 - 6) - (DIVIDER_X + 6); }
    private int fitW(String label) { return this.textRenderer.getWidth(label) + BTN_PAD * 2; }
    private int frameCount() { return deathMode ? FrameManager.getDeathFrameCount() : FrameManager.getFrameCount(); }
    private FrameConfig frameAt(int i) {
        ModConfig c = ModConfig.get();
        return deathMode ? c.getDeathFrame(i) : c.getFrame(i);
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.get();

        int fieldX = controlFieldX();

        topToggle = ButtonWidget.builder(topToggleText(), btn -> {
            ModConfig c = ModConfig.get();
            if (deathMode) c.deathFramesEnabled = !c.deathFramesEnabled;
            else           c.impactFramesEnabled = !c.impactFramesEnabled;
            ModConfig.save();
            btn.setMessage(topToggleText());
            btn.setWidth(fitW(topToggleText().getString()));
        }).dimensions(centeredX(fitW(topToggleText().getString())), 8,
                fitW(topToggleText().getString()), 20).build();
        this.addDrawableChild(topToggle);

        int cy = 40;
        bgField = new TextFieldWidget(this.textRenderer, fieldX, cy, FIELD_W, 18, Text.literal("0-100"));
        bgField.setMaxLength(3);
        this.addDrawableChild(bgField);

        cy += 24;
        frameField = new TextFieldWidget(this.textRenderer, fieldX, cy, FIELD_W, 18, Text.literal("0-100"));
        frameField.setMaxLength(3);
        this.addDrawableChild(frameField);

        cy += 24;
        durField = new TextFieldWidget(this.textRenderer, fieldX, cy, FIELD_W, 18, Text.literal("ms"));
        durField.setMaxLength(4);
        this.addDrawableChild(durField);

        cy += 24;
        colField = new TextFieldWidget(this.textRenderer, fieldX, cy, FIELD_W, 18, Text.literal("RRGGBB"));
        colField.setMaxLength(6);
        this.addDrawableChild(colField);

        cy += 28;
        String setLbl = "Set";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(setLbl), btn -> applySet())
                .dimensions(centeredX(fitW(setLbl)), cy, fitW(setLbl), 20).build());

        cy += 24;
        String trigLbl = "Frame Triggers..";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(trigLbl), btn ->
                        this.client.setScreen(new FrameTriggersScreen(this)))
                .dimensions(centeredX(fitW(trigLbl)), cy, fitW(trigLbl), 20).build());

        cy += 24;
        String reloadLbl = "\u27F3  Reload Frames";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(reloadLbl), btn -> {
            FrameManager.reload();
            if (selected >= frameCount()) selectFrame(-1);
            rebuildList();
        }).dimensions(centeredX(fitW(reloadLbl)), cy, fitW(reloadLbl), 20).build());

        cy += 24;
        String doneLbl = "Done";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(doneLbl), btn ->
                        this.client.setScreen(this.parent))
                .dimensions(centeredX(fitW(doneLbl)), cy, fitW(doneLbl), 20).build());

        int modeBtnW = (ctrlW() - 4) / 2;
        int modeRowX = centeredX(modeBtnW * 2 + 4);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Impact Frames"), btn -> switchMode(false))
                .dimensions(modeRowX, this.height - 28, modeBtnW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Death Frames"), btn -> switchMode(true))
                .dimensions(modeRowX + modeBtnW + 4, this.height - 28, modeBtnW, 20).build());

        selectFrame(-1);
        rebuildList();
    }

    private Text topToggleText() {
        ModConfig c = ModConfig.get();
        boolean on = deathMode ? c.deathFramesEnabled : c.impactFramesEnabled;
        String label = deathMode ? "Death Frames" : "Impact Frames";
        return Text.literal(label + ": " + (on ? "§aON" : "§cOFF"));
    }

    private void switchMode(boolean death) {
        if (deathMode == death) return;
        deathMode = death;
        listScroll = 0;
        topToggle.setMessage(topToggleText());
        topToggle.setWidth(fitW(topToggleText().getString()));
        selectFrame(-1);
        rebuildList();
    }

    private void rebuildList() {
        frameButtons.forEach(this::remove);
        frameButtons.clear();

        int count = frameCount();
        int top = listTop();
        int bottom = listBottom();

        for (int i = 0; i < count; i++) {
            int by = top + i * ROW_H - listScroll;
            if (by + ROW_H <= top || by >= bottom) continue;

            int idx = i;
            String name = (deathMode ? "Death " : "") + "Frame " + (i + 1);
            ButtonWidget b = ButtonWidget.builder(Text.literal(name), btn -> selectFrame(idx))
                    .dimensions(LIST_X, by, LIST_W, ROW_H - 2).build();
            frameButtons.add(b);
            this.addDrawableChild(b);
        }
    }

    private void selectFrame(int i) {
        selected = i;
        boolean has = i >= 0 && i < frameCount();
        bgField.setEditable(has);
        frameField.setEditable(has);
        durField.setEditable(has);
        colField.setEditable(has);

        if (has) {
            FrameConfig fc = frameAt(i);
            bgField.setText(String.valueOf(FrameConfig.clampOpacity(fc.bgOpacity)));
            frameField.setText(String.valueOf(FrameConfig.clampOpacity(fc.frameOpacity)));
            durField.setText(String.valueOf(fc.durationMs));
            colField.setText(FrameConfig.sanitizeHex(fc.backgroundColor));
        } else {
            bgField.setText("");
            frameField.setText("");
            durField.setText("");
            colField.setText("");
        }
    }

    private void applySet() {
        if (selected < 0 || selected >= frameCount()) return;
        FrameConfig fc = frameAt(selected);

        fc.bgOpacity    = FrameConfig.clampOpacity(parseInt(bgField.getText(), fc.bgOpacity));
        fc.frameOpacity = FrameConfig.clampOpacity(parseInt(frameField.getText(), fc.frameOpacity));
        fc.durationMs   = Math.max(10, Math.min(1000, parseInt(durField.getText(), fc.durationMs)));
        fc.backgroundColor = FrameConfig.sanitizeHex(colField.getText());
        ModConfig.save();

        selectFrame(selected);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (mouseX <= LIST_X + LIST_W && mouseY >= listTop() && mouseY <= listBottom()) {
            int contentH = frameCount() * ROW_H;
            int visibleH = listBottom() - listTop();
            int maxScroll = Math.max(0, contentH - visibleH);
            listScroll = (int) Math.max(0, Math.min(maxScroll, listScroll - v * 12.0));
            rebuildList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int midX = this.width / 2;

        context.fill(DIVIDER_X - 1, 0, DIVIDER_X + 1, this.height, -10066330);

        if (selected >= 0) {
            int by = listTop() + selected * ROW_H - listScroll;
            if (by + ROW_H > listTop() && by < listBottom()) {
                int bx0 = LIST_X - 1;
                int by0 = by - 1;
                int bx1 = LIST_X + LIST_W + 1;
                int by1 = by + ROW_H - 2 + 1;
                context.fill(bx0, by0, bx1, by0 + 1, -1);
                context.fill(bx0, by1 - 1, bx1, by1, -1);
                context.fill(bx0, by0, bx0 + 1, by1, -1);
                context.fill(bx1 - 1, by0, bx1, by1, -1);
            }
        }

        int labelRight = controlLabelRight();

        drawRightAlignedLabel(context, "BG Opacity", labelRight, 44);
        drawRightAlignedLabel(context, "Frame Opacity", labelRight, 68);
        drawRightAlignedLabel(context, "Duration (ms)", labelRight, 92);

        drawRightAlignedLabel(context, "Color:", labelRight - 22, 116);

        if (colField != null && colField.isVisible()) {
            try {
                int argb = -16777216 | Integer.parseInt(FrameConfig.sanitizeHex(colField.getText()), 16);
                int sx = controlFieldX() - 18;
                int sy = 116;
                context.fill(sx, sy, sx + 14, sy + 14, argb);
                context.fill(sx, sy, sx + 14, sy + 1, -8947849);
                context.fill(sx, sy + 13, sx + 14, sy + 14, -8947849);
                context.fill(sx, sy + 1, sx + 1, sy + 13, -8947849);
                context.fill(sx + 13, sy + 1, sx + 14, sy + 13, -8947849);
            } catch (NumberFormatException ignored) {}
        }

        int loaded = frameCount();
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(loaded + " " + (deathMode ? "death " : "") + "frame" + (loaded == 1 ? "" : "s")),
                LIST_X, listTop() - 12, -1);

        renderPreview(context, midX);

        if (FrameManager.hasErrors()) {
            int ey = listBottom() - 12 * FrameManager.getErrors().size() - 4;
            context.drawTextWithShadow(this.textRenderer, Text.literal("§c⚠ Fix:"), LIST_X, ey - 12, -1);
            for (String err : FrameManager.getErrors()) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("§c" + err), LIST_X, ey, -1);
                ey += 11;
            }
        }
    }

    private void drawRightAlignedLabel(DrawContext context, String label, int rightX, int y) {
        int w = this.textRenderer.getWidth(label);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(label), rightX - w, y, -1);
    }

    private void renderPreview(DrawContext context, int midX) {
        int px0 = midX + 11;
        int py0 = 35;
        int px1 = this.width - 11;
        int py1 = this.height - 41;
        int pw = px1 - px0;
        int ph = py1 - py0;
        if (pw <= 0 || ph <= 0) return;

        boolean has = selected >= 0 && selected < frameCount();
        boolean hasTex = has && (deathMode
                ? FrameManager.hasDeathTexture(selected)
                : FrameManager.hasTexture(selected));

        drawPreviewBorder(context, px0, py0, px1, py1);

        if (!has || !hasTex) {
            String msg = !has ? "No Frame Selected" : "No image file";
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(msg), (px0 + px1) / 2, (py0 + py1) / 2 - 4, -1);
            return;
        }

        FrameConfig fc = frameAt(selected);

        int bgArgb = fc.getBackgroundArgbWithOpacity();
        if ((bgArgb >>> 24) != 0) {
            context.fill(px0, py0, px1, py1, bgArgb);
        }

        int texW = deathMode ? FrameManager.getDeathTextureWidth(selected) : FrameManager.getTextureWidth(selected);
        int texH = deathMode ? FrameManager.getDeathTextureHeight(selected) : FrameManager.getTextureHeight(selected);
        Identifier tex = deathMode ? FrameManager.getDeathTexture(selected) : FrameManager.getTexture(selected);
        if (texW <= 0 || texH <= 0) return;

        float scale = Math.min(pw / (float) texW, ph / (float) texH);
        int dw = Math.max(1, (int) (texW * scale));
        int dh = Math.max(1, (int) (texH * scale));
        int dx = px0 + (pw - dw) / 2;
        int dy = py0 + (ph - dh) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                dx, dy, 0.0F, 0.0F, dw, dh, dw, dh, fc.getFrameTint());
    }

    private void drawPreviewBorder(DrawContext context, int px0, int py0, int px1, int py1) {
        int col = -16777216;
        int gap = 1;
        int bx0 = px0 - gap;
        int by0 = py0 - gap;
        int bx1 = px1 + gap;
        int by1 = py1 + gap;
        context.fill(bx0 - 1, by0 - 1, bx1 + 1, by0, col);
        context.fill(bx0 - 1, by1, bx1 + 1, by1 + 1, col);
        context.fill(bx0 - 1, by0, bx0, by1, col);
        context.fill(bx1, by0, bx1 + 1, by1, col);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}