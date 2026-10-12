package prism.application.component;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.configuration.context.AppContext;
import prism.domain.vo.ColorCluster;
import prism.domain.vo.Oklch;
import prism.infrastructure.filesystem.FileImageReader;
import prism.utils.ColorUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageReader Unit Tests")
class TestImageReader {

    @Mock
    private FileImageReader mockFileImageReader;

    private ImageReader imageReader;

    @BeforeEach
    void setUp() throws Exception {
        resetAppContext();
        imageReader = new ImageReader();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetAppContext();
    }

    private static void resetAppContext() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    private void initializeContextWithFileReader() {
        AppContext.initialize(mockFileImageReader);
    }

    private static BufferedImage createTestImage(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Nested
    @DisplayName("AppContext Integration Tests")
    class AppContextIntegrationTests {

        @Test
        @DisplayName("readClusters should throw IllegalStateException when AppContext is uninitialized")
        void readClusters_whenAppContextNotInitialized_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> imageReader.readClusters("sample.png")
            );
            assertTrue(ex.getMessage().contains("App context has not been initialized"));
        }

        @Test
        @DisplayName("readColors should throw IllegalStateException when AppContext is uninitialized")
        @SuppressWarnings("deprecation")
        void readColors_whenAppContextNotInitialized_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> imageReader.readColors("sample.png")
            );
            assertTrue(ex.getMessage().contains("App context has not been initialized"));
        }

        @Test
        @DisplayName("readClusters should throw IllegalStateException when FileImageReader is not registered in AppContext")
        void readClusters_whenFileImageReaderNotRegistered_throwsIllegalStateException() {
            AppContext.initialize("UnrelatedComponent");

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> imageReader.readClusters("sample.png")
            );
            assertTrue(ex.getMessage().contains("FileImageReader"));
        }

        @Test
        @DisplayName("readColors should throw IllegalStateException when FileImageReader is not registered in AppContext")
        @SuppressWarnings("deprecation")
        void readColors_whenFileImageReaderNotRegistered_throwsIllegalStateException() {
            AppContext.initialize("UnrelatedComponent");

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> imageReader.readColors("sample.png")
            );
            assertTrue(ex.getMessage().contains("FileImageReader"));
        }
    }

    @Nested
    @DisplayName("I/O and FileReader Exception Propagation Tests")
    class IOExceptionPropagationTests {

        @Test
        @DisplayName("readClusters should propagate IOException thrown by FileImageReader")
        void readClusters_whenFileReaderThrowsIOException_propagatesIOException() throws IOException {
            initializeContextWithFileReader();
            when(mockFileImageReader.readSample("corrupted.png"))
                    .thenThrow(new IOException("Corrupted image stream"));

            IOException ex = assertThrows(
                    IOException.class,
                    () -> imageReader.readClusters("corrupted.png")
            );
            assertEquals("Corrupted image stream", ex.getMessage());
            verify(mockFileImageReader).readSample("corrupted.png");
        }

        @Test
        @DisplayName("readColors should propagate IOException thrown by FileImageReader")
        @SuppressWarnings("deprecation")
        void readColors_whenFileReaderThrowsIOException_propagatesIOException() throws IOException {
            initializeContextWithFileReader();
            when(mockFileImageReader.readSample("corrupted.png"))
                    .thenThrow(new IOException("Disk read error"));

            IOException ex = assertThrows(
                    IOException.class,
                    () -> imageReader.readColors("corrupted.png")
            );
            assertEquals("Disk read error", ex.getMessage());
            verify(mockFileImageReader).readSample("corrupted.png");
        }
    }

    @Nested
    @DisplayName("readClusters() - Color Cluster Extraction Tests")
    class ReadClustersTests {

        @Test
        @DisplayName("readClusters should return empty list when image has only fully transparent pixels")
        void readClusters_withFullyTransparentImage_returnsEmptyList() throws IOException {
            initializeContextWithFileReader();
            BufferedImage transparentImage = createTestImage(3, 3, 0x00000000);
            when(mockFileImageReader.readSample("transparent.png")).thenReturn(transparentImage);

            List<ColorCluster> clusters = imageReader.readClusters("transparent.png");

            assertNotNull(clusters);
            assertTrue(clusters.isEmpty());
        }

        @Test
        @DisplayName("readClusters should return single cluster with share 1.0 for solid single-color image")
        void readClusters_withSolidOpaqueColor_returnsSingleClusterWithFullShare() throws IOException {
            initializeContextWithFileReader();
            int redRgb = 0xFFFF0000;
            BufferedImage redImage = createTestImage(4, 4, redRgb);
            when(mockFileImageReader.readSample("red.png")).thenReturn(redImage);

            List<ColorCluster> clusters = imageReader.readClusters("red.png");

            assertEquals(1, clusters.size());
            ColorCluster cluster = clusters.getFirst();
            assertEquals(1.0, cluster.share(), 0.0001);
            assertEquals(Oklch.fromRgb(0xFF0000), cluster.color());
        }

        @Test
        @DisplayName("readClusters should exclude alpha=0 pixels and compute share based only on non-transparent pixels")
        void readClusters_withPartiallyTransparentImage_calculatesShareOnlyFromOpaquePixels() throws IOException {
            initializeContextWithFileReader();
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0x00FFFFFF); // alpha == 0
            image.setRGB(1, 0, 0x00123456); // alpha == 0
            image.setRGB(0, 1, 0xFF0000FF); // opaque blue
            image.setRGB(1, 1, 0xFF0000FF); // opaque blue

            when(mockFileImageReader.readSample("half_transparent.png")).thenReturn(image);

            List<ColorCluster> clusters = imageReader.readClusters("half_transparent.png");

            assertEquals(1, clusters.size());
            ColorCluster cluster = clusters.getFirst();
            assertEquals(1.0, cluster.share(), 0.0001);
            assertEquals(Oklch.fromRgb(0x0000FF), cluster.color());
        }

        @Test
        @DisplayName("readClusters should sort distinct color clusters in descending order of their image share")
        void readClusters_withMultipleDistinctColors_sortsClustersByDescendingShare() throws IOException {
            initializeContextWithFileReader();
            BufferedImage image = createImage();

            when(mockFileImageReader.readSample("rgb_distribution.png")).thenReturn(image);

            List<ColorCluster> clusters = imageReader.readClusters("rgb_distribution.png");

            assertEquals(3, clusters.size());
            assertEquals(0.60, clusters.get(0).share(), 0.0001);
            assertEquals(Oklch.fromRgb(0xFF0000), clusters.get(0).color());

            assertEquals(0.30, clusters.get(1).share(), 0.0001);
            assertEquals(Oklch.fromRgb(0x00FF00), clusters.get(1).color());

            assertEquals(0.10, clusters.get(2).share(), 0.0001);
            assertEquals(Oklch.fromRgb(0x0000FF), clusters.get(2).color());
        }

        private static @NonNull BufferedImage createImage() {
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            // 60 pixels red (0.60 share)
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 10; x++) {
                    image.setRGB(x, y, 0xFFFF0000);
                }
            }
            // 30 pixels green (0.30 share)
            for (int y = 6; y < 9; y++) {
                for (int x = 0; x < 10; x++) {
                    image.setRGB(x, y, 0xFF00FF00);
                }
            }
            // 10 pixels blue (0.10 share)
            for (int x = 0; x < 10; x++) {
                image.setRGB(x, 9, 0xFF0000FF);
            }
            return image;
        }

        @Test
        @DisplayName("readClusters should merge perceptually similar colors within MERGE_DISTANCE into a single cluster")
        void readClusters_withPerceptuallyCloseColors_mergesIntoSingleCluster() throws IOException {
            initializeContextWithFileReader();
            // 0x0F2030 -> R=15 (bucket R=0), G=32 (bucket G=2), B=48 (bucket B=3) -> bucket 35
            // 0x102030 -> R=16 (bucket R=1), G=32 (bucket G=2), B=48 (bucket B=3) -> bucket 291
            // Distance between them is ~0.002, well below MERGE_DISTANCE (0.05)
            BufferedImage image = new BufferedImage(2, 5, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 5; y++) {
                image.setRGB(0, y, 0xFF0F2030); // 5 pixels of 0x0F2030
                image.setRGB(1, y, 0xFF102030); // 5 pixels of 0x102030
            }

            when(mockFileImageReader.readSample("close_colors.png")).thenReturn(image);

            List<ColorCluster> clusters = imageReader.readClusters("close_colors.png");

            assertEquals(1, clusters.size());
            ColorCluster cluster = clusters.getFirst();
            assertEquals(1.0, cluster.share(), 0.0001);

            // Weighted average RGB: R = Math.round((15*5 + 16*5) / 10.0) = 16, G = 32, B = 48 -> 0x102030
            int expectedRgb = (16 << 16) | (32 << 8) | 48;
            assertEquals(Oklch.fromRgb(expectedRgb), cluster.color());
        }

        @Test
        @DisplayName("readClusters should keep colors separate when OKLab distance is at or above MERGE_DISTANCE")
        void readClusters_withPerceptuallyDivergentColors_keepsClustersSeparate() throws IOException {
            initializeContextWithFileReader();
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xFFFF0000); // pure red
            image.setRGB(1, 0, 0xFFFF0000);
            image.setRGB(0, 1, 0xFF00FFFF); // pure cyan
            image.setRGB(1, 1, 0xFF00FFFF);

            when(mockFileImageReader.readSample("contrast.png")).thenReturn(image);

            List<ColorCluster> clusters = imageReader.readClusters("contrast.png");

            assertEquals(2, clusters.size());
            assertEquals(0.5, clusters.get(0).share(), 0.0001);
            assertEquals(0.5, clusters.get(1).share(), 0.0001);
        }

        @Test
        @DisplayName("readClusters should compute true mean RGB for pixels falling into the exact same 12-bit bucket")
        void readClusters_withMultiplePixelsInSameBucket_computesTrueMeanRgb() throws IOException {
            initializeContextWithFileReader();
            // 0x102030: R=16, G=32, B=48 -> quantized: (1<<8) | (2<<4) | 3 = bucket 291
            // 0x162030: R=22, G=32, B=48 -> quantized: (1<<8) | (2<<4) | 3 = bucket 291
            BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xFF102030);
            image.setRGB(1, 0, 0xFF162030);

            when(mockFileImageReader.readSample("same_bucket.png")).thenReturn(image);

            List<ColorCluster> clusters = imageReader.readClusters("same_bucket.png");

            assertEquals(1, clusters.size());
            // True mean R = (16 + 22) / 2 = 19 (0x13), G = 32 (0x20), B = 48 (0x30) -> 0x132030
            int expectedRgb = (19 << 16) | (32 << 8) | 48;
            assertEquals(Oklch.fromRgb(expectedRgb), clusters.getFirst().color());
        }
    }

    @Nested
    @DisplayName("readColors() [Deprecated] Tests")
    @SuppressWarnings("deprecation")
    class ReadColorsTests {

        @Test
        @DisplayName("readColors should return single row with bucket and count for solid single-color image")
        void readColors_withSolidColor_returnsSingleRowWithBucketAndCount() throws IOException {
            initializeContextWithFileReader();
            BufferedImage redImage = createTestImage(4, 4, 0xFFFF0000);
            when(mockFileImageReader.readSample("red.png")).thenReturn(redImage);

            Integer[][] pairs = imageReader.readColors("red.png");

            assertEquals(1, pairs.length);
            int expectedBucket = ColorUtils.quantizeColor(0xFF0000);
            assertEquals(expectedBucket, pairs[0][0]);
            assertEquals(16, pairs[0][1]);
        }

        @Test
        @DisplayName("readColors should return all active buckets and omit buckets with zero occurrences")
        void readColors_withMultipleColors_returnsAllActiveBuckets() throws IOException {
            initializeContextWithFileReader();
            BufferedImage image = new BufferedImage(5, 2, BufferedImage.TYPE_INT_ARGB);
            // 7 pixels blue, 3 pixels red
            for (int x = 0; x < 5; x++) {
                image.setRGB(x, 0, 0xFF0000FF);
            }
            image.setRGB(0, 1, 0xFF0000FF);
            image.setRGB(1, 1, 0xFF0000FF);
            image.setRGB(2, 1, 0xFFFF0000);
            image.setRGB(3, 1, 0xFFFF0000);
            image.setRGB(4, 1, 0xFFFF0000);

            when(mockFileImageReader.readSample("multi.png")).thenReturn(image);

            Integer[][] pairs = imageReader.readColors("multi.png");

            assertEquals(2, pairs.length);
            int blueBucket = ColorUtils.quantizeColor(0x0000FF);
            int redBucket = ColorUtils.quantizeColor(0xFF0000);

            // Buckets are ordered by bucket index
            assertEquals(blueBucket, pairs[0][0]);
            assertEquals(7, pairs[0][1]);
            assertEquals(redBucket, pairs[1][0]);
            assertEquals(3, pairs[1][1]);
        }

        @Test
        @DisplayName("readColors should return empty 2D array for fully transparent image")
        void readColors_withFullyTransparentImage_returnsEmpty2DArray() throws IOException {
            initializeContextWithFileReader();
            BufferedImage transparentImage = createTestImage(2, 2, 0x00FFFFFF);
            when(mockFileImageReader.readSample("trans.png")).thenReturn(transparentImage);

            Integer[][] pairs = imageReader.readColors("trans.png");

            assertNotNull(pairs);
            assertEquals(0, pairs.length);
        }

        @Test
        @DisplayName("readColors should skip transparent pixels and count only opaque pixels")
        void readColors_skipsFullyTransparentPixels_countsOnlyOpaquePixels() throws IOException {
            initializeContextWithFileReader();
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0x00000000); // transparent
            image.setRGB(1, 0, 0x00112233); // transparent
            image.setRGB(0, 1, 0xFF00FF00); // green
            image.setRGB(1, 1, 0xFF00FF00); // green

            when(mockFileImageReader.readSample("partial.png")).thenReturn(image);

            Integer[][] pairs = imageReader.readColors("partial.png");

            assertEquals(1, pairs.length);
            int greenBucket = ColorUtils.quantizeColor(0x00FF00);
            assertEquals(greenBucket, pairs[0][0]);
            assertEquals(2, pairs[0][1]);
        }
    }

    @Nested
    @DisplayName("Private Helper Methods Reflection Tests")
    class PrivateMethodsReflectionTests {

        @Test
        @DisplayName("toColorCountPairs should throw IllegalArgumentException when occurrences array is empty")
        void toColorCountPairs_whenArrayIsEmpty_throwsIllegalArgumentException() throws Exception {
            Method method = ImageReader.class.getDeclaredMethod("toColorCountPairs", Integer[].class);
            method.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> method.invoke(imageReader, (Object) new Integer[0])
            );
            assertInstanceOf(IllegalArgumentException.class, ite.getCause());
            assertEquals("occurrences array can not be empty", ite.getCause().getMessage());
        }

        @Test
        @DisplayName("toColorCountPairs should throw IllegalArgumentException when occurrences array is null")
        void toColorCountPairs_whenArrayIsNull_throwsIllegalArgumentException() throws Exception {
            Method method = ImageReader.class.getDeclaredMethod("toColorCountPairs", Integer[].class);
            method.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> method.invoke(imageReader, (Object) null)
            );
            assertInstanceOf(IllegalArgumentException.class, ite.getCause());
            assertEquals("occurrences array can not be empty", ite.getCause().getMessage());
        }

        @Test
        @DisplayName("mean should accurately pack RGB components with Math.round rounding")
        void mean_computesPackedRgbAccuratelyWithRounding() throws Exception {
            Method method = ImageReader.class.getDeclaredMethod("mean", double.class, double.class, double.class, double.class);
            method.setAccessible(true);

            // Exact division: sumR=255.0, sumG=128.0, sumB=64.0, count=1.0 -> 0xFF8040
            int exactResult = (int) method.invoke(null, 255.0, 128.0, 64.0, 1.0);
            int expectedExact = (255 << 16) | (128 << 8) | 64;
            assertEquals(expectedExact, exactResult);

            // Rounding test: 10/3 = 3.333 -> 3, 11/3 = 3.667 -> 4, 12/3 = 4.0 -> 4
            int roundedResult = (int) method.invoke(null, 10.0, 11.0, 12.0, 3.0);
            int expectedRounded = (3 << 16) | (4 << 8) | 4;
            assertEquals(expectedRounded, roundedResult);
        }

        @Test
        @DisplayName("toClusters should return empty list when total pixel count is zero")
        @SuppressWarnings("unchecked")
        void toClusters_whenTotalIsZero_returnsEmptyList() throws Exception {
            Method method = ImageReader.class.getDeclaredMethod("toClusters",
                    long[].class, long[].class, long[].class, long[].class, long.class);
            method.setAccessible(true);

            long[] zeros = new long[4096];
            List<ColorCluster> result = (List<ColorCluster>) method.invoke(
                    imageReader, zeros, zeros, zeros, zeros, 0L);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}