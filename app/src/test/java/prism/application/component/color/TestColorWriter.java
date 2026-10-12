package prism.application.component.color;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import prism.domain.vo.Oklch;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ColorWriter Unit Tests")
class TestColorWriter {

    private ColorWriter colorWriter;

    private static final double[] EXPECTED_LIGHTNESS_STEPS = {
            0.97, 0.92, 0.84, 0.75, 0.65, 0.55, 0.45, 0.35, 0.25, 0.15
    };
    private static final double EXPECTED_CHROMA_TAPER = 1.4;
    private static final double EXPECTED_MIN_CHROMA_FACTOR = 0.15;

    @BeforeEach
    void setUp() {
        colorWriter = new ColorWriter();
    }

    @Nested
    @DisplayName("Anchor Step Selection Tests")
    class AnchorSelectionTests {

        @Test
        @DisplayName("anchor matches index 0 when base lightness aligns with step 100 (0.97)")
        void anchor_whenLightnessMatchesLightestStep_anchorsAtIndex0() {
            Oklch base = new Oklch(0.97, 0.10, 120.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            assertEquals(base.toRgb(), spectrum[0]);
        }

        @Test
        @DisplayName("anchor matches index 9 when base lightness aligns with step 1000 (0.15)")
        void anchor_whenLightnessMatchesDarkestStep_anchorsAtIndex9() {
            Oklch base = new Oklch(0.15, 0.10, 120.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            assertEquals(base.toRgb(), spectrum[9]);
        }

        @Test
        @DisplayName("anchor matches mid step index when base lightness matches exactly")
        void anchor_whenLightnessMatchesMidStep_anchorsAtMidIndex() {
            Oklch base = new Oklch(0.55, 0.15, 200.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            // Index 5 corresponds to lightness 0.55
            assertEquals(base.toRgb(), spectrum[5]);
        }

        @Test
        @DisplayName("anchor selects closest step when base lightness falls between defined steps")
        void anchor_whenLightnessBetweenSteps_selectsClosestStep() {
            // Steps at index 4 (0.65) and index 5 (0.55)
            // L = 0.63 is closer to 0.65 (dist 0.02) than to 0.55 (dist 0.08)
            Oklch baseCloserToUpper = new Oklch(0.63, 0.12, 180.0);
            Integer[] spectrumUpper = colorWriter.calculateSpectrum(baseCloserToUpper);
            assertEquals(baseCloserToUpper.toRgb(), spectrumUpper[4]);

            // L = 0.57 is closer to 0.55 (dist 0.02) than to 0.65 (dist 0.08)
            Oklch baseCloserToLower = new Oklch(0.57, 0.12, 180.0);
            Integer[] spectrumLower = colorWriter.calculateSpectrum(baseCloserToLower);
            assertEquals(baseCloserToLower.toRgb(), spectrumLower[5]);
        }

        @Test
        @DisplayName("anchor correctly identifies each of the 10 defined steps as anchor")
        void anchor_correctlySelectsEachDefinedStepWhenExactMatch() {
            for (int step = 0; step < EXPECTED_LIGHTNESS_STEPS.length; step++) {
                Oklch base = new Oklch(EXPECTED_LIGHTNESS_STEPS[step], 0.15, 200.0);
                Integer[] spectrum = colorWriter.calculateSpectrum(base);
                assertEquals(base.toRgb(), spectrum[step],
                        "Step " + step + " should be selected as the anchor when base lightness is " + EXPECTED_LIGHTNESS_STEPS[step]);
            }
        }

        @Test
        @DisplayName("anchor selects index 0 when base lightness exceeds maximum step (1.0 > 0.97)")
        void anchor_whenLightnessExceedsMaximum_selectsIndex0() {
            Oklch baseHigh = new Oklch(1.0, 0.05, 90.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(baseHigh);

            assertEquals(baseHigh.toRgb(), spectrum[0]);
        }

        @Test
        @DisplayName("anchor selects index 9 when base lightness falls below minimum step (0.0 < 0.15)")
        void anchor_whenLightnessFallsBelowMinimum_selectsIndex9() {
            Oklch baseLow = new Oklch(0.0, 0.0, 0.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(baseLow);

            assertEquals(baseLow.toRgb(), spectrum[9]);
        }
    }

    @Nested
    @DisplayName("Chroma Tapering and Clamping Tests")
    class ChromaTaperingTests {

        @Test
        @DisplayName("chroma tapers progressively with distance from the anchor step")
        void chroma_fadesProgressivelyWithDistanceAccordingToFormula() {
            // Anchor at index 5 (L=0.55)
            Oklch base = new Oklch(0.55, 0.20, 240.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            // Verify index 5 is exact base RGB
            assertEquals(base.toRgb(), spectrum[5]);

            // Index 4: L = 0.65, dist = 0.10 -> factor = 1.0 - 1.4 * 0.10 = 0.86 -> chroma = 0.20 * 0.86 = 0.172
            int expectedRgbStep4 = new Oklch(0.65, 0.20 * 0.86, 240.0).toRgb();
            assertEquals(expectedRgbStep4, spectrum[4]);

            // Index 3: L = 0.75, dist = 0.20 -> factor = 1.0 - 1.4 * 0.20 = 0.72 -> chroma = 0.20 * 0.72 = 0.144
            int expectedRgbStep3 = new Oklch(0.75, 0.20 * 0.72, 240.0).toRgb();
            assertEquals(expectedRgbStep3, spectrum[3]);

            // Index 6: L = 0.45, dist = 0.10 -> factor = 1.0 - 1.4 * 0.10 = 0.86 -> chroma = 0.20 * 0.86 = 0.172
            int expectedRgbStep6 = new Oklch(0.45, 0.20 * 0.86, 240.0).toRgb();
            assertEquals(expectedRgbStep6, spectrum[6]);
        }

        @Test
        @DisplayName("chroma is clamped to MIN_CHROMA_FACTOR (0.15) for distant steps")
        void chroma_clampsToMinimumFactorAtDistantSteps() {
            // Anchor at index 0 (L=0.97)
            Oklch base = new Oklch(0.97, 0.20, 100.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            // For step 9 (L = 0.15): dist = |0.15 - 0.97| = 0.82
            // 1.0 - 1.4 * 0.82 = -0.148 < 0.15 -> clamped to 0.15
            // Expected chroma = 0.20 * 0.15 = 0.03
            int expectedRgbStep9 = new Oklch(0.15, 0.20 * 0.15, 100.0).toRgb();
            assertEquals(expectedRgbStep9, spectrum[9]);

            // For step 8 (L = 0.25): dist = |0.25 - 0.97| = 0.72
            // 1.0 - 1.4 * 0.72 = -0.008 < 0.15 -> clamped to 0.15
            int expectedRgbStep8 = new Oklch(0.25, 0.20 * 0.15, 100.0).toRgb();
            assertEquals(expectedRgbStep8, spectrum[8]);
        }

        @Test
        @DisplayName("chroma remains zero across all steps when base color is achromatic (c = 0)")
        void chroma_withZeroBaseChroma_remainsZeroAcrossAllSteps() {
            Oklch grayBase = new Oklch(0.55, 0.0, 0.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(grayBase);

            for (int rgb : spectrum) {
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Pure gray has equal R, G, and B components
                assertEquals(r, g, "Red and Green must match in achromatic step");
                assertEquals(g, b, "Green and Blue must match in achromatic step");
            }
        }
    }

    @Nested
    @DisplayName("Spectrum Structure and Lightness Monotonicity Tests")
    class SpectrumStructureTests {

        @Test
        @DisplayName("calculateSpectrum always returns an array of exactly 10 elements")
        void calculateSpectrum_returnsArrayOfExactLength10() {
            Oklch base = new Oklch(0.60, 0.15, 30.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            assertNotNull(spectrum);
            assertEquals(10, spectrum.length);
        }

        @Test
        @DisplayName("calculateSpectrum elements are all non-null valid 24-bit RGB values")
        void calculateSpectrum_allElementsAreNonNullValidRgb() {
            Oklch base = new Oklch(0.70, 0.18, 220.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(base);

            for (int i = 0; i < spectrum.length; i++) {
                assertNotNull(spectrum[i], "Element at index " + i + " must not be null");
                assertTrue(spectrum[i] >= 0 && spectrum[i] <= 0xFFFFFF,
                        "Element at index " + i + " must be a valid 24-bit RGB integer");
            }
        }

        @Test
        @DisplayName("perceived brightness strictly decreases from step 0 (lightest) to step 9 (darkest)")
        void calculateSpectrum_brightnessDecreasesMonotonicallyForGrayscale() {
            Oklch gray = new Oklch(0.55, 0.0, 0.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(gray);

            for (int i = 0; i < spectrum.length - 1; i++) {
                int currentComponent = spectrum[i] & 0xFF;
                int nextComponent = spectrum[i + 1] & 0xFF;
                assertTrue(currentComponent > nextComponent,
                        "Step " + i + " brightness (" + currentComponent + ") must be strictly greater than step "
                                + (i + 1) + " (" + nextComponent + ")");
            }
        }

        @Test
        @DisplayName("calculateSpectrum generates diverse spectrums across various hue angles")
        void calculateSpectrum_worksAcrossVariousHues() {
            double[] hues = {0.0, 60.0, 120.0, 180.0, 240.0, 300.0, 359.0};

            for (double hue : hues) {
                Oklch base = new Oklch(0.50, 0.20, hue);
                Integer[] spectrum = colorWriter.calculateSpectrum(base);

                assertEquals(10, spectrum.length);
                for (Integer rgb : spectrum) {
                    assertNotNull(rgb);
                    assertTrue(rgb >= 0 && rgb <= 0xFFFFFF);
                }
            }
        }
    }

    @Nested
    @DisplayName("Validation and Error Handling Tests")
    class ValidationAndErrorHandlingTests {

        @Test
        @DisplayName("calculateSpectrum throws NullPointerException when base color is null")
        void calculateSpectrum_whenBaseIsNull_throwsNullPointerException() {
            assertThrows(
                    NullPointerException.class,
                    () -> colorWriter.calculateSpectrum(null)
            );
        }

        @Test
        @DisplayName("calculateSpectrum safely handles extreme lightness values (0.0 and 1.0)")
        void calculateSpectrum_withExtremeLightnessValues() {
            Oklch black = new Oklch(0.0, 0.0, 0.0);
            Integer[] blackSpectrum = colorWriter.calculateSpectrum(black);
            assertEquals(10, blackSpectrum.length);

            Oklch white = new Oklch(1.0, 0.0, 0.0);
            Integer[] whiteSpectrum = colorWriter.calculateSpectrum(white);
            assertEquals(10, whiteSpectrum.length);
        }

        @Test
        @DisplayName("calculateSpectrum handles high chroma without throwing gamut errors")
        void calculateSpectrum_withHighChroma_handlesGamutMappingGracefully() {
            Oklch vibrant = new Oklch(0.65, 0.40, 140.0);
            Integer[] spectrum = colorWriter.calculateSpectrum(vibrant);

            assertEquals(10, spectrum.length);
            for (Integer rgb : spectrum) {
                assertNotNull(rgb);
                assertTrue(rgb >= 0 && rgb <= 0xFFFFFF);
            }
        }
    }

    @Nested
    @DisplayName("Constant Verification Tests")
    class ConstantVerificationTests {

        @Test
        @DisplayName("LIGHTNESS_STEPS constant has 10 strictly decreasing steps matching specification")
        void lightnessSteps_constantVerification() throws Exception {
            Field field = ColorWriter.class.getDeclaredField("LIGHTNESS_STEPS");
            field.setAccessible(true);
            double[] actualSteps = (double[]) field.get(null);

            assertArrayEquals(EXPECTED_LIGHTNESS_STEPS, actualSteps, 0.0001);
            assertEquals(10, actualSteps.length);

            for (int i = 0; i < actualSteps.length - 1; i++) {
                assertTrue(actualSteps[i] > actualSteps[i + 1],
                        "LIGHTNESS_STEPS must be in strictly descending order");
            }
        }

        @Test
        @DisplayName("CHROMA_TAPER constant equals 1.4")
        void chromaTaper_constantVerification() throws Exception {
            Field field = ColorWriter.class.getDeclaredField("CHROMA_TAPER");
            field.setAccessible(true);
            double actualTaper = (double) field.get(null);

            assertEquals(EXPECTED_CHROMA_TAPER, actualTaper, 0.0001);
        }

        @Test
        @DisplayName("MIN_CHROMA_FACTOR constant equals 0.15")
        void minChromaFactor_constantVerification() throws Exception {
            Field field = ColorWriter.class.getDeclaredField("MIN_CHROMA_FACTOR");
            field.setAccessible(true);
            double actualMinFactor = (double) field.get(null);

            assertEquals(EXPECTED_MIN_CHROMA_FACTOR, actualMinFactor, 0.0001);
        }
    }
}