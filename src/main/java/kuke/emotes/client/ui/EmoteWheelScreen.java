package kuke.emotes.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import kuke.emotes.client.EmoteController;
import kuke.emotes.client.EmoteDefinition;
import kuke.emotes.client.EmoteRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The emote wheel: hold the key, point a direction, let go to perform it.
 *
 * <p>Drawn from three generated textures (see {@link EmoteWheelTextures}) rather than from
 * rectangles — {@code GuiGraphics} can only fill axis-aligned integer rects, and a ring built out
 * of those is a staircase no matter how it is coloured. One blit for the ring, one rotated blit per
 * lit wedge, one for the hub.
 *
 * <p>Aiming is by <b>angle</b> with a dead zone in the middle, so a flick of the mouse picks a slot
 * and releasing while centred cancels. Only unlocked emotes are listed.
 */
public final class EmoteWheelScreen extends Screen {

    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    private static final int SLOTS = 8;

    private static final int GOLD = 0xE7C87A;
    private static final int GOLD_DIM = 0x8A7440;
    private static final int TEXT = 0xEDE7DA;
    private static final int TEXT_DIM = 0x9E968A;

    /** Drawn diameter of the ring, in GUI pixels, at full open. */
    private static final int WHEEL_SIZE = 268;

    /** Open animation length, in seconds. */
    private static final float OPEN_SECONDS = 0.18F;

    private final List<EmoteDefinition> catalogue = new ArrayList<>();
    private final int pages;

    private int page;
    private int selected = -1;

    private float open;
    private long openedAt;
    private long lastFrameAt;

    /** Per-slot highlight, chased each frame so selection cross-fades instead of flicking. */
    private final float[] glow = new float[SLOTS];

    private static int lastPage;

    /**
     * Forces a slot to read as selected. Only set by {@code /emote debug select}: the automated
     * lab warps the system cursor, which an unfocused window ignores, so hover states cannot be
     * screenshotted any other way.
     */
    public static int debugForcedSlot = -1;

    public EmoteWheelScreen() {
        super(Component.translatable("kukeemotes.ui.title"));

        for (EmoteDefinition definition : EmoteRegistry.all()) {
            if (EmoteRegistry.isUnlocked(definition.key())) {
                this.catalogue.add(definition);
            }
        }

        this.pages = Math.max(1, (this.catalogue.size() + SLOTS - 1) / SLOTS);
        this.page = Mth.clamp(lastPage, 0, this.pages - 1);
    }

    @Override
    protected void init() {
        EmoteWheelTextures.ensureBuilt();
        this.openedAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public EmoteDefinition selectedEmote() {
        return this.selected < 0 ? null : this.emoteAt(this.selected);
    }

    private EmoteDefinition emoteAt(int slot) {
        int index = this.page * SLOTS + slot;

        return index < this.catalogue.size() ? this.catalogue.get(index) : null;
    }

    public void confirmAndClose() {
        EmoteDefinition definition = this.selectedEmote();

        lastPage = this.page;
        this.onClose();

        if (definition != null) {
            EmoteController.play(definition.key());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.turnPage(scrollY > 0 ? -1 : 1);

        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        /* A/D page as well, for anyone who would rather not scroll mid-flick. */
        if (key == 65) {
            this.turnPage(-1);

            return true;
        }

        if (key == 68) {
            this.turnPage(1);

            return true;
        }

        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (KukeEmoteKeys.WHEEL.matches(key, scanCode)) {
            this.confirmAndClose();

            return true;
        }

        return super.keyReleased(key, scanCode, modifiers);
    }

    private void turnPage(int delta) {
        if (this.pages > 1) {
            this.page = Math.floorMod(this.page + delta, this.pages);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        /* No vanilla dim or blur: the wheel's own glass is all the darkening there should be. */
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.open = ease(Mth.clamp(
            (System.currentTimeMillis() - this.openedAt) / (OPEN_SECONDS * 1000F), 0F, 1F));

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.selected = debugForcedSlot >= 0
            ? debugForcedSlot
            : this.slotAt(mouseX - centerX, mouseY - centerY);
        this.advanceGlow();

        if (this.catalogue.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("kukeemotes.ui.empty"),
                centerX, centerY - 4, 0xFF000000 | TEXT_DIM);

            return;
        }

        float scale = 0.92F + 0.08F * this.open;
        int size = Math.round(WHEEL_SIZE * scale);
        int alpha = (int) (this.open * 255F);

        blit(graphics, EmoteWheelTextures.RING, centerX, centerY, size, (alpha << 24) | 0xFFFFFF);
        this.drawWedges(graphics, centerX, centerY, size, alpha);
        blit(graphics, EmoteWheelTextures.HUB, centerX, centerY, size, (alpha << 24) | 0xFFFFFF);

        this.drawLabels(graphics, centerX, centerY, size, alpha);
        this.drawCentreText(graphics, centerX, centerY, size, alpha);
    }

    /** Smoothstep — the wheel should arrive, not snap. */
    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    private void advanceGlow() {
        long now = System.currentTimeMillis();
        float dt = this.lastFrameAt == 0L ? 0.016F : Math.min((now - this.lastFrameAt) / 1000F, 0.1F);

        this.lastFrameAt = now;

        float rate = Math.min(1F, dt * 16F);

        for (int slot = 0; slot < SLOTS; slot++) {
            float target = slot == this.selected && this.emoteAt(slot) != null ? 1F : 0F;

            this.glow[slot] += (target - this.glow[slot]) * rate;
        }
    }

    /**
     * One rotated blit per lit wedge. Rotating the pose instead of baking eight textures keeps the
     * highlight identical in every direction, and lets a fading-out wedge overlap a fading-in one.
     */
    private void drawWedges(GuiGraphics graphics, int centerX, int centerY, int size, int alpha) {
        for (int slot = 0; slot < SLOTS; slot++) {
            float lit = this.glow[slot];

            if (lit <= 0.01F) {
                continue;
            }

            int wedgeAlpha = (int) (alpha * lit);

            if (wedgeAlpha <= 0) {
                continue;
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate((float) (slot * (Math.PI * 2 / SLOTS)));
            /* The lit wedge swells very slightly — enough to feel, not enough to show a seam. */
            graphics.pose().scale(1F + 0.015F * lit, 1F + 0.015F * lit);
            graphics.pose().translate(-centerX, -centerY);

            blit(graphics, EmoteWheelTextures.WEDGE, centerX, centerY, size,
                (wedgeAlpha << 24) | 0xFFFFFF);

            graphics.pose().popMatrix();
        }
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture,
            int centerX, int centerY, int size, int colour) {
        graphics.blit(PIPELINE, texture, centerX - size / 2, centerY - size / 2,
            0F, 0F, size, size, size, size, colour);
    }

    private void drawLabels(GuiGraphics graphics, int centerX, int centerY, int size, int alpha) {
        float radius = size / 2F
            * (EmoteWheelTextures.innerFraction() + EmoteWheelTextures.outerFraction()) / 2F;

        for (int slot = 0; slot < SLOTS; slot++) {
            EmoteDefinition definition = this.emoteAt(slot);

            if (definition == null) {
                continue;
            }

            double angle = slot * (Math.PI * 2 / SLOTS) - Math.PI / 2;
            float lit = this.glow[slot];
            float distance = radius + 3F * lit;
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance) - this.font.lineHeight / 2;

            /* On the gold highlight the label flips dark, or it would drown in the wedge. */
            graphics.drawCenteredString(this.font, definition.shortTitle(), x, y,
                (alpha << 24) | (lit > 0.5F ? 0x2A1F08 : TEXT));
        }
    }

    private void drawCentreText(GuiGraphics graphics, int centerX, int centerY, int size, int alpha) {
        EmoteDefinition definition = this.selectedEmote();

        /* The hub holds one thing only: the picked emote's name (or the title while idle). */
        if (definition == null) {
            graphics.drawCenteredString(this.font, Component.translatable("kukeemotes.ui.title"),
                centerX, centerY - 4, (alpha << 24) | TEXT_DIM);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY - 4);
            graphics.pose().scale(1.15F, 1.15F);
            graphics.drawCenteredString(this.font, definition.shortTitle(), 0, 0,
                (alpha << 24) | GOLD);
            graphics.pose().popMatrix();
        }

        /* Everything secondary lives below the wheel, off the artwork. */
        int below = centerY + size / 2 + 6;

        if (definition != null) {
            String description = definition.description().getString();

            if (!description.isEmpty()) {
                graphics.drawCenteredString(this.font,
                    this.font.plainSubstrByWidth(description, 220),
                    centerX, below, (alpha << 24) | TEXT_DIM);
                below += this.font.lineHeight + 3;
            }
        }

        if (this.pages > 1) {
            this.drawPageDots(graphics, centerX, below + 2, alpha);
        }
    }

    /** One dot per page, the current one gold and slightly larger. */
    private void drawPageDots(GuiGraphics graphics, int centerX, int y, int alpha) {
        int spacing = 8;
        int left = centerX - (this.pages - 1) * spacing / 2;

        for (int i = 0; i < this.pages; i++) {
            int x = left + i * spacing;

            if (i == this.page) {
                graphics.fill(x - 2, y - 2, x + 2, y + 2, (alpha << 24) | GOLD);
            } else {
                graphics.fill(x - 1, y - 1, x + 1, y + 1,
                    ((int) (alpha * 0.7F) << 24) | GOLD_DIM);
            }
        }
    }

    /** Which wedge the cursor points at, or -1 in the dead zone. */
    private int slotAt(int dx, int dy) {
        /* Cancel zone reaches midway across the gap between hub and ring, so aiming does not
         * demand pixel accuracy but resting near the centre still cancels. */
        float deadZone = WHEEL_SIZE / 2F
            * (EmoteWheelTextures.hubFraction() + EmoteWheelTextures.innerFraction()) / 2F;

        if (Math.sqrt((double) dx * dx + (double) dy * dy) < deadZone) {
            return -1;
        }

        double step = Math.PI * 2 / SLOTS;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
        double normalised = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);

        return (int) (normalised / step) % SLOTS;
    }
}
