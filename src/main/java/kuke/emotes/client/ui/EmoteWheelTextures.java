package kuke.emotes.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import kuke.emotes.KukeEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The wheel's artwork, generated once into three textures.
 *
 * <p><b>Why textures and not rectangles.</b> {@code GuiGraphics} can only fill axis-aligned
 * integer rectangles, so a ring assembled from them is a staircase — no amount of colour tuning
 * fixes that. Here each pixel is evaluated analytically instead: distance to the ring edges and to
 * the wedge's angular edges becomes a coverage value, and coverage becomes alpha. The result has
 * genuinely smooth arcs and lets the edges glow softly rather than stopping dead.
 *
 * <p>Three pieces, each drawn with a single blit:
 * <ul>
 *   <li>{@link #RING} — the dark glass ring, radial gradient, soft gold rim.</li>
 *   <li>{@link #WEDGE} — one 45° highlight, pointing up. The screen rotates it to the selected
 *       slot, so one texture serves all eight.</li>
 *   <li>{@link #HUB} — the centre disc the label sits on.</li>
 * </ul>
 */
public final class EmoteWheelTextures {

    /** Texture resolution. Generous, so the ring stays crisp when the GUI is scaled up. */
    private static final int SIZE = 512;
    private static final float CENTRE = SIZE / 2F;

    /* Normalised radii (fraction of the texture's half-width). */
    private static final float RING_INNER = 0.395F;
    private static final float RING_OUTER = 0.965F;
    /** Deliberately larger than {@link #RING_INNER}: the two must overlap, or the gap
     * between them shows the world through as a bright ring. */
    private static final float HUB_RADIUS = 0.420F;

    /** Half-width of a wedge, in radians: 45° minus a small breathing gap. */
    private static final float WEDGE_HALF = (float) Math.toRadians(21.2D);

    public static final ResourceLocation RING =
        ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, "wheel/ring");
    public static final ResourceLocation WEDGE =
        ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, "wheel/wedge");
    public static final ResourceLocation HUB =
        ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, "wheel/hub");

    private static boolean built;

    private EmoteWheelTextures() {
    }

    /** Build and register the three textures. Safe to call repeatedly; only the first does work. */
    public static void ensureBuilt() {
        if (built) {
            return;
        }

        built = true;

        try {
            register(RING, buildRing());
            register(WEDGE, buildWedge());
            register(HUB, buildHub());
        } catch (Throwable t) {
            KukeEmotes.LOGGER.error("Could not build the emote wheel textures", t);
        }
    }

    private static void register(ResourceLocation id, NativeImage image) {
        Minecraft.getInstance().getTextureManager()
            .register(id, new DynamicTexture(id::toString, image));
    }

    /**
     * Smooth 0→1 over a one-pixel-ish band. Everything soft in these textures goes through here,
     * which is what stops the arcs from stair-stepping.
     */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.min(1F, Math.max(0F, (x - edge0) / (edge1 - edge0)));

        return t * t * (3F - 2F * t);
    }

    private static int argb(float alpha, int rgb) {
        int a = (int) (Math.min(1F, Math.max(0F, alpha)) * 255F);

        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /** Blend {@code over} onto {@code under}, both premultiplied-free ARGB. */
    private static int over(int under, int over) {
        float sa = ((over >>> 24) & 0xFF) / 255F;
        float da = ((under >>> 24) & 0xFF) / 255F;
        float outA = sa + da * (1F - sa);

        if (outA <= 0F) {
            return 0;
        }

        float r = channel(over, 16) * sa + channel(under, 16) * da * (1F - sa);
        float g = channel(over, 8) * sa + channel(under, 8) * da * (1F - sa);
        float b = channel(over, 0) * sa + channel(under, 0) * da * (1F - sa);

        return ((int) (outA * 255F) << 24)
            | ((int) (r / outA * 255F) << 16)
            | ((int) (g / outA * 255F) << 8)
            | (int) (b / outA * 255F);
    }

    private static float channel(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255F;
    }

    /**
     * The ring: near-black glass, slightly more opaque on the inside so it grounds the labels and
     * fades toward the world at the rim, with a soft gold edge top and bottom.
     */
    private static NativeImage buildRing() {
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        float px = 1F / CENTRE;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x + 0.5F - CENTRE) / CENTRE;
                float dy = (y + 0.5F - CENTRE) / CENTRE;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                /* Coverage: fade in just inside the inner edge, out just inside the outer. */
                float coverage = smoothstep(RING_INNER - px * 1.5F, RING_INNER + px * 1.5F, dist)
                    * (1F - smoothstep(RING_OUTER - px * 1.5F, RING_OUTER + px * 1.5F, dist));

                if (coverage <= 0F) {
                    image.setPixel(x, y, 0);

                    continue;
                }

                float t = (dist - RING_INNER) / (RING_OUTER - RING_INNER);
                float glassAlpha = (0.90F - 0.16F * t) * coverage;
                int pixel = argb(glassAlpha, 0x0A0910);

                /* Outer rim: a soft gold line that bleeds a couple of pixels inward. */
                float outerRim = 1F - smoothstep(0F, px * 3.5F, Math.abs(dist - (RING_OUTER - px * 1.6F)));
                if (outerRim > 0F) {
                    pixel = over(pixel, argb(outerRim * 0.85F * coverage, 0xD9B45A));
                }

                image.setPixel(x, y, pixel);
            }
        }

        return image;
    }

    /**
     * One highlight wedge, pointing up. Gold that is strongest at the inner edge and along the
     * wedge's own arc, falling off toward its angular edges so neighbouring slots never show a
     * hard seam.
     */
    private static NativeImage buildWedge() {
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        float px = 1F / CENTRE;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x + 0.5F - CENTRE) / CENTRE;
                float dy = (y + 0.5F - CENTRE) / CENTRE;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float radial = smoothstep(RING_INNER - px * 1.5F, RING_INNER + px * 1.5F, dist)
                    * (1F - smoothstep(RING_OUTER - px * 1.5F, RING_OUTER + px * 1.5F, dist));

                if (radial <= 0F) {
                    image.setPixel(x, y, 0);

                    continue;
                }

                /* Angle from straight up, folded so both sides of the wedge behave the same. */
                float angle = Math.abs((float) Math.atan2(dx, -dy));
                float angular = 1F - smoothstep(WEDGE_HALF - 0.05F, WEDGE_HALF, angle);

                if (angular <= 0F) {
                    image.setPixel(x, y, 0);

                    continue;
                }

                float t = (dist - RING_INNER) / (RING_OUTER - RING_INNER);
                /* Brighter near the hub, so the highlight looks lit from the centre outward. */
                float body = (0.46F - 0.13F * t) * radial * angular;
                int pixel = argb(body, 0xE0BC6A);

                float arc = 1F - smoothstep(0F, px * 6F, Math.abs(dist - (RING_OUTER - px * 2.2F)));

                if (arc > 0F) {
                    pixel = over(pixel, argb(arc * angular * 0.95F, 0xFFE6A8));
                }

                image.setPixel(x, y, pixel);
            }
        }

        return image;
    }

    /** The centre disc: darker than the ring so the label reads, with its own faint gold edge. */
    private static NativeImage buildHub() {
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        float px = 1F / CENTRE;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x + 0.5F - CENTRE) / CENTRE;
                float dy = (y + 0.5F - CENTRE) / CENTRE;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float coverage = 1F - smoothstep(HUB_RADIUS - px * 1.5F, HUB_RADIUS + px * 1.5F, dist);

                if (coverage <= 0F) {
                    image.setPixel(x, y, 0);

                    continue;
                }

                /* A touch lighter at the very centre keeps it from looking like a hole. */
                float lift = 0.06F * (1F - smoothstep(0F, HUB_RADIUS, dist));
                int pixel = argb((0.86F + lift) * coverage, 0x07060B);

                float rim = 1F - smoothstep(0F, px * 2.6F, Math.abs(dist - (HUB_RADIUS - px * 1.3F)));

                if (rim > 0F) {
                    pixel = over(pixel, argb(rim * 0.45F * coverage, 0xC9A550));
                }

                image.setPixel(x, y, pixel);
            }
        }

        return image;
    }

    /** Screen-space radius of the ring's outer edge for a given drawn size. */
    public static float outerFraction() {
        return RING_OUTER;
    }

    public static float innerFraction() {
        return RING_INNER;
    }
}
