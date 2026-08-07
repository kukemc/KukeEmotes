package kuke.emotes.math;

/**
 * Drop-in replacement for {@code javax.vecmath.Vector2d} — see {@link Vector3f} for why this
 * package exists. Used only to hold BOBJ texture coordinates.
 */
public class Vector2d {

    public double x;
    public double y;

    public Vector2d() {
    }

    public Vector2d(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }
}
