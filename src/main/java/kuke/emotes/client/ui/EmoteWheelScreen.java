package kuke.emotes.client.ui;

import kuke.emotes.client.EmoteController;
import kuke.emotes.client.EmoteDefinition;
import kuke.emotes.client.EmoteRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The emote wheel: hold the key, point at a slot, let go to perform it.
 *
 * <p>Eight slots on a ring with the selected emote's name and description in the middle. Scroll (or
 * A/D) pages through the full catalogue eight at a time. Pointing is by <em>angle</em> from the
 * centre rather than by hovering a rectangle, so flicking the mouse in a direction is enough — the
 * slot boxes are just where the label is drawn.
 *
 * <p>Drawn with plain {@code GuiGraphics} rectangles in the server's dark-and-gold palette. No
 * texture atlas, no dependency on KukeUI's screen stack — this mod has to work on its own.
 */
public final class EmoteWheelScreen extends Screen {

    private static final int SLOTS = 8;

    /* Dark-and-gold palette (matches the KukeMC native screens). */
    private static final int PANEL = 0xF2141014;
    private static final int PANEL_EDGE = 0xFF6B5A2E;
    private static final int SLOT = 0xF01E1A22;
    private static final int SLOT_EDGE = 0xFF4A4250;
    private static final int SLOT_SELECTED = 0xF04A3A16;
    private static final int GOLD = 0xFFD9B45A;
    private static final int TEXT = 0xFFE8E2D4;
    private static final int TEXT_DIM = 0xFF8A8272;

    private static final int RING_RADIUS = 104;
    private static final int SLOT_MIN_WIDTH = 54;
    private static final int SLOT_PADDING = 14;
    private static final int SLOT_HEIGHT = 26;

    /** Dead zone around the centre where nothing is selected, so releasing without aiming cancels. */
    private static final double DEAD_ZONE = 28.0D;

    private final List<EmoteDefinition> catalogue;
    private final int pages;

    private int page;
    private int selected = -1;

    /** Remembered across openings — the wheel comes back where you left it. */
    private static int lastPage;

    public EmoteWheelScreen() {
        super(Component.translatable("kukeemotes.ui.title"));

        this.catalogue = EmoteRegistry.all();
        this.pages = Math.max(1, (this.catalogue.size() + SLOTS - 1) / SLOTS);
        this.page = Mth.clamp(lastPage, 0, this.pages - 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The emote under the cursor right now, or null when in the dead zone. */
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

    private void turnPage(int delta) {
        this.page = Math.floorMod(this.page + delta, this.pages);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (KukeEmoteKeys.WHEEL.matches(key, scanCode)) {
            this.confirmAndClose();

            return true;
        }

        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.selected = this.slotAt(mouseX - centerX, mouseY - centerY);

        this.drawCentrePanel(graphics, centerX, centerY);

        for (int slot = 0; slot < SLOTS; slot++) {
            EmoteDefinition definition = this.emoteAt(slot);

            if (definition == null) {
                continue;
            }

            Component label = definition.shortTitle();
            int slotWidth = Math.max(SLOT_MIN_WIDTH, this.font.width(label) + SLOT_PADDING * 2);

            double angle = slotAngle(slot);
            int x = centerX + (int) Math.round(Math.cos(angle) * RING_RADIUS) - slotWidth / 2;
            int y = centerY + (int) Math.round(Math.sin(angle) * RING_RADIUS) - SLOT_HEIGHT / 2;

            this.drawSlot(graphics, x, y, slotWidth, label, slot == this.selected);
        }
    }

    /**
     * Which slot an offset from the centre points at. Angles are measured the same way the slots
     * are laid out, so the whole 45° wedge around a slot selects it — not just its label box.
     */
    private int slotAt(int dx, int dy) {
        if (Math.sqrt((double) dx * dx + (double) dy * dy) < DEAD_ZONE) {
            return -1;
        }

        double angle = Math.atan2(dy, dx);
        double step = Math.PI * 2 / SLOTS;
        /* Slot 0 sits at -90°; shift by half a wedge so the boundaries fall between slots. */
        int slot = (int) Math.floor(((angle + Math.PI / 2 + step / 2) % (Math.PI * 2)) / step);

        return Math.floorMod(slot, SLOTS);
    }

    private static double slotAngle(int slot) {
        return slot * (Math.PI * 2 / SLOTS) - Math.PI / 2;
    }

    private void drawCentrePanel(GuiGraphics graphics, int centerX, int centerY) {
        int width = 150;
        int height = 46;
        int x = centerX - width / 2;
        int y = centerY - height / 2;

        graphics.fill(x, y, x + width, y + height, PANEL);
        this.drawBorder(graphics, x, y, width, height, PANEL_EDGE);

        EmoteDefinition definition = this.selectedEmote();
        Component title = definition == null
            ? Component.translatable("kukeemotes.ui.title")
            : definition.title();

        graphics.drawCenteredString(this.font, title, centerX, y + 8, definition == null ? TEXT_DIM : GOLD);

        if (definition != null) {
            String description = definition.description().getString();

            if (!description.isEmpty()) {
                graphics.drawCenteredString(this.font,
                    this.font.plainSubstrByWidth(description, width - 12), centerX, y + 20, TEXT_DIM);
            }
        }

        graphics.drawCenteredString(this.font,
            Component.translatable("kukeemotes.ui.page", this.page + 1, this.pages), centerX, y + 33, TEXT_DIM);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, int width, Component label, boolean selected) {
        graphics.fill(x, y, x + width, y + SLOT_HEIGHT, selected ? SLOT_SELECTED : SLOT);
        this.drawBorder(graphics, x, y, width, SLOT_HEIGHT, selected ? GOLD : SLOT_EDGE);

        graphics.drawCenteredString(this.font, label, x + width / 2,
            y + (SLOT_HEIGHT - this.font.lineHeight) / 2 + 1, selected ? GOLD : TEXT);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        /* No blur or dim: the wheel is meant to sit over a clear view of the world. */
    }
}
