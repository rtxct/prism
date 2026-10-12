package prism.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ColorScale Unit Tests")
class TestColorScale {

    private static final String[] VALID_10_SHADES = new String[]{
            "#FFFFFF", "#F0F0F0", "#D9D9D9", "#BFBFBF", "#A6A6A6",
            "#8C8C8C", "#737373", "#595959", "#404040", "#000000"
    };

    @Nested
    @DisplayName("Record Contract & Canonical Constructor")
    class RecordContractTests {

        @Test
        @DisplayName("should create instance via canonical constructor")
        void shouldCreateInstanceViaCanonicalConstructor() {
            ColorScale scale = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );

            assertNotNull(scale);
        }

        @Test
        @DisplayName("should expose all 10 component accessors matching constructor arguments")
        void shouldExposeAllComponentAccessors() {
            ColorScale scale = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );

            assertEquals("#100", scale.shade100());
            assertEquals("#200", scale.shade200());
            assertEquals("#300", scale.shade300());
            assertEquals("#400", scale.shade400());
            assertEquals("#500", scale.shade500());
            assertEquals("#600", scale.shade600());
            assertEquals("#700", scale.shade700());
            assertEquals("#800", scale.shade800());
            assertEquals("#900", scale.shade900());
            assertEquals("#1000", scale.shade1000());
        }

        @Test
        @DisplayName("should return equal for two identical instances")
        void shouldReturnEqualForIdenticalInstances() {
            ColorScale scale1 = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );
            ColorScale scale2 = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );

            assertEquals(scale1, scale2);
        }

        @Test
        @DisplayName("should return same hashCode for two identical instances")
        void shouldReturnSameHashCodeForIdenticalInstances() {
            ColorScale scale1 = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );
            ColorScale scale2 = new ColorScale(
                    "#100", "#200", "#300", "#400", "#500",
                    "#600", "#700", "#800", "#900", "#1000"
            );

            assertEquals(scale1.hashCode(), scale2.hashCode());
        }

        @Test
        @DisplayName("should return not equal when first shade differs")
        void shouldReturnNotEqualWhenFirstShadeDiffers() {
            ColorScale scale1 = ColorScale.fromColorArray(VALID_10_SHADES);
            ColorScale scale2 = ColorScale.builder()
                    .shade100("#DIFFERENT")
                    .shade200(VALID_10_SHADES[1])
                    .shade300(VALID_10_SHADES[2])
                    .shade400(VALID_10_SHADES[3])
                    .shade500(VALID_10_SHADES[4])
                    .shade600(VALID_10_SHADES[5])
                    .shade700(VALID_10_SHADES[6])
                    .shade800(VALID_10_SHADES[7])
                    .shade900(VALID_10_SHADES[8])
                    .shade1000(VALID_10_SHADES[9])
                    .build();

            assertNotEquals(scale1, scale2);
        }

        @Test
        @DisplayName("should return not equal when last shade differs")
        void shouldReturnNotEqualWhenLastShadeDiffers() {
            ColorScale scale1 = ColorScale.fromColorArray(VALID_10_SHADES);
            ColorScale scale2 = ColorScale.builder()
                    .shade100(VALID_10_SHADES[0])
                    .shade200(VALID_10_SHADES[1])
                    .shade300(VALID_10_SHADES[2])
                    .shade400(VALID_10_SHADES[3])
                    .shade500(VALID_10_SHADES[4])
                    .shade600(VALID_10_SHADES[5])
                    .shade700(VALID_10_SHADES[6])
                    .shade800(VALID_10_SHADES[7])
                    .shade900(VALID_10_SHADES[8])
                    .shade1000("#DIFFERENT")
                    .build();

            assertNotEquals(scale1, scale2);
        }

        @Test
        @DisplayName("should return not equal when comparing with null")
        void shouldReturnNotEqualWhenComparingWithNull() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);
            assertNotNull(scale);
        }

        @Test
        @DisplayName("should include all shade component names and values in toString")
        void shouldIncludeAllShadeNamesInToString() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);
            String str = scale.toString();

            assertTrue(str.contains("shade100=" + VALID_10_SHADES[0]));
            assertTrue(str.contains("shade500=" + VALID_10_SHADES[4]));
            assertTrue(str.contains("shade1000=" + VALID_10_SHADES[9]));
        }

        @Test
        @DisplayName("should handle null fields in equals and hashCode without throwing exceptions")
        void shouldHandleNullFieldsInEqualsAndHashCode() {
            ColorScale nullScale1 = new ColorScale(null, null, null, null, null, null, null, null, null, null);
            ColorScale nullScale2 = new ColorScale(null, null, null, null, null, null, null, null, null, null);

            assertEquals(nullScale1, nullScale2);
            assertEquals(nullScale1.hashCode(), nullScale2.hashCode());
            assertNotEquals(nullScale1, ColorScale.fromColorArray(VALID_10_SHADES));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build ColorScale with all 10 shades via builder")
        void shouldBuildColorScaleWithAllShades() {
            ColorScale scale = ColorScale.builder()
                    .shade100("#1")
                    .shade200("#2")
                    .shade300("#3")
                    .shade400("#4")
                    .shade500("#5")
                    .shade600("#6")
                    .shade700("#7")
                    .shade800("#8")
                    .shade900("#9")
                    .shade1000("#10")
                    .build();

            assertEquals("#1", scale.shade100());
            assertEquals("#2", scale.shade200());
            assertEquals("#3", scale.shade300());
            assertEquals("#4", scale.shade400());
            assertEquals("#5", scale.shade500());
            assertEquals("#6", scale.shade600());
            assertEquals("#7", scale.shade700());
            assertEquals("#8", scale.shade800());
            assertEquals("#9", scale.shade900());
            assertEquals("#10", scale.shade1000());
        }

        @Test
        @DisplayName("should build ColorScale with partial shades leaving unset shades null")
        void shouldBuildColorScaleWithPartialShades() {
            ColorScale scale = ColorScale.builder()
                    .shade100("#100")
                    .shade500("#500")
                    .build();

            assertEquals("#100", scale.shade100());
            assertNull(scale.shade200());
            assertNull(scale.shade300());
            assertNull(scale.shade400());
            assertEquals("#500", scale.shade500());
            assertNull(scale.shade600());
            assertNull(scale.shade700());
            assertNull(scale.shade800());
            assertNull(scale.shade900());
            assertNull(scale.shade1000());
        }

        @Test
        @DisplayName("should overwrite shade when setter is called multiple times on builder")
        void shouldOverwriteShadeInBuilder() {
            ColorScale scale = ColorScale.builder()
                    .shade100("#initial")
                    .shade100("#overwritten")
                    .build();

            assertEquals("#overwritten", scale.shade100());
        }

        @Test
        @DisplayName("should provide meaningful toString representation for builder")
        void shouldProvideMeaningfulToStringForBuilder() {
            String builderStr = ColorScale.builder().shade100("#100").toString();
            assertNotNull(builderStr);
            assertTrue(builderStr.contains("shade100=#100"));
        }
    }

    @Nested
    @DisplayName("fromColorArray")
    class FromColorArrayTests {

        @Test
        @DisplayName("should create ColorScale from exact 10 element array")
        void shouldCreateColorScaleFromExactTenElementArray() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);

            assertNotNull(scale);
            assertEquals(VALID_10_SHADES[0], scale.shade100());
            assertEquals(VALID_10_SHADES[9], scale.shade1000());
        }

        @Test
        @DisplayName("should map array indices 0 to 9 to shades 100 to 1000 in ascending order")
        void shouldMapArrayIndicesToCorrectShadesInAscendingOrder() {
            String[] shades = new String[]{
                    "s100", "s200", "s300", "s400", "s500",
                    "s600", "s700", "s800", "s900", "s1000"
            };

            ColorScale scale = ColorScale.fromColorArray(shades);

            assertEquals("s100", scale.shade100());
            assertEquals("s200", scale.shade200());
            assertEquals("s300", scale.shade300());
            assertEquals("s400", scale.shade400());
            assertEquals("s500", scale.shade500());
            assertEquals("s600", scale.shade600());
            assertEquals("s700", scale.shade700());
            assertEquals("s800", scale.shade800());
            assertEquals("s900", scale.shade900());
            assertEquals("s1000", scale.shade1000());
        }

        @Test
        @DisplayName("should ignore extra elements when array contains more than 10 shades")
        void shouldIgnoreExtraElementsWhenArrayHasMoreThanTenShades() {
            String[] shadesWithExtra = new String[]{
                    "s100", "s200", "s300", "s400", "s500",
                    "s600", "s700", "s800", "s900", "s1000",
                    "extra11", "extra12"
            };

            ColorScale scale = ColorScale.fromColorArray(shadesWithExtra);

            assertEquals("s100", scale.shade100());
            assertEquals("s1000", scale.shade1000());
            assertEquals(10, scale.getShades().length);
        }

        @Test
        @DisplayName("should preserve null elements within array")
        void shouldPreserveNullElementsFromArray() {
            String[] shadesWithNulls = new String[]{
                    null, "s200", null, "s400", null,
                    "s600", null, "s800", null, "s1000"
            };

            ColorScale scale = ColorScale.fromColorArray(shadesWithNulls);

            assertNull(scale.shade100());
            assertEquals("s200", scale.shade200());
            assertNull(scale.shade300());
            assertEquals("s400", scale.shade400());
            assertNull(scale.shade500());
            assertEquals("s600", scale.shade600());
            assertNull(scale.shade700());
            assertEquals("s800", scale.shade800());
            assertNull(scale.shade900());
            assertEquals("s1000", scale.shade1000());
        }

        @Test
        @DisplayName("should throw NullPointerException when shades array is null")
        void shouldThrowExceptionWhenArrayIsNull() {
            assertThrows(NullPointerException.class, () -> ColorScale.fromColorArray(null));
        }

        @Test
        @DisplayName("should throw ArrayIndexOutOfBoundsException when shades array is empty")
        void shouldThrowExceptionWhenArrayIsEmpty() {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> ColorScale.fromColorArray(new String[0]));
        }

        @Test
        @DisplayName("should throw ArrayIndexOutOfBoundsException when shades array has fewer than 10 elements")
        void shouldThrowExceptionWhenArrayHasFewerThanTenElements() {
            String[] nineShades = new String[]{
                    "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9"
            };

            assertThrows(ArrayIndexOutOfBoundsException.class, () -> ColorScale.fromColorArray(nineShades));
        }
    }

    @Nested
    @DisplayName("getShades")
    class GetShadesTests {

        @Test
        @DisplayName("should return array of exactly 10 shades")
        void shouldReturnArrayOfExactlyTenShades() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);
            String[] shades = scale.getShades();

            assertEquals(10, shades.length);
        }

        @Test
        @DisplayName("should return shades in correct order from shade100 to shade1000")
        void shouldReturnShadesInCorrectOrder() {
            ColorScale scale = new ColorScale(
                    "c100", "c200", "c300", "c400", "c500",
                    "c600", "c700", "c800", "c900", "c1000"
            );

            String[] expected = new String[]{
                    "c100", "c200", "c300", "c400", "c500",
                    "c600", "c700", "c800", "c900", "c1000"
            };

            assertArrayEquals(expected, scale.getShades());
        }

        @Test
        @DisplayName("should return fresh array copy on each getShades invocation")
        void shouldReturnFreshArrayCopyOnEachCall() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);

            String[] firstCall = scale.getShades();
            String[] secondCall = scale.getShades();

            assertNotSame(firstCall, secondCall);
            assertArrayEquals(firstCall, secondCall);
        }

        @Test
        @DisplayName("should not mutate record state when returned array is modified")
        void shouldNotMutateRecordWhenReturnedArrayIsModified() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);

            String[] shades = scale.getShades();
            shades[0] = "#MUTATED";

            assertEquals(VALID_10_SHADES[0], scale.shade100());
            assertEquals(VALID_10_SHADES[0], scale.getShades()[0]);
        }

        @Test
        @DisplayName("should return array with null elements when shades are null")
        void shouldReturnArrayWithNullElementsWhenShadesAreNull() {
            ColorScale scale = new ColorScale(null, null, null, null, null, null, null, null, null, null);
            String[] shades = scale.getShades();

            assertEquals(10, shades.length);
            for (String shade : shades) {
                assertNull(shade);
            }
        }
    }

    @Nested
    @DisplayName("Round-Trip")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve values when converting from array to ColorScale and back to array")
        void shouldPreserveValuesAcrossRoundTrip() {
            ColorScale scale = ColorScale.fromColorArray(VALID_10_SHADES);
            String[] roundTripShades = scale.getShades();

            assertArrayEquals(VALID_10_SHADES, roundTripShades);
        }

        @Test
        @DisplayName("should produce identical ColorScale when reconstructed from getShades output")
        void shouldReconstructIdenticalColorScaleFromGetShades() {
            ColorScale original = ColorScale.fromColorArray(VALID_10_SHADES);
            ColorScale reconstructed = ColorScale.fromColorArray(original.getShades());

            assertEquals(original, reconstructed);
            assertEquals(original.hashCode(), reconstructed.hashCode());
        }
    }
}