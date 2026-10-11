package prism.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ColorUtils Unit Tests")
class TestColorUtils {

    @Nested
    @DisplayName("quantizeColor")
    class QuantizeColorTests {

        @Test
        @DisplayName("should throw NullPointerException when rgb parameter is null")
        void shouldThrowNullPointerExceptionWhenRgbIsNull() {
            assertThrows(NullPointerException.class, () -> ColorUtils.quantizeColor(null));
        }

        @Test
        @DisplayName("should quantize black (0x000000) to 0x000")
        void shouldQuantizeBlack() {
            int result = ColorUtils.quantizeColor(0x000000);
            assertEquals(0x000, result);
        }

        @Test
        @DisplayName("should quantize white (0xFFFFFF) to 0xFFF")
        void shouldQuantizeWhite() {
            int result = ColorUtils.quantizeColor(0xFFFFFF);
            assertEquals(0xFFF, result);
        }

        @Test
        @DisplayName("should quantize pure red (0xFF0000) to 0xF00")
        void shouldQuantizePureRed() {
            int result = ColorUtils.quantizeColor(0xFF0000);
            assertEquals(0xF00, result);
        }

        @Test
        @DisplayName("should quantize pure green (0x00FF00) to 0x0F0")
        void shouldQuantizePureGreen() {
            int result = ColorUtils.quantizeColor(0x00FF00);
            assertEquals(0x0F0, result);
        }

        @Test
        @DisplayName("should quantize pure blue (0x0000FF) to 0x00F")
        void shouldQuantizePureBlue() {
            int result = ColorUtils.quantizeColor(0x0000FF);
            assertEquals(0x00F, result);
        }

        @Test
        @DisplayName("should discard lower 4 bits of each channel")
        void shouldDiscardLowerFourBitsOfEachChannel() {
            int result = ColorUtils.quantizeColor(0x0F0F0F);
            assertEquals(0x000, result);
        }

        @Test
        @DisplayName("should preserve upper 4 bits of each channel")
        void shouldPreserveUpperFourBitsOfEachChannel() {
            int result = ColorUtils.quantizeColor(0xF0F0F0);
            assertEquals(0xFFF, result);
        }

        @Test
        @DisplayName("should quantize distinct channels accurately")
        void shouldQuantizeDistinctChannelsAccurately() {
            int result = ColorUtils.quantizeColor(0x1F2E3D);
            assertEquals(0x123, result);
        }

        @Test
        @DisplayName("should quantize arbitrary hex color 0xABCDEF to 0xACE")
        void shouldQuantizeArbitraryHexColor() {
            int result = ColorUtils.quantizeColor(0xABCDEF);
            assertEquals(0xACE, result);
        }

        @Test
        @DisplayName("should collapse colors within the same 4-bit quantize bucket")
        void shouldCollapseColorsWithinSameBucket() {
            int first = ColorUtils.quantizeColor(0x102030);
            int second = ColorUtils.quantizeColor(0x1F2F3F);
            assertEquals(first, second);
            assertEquals(0x123, first);
        }

        @Test
        @DisplayName("should differentiate adjacent colors across bucket boundary")
        void shouldDifferentiateColorsAcrossBucketBoundary() {
            int bucket1 = ColorUtils.quantizeColor(0x1F0000);
            int bucket2 = ColorUtils.quantizeColor(0x200000);
            assertEquals(0x100, bucket1);
            assertEquals(0x200, bucket2);
        }

        @Test
        @DisplayName("should quantize mid-gray (0x808080) to 0x888")
        void shouldQuantizeMidGray() {
            int result = ColorUtils.quantizeColor(0x808080);
            assertEquals(0x888, result);
        }

        @Test
        @DisplayName("should quantize 0x7F7F7F to 0x777")
        void shouldQuantizeGrayJustBelowMid() {
            int result = ColorUtils.quantizeColor(0x7F7F7F);
            assertEquals(0x777, result);
        }

        @Test
        @DisplayName("should ignore alpha channel in 32-bit ARGB values")
        void shouldIgnoreAlphaChannelInArgbValues() {
            int withAlpha = ColorUtils.quantizeColor(0xFF123456);
            int withoutAlpha = ColorUtils.quantizeColor(0x00123456);
            assertEquals(0x135, withAlpha);
            assertEquals(withoutAlpha, withAlpha);
        }

        @Test
        @DisplayName("should handle negative integer input safely")
        void shouldHandleNegativeIntegerInputSafely() {
            int result = ColorUtils.quantizeColor(-1);
            assertEquals(0xFFF, result);
        }
    }
}