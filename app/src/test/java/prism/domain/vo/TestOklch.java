package prism.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Oklch Unit Tests")
class TestOklch {

    private static final double EPSILON = 1e-3;

    @Nested
    @DisplayName("record instantiation and properties")
    class RecordTests {

        @Test
        @DisplayName("should construct Oklch record and expose components")
        void shouldConstructRecordAndExposeComponents() {
            Oklch oklch = new Oklch(0.7, 0.15, 120.0);
            assertEquals(0.7, oklch.l());
            assertEquals(0.15, oklch.c());
            assertEquals(120.0, oklch.h());
        }

        @Test
        @DisplayName("should satisfy record equality and hashCode contract")
        void shouldSatisfyEqualityAndHashCode() {
            Oklch color1 = new Oklch(0.5, 0.2, 180.0);
            Oklch color2 = new Oklch(0.5, 0.2, 180.0);
            Oklch color3 = new Oklch(0.6, 0.2, 180.0);

            assertEquals(color1, color2);
            assertEquals(color1.hashCode(), color2.hashCode());
            assertNotEquals(color1, color3);
        }

        @Test
        @DisplayName("should format toString with record components")
        void shouldFormatToString() {
            Oklch oklch = new Oklch(0.5, 0.1, 90.0);
            String str = oklch.toString();
            assertNotNull(str);
            assertTrue(str.contains("0.5"));
            assertTrue(str.contains("0.1"));
            assertTrue(str.contains("90.0"));
        }
    }

    @Nested
    @DisplayName("fromRgb")
    class FromRgbTests {

        @Test
        @DisplayName("should convert pure black (0x000000) to zero lightness and chroma")
        void shouldConvertBlack() {
            Oklch oklch = Oklch.fromRgb(0x000000);
            assertEquals(0.0, oklch.l(), EPSILON);
            assertEquals(0.0, oklch.c(), EPSILON);
        }

        @Test
        @DisplayName("should convert pure white (0xFFFFFF) to lightness 1.0 and near-zero chroma")
        void shouldConvertWhite() {
            Oklch oklch = Oklch.fromRgb(0xFFFFFF);
            assertEquals(1.0, oklch.l(), EPSILON);
            assertEquals(0.0, oklch.c(), EPSILON);
        }

        @Test
        @DisplayName("should convert pure red (0xFF0000) with expected perceptual lightness and hue")
        void shouldConvertPureRed() {
            Oklch oklch = Oklch.fromRgb(0xFF0000);
            assertEquals(0.6279, oklch.l(), 1e-2);
            assertEquals(0.2576, oklch.c(), 1e-2);
            assertEquals(29.23, oklch.h(), 1.0);
        }

        @Test
        @DisplayName("should convert pure green (0x00FF00) with expected perceptual lightness and hue")
        void shouldConvertPureGreen() {
            Oklch oklch = Oklch.fromRgb(0x00FF00);
            assertEquals(0.8664, oklch.l(), 1e-2);
            assertEquals(0.2886, oklch.c(), 1e-2);
            assertEquals(142.5, oklch.h(), 1.0);
        }

        @Test
        @DisplayName("should convert pure blue (0x0000FF) with expected perceptual lightness and hue")
        void shouldConvertPureBlue() {
            Oklch oklch = Oklch.fromRgb(0x0000FF);
            assertEquals(0.4520, oklch.l(), 1e-2);
            assertEquals(0.3132, oklch.c(), 1e-2);
            assertEquals(264.05, oklch.h(), 1.0);
        }

        @Test
        @DisplayName("should convert neutral gray with near-zero chroma")
        void shouldConvertNeutralGray() {
            Oklch oklch = Oklch.fromRgb(0x808080);
            assertEquals(0.5998, oklch.l(), 1e-2);
            assertEquals(0.0, oklch.c(), 1e-2);
        }

        @Test
        @DisplayName("should ignore alpha channel bits in packed ARGB integer")
        void shouldIgnoreAlphaChannel() {
            Oklch withAlpha = Oklch.fromRgb(0xFF4488CC);
            Oklch withoutAlpha = Oklch.fromRgb(0x004488CC);
            assertEquals(withoutAlpha, withAlpha);
        }
    }

    @Nested
    @DisplayName("toRgb")
    class ToRgbTests {

        @Test
        @DisplayName("should convert black Oklch to 0x000000")
        void shouldConvertBlackToRgb() {
            Oklch oklch = new Oklch(0.0, 0.0, 0.0);
            assertEquals(0x000000, oklch.toRgb());
        }

        @Test
        @DisplayName("should convert white Oklch to 0xFFFFFF")
        void shouldConvertWhiteToRgb() {
            Oklch oklch = new Oklch(1.0, 0.0, 0.0);
            assertEquals(0xFFFFFF, oklch.toRgb());
        }

        @Test
        @DisplayName("should round-trip pure red (0xFF0000)")
        void shouldRoundTripPureRed() {
            int original = 0xFF0000;
            assertEquals(original, Oklch.fromRgb(original).toRgb());
        }

        @Test
        @DisplayName("should round-trip pure green (0x00FF00)")
        void shouldRoundTripPureGreen() {
            int original = 0x00FF00;
            assertEquals(original, Oklch.fromRgb(original).toRgb());
        }

        @Test
        @DisplayName("should round-trip pure blue (0x0000FF)")
        void shouldRoundTripPureBlue() {
            int original = 0x0000FF;
            assertEquals(original, Oklch.fromRgb(original).toRgb());
        }

        @Test
        @DisplayName("should reduce chroma when color is outside sRGB gamut while preserving lightness and hue")
        void shouldReduceChromaWhenOutOfGamut() {
            // Chroma 0.4 at lightness 0.6, hue 140° is outside sRGB gamut
            Oklch outOfGamut = new Oklch(0.6, 0.4, 140.0);
            int rgb = outOfGamut.toRgb();

            Oklch converted = Oklch.fromRgb(rgb);

            // Chroma should be reduced to fit sRGB gamut
            assertTrue(converted.c() < outOfGamut.c(), "Chroma should be reduced from out-of-gamut value");
            assertTrue(converted.c() > 0.1, "Chroma should not collapse to zero");

            // Lightness and hue should remain preserved
            assertEquals(outOfGamut.l(), converted.l(), 0.05, "Lightness should be preserved");
            assertEquals(outOfGamut.h(), converted.h(), 5.0, "Hue should be preserved");
        }

        @Test
        @DisplayName("should fallback to zero chroma when max iterations are exhausted")
        void shouldFallbackWhenMaxIterationsExhausted() {
            Oklch extreme = new Oklch(0.5, 1000.0, 200.0);
            int rgb = extreme.toRgb();

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Grayscale color where all RGB channels are equal
            assertEquals(r, g);
            assertEquals(g, b);

            // Converted back, chroma is zero
            assertEquals(0.0, Oklch.fromRgb(rgb).c(), EPSILON);
        }
    }

    @Nested
    @DisplayName("dist")
    class DistanceTests {

        @Test
        @DisplayName("should return zero distance between identical colors")
        void shouldReturnZeroForIdenticalColors() {
            Oklch color = Oklch.fromRgb(0x336699);
            assertEquals(0.0, color.dist(color), EPSILON);
        }

        @Test
        @DisplayName("should return symmetric distance between two colors")
        void shouldReturnSymmetricDistance() {
            Oklch color1 = Oklch.fromRgb(0xFF0000);
            Oklch color2 = Oklch.fromRgb(0x00FF00);

            double d1 = color1.dist(color2);
            double d2 = color2.dist(color1);

            assertEquals(d1, d2, EPSILON);
            assertTrue(d1 > 0);
        }

        @Test
        @DisplayName("should return small distance for nearly identical colors")
        void shouldReturnSmallDistanceForSimilarColors() {
            Oklch color1 = Oklch.fromRgb(0x102030);
            Oklch color2 = Oklch.fromRgb(0x112131);

            double distance = color1.dist(color2);
            assertTrue(distance < 0.02);
        }

        @Test
        @DisplayName("should return large distance for opposing colors (black and white)")
        void shouldReturnLargeDistanceForOpposingColors() {
            Oklch black = Oklch.fromRgb(0x000000);
            Oklch white = Oklch.fromRgb(0xFFFFFF);

            double distance = black.dist(white);
            assertEquals(1.0, distance, 1e-2);
        }

        @Test
        @DisplayName("should throw NullPointerException when comparing distance to null")
        void shouldThrowNullPointerExceptionWhenOtherIsNull() {
            Oklch color = Oklch.fromRgb(0x123456);
            assertThrows(NullPointerException.class, () -> color.dist(null));
        }
    }

    @Nested
    @DisplayName("hueGap")
    class HueGapTests {

        @Test
        @DisplayName("should return 0 when both hues are identical")
        void shouldReturnZeroForIdenticalHues() {
            assertEquals(0.0, Oklch.hueGap(120.0, 120.0), EPSILON);
        }

        @Test
        @DisplayName("should return direct difference when distance is less than 180 degrees")
        void shouldReturnDirectDifferenceBelow180() {
            assertEquals(45.0, Oklch.hueGap(30.0, 75.0), EPSILON);
            assertEquals(45.0, Oklch.hueGap(75.0, 30.0), EPSILON);
        }

        @Test
        @DisplayName("should return 180 when hues are diametrically opposite")
        void shouldReturn180ForOppositeHues() {
            assertEquals(180.0, Oklch.hueGap(0.0, 180.0), EPSILON);
            assertEquals(180.0, Oklch.hueGap(90.0, 270.0), EPSILON);
        }

        @Test
        @DisplayName("should return shortest angle wrapping around the 0/360 boundary")
        void shouldWrapAroundZeroDegreeBoundary() {
            assertEquals(20.0, Oklch.hueGap(10.0, 350.0), EPSILON);
            assertEquals(20.0, Oklch.hueGap(350.0, 10.0), EPSILON);
        }

        @Test
        @DisplayName("should return 0 when hues are 360 degrees apart")
        void shouldHandle360DegreeDifference() {
            assertEquals(0.0, Oklch.hueGap(0.0, 360.0), EPSILON);
        }

        @Test
        @DisplayName("should handle hues beyond 360 degrees correctly")
        void shouldHandleHuesBeyond360() {
            assertEquals(10.0, Oklch.hueGap(370.0, 0.0), EPSILON);
        }
    }
}