package kuke.emotes.util;

/**
 * The interpolation helpers the BOBJ keyframe evaluator needs.
 *
 * <p>Copied from <a href="https://github.com/mchorse/mclib">McHorse's McLib</a>
 * ({@code mchorse.mclib.utils.Interpolations}, MIT licence) so that the port carries no runtime
 * dependency on McLib. The bezier solver in particular must stay bit-identical to upstream: the
 * animation curves in {@code actions.bobj} were authored against exactly this evaluator, and a
 * "mathematically equivalent" replacement changes the feel of every emote.
 */
public final class Interpolations {

    private Interpolations() {
    }

    /** Linear interpolation. */
    public static float lerp(float a, float b, float position) {
        return a + (b - a) * position;
    }

    /**
     * Solves for the bezier parameter that yields {@code t} on the X axis, by the same iterative
     * search McLib uses (start at t, step by ±0.1, quarter and flip the step on every overshoot).
     */
    public static float bezierX(float x1, float x2, float t, final float epsilon) {
        float x = t;
        float init = bezier(0, x1, x2, 1, t);
        float factor = Math.copySign(0.1F, t - init);

        while (Math.abs(t - init) > epsilon) {
            float oldFactor = factor;

            x += factor;
            init = bezier(0, x1, x2, 1, x);

            if (Math.copySign(factor, t - init) != oldFactor) {
                factor *= -0.25F;
            }
        }

        return x;
    }

    public static float bezierX(float x1, float x2, float t) {
        return bezierX(x1, x2, t, 0.0005F);
    }

    /** Cubic bezier through de Casteljau's algorithm. */
    public static float bezier(float x1, float x2, float x3, float x4, float t) {
        float t1 = lerp(x1, x2, t);
        float t2 = lerp(x2, x3, t);
        float t3 = lerp(x3, x4, t);
        float t4 = lerp(t1, t2, t);
        float t5 = lerp(t2, t3, t);

        return lerp(t4, t5, t);
    }

    public static float clamp(float x, float min, float max) {
        return x < min ? min : (x > max ? max : x);
    }

    public static int clamp(int x, int min, int max) {
        return x < min ? min : (x > max ? max : x);
    }
}
