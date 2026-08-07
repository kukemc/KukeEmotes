package kuke.emotes.math;

/**
 * Drop-in replacement for {@code javax.vecmath.Vector3f}.
 *
 * <p>Upstream Emoticons is built on Java3D's vecmath, which does not exist on modern Minecraft.
 * JOML (which does ship with the game) is <em>column</em>-major and has different in-place
 * semantics, so swapping it in would silently change every bone transform. Instead this package
 * re-implements the handful of vecmath operations the BOBJ layer actually uses, method for method,
 * so the ported maths stays bit-for-bit the same shape as the original.
 *
 * <p>Only the members used by the port are implemented.
 */
public class Vector3f {

    public float x;
    public float y;
    public float z;

    public Vector3f() {
    }

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3f(Vector3f other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(Vector3f other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    /** {@code this -= other} */
    public void sub(Vector3f other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
    }

    /** {@code this = a - b} */
    public void sub(Vector3f a, Vector3f b) {
        this.x = a.x - b.x;
        this.y = a.y - b.y;
        this.z = a.z - b.z;
    }

    /** {@code this += other} */
    public void add(Vector3f other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
    }

    /** {@code this *= scale} */
    public void scale(float scale) {
        this.x *= scale;
        this.y *= scale;
        this.z *= scale;
    }

    public float length() {
        return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public float dot(Vector3f other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * Normalises in place. vecmath divides by the length unconditionally (producing NaN for a zero
     * vector); guard instead — a zero-length bone axis is a data problem, not a reason to poison
     * the whole armature with NaN.
     */
    public void normalize() {
        float length = this.length();

        if (length != 0F) {
            this.x /= length;
            this.y /= length;
            this.z /= length;
        }
    }

    /** {@code this = a × b} (vecmath computes into temporaries so aliasing with a/b is safe). */
    public void cross(Vector3f a, Vector3f b) {
        float x = a.y * b.z - a.z * b.y;
        float y = b.x * a.z - b.z * a.x;

        this.z = a.x * b.y - a.y * b.x;
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }
}
