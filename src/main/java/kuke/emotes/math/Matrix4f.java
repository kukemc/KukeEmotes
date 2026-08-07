package kuke.emotes.math;

/**
 * Drop-in replacement for {@code javax.vecmath.Matrix4f} — see {@link Vector3f} for why this
 * package exists.
 *
 * <p><b>Row-major</b>, like vecmath: field {@code mRC} is row R, column C, so {@code m03/m13/m23}
 * is the translation column. Every semantic that the BOBJ maths depends on is preserved:
 *
 * <ul>
 *   <li>the no-arg constructor produces the <b>zero</b> matrix, not the identity (upstream's IK
 *       modifier relies on this);</li>
 *   <li>{@link #set(float[])} fills row by row;</li>
 *   <li>{@link #mul(Matrix4f)} is {@code this = this * m};</li>
 *   <li>{@link #rotX}/{@link #rotY}/{@link #rotZ} <b>overwrite</b> the whole matrix with a pure
 *       rotation rather than multiplying into it;</li>
 *   <li>{@link #transform(Vector3f)} uses the upper-left 3×3 only (it transforms a direction).</li>
 * </ul>
 */
public class Matrix4f {

    public float m00, m01, m02, m03;
    public float m10, m11, m12, m13;
    public float m20, m21, m22, m23;
    public float m30, m31, m32, m33;

    /** vecmath's no-arg constructor leaves every element at zero. */
    public Matrix4f() {
    }

    public Matrix4f(Matrix4f other) {
        this.set(other);
    }

    public Matrix4f(float[] values) {
        this.set(values);
    }

    public void setIdentity() {
        this.m00 = 1F; this.m01 = 0F; this.m02 = 0F; this.m03 = 0F;
        this.m10 = 0F; this.m11 = 1F; this.m12 = 0F; this.m13 = 0F;
        this.m20 = 0F; this.m21 = 0F; this.m22 = 1F; this.m23 = 0F;
        this.m30 = 0F; this.m31 = 0F; this.m32 = 0F; this.m33 = 1F;
    }

    public void setZero() {
        this.m00 = this.m01 = this.m02 = this.m03 = 0F;
        this.m10 = this.m11 = this.m12 = this.m13 = 0F;
        this.m20 = this.m21 = this.m22 = this.m23 = 0F;
        this.m30 = this.m31 = this.m32 = this.m33 = 0F;
    }

    public void set(Matrix4f o) {
        this.m00 = o.m00; this.m01 = o.m01; this.m02 = o.m02; this.m03 = o.m03;
        this.m10 = o.m10; this.m11 = o.m11; this.m12 = o.m12; this.m13 = o.m13;
        this.m20 = o.m20; this.m21 = o.m21; this.m22 = o.m22; this.m23 = o.m23;
        this.m30 = o.m30; this.m31 = o.m31; this.m32 = o.m32; this.m33 = o.m33;
    }

    /** Row-major fill: {@code v[0..3]} is the first row. */
    public void set(float[] v) {
        this.m00 = v[0];  this.m01 = v[1];  this.m02 = v[2];  this.m03 = v[3];
        this.m10 = v[4];  this.m11 = v[5];  this.m12 = v[6];  this.m13 = v[7];
        this.m20 = v[8];  this.m21 = v[9];  this.m22 = v[10]; this.m23 = v[11];
        this.m30 = v[12]; this.m31 = v[13]; this.m32 = v[14]; this.m33 = v[15];
    }

    /** {@code this = this * m} */
    public void mul(Matrix4f m) {
        float r00 = this.m00 * m.m00 + this.m01 * m.m10 + this.m02 * m.m20 + this.m03 * m.m30;
        float r01 = this.m00 * m.m01 + this.m01 * m.m11 + this.m02 * m.m21 + this.m03 * m.m31;
        float r02 = this.m00 * m.m02 + this.m01 * m.m12 + this.m02 * m.m22 + this.m03 * m.m32;
        float r03 = this.m00 * m.m03 + this.m01 * m.m13 + this.m02 * m.m23 + this.m03 * m.m33;

        float r10 = this.m10 * m.m00 + this.m11 * m.m10 + this.m12 * m.m20 + this.m13 * m.m30;
        float r11 = this.m10 * m.m01 + this.m11 * m.m11 + this.m12 * m.m21 + this.m13 * m.m31;
        float r12 = this.m10 * m.m02 + this.m11 * m.m12 + this.m12 * m.m22 + this.m13 * m.m32;
        float r13 = this.m10 * m.m03 + this.m11 * m.m13 + this.m12 * m.m23 + this.m13 * m.m33;

        float r20 = this.m20 * m.m00 + this.m21 * m.m10 + this.m22 * m.m20 + this.m23 * m.m30;
        float r21 = this.m20 * m.m01 + this.m21 * m.m11 + this.m22 * m.m21 + this.m23 * m.m31;
        float r22 = this.m20 * m.m02 + this.m21 * m.m12 + this.m22 * m.m22 + this.m23 * m.m32;
        float r23 = this.m20 * m.m03 + this.m21 * m.m13 + this.m22 * m.m23 + this.m23 * m.m33;

        float r30 = this.m30 * m.m00 + this.m31 * m.m10 + this.m32 * m.m20 + this.m33 * m.m30;
        float r31 = this.m30 * m.m01 + this.m31 * m.m11 + this.m32 * m.m21 + this.m33 * m.m31;
        float r32 = this.m30 * m.m02 + this.m31 * m.m12 + this.m32 * m.m22 + this.m33 * m.m32;
        float r33 = this.m30 * m.m03 + this.m31 * m.m13 + this.m32 * m.m23 + this.m33 * m.m33;

        this.m00 = r00; this.m01 = r01; this.m02 = r02; this.m03 = r03;
        this.m10 = r10; this.m11 = r11; this.m12 = r12; this.m13 = r13;
        this.m20 = r20; this.m21 = r21; this.m22 = r22; this.m23 = r23;
        this.m30 = r30; this.m31 = r31; this.m32 = r32; this.m33 = r33;
    }

    /** {@code this = a * b} */
    public void mul(Matrix4f a, Matrix4f b) {
        this.set(a);
        this.mul(b);
    }

    /** Overwrites the matrix with a rotation of {@code angle} radians about X. */
    public void rotX(float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        this.m00 = 1F;  this.m01 = 0F;  this.m02 = 0F;   this.m03 = 0F;
        this.m10 = 0F;  this.m11 = cos; this.m12 = -sin; this.m13 = 0F;
        this.m20 = 0F;  this.m21 = sin; this.m22 = cos;  this.m23 = 0F;
        this.m30 = 0F;  this.m31 = 0F;  this.m32 = 0F;   this.m33 = 1F;
    }

    /** Overwrites the matrix with a rotation of {@code angle} radians about Y. */
    public void rotY(float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        this.m00 = cos;  this.m01 = 0F; this.m02 = sin; this.m03 = 0F;
        this.m10 = 0F;   this.m11 = 1F; this.m12 = 0F;  this.m13 = 0F;
        this.m20 = -sin; this.m21 = 0F; this.m22 = cos; this.m23 = 0F;
        this.m30 = 0F;   this.m31 = 0F; this.m32 = 0F;  this.m33 = 1F;
    }

    /** Overwrites the matrix with a rotation of {@code angle} radians about Z. */
    public void rotZ(float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        this.m00 = cos; this.m01 = -sin; this.m02 = 0F; this.m03 = 0F;
        this.m10 = sin; this.m11 = cos;  this.m12 = 0F; this.m13 = 0F;
        this.m20 = 0F;  this.m21 = 0F;   this.m22 = 1F; this.m23 = 0F;
        this.m30 = 0F;  this.m31 = 0F;   this.m32 = 0F; this.m33 = 1F;
    }

    /** {@code v = this * v}, in place (full 4×4, translation included). */
    public void transform(Vector4f v) {
        float x = this.m00 * v.x + this.m01 * v.y + this.m02 * v.z + this.m03 * v.w;
        float y = this.m10 * v.x + this.m11 * v.y + this.m12 * v.z + this.m13 * v.w;
        float z = this.m20 * v.x + this.m21 * v.y + this.m22 * v.z + this.m23 * v.w;
        float w = this.m30 * v.x + this.m31 * v.y + this.m32 * v.z + this.m33 * v.w;

        v.x = x;
        v.y = y;
        v.z = z;
        v.w = w;
    }

    /** Direction transform: upper-left 3×3 only, in place — matches vecmath's normal overload. */
    public void transform(Vector3f v) {
        float x = this.m00 * v.x + this.m01 * v.y + this.m02 * v.z;
        float y = this.m10 * v.x + this.m11 * v.y + this.m12 * v.z;
        float z = this.m20 * v.x + this.m21 * v.y + this.m22 * v.z;

        v.x = x;
        v.y = y;
        v.z = z;
    }

    /**
     * Inverts in place via Gauss-Jordan elimination with partial pivoting (vecmath uses an LU
     * decomposition; both are exact for the well-conditioned rigid transforms in a BOBJ armature).
     * A singular matrix leaves the matrix untouched rather than throwing, so one bad bone cannot
     * take the whole render down.
     */
    public void invert() {
        float[][] a = {
            {this.m00, this.m01, this.m02, this.m03, 1F, 0F, 0F, 0F},
            {this.m10, this.m11, this.m12, this.m13, 0F, 1F, 0F, 0F},
            {this.m20, this.m21, this.m22, this.m23, 0F, 0F, 1F, 0F},
            {this.m30, this.m31, this.m32, this.m33, 0F, 0F, 0F, 1F},
        };

        for (int col = 0; col < 4; col++) {
            int pivot = col;

            for (int row = col + 1; row < 4; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }

            if (Math.abs(a[pivot][col]) < 1.0E-9F) {
                return; // singular — leave as-is
            }

            if (pivot != col) {
                float[] swap = a[pivot];
                a[pivot] = a[col];
                a[col] = swap;
            }

            float diagonal = a[col][col];

            for (int k = 0; k < 8; k++) {
                a[col][k] /= diagonal;
            }

            for (int row = 0; row < 4; row++) {
                if (row == col) {
                    continue;
                }

                float factor = a[row][col];

                if (factor == 0F) {
                    continue;
                }

                for (int k = 0; k < 8; k++) {
                    a[row][k] -= factor * a[col][k];
                }
            }
        }

        this.m00 = a[0][4]; this.m01 = a[0][5]; this.m02 = a[0][6]; this.m03 = a[0][7];
        this.m10 = a[1][4]; this.m11 = a[1][5]; this.m12 = a[1][6]; this.m13 = a[1][7];
        this.m20 = a[2][4]; this.m21 = a[2][5]; this.m22 = a[2][6]; this.m23 = a[2][7];
        this.m30 = a[3][4]; this.m31 = a[3][5]; this.m32 = a[3][6]; this.m33 = a[3][7];
    }

    @Override
    public String toString() {
        return "[" + this.m00 + ", " + this.m01 + ", " + this.m02 + ", " + this.m03 + "]\n"
            + "[" + this.m10 + ", " + this.m11 + ", " + this.m12 + ", " + this.m13 + "]\n"
            + "[" + this.m20 + ", " + this.m21 + ", " + this.m22 + ", " + this.m23 + "]\n"
            + "[" + this.m30 + ", " + this.m31 + ", " + this.m32 + ", " + this.m33 + "]";
    }
}
