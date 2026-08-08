package kuke.emotes.client.ui;

import kuke.emotes.client.EmoteController;
import kuke.emotes.client.EmoteDefinition;
import kuke.emotes.client.EmoteRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The emote wheel: hold the key, point a direction, let go to perform it.
 *
 * <p>A real ring of wedges rather than a row of boxes. {@link GuiGraphics} can only fill
 * axis-aligned rectangles, so the ring is drawn by scanning it row by row: every column of a row is
 * classified (which wedge, which radial band, or outside) and consecutive columns with the same
 * classification collapse into one {@code fill}. That is a few hundred rectangles a frame — cheap —
 * and it buys smooth arcs, per-wedge highlighting and a radial fade that a grid of boxes cannot.
 *
 * <p>Aiming is by <b>angle</b>, with a dead zone in the middle: flick the mouse in a direction and
 * release. Only unlocked emotes are listed.
 */
public final class EmoteWheelScreen extends Screen {

    private static final int SLOTS = 8;

    /* Dark-and-gold, matching the quest HUD: near-black glass with a thin gold edge. */
    private static final int GOLD = 0xD9B45A;
    private static final int GOLD_DIM = 0x8C7434;
    private static final int TEXT = 0xE8E2D4;
    private static final int TEXT_DIM = 0x9A9184;

    /** Ring radii at full open, before the selected wedge's bump. */
    private static final int INNER_RADIUS = 54;
    private static final int OUTER_RADIUS = 122;

    /** How far the selected wedge grows outward — the "pop" that makes aiming feel responsive. */
    private static final int SELECT_BUMP = 7;

    /** Space between wedges, in degrees, drawn as a gap rather than a line. */
    private static final double WEDGE_GAP_DEGREES = 1.6D;

    /** Nothing is selected inside this radius, so releasing without aiming cancels. */
    private static final double DEAD_ZONE = INNER_RADIUS - 6;

    /** Open animation length, in seconds. */
    private static final float OPEN_SECONDS = 0.16F;

    /* Scan-line classification keys that are not a wedge. */
    private static final int KEY_CENTRE = -2;
    private static final int KEY_GAP = -3;

    private final List<EmoteDefinition> catalogue = new ArrayList<>();
    private final int pages;

    private int page;
    private int selected = -1;

    /** 0..1 open progress, eased. */
    private float open;
    private long openedAt;
    private long lastFrameAt;

    /** Per-slot highlight, chased toward 0/1 each frame so selection fades rather than snaps. */
    private final float[] glow = new float[SLOTS];

    /** Remembered across openings — the wheel comes back where you left it. */
    private static int lastPage;

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

    /** Play what is aimed at and close. Called when the wheel key is released. */
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
        /* No vanilla dim or blur: the wheel's own glass is the only thing that should darken the view. */
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.open = ease(Mth.clamp(
            (System.currentTimeMillis() - this.openedAt) / (OPEN_SECONDS * 1000F), 0F, 1F));

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.selected = this.slotAt(mouseX - centerX, mouseY - centerY);
        this.advanceGlow();

        if (this.catalogue.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("kukeemotes.ui.empty"),
                centerX, centerY - 4, 0xFF000000 | TEXT_DIM);

            return;
        }

        this.drawRing(graphics, centerX, centerY);
        this.drawLabels(graphics, centerX, centerY);
        this.drawCentre(graphics, centerX, centerY);
    }

    /** Smoothstep — the wheel should arrive, not snap. */
    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    /** Chase each slot's highlight toward its target. Frame-rate independent. */
    private void advanceGlow() {
        long now = System.currentTimeMillis();
        float dt = this.lastFrameAt == 0L ? 0.016F : Math.min((now - this.lastFrameAt) / 1000F, 0.1F);

        this.lastFrameAt = now;

        float rate = Math.min(1F, dt * 14F);

        for (int slot = 0; slot < SLOTS; slot++) {
            float target = slot == this.selected ? 1F : 0F;

            this.glow[slot] += (target - this.glow[slot]) * rate;
        }
    }

    /**
     * Scan-line pass over the ring: one row at a time, one rectangle per run of columns that share
     * a (wedge, band) classification.
     */
    private void drawRing(GuiGraphics graphics, int centerX, int centerY) {
        float scale = 0.88F + 0.12F * this.open;
        int inner = (int) (INNER_RADIUS * scale);
        int outerBase = (int) (OUTER_RADIUS * scale);
        int outerMax = outerBase + SELECT_BUMP;

        double gap = Math.toRadians(WEDGE_GAP_DEGREES);
        double step = Math.PI * 2 / SLOTS;
        int innerSq = inner * inner;

        for (int dy = -outerMax; dy <= outerMax; dy++) {
            int runStart = 0;
            int runKey = Integer.MIN_VALUE;

            for (int dx = -outerMax; dx <= outerMax + 1; dx++) {
                int key = Integer.MIN_VALUE;

                if (dx <= outerMax) {
                    int distSq = dx * dx + dy * dy;

                    if (distSq < innerSq) {
                        /* Centre disc: the label on it has to stay readable over the world. */
                        key = KEY_CENTRE;
                    } else {
                        int slot = this.wedgeAt(dx, dy, step, gap);
                        int forRadius = slot < 0 ? this.selected : slot;
                        int outer = outerBase
                            + (int) (SELECT_BUMP * (forRadius >= 0 ? this.glow[forRadius] : 0F));

                        if (distSq <= outer * outer) {
                            if (slot < 0 || this.emoteAt(slot) == null) {
                                /* The gap between wedges, and any empty slot, stay dark glass —
                                 * leaving a hole there reads as a bright spoke of sky. */
                                key = KEY_GAP;
                            } else {
                                double dist = Math.sqrt(distSq);
                                int band = dist >= outer - 1.5D
                                    ? 3
                                    : dist <= inner + 1.0D
                                        ? 4
                                        : Math.min(2, (int) ((dist - inner) / (double) (outer - inner) * 3D));

                                key = slot * 8 + band;
                            }
                        }
                    }
                }

                if (key != runKey) {
                    if (runKey != Integer.MIN_VALUE) {
                        graphics.fill(centerX + runStart, centerY + dy, centerX + dx, centerY + dy + 1,
                            this.colourFor(runKey));
                    }

                    runKey = key;
                    runStart = dx;
                }
            }
        }
    }

    /** Which wedge an offset belongs to, or -1 when it falls in the gap between two. */
    private int wedgeAt(int dx, int dy, double step, double gap) {
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
        double normalised = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        double within = normalised % step;

        if (within < gap / 2 || within > step - gap / 2) {
            return -1;
        }

        return (int) (normalised / step) % SLOTS;
    }

    private int colourFor(int key) {
        if (key == KEY_CENTRE) {
            return (int) (this.open * 248F) << 24;
        }

        if (key == KEY_GAP) {
            return (int) (this.open * 242F) << 24;
        }

        return this.bandColour(key / 8, key % 8);
    }

    /**
     * Glass colour for one band of one wedge. Unselected is near-black and fades outward so the
     * ring melts into the world at its edge; selected warms toward gold. Driven by {@link #glow},
     * so moving between wedges cross-fades instead of flicking.
     */
    private int bandColour(int slot, int band) {
        float lit = this.glow[slot];

        if (band == 3) {
            int alpha = (int) (this.open * Mth.lerp(lit, 110F, 255F));

            return (alpha << 24) | lerpColour(GOLD_DIM, GOLD, lit);
        }

        if (band == 4) {
            /* Inner hairline: enough to separate ring from hub, not enough to read as a halo. */
            int alpha = (int) (this.open * Mth.lerp(lit, 64F, 200F));

            return (alpha << 24) | lerpColour(GOLD_DIM, GOLD, lit);
        }

        float fade = 1F - band * 0.07F;
        int alpha = (int) (this.open * Mth.lerp(lit, 240F, 248F) * fade);

        return (alpha << 24) | lerpColour(0x040407, 0x4A3A16, lit);
    }

    private static int lerpColour(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);

        return (r << 16) | (g << 8) | b;
    }

    private void drawLabels(GuiGraphics graphics, int centerX, int centerY) {
        float scale = 0.88F + 0.12F * this.open;
        int labelRadius = (int) ((INNER_RADIUS + OUTER_RADIUS) / 2F * scale);
        int alpha = (int) (this.open * 255F) << 24;

        for (int slot = 0; slot < SLOTS; slot++) {
            EmoteDefinition definition = this.emoteAt(slot);

            if (definition == null) {
                continue;
            }

            double angle = slot * (Math.PI * 2 / SLOTS) - Math.PI / 2;
            int radius = labelRadius + (int) (SELECT_BUMP / 2F * this.glow[slot]);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius) - this.font.lineHeight / 2;

            graphics.drawCenteredString(this.font, definition.shortTitle(), x, y,
                alpha | lerpColour(TEXT, GOLD, this.glow[slot]));
        }
    }

    private void drawCentre(GuiGraphics graphics, int centerX, int centerY) {
        int alpha = (int) (this.open * 255F) << 24;
        EmoteDefinition definition = this.selectedEmote();

        if (definition == null) {
            graphics.drawCenteredString(this.font, Component.translatable("kukeemotes.ui.title"),
                centerX, centerY - 4, alpha | TEXT_DIM);
        } else {
            graphics.drawCenteredString(this.font, definition.shortTitle(),
                centerX, centerY - 12, alpha | GOLD);

            String description = definition.description().getString();

            if (!description.isEmpty()) {
                graphics.drawCenteredString(this.font,
                    this.font.plainSubstrByWidth(description, INNER_RADIUS * 2 - 16),
                    centerX, centerY + 1, alpha | TEXT_DIM);
            }
        }

        if (this.pages > 1) {
            graphics.drawCenteredString(this.font,
                Component.translatable("kukeemotes.ui.page", this.page + 1, this.pages),
                centerX, centerY + 14, alpha | GOLD_DIM);
        }
    }

    /** Which wedge the cursor points at, ignoring the gaps so aiming never falls between slots. */
    private int slotAt(int dx, int dy) {
        if (Math.sqrt((double) dx * dx + (double) dy * dy) < DEAD_ZONE) {
            return -1;
        }

        double step = Math.PI * 2 / SLOTS;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
        double normalised = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);

        return (int) (normalised / step) % SLOTS;
    }
}
