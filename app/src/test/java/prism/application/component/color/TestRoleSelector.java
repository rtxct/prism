package prism.application.component.color;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import prism.domain.vo.ColorCluster;
import prism.domain.vo.Oklch;
import prism.domain.vo.Roles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RoleSelector Unit Tests")
class TestRoleSelector {

    private RoleSelector roleSelector;

    private static final double EXPECTED_MIN_SHARE = 0.003;
    private static final double EXPECTED_GRAY_CHROMA = 0.02;
    private static final double EXPECTED_FLARE_MIN_DISTANCE = 0.08;
    private static final double EXPECTED_WAVE_MIN_DISTANCE = 0.10;
    private static final double EXPECTED_SPARK_MIN_DISTANCE = 0.08;

    @BeforeEach
    void setUp() {
        roleSelector = new RoleSelector();
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("select throws IllegalArgumentException when clusters list is empty")
        void select_whenClustersListIsEmpty_throwsIllegalArgumentException() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> roleSelector.select(List.of())
            );
            assertEquals("The image has no visible pixels", ex.getMessage());
        }

        @Test
        @DisplayName("select throws NullPointerException when clusters list is null")
        void select_whenClustersListIsNull_throwsNullPointerException() {
            assertThrows(
                    NullPointerException.class,
                    () -> roleSelector.select(null)
            );
        }
    }

    @Nested
    @DisplayName("Core Selection & Sorting Tests")
    class CoreSelectionTests {

        @Test
        @DisplayName("select chooses cluster with largest share as core regardless of input order")
        void select_choosesClusterWithLargestShareAsCore() {
            Oklch color1 = new Oklch(0.50, 0.10, 30.0);
            Oklch dominant = new Oklch(0.60, 0.15, 120.0);
            Oklch color3 = new Oklch(0.40, 0.08, 200.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(color1, 0.20),
                    new ColorCluster(dominant, 0.65),
                    new ColorCluster(color3, 0.15)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(dominant, roles.core());
        }

        @Test
        @DisplayName("select falls back to top clusters up to 8 when all clusters are below MIN_SHARE")
        void select_whenAllClustersBelowMinShare_fallsBackToTopClusters() {
            List<ColorCluster> tinyClusters = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                // Shares from 0.00012 down to 0.00001, all < MIN_SHARE (0.003)
                tinyClusters.add(new ColorCluster(new Oklch(0.50, 0.10, i * 25.0), 0.00010 - i * 0.000005));
            }

            Roles roles = roleSelector.select(tinyClusters);

            // Highest share cluster (index 0) must still be selected as core
            assertEquals(tinyClusters.getFirst().color(), roles.core());
        }

        @Test
        @DisplayName("select handles fewer than 8 clusters when all are below MIN_SHARE")
        void select_whenAllClustersBelowMinShareAndFewerThan8_handlesGracefully() {
            List<ColorCluster> tinyClusters = List.of(
                    new ColorCluster(new Oklch(0.40, 0.05, 50.0), 0.001),
                    new ColorCluster(new Oklch(0.70, 0.10, 150.0), 0.002)
            );

            Roles roles = roleSelector.select(tinyClusters);

            // Dominant (0.002) is index 1
            assertEquals(tinyClusters.get(1).color(), roles.core());
        }
    }

    @Nested
    @DisplayName("Fallback Role Derivation Tests")
    class FallbackRoleDerivationTests {

        @Test
        @DisplayName("single chromatic cluster derives flare, wave, spark, and tinted lux using exact fallback formulas")
        void select_withSingleChromaticCluster_derivesAllRolesViaFallbacks() {
            Oklch chromaticCore = new Oklch(0.50, 0.12, 80.0);
            List<ColorCluster> clusters = List.of(new ColorCluster(chromaticCore, 1.0));

            Roles roles = roleSelector.select(clusters);

            // Core matches input exactly
            assertEquals(chromaticCore, roles.core());

            // Flare fallback: lightness 0.88, chroma max(0.10, core.c()), hue core.h()
            // core.c() is 0.12 > 0.10 -> chroma 0.12
            Oklch expectedFlare = new Oklch(0.88, 0.12, 80.0);
            assertEquals(expectedFlare, roles.flare());

            // Wave fallback: lightness max(0.15, core.l() - 0.08) = max(0.15, 0.42) = 0.42
            // chroma: max(0.06, core.c() * 1.6) = max(0.06, 0.192) = 0.192, hue core.h()
            Oklch expectedWave = new Oklch(0.42, 0.12 * 1.6, 80.0);
            assertEquals(expectedWave, roles.wave());

            // Spark fallback: lightness 0.65, chroma 0.09, hue (core.h() + 150) % 360 = 230.0
            Oklch expectedSpark = new Oklch(0.65, 0.09, 230.0);
            assertEquals(expectedSpark, roles.spark());

            // Lux: core.c() > 0.02 (chromatic) -> lightness 0.55, chroma 0.012, hue core.h()
            Oklch expectedLux = new Oklch(0.55, 0.012, 80.0);
            assertEquals(expectedLux, roles.lux());
        }

        @Test
        @DisplayName("single achromatic cluster derives roles with boundary clamping and untinted lux")
        void select_withSingleAchromaticCluster_derivesRolesWithBoundaryClampingAndUntintedLux() {
            // Low lightness and low chroma (achromatic: c <= 0.02)
            Oklch darkGrayCore = new Oklch(0.20, 0.01, 45.0);
            List<ColorCluster> clusters = List.of(new ColorCluster(darkGrayCore, 1.0));

            Roles roles = roleSelector.select(clusters);

            assertEquals(darkGrayCore, roles.core());

            // Flare fallback: lightness 0.88, chroma max(0.10, 0.01) = 0.10, hue 45.0
            Oklch expectedFlare = new Oklch(0.88, 0.10, 45.0);
            assertEquals(expectedFlare, roles.flare());

            // Wave fallback: lightness max(0.15, 0.20 - 0.08) = max(0.15, 0.12) = 0.15 (clamped)
            // chroma: max(0.06, 0.01 * 1.6) = max(0.06, 0.016) = 0.06 (clamped), hue 45.0
            Oklch expectedWave = new Oklch(0.15, 0.06, 45.0);
            assertEquals(expectedWave, roles.wave());

            // Spark fallback: lightness 0.65, chroma 0.09, hue (45 + 150) % 360 = 195.0
            Oklch expectedSpark = new Oklch(0.65, 0.09, 195.0);
            assertEquals(expectedSpark, roles.spark());

            // Lux: core.c() <= 0.02 (achromatic) -> lightness 0.55, chroma 0.0, hue 45.0
            Oklch expectedLux = new Oklch(0.55, 0.0, 45.0);
            assertEquals(expectedLux, roles.lux());
        }

        @Test
        @DisplayName("spark fallback hue wraps correctly around 360 degrees")
        void select_sparkFallbackWrapsHueModulo360() {
            Oklch coreNear360 = new Oklch(0.50, 0.10, 300.0);
            List<ColorCluster> clusters = List.of(new ColorCluster(coreNear360, 1.0));

            Roles roles = roleSelector.select(clusters);

            // (300.0 + 150) % 360 = 450 % 360 = 90.0
            assertEquals(90.0, roles.spark().h(), 0.0001);
        }
    }

    @Nested
    @DisplayName("Candidate Selection Tests (Pool Matching)")
    class CandidateSelectionTests {

        @Test
        @DisplayName("select picks flare from pool when candidate satisfies minimum distance and high score")
        void select_picksFlareFromPoolWhenCandidateMatchesCriteria() {
            Oklch coreColor = new Oklch(0.40, 0.10, 20.0);
            // High lightness, high chroma, distant from core (> 0.08)
            Oklch flareCandidate = new Oklch(0.85, 0.25, 40.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(coreColor, 0.70),
                    new ColorCluster(flareCandidate, 0.30)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(coreColor, roles.core());
            assertEquals(flareCandidate, roles.flare());
        }

        @Test
        @DisplayName("select picks wave from pool when candidate is in core hue family and within lightness threshold")
        void select_picksWaveFromPoolWhenCandidateInHueFamilyAndLightnessRange() {
            Oklch coreColor = new Oklch(0.50, 0.12, 100.0);
            Oklch flareCandidate = new Oklch(0.90, 0.20, 100.0);
            Oklch waveCandidate = new Oklch(0.35, 0.25, 110.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(coreColor, 0.50),
                    new ColorCluster(flareCandidate, 0.30),
                    new ColorCluster(waveCandidate, 0.20)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(coreColor, roles.core());
            assertEquals(flareCandidate, roles.flare());
            assertEquals(waveCandidate, roles.wave());
        }

        @Test
        @DisplayName("wave candidate selection relaxes hue gap requirement when core is achromatic")
        void select_waveCandidateRelaxesHueGapWhenCoreIsAchromatic() {
            Oklch grayCore = new Oklch(0.50, 0.01, 10.0);
            Oklch flareCandidate = new Oklch(0.90, 0.15, 10.0);
            Oklch waveCandidate = new Oklch(0.35, 0.15, 180.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(grayCore, 0.50),
                    new ColorCluster(flareCandidate, 0.30),
                    new ColorCluster(waveCandidate, 0.20)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(grayCore, roles.core());
            assertEquals(flareCandidate, roles.flare());
            assertEquals(waveCandidate, roles.wave());
        }

        @Test
        @DisplayName("select picks spark from pool when candidate has chroma > 0.04 and large hue gap from taken roles")
        void select_picksSparkFromPoolWhenHighChromaAndDistantHue() {
            Oklch coreColor = new Oklch(0.50, 0.10, 30.0);
            Oklch flareCandidate = new Oklch(0.90, 0.15, 30.0);
            Oklch sparkCandidate = new Oklch(0.60, 0.20, 210.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(coreColor, 0.50),
                    new ColorCluster(flareCandidate, 0.30),
                    new ColorCluster(sparkCandidate, 0.20)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(coreColor, roles.core());
            assertEquals(flareCandidate, roles.flare());
            assertEquals(sparkCandidate, roles.spark());
        }

        @Test
        @DisplayName("select populates all distinct roles from a rich multi-color palette")
        void select_populatesAllDistinctRolesFromRichPalette() {
            Oklch core = new Oklch(0.50, 0.12, 30.0);
            Oklch flare = new Oklch(0.85, 0.22, 50.0);
            Oklch wave = new Oklch(0.35, 0.20, 35.0);
            Oklch spark = new Oklch(0.60, 0.25, 210.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(core, 0.40),
                    new ColorCluster(flare, 0.25),
                    new ColorCluster(wave, 0.20),
                    new ColorCluster(spark, 0.15)
            );

            Roles roles = roleSelector.select(clusters);

            assertEquals(core, roles.core());
            assertEquals(flare, roles.flare());
            assertEquals(wave, roles.wave());
            assertEquals(spark, roles.spark());
            assertNotNull(roles.lux());
        }

        @Test
        @DisplayName("candidate within distance threshold is excluded and falls back to derivation")
        void select_candidateWithinDistanceThresholdIsExcluded() {
            Oklch core = new Oklch(0.50, 0.10, 100.0);
            // Candidate practically identical to core (dist < 0.08)
            Oklch closeCandidate = new Oklch(0.51, 0.10, 100.0);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(core, 0.60),
                    new ColorCluster(closeCandidate, 0.40)
            );

            Roles roles = roleSelector.select(clusters);

            // Flare should fall back because closeCandidate is too close
            Oklch expectedFlareFallback = new Oklch(0.88, 0.10, 100.0);
            assertEquals(expectedFlareFallback, roles.flare());
        }
    }

    @Nested
    @DisplayName("Private Helper Methods Reflection Tests")
    class PrivateMethodsReflectionTests {

        @Test
        @DisplayName("minHueGap returns 180 when no taken color has chroma exceeding GRAY_CHROMA")
        void minHueGap_whenNoTakenColorHasHue_returns180() throws Exception {
            Method method = RoleSelector.class.getDeclaredMethod("minHueGap", Oklch.class, List.class);
            method.setAccessible(true);

            Oklch testColor = new Oklch(0.50, 0.15, 90.0);
            List<Oklch> grayTaken = List.of(
                    new Oklch(0.50, 0.01, 10.0), // c <= 0.02
                    new Oklch(0.20, 0.00, 20.0)  // c <= 0.02
            );

            double gap = (double) method.invoke(roleSelector, testColor, grayTaken);
            assertEquals(180.0, gap, 0.0001);
        }

        @Test
        @DisplayName("minHueGap calculates smallest angle across chromatic taken colors")
        void minHueGap_calculatesSmallestAngleAcrossChromaticTakenColors() throws Exception {
            Method method = RoleSelector.class.getDeclaredMethod("minHueGap", Oklch.class, List.class);
            method.setAccessible(true);

            Oklch testColor = new Oklch(0.50, 0.15, 100.0);
            List<Oklch> taken = List.of(
                    new Oklch(0.50, 0.01, 100.0), // Ignored (gray, c <= 0.02)
                    new Oklch(0.50, 0.10, 80.0),   // gap = 20
                    new Oklch(0.50, 0.10, 150.0)   // gap = 50
            );

            double gap = (double) method.invoke(roleSelector, testColor, taken);
            assertEquals(20.0, gap, 0.0001);
        }

        @Test
        @DisplayName("best returns empty when candidate does not satisfy minimum distance")
        @SuppressWarnings("unchecked")
        void best_whenCandidateBelowMinDistance_returnsEmpty() throws Exception {
            Method method = RoleSelector.class.getDeclaredMethod("best",
                    List.class, List.class, double.class, Predicate.class, ToDoubleFunction.class);
            method.setAccessible(true);

            Oklch takenColor = new Oklch(0.50, 0.10, 50.0);
            Oklch candidateColor = new Oklch(0.51, 0.10, 50.0); // dist ~ 0.01
            List<ColorCluster> pool = List.of(new ColorCluster(candidateColor, 0.50));
            List<Oklch> taken = List.of(takenColor);

            Optional<Oklch> result = (Optional<Oklch>) method.invoke(
                    roleSelector, pool, taken, 0.08, (Predicate<ColorCluster>) _ -> true,
                    (ToDoubleFunction<ColorCluster>) _ -> 1.0);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("best returns candidate with highest score among eligible candidates")
        @SuppressWarnings("unchecked")
        void best_selectsCandidateWithHighestScore() throws Exception {
            Method method = RoleSelector.class.getDeclaredMethod("best",
                    List.class, List.class, double.class, Predicate.class, ToDoubleFunction.class);
            method.setAccessible(true);

            Oklch takenColor = new Oklch(0.20, 0.05, 10.0);
            Oklch candidateA = new Oklch(0.70, 0.15, 100.0);
            Oklch candidateB = new Oklch(0.80, 0.20, 200.0);

            List<ColorCluster> pool = List.of(
                    new ColorCluster(candidateA, 0.30),
                    new ColorCluster(candidateB, 0.40)
            );
            List<Oklch> taken = List.of(takenColor);

            // Score candidateB higher
            Optional<Oklch> result = (Optional<Oklch>) method.invoke(
                    roleSelector, pool, taken, 0.05, (Predicate<ColorCluster>) _ -> true,
                    (ToDoubleFunction<ColorCluster>) c -> c.color().equals(candidateB) ? 10.0 : 1.0);

            assertTrue(result.isPresent());
            assertEquals(candidateB, result.get());
        }
    }

    @Nested
    @DisplayName("Constant Verification Tests")
    class ConstantVerificationTests {

        @Test
        @DisplayName("verifies all internal selection constants match specification")
        void verifyInternalConstants() throws Exception {
            Field minShare = RoleSelector.class.getDeclaredField("MIN_SHARE");
            minShare.setAccessible(true);
            assertEquals(EXPECTED_MIN_SHARE, (double) minShare.get(null), 0.0001);

            Field grayChroma = RoleSelector.class.getDeclaredField("GRAY_CHROMA");
            grayChroma.setAccessible(true);
            assertEquals(EXPECTED_GRAY_CHROMA, (double) grayChroma.get(null), 0.0001);

            Field flareMinDist = RoleSelector.class.getDeclaredField("FLARE_MIN_DISTANCE");
            flareMinDist.setAccessible(true);
            assertEquals(EXPECTED_FLARE_MIN_DISTANCE, (double) flareMinDist.get(null), 0.0001);

            Field waveMinDist = RoleSelector.class.getDeclaredField("WAVE_MIN_DISTANCE");
            waveMinDist.setAccessible(true);
            assertEquals(EXPECTED_WAVE_MIN_DISTANCE, (double) waveMinDist.get(null), 0.0001);

            Field sparkMinDist = RoleSelector.class.getDeclaredField("SPARK_MIN_DISTANCE");
            sparkMinDist.setAccessible(true);
            assertEquals(EXPECTED_SPARK_MIN_DISTANCE, (double) sparkMinDist.get(null), 0.0001);
        }
    }
}