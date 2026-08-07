package kuke.emotes.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the vecmath semantics the ported BOBJ maths depends on. If any of these change, every bone
 * transform silently changes shape — which is exactly the failure this package exists to prevent.
 */
class Matrix4fTest {

    private static final float EPS = 1.0E-5F;

    @Test
    void defaultConstructorIsZeroNotIdentity() {
        Matrix4f m = new Matrix4f();

        assertEquals(0F, m.m00);
        assertEquals(0F, m.m11);
        assertEquals(0F, m.m33);
    }

    @Test
    void setFromArrayIsRowMajor() {
        float[] values = new float[16];

        for (int i = 0; i < 16; i++) {
            values[i] = i;
        }

        Matrix4f m = new Matrix4f(values);

        assertEquals(1F, m.m01, "second array element is row 0, column 1");
        assertEquals(4F, m.m10, "fifth array element starts row 1");
        assertEquals(3F, m.m03, "translation x sits at index 3");
    }

    @Test
    void mulIsThisTimesArgument() {
        Matrix4f translate = new Matrix4f();
        translate.setIdentity();
        translate.m03 = 10F;

        Matrix4f scale = new Matrix4f();
        scale.setIdentity();
        scale.m00 = 2F;

        // this = translate * scale  →  scales first, then translates
        translate.mul(scale);

        Vector4f v = new Vector4f(1F, 0F, 0F, 1F);
        translate.transform(v);

        assertEquals(12F, v.x, EPS);
    }

    @Test
    void rotationsOverwriteTheWholeMatrix() {
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        m.m03 = 99F;
        m.rotZ((float) Math.PI / 2F);

        assertEquals(0F, m.m03, EPS, "rotZ must clear the translation column");

        Vector4f v = new Vector4f(1F, 0F, 0F, 1F);
        m.transform(v);

        assertEquals(0F, v.x, EPS);
        assertEquals(1F, v.y, EPS);
    }

    @Test
    void rotationHandednessMatchesVecmath() {
        Vector4f v = new Vector4f(1F, 0F, 0F, 1F);
        Matrix4f m = new Matrix4f();

        m.rotY((float) Math.PI / 2F);
        m.transform(v);

        // vecmath's rotY sends +X to -Z
        assertEquals(0F, v.x, EPS);
        assertEquals(-1F, v.z, EPS);
    }

    @Test
    void invertRoundTrips() {
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        m.m03 = 3F;
        m.m13 = -2F;
        m.m23 = 7F;

        Matrix4f rotation = new Matrix4f();
        rotation.rotX(0.7F);
        m.mul(rotation);

        Matrix4f inverse = new Matrix4f(m);
        inverse.invert();
        inverse.mul(m);

        assertEquals(1F, inverse.m00, EPS);
        assertEquals(1F, inverse.m11, EPS);
        assertEquals(1F, inverse.m22, EPS);
        assertEquals(1F, inverse.m33, EPS);
        assertEquals(0F, inverse.m03, EPS);
        assertEquals(0F, inverse.m12, EPS);
    }

    @Test
    void singularMatrixIsLeftAlone() {
        Matrix4f m = new Matrix4f(); // all zeroes
        m.invert();

        assertEquals(0F, m.m00, "inverting a singular matrix must not produce NaN");
        assertTrue(Float.isFinite(m.m33));
    }

    @Test
    void vector3TransformIgnoresTranslation() {
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        m.m03 = 100F;

        Vector3f normal = new Vector3f(0F, 1F, 0F);
        m.transform(normal);

        assertEquals(0F, normal.x, EPS);
        assertEquals(1F, normal.y, EPS);
    }

    @Test
    void crossProductMatchesRightHandRule() {
        Vector3f result = new Vector3f();
        result.cross(new Vector3f(1F, 0F, 0F), new Vector3f(0F, 1F, 0F));

        assertEquals(0F, result.x, EPS);
        assertEquals(0F, result.y, EPS);
        assertEquals(1F, result.z, EPS);
    }
}
