package prism.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.domain.vo.ColorScale;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Prism Unit Tests")
class TestPrism {

    private static final String[] SHADES_1 = new String[]{
            "#11", "#12", "#13", "#14", "#15", "#16", "#17", "#18", "#19", "#1A"
    };
    private static final String[] SHADES_2 = new String[]{
            "#21", "#22", "#23", "#24", "#25", "#26", "#27", "#28", "#29", "#2A"
    };
    private static final String[] SHADES_3 = new String[]{
            "#31", "#32", "#33", "#34", "#35", "#36", "#37", "#38", "#39", "#3A"
    };
    private static final String[] SHADES_4 = new String[]{
            "#41", "#42", "#43", "#44", "#45", "#46", "#47", "#48", "#49", "#4A"
    };
    private static final String[] SHADES_5 = new String[]{
            "#51", "#52", "#53", "#54", "#55", "#56", "#57", "#58", "#59", "#5A"
    };

    private ColorScale luxScale;
    private ColorScale coreScale;
    private ColorScale waveScale;
    private ColorScale flareScale;
    private ColorScale sparkScale;

    @BeforeEach
    void setUp() {
        luxScale = ColorScale.fromColorArray(SHADES_1);
        coreScale = ColorScale.fromColorArray(SHADES_2);
        waveScale = ColorScale.fromColorArray(SHADES_3);
        flareScale = ColorScale.fromColorArray(SHADES_4);
        sparkScale = ColorScale.fromColorArray(SHADES_5);
    }

    private Prism createSamplePrism() {
        return Prism.builder()
                .lux(luxScale)
                .core(coreScale)
                .wave(waveScale)
                .flare(flareScale)
                .spark(sparkScale)
                .build();
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("should define custom file extension as .spec")
        void shouldDefineExtensionAsSpec() {
            assertEquals(".spec", Prism.EXTENSION);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build Prism instance with all five color scale roles")
        void shouldBuildPrismWithAllRoles() {
            Prism prism = createSamplePrism();
            assertNotNull(prism);
        }

        @Test
        @DisplayName("should overwrite color scale when role setter is called repeatedly")
        void shouldAllowOverwritingRoleInBuilder() {
            ColorScale alternateScale = ColorScale.fromColorArray(SHADES_2);
            Prism prism = Prism.builder()
                    .lux(luxScale)
                    .lux(alternateScale)
                    .core(coreScale)
                    .wave(waveScale)
                    .flare(flareScale)
                    .spark(sparkScale)
                    .build();

            // When formatted, the lux role should reflect the alternate scale
            assertTrue(prism.getFormatted().startsWith("lux100::#21"));
        }

        @Test
        @DisplayName("should provide non-null toString representation for PrismBuilder")
        void shouldProvideMeaningfulToStringOnBuilder() {
            String builderStr = Prism.builder().lux(luxScale).toString();
            assertNotNull(builderStr);
            assertTrue(builderStr.contains("lux="));
        }

        @Test
        @DisplayName("should throw NullPointerException when setting null lux in builder")
        void shouldThrowExceptionWhenSettingNullLuxInBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder().lux(null));
        }

        @Test
        @DisplayName("should throw NullPointerException when setting null core in builder")
        void shouldThrowExceptionWhenSettingNullCoreInBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder().core(null));
        }

        @Test
        @DisplayName("should throw NullPointerException when setting null wave in builder")
        void shouldThrowExceptionWhenSettingNullWaveInBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder().wave(null));
        }

        @Test
        @DisplayName("should throw NullPointerException when setting null flare in builder")
        void shouldThrowExceptionWhenSettingNullFlareInBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder().flare(null));
        }

        @Test
        @DisplayName("should throw NullPointerException when setting null spark in builder")
        void shouldThrowExceptionWhenSettingNullSparkInBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder().spark(null));
        }

        @Test
        @DisplayName("should throw NullPointerException when lux role is omitted during build")
        void shouldThrowExceptionWhenLuxOmittedFromBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder()
                    .core(coreScale).wave(waveScale).flare(flareScale).spark(sparkScale)
                    .build());
        }

        @Test
        @DisplayName("should throw NullPointerException when core role is omitted during build")
        void shouldThrowExceptionWhenCoreOmittedFromBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder()
                    .lux(luxScale).wave(waveScale).flare(flareScale).spark(sparkScale)
                    .build());
        }

        @Test
        @DisplayName("should throw NullPointerException when wave role is omitted during build")
        void shouldThrowExceptionWhenWaveOmittedFromBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder()
                    .lux(luxScale).core(coreScale).flare(flareScale).spark(sparkScale)
                    .build());
        }

        @Test
        @DisplayName("should throw NullPointerException when flare role is omitted during build")
        void shouldThrowExceptionWhenFlareOmittedFromBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder()
                    .lux(luxScale).core(coreScale).wave(waveScale).spark(sparkScale)
                    .build());
        }

        @Test
        @DisplayName("should throw NullPointerException when spark role is omitted during build")
        void shouldThrowExceptionWhenSparkOmittedFromBuilder() {
            assertThrows(NullPointerException.class, () -> Prism.builder()
                    .lux(luxScale).core(coreScale).wave(waveScale).flare(flareScale)
                    .build());
        }

        @Test
        @DisplayName("should throw NullPointerException when lux is null in constructor")
        void shouldThrowExceptionWhenLuxIsNullInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new Prism(null, coreScale, waveScale, flareScale, sparkScale));
        }

        @Test
        @DisplayName("should throw NullPointerException when core is null in constructor")
        void shouldThrowExceptionWhenCoreIsNullInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new Prism(luxScale, null, waveScale, flareScale, sparkScale));
        }

        @Test
        @DisplayName("should throw NullPointerException when wave is null in constructor")
        void shouldThrowExceptionWhenWaveIsNullInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new Prism(luxScale, coreScale, null, flareScale, sparkScale));
        }

        @Test
        @DisplayName("should throw NullPointerException when flare is null in constructor")
        void shouldThrowExceptionWhenFlareIsNullInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new Prism(luxScale, coreScale, waveScale, null, sparkScale));
        }

        @Test
        @DisplayName("should throw NullPointerException when spark is null in constructor")
        void shouldThrowExceptionWhenSparkIsNullInConstructor() {
            assertThrows(NullPointerException.class, () ->
                    new Prism(luxScale, coreScale, waveScale, flareScale, null));
        }
    }

    @Nested
    @DisplayName("getFormatted")
    class GetFormattedTests {

        @Test
        @DisplayName("should include all five role prefixes in formatted output")
        void shouldIncludeAllFiveRolesInOutput() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();

            assertTrue(formatted.contains("lux100::"));
            assertTrue(formatted.contains("core100::"));
            assertTrue(formatted.contains("wave100::"));
            assertTrue(formatted.contains("flare100::"));
            assertTrue(formatted.contains("spark100::"));
        }

        @Test
        @DisplayName("should format shades with level indices incrementing from 100 to 1000")
        void shouldFormatShadesWithCorrectLevelIndices() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();

            for (int level = 100; level <= 1000; level += 100) {
                assertTrue(formatted.contains("core" + level + "::"),
                        "Expected formatted output to contain level: core" + level + "::");
            }
        }

        @Test
        @DisplayName("should use double colon separator between role-level and color value")
        void shouldUseDoubleColonSeparator() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();

            assertTrue(formatted.contains("lux100::#11"));
            assertTrue(formatted.contains("spark1000::#5A"));
        }

        @Test
        @DisplayName("should separate role sections with a blank line separator")
        void shouldSeparateRolesWithBlankLine() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();
            String sep = System.lineSeparator();

            // Each role ends with its 1000 shade line followed by an empty line
            assertTrue(formatted.contains("lux1000::#1A" + sep + sep));
            assertTrue(formatted.contains("core1000::#2A" + sep + sep));
            assertTrue(formatted.contains("wave1000::#3A" + sep + sep));
            assertTrue(formatted.contains("flare1000::#4A" + sep + sep));
            assertTrue(formatted.contains("spark1000::#5A" + sep + sep));
        }

        @Test
        @DisplayName("should format in strict role order: lux, core, wave, flare, spark")
        void shouldFormatInStrictRoleOrder() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();

            int luxIndex = formatted.indexOf("lux100::");
            int coreIndex = formatted.indexOf("core100::");
            int waveIndex = formatted.indexOf("wave100::");
            int flareIndex = formatted.indexOf("flare100::");
            int sparkIndex = formatted.indexOf("spark100::");

            assertTrue(luxIndex < coreIndex);
            assertTrue(coreIndex < waveIndex);
            assertTrue(waveIndex < flareIndex);
            assertTrue(flareIndex < sparkIndex);
        }

        @Test
        @DisplayName("should format exact total line count of 55 lines for ten-shade scales")
        void shouldFormatExactTotalLineCount() {
            Prism prism = createSamplePrism();
            String formatted = prism.getFormatted();
            String[] lines = formatted.split(System.lineSeparator(), -1);

            // 5 roles * 10 shade lines + 5 blank separator lines + 1 trailing empty string from split = 56 tokens
            // Or count non-empty lines = exactly 50
            long nonEmptyLines = Arrays.stream(lines).filter(l -> !l.isEmpty()).count();
            assertEquals(50, nonEmptyLines);
        }
    }

    @Nested
    @DisplayName("getId")
    class GetIdTests {

        @Test
        @DisplayName("should match ID format starting with 'ID: ' followed by 5 hyphen-separated hex tokens")
        void shouldMatchExpectedIdFormat() {
            Prism prism = createSamplePrism();
            String id = prism.getId();

            assertTrue(id.matches("^ID: [0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+$"),
                    "ID did not match expected pattern: " + id);
        }

        @Test
        @DisplayName("should return identical ID for prisms with identical shades")
        void shouldReturnIdenticalIdForIdenticalPrisms() {
            Prism prism1 = createSamplePrism();
            Prism prism2 = createSamplePrism();

            assertEquals(prism1.getId(), prism2.getId());
        }

        @Test
        @DisplayName("should compute different ID when lux shades change")
        void shouldChangeIdWhenLuxChanges() {
            Prism original = createSamplePrism();
            Prism modified = Prism.builder()
                    .lux(ColorScale.fromColorArray(SHADES_2))
                    .core(coreScale).wave(waveScale).flare(flareScale).spark(sparkScale)
                    .build();

            assertNotEquals(original.getId(), modified.getId());

            String[] origTokens = original.getId().replace("ID: ", "").split("-");
            String[] modTokens = modified.getId().replace("ID: ", "").split("-");

            assertNotEquals(origTokens[0], modTokens[0], "Lux token should change");
            assertEquals(origTokens[1], modTokens[1], "Core token should remain unchanged");
        }

        @Test
        @DisplayName("should compute different ID when core shades change")
        void shouldChangeIdWhenCoreChanges() {
            Prism original = createSamplePrism();
            Prism modified = Prism.builder()
                    .lux(luxScale)
                    .core(ColorScale.fromColorArray(SHADES_1))
                    .wave(waveScale).flare(flareScale).spark(sparkScale)
                    .build();

            assertNotEquals(original.getId(), modified.getId());

            String[] origTokens = original.getId().replace("ID: ", "").split("-");
            String[] modTokens = modified.getId().replace("ID: ", "").split("-");

            assertEquals(origTokens[0], modTokens[0], "Lux token should remain unchanged");
            assertNotEquals(origTokens[1], modTokens[1], "Core token should change");
        }

        @Test
        @DisplayName("should compute different ID when wave shades change")
        void shouldChangeIdWhenWaveChanges() {
            Prism original = createSamplePrism();
            Prism modified = Prism.builder()
                    .lux(luxScale).core(coreScale)
                    .wave(ColorScale.fromColorArray(SHADES_1))
                    .flare(flareScale).spark(sparkScale)
                    .build();

            assertNotEquals(original.getId(), modified.getId());

            String[] origTokens = original.getId().replace("ID: ", "").split("-");
            String[] modTokens = modified.getId().replace("ID: ", "").split("-");

            assertNotEquals(origTokens[2], modTokens[2], "Wave token should change");
        }

        @Test
        @DisplayName("should compute different ID when flare shades change")
        void shouldChangeIdWhenFlareChanges() {
            Prism original = createSamplePrism();
            Prism modified = Prism.builder()
                    .lux(luxScale).core(coreScale).wave(waveScale)
                    .flare(ColorScale.fromColorArray(SHADES_1))
                    .spark(sparkScale)
                    .build();

            assertNotEquals(original.getId(), modified.getId());

            String[] origTokens = original.getId().replace("ID: ", "").split("-");
            String[] modTokens = modified.getId().replace("ID: ", "").split("-");

            assertNotEquals(origTokens[3], modTokens[3], "Flare token should change");
        }

        @Test
        @DisplayName("should compute different ID when spark shades change")
        void shouldChangeIdWhenSparkChanges() {
            Prism original = createSamplePrism();
            Prism modified = Prism.builder()
                    .lux(luxScale).core(coreScale).wave(waveScale).flare(flareScale)
                    .spark(ColorScale.fromColorArray(SHADES_1))
                    .build();

            assertNotEquals(original.getId(), modified.getId());

            String[] origTokens = original.getId().replace("ID: ", "").split("-");
            String[] modTokens = modified.getId().replace("ID: ", "").split("-");

            assertNotEquals(origTokens[4], modTokens[4], "Spark token should change");
        }

        @Test
        @DisplayName("should compute tokens matching hex representation of Arrays.hashCode")
        void shouldComputeTokensMatchingHexHashCode() {
            Prism prism = createSamplePrism();
            String id = prism.getId();

            String expectedLuxHex = Integer.toHexString(Arrays.hashCode(SHADES_1));
            String expectedCoreHex = Integer.toHexString(Arrays.hashCode(SHADES_2));
            String expectedWaveHex = Integer.toHexString(Arrays.hashCode(SHADES_3));
            String expectedFlareHex = Integer.toHexString(Arrays.hashCode(SHADES_4));
            String expectedSparkHex = Integer.toHexString(Arrays.hashCode(SHADES_5));

            String expectedId = "ID: " + expectedLuxHex + "-" + expectedCoreHex + "-" +
                    expectedWaveHex + "-" + expectedFlareHex + "-" + expectedSparkHex;

            assertEquals(expectedId, id);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Role Delegation (Mockito)")
    class RoleDelegationTests {

        @Mock
        private ColorScale mockLux;

        @Mock
        private ColorScale mockCore;

        @Mock
        private ColorScale mockWave;

        @Mock
        private ColorScale mockFlare;

        @Mock
        private ColorScale mockSpark;

        @Test
        @DisplayName("should invoke getShades on all five ColorScale roles during getFormatted")
        void shouldInvokeGetShadesOnAllRolesDuringGetFormatted() {
            String[] dummyShades = new String[]{"#00"};
            when(mockLux.getShades()).thenReturn(dummyShades);
            when(mockCore.getShades()).thenReturn(dummyShades);
            when(mockWave.getShades()).thenReturn(dummyShades);
            when(mockFlare.getShades()).thenReturn(dummyShades);
            when(mockSpark.getShades()).thenReturn(dummyShades);

            Prism prism = Prism.builder()
                    .lux(mockLux)
                    .core(mockCore)
                    .wave(mockWave)
                    .flare(mockFlare)
                    .spark(mockSpark)
                    .build();

            String formatted = prism.getFormatted();

            assertNotNull(formatted);
            verify(mockLux).getShades();
            verify(mockCore).getShades();
            verify(mockWave).getShades();
            verify(mockFlare).getShades();
            verify(mockSpark).getShades();
        }

        @Test
        @DisplayName("should invoke getShades on all five ColorScale roles during getId")
        void shouldInvokeGetShadesOnAllRolesDuringGetId() {
            String[] dummyShades = new String[]{"#00"};
            when(mockLux.getShades()).thenReturn(dummyShades);
            when(mockCore.getShades()).thenReturn(dummyShades);
            when(mockWave.getShades()).thenReturn(dummyShades);
            when(mockFlare.getShades()).thenReturn(dummyShades);
            when(mockSpark.getShades()).thenReturn(dummyShades);

            Prism prism = Prism.builder()
                    .lux(mockLux)
                    .core(mockCore)
                    .wave(mockWave)
                    .flare(mockFlare)
                    .spark(mockSpark)
                    .build();

            String id = prism.getId();

            assertNotNull(id);
            verify(mockLux).getShades();
            verify(mockCore).getShades();
            verify(mockWave).getShades();
            verify(mockFlare).getShades();
            verify(mockSpark).getShades();
        }
    }
}