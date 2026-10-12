package prism.infrastructure.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FileImageReader Unit Tests")
class TestFileImageReader {

    private FileImageReader reader;

    @BeforeEach
    void setUp() {
        reader = new FileImageReader();
    }

    @Nested
    @DisplayName("readSample")
    class ReadSampleTests {

        @Test
        @DisplayName("should throw NullPointerException when path is null")
        void shouldThrowNullPointerExceptionWhenPathIsNull() {
            assertThrows(NullPointerException.class, () -> reader.readSample(null));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when path is empty string")
        void shouldThrowIllegalArgumentExceptionWhenPathIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.readSample("")
            );
            assertEquals("File path can not be empty", exception.getMessage());
        }

        @Test
        @DisplayName("should throw IOException when file does not exist")
        void shouldThrowIOExceptionWhenFileDoesNotExist(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("non_existent_image.png");

            IOException exception = assertThrows(
                    IOException.class,
                    () -> reader.readSample(nonExistent.toString())
            );
            assertEquals("Could not open image: " + nonExistent, exception.getMessage());
        }

        @Test
        @DisplayName("should throw IOException when file is not a supported image")
        void shouldThrowIOExceptionWhenFileIsNotAnImage(@TempDir Path tempDir) throws IOException {
            Path textFile = tempDir.resolve("not_an_image.txt");
            Files.writeString(textFile, "This is plain text, not an image.");

            IOException exception = assertThrows(
                    IOException.class,
                    () -> reader.readSample(textFile.toString())
            );
            assertEquals("Unsupported image format: " + textFile, exception.getMessage());
        }

        @Test
        @DisplayName("should read image without subsampling when dimensions are below 1000px")
        void shouldReadImageWithoutSubsamplingBelowMaxDimension(@TempDir Path tempDir) throws IOException {
            Path imagePath = createTestImage(tempDir, "small.png", 200, 150, "PNG");

            BufferedImage result = reader.readSample(imagePath.toString());

            assertNotNull(result);
            assertEquals(200, result.getWidth());
            assertEquals(150, result.getHeight());
        }

        @Test
        @DisplayName("should read image without subsampling when largest dimension is exactly 1000px")
        void shouldReadImageWithoutSubsamplingAtExactMaxDimension(@TempDir Path tempDir) throws IOException {
            Path imagePath = createTestImage(tempDir, "boundary.png", 1000, 800, "PNG");

            BufferedImage result = reader.readSample(imagePath.toString());

            assertNotNull(result);
            assertEquals(1000, result.getWidth());
            assertEquals(800, result.getHeight());
        }

        @Test
        @DisplayName("should subsample image when width exceeds 1000px")
        void shouldSubsampleImageWhenWidthExceedsMaxDimension(@TempDir Path tempDir) throws IOException {
            // 2400 width -> sample factor ceil(2400/1000) = 3 -> resulting width 800 <= 1000
            Path imagePath = createTestImage(tempDir, "wide.png", 2400, 600, "PNG");

            BufferedImage result = reader.readSample(imagePath.toString());

            assertNotNull(result);
            assertTrue(result.getWidth() <= 1000);
            assertEquals(800, result.getWidth());
            assertEquals(200, result.getHeight());
        }

        @Test
        @DisplayName("should subsample image when height exceeds 1000px")
        void shouldSubsampleImageWhenHeightExceedsMaxDimension(@TempDir Path tempDir) throws IOException {
            // 2100 height -> sample factor ceil(2100/1000) = 3 -> resulting height 700 <= 1000
            Path imagePath = createTestImage(tempDir, "tall.png", 600, 2100, "PNG");

            BufferedImage result = reader.readSample(imagePath.toString());

            assertNotNull(result);
            assertTrue(result.getHeight() <= 1000);
            assertEquals(200, result.getWidth());
            assertEquals(700, result.getHeight());
        }

        @Test
        @DisplayName("should read JPEG image format successfully")
        void shouldReadJpegImageSuccessfully(@TempDir Path tempDir) throws IOException {
            Path imagePath = createTestImage(tempDir, "sample.jpg", 300, 200, "JPEG");

            BufferedImage result = reader.readSample(imagePath.toString());

            assertNotNull(result);
            assertEquals(300, result.getWidth());
            assertEquals(200, result.getHeight());
        }
    }

    @Nested
    @DisplayName("calculateSample")
    class CalculateSampleTests {

        private int invokeCalculateSample(int width, int height) throws Exception {
            Method method = FileImageReader.class.getDeclaredMethod("calculateSample", Integer.class, Integer.class);
            method.setAccessible(true);
            return (Integer) method.invoke(reader, width, height);
        }

        @Test
        @DisplayName("should return sample factor of 1 when largest dimension is less than 1000")
        void shouldReturnOneWhenDimensionLessThanMax() throws Exception {
            assertEquals(1, invokeCalculateSample(500, 300));
        }

        @Test
        @DisplayName("should return sample factor of 1 when largest dimension is exactly 1000")
        void shouldReturnOneWhenDimensionEqualsMax() throws Exception {
            assertEquals(1, invokeCalculateSample(1000, 800));
        }

        @Test
        @DisplayName("should return sample factor of 2 when largest dimension is 1001")
        void shouldReturnTwoWhenDimensionIsJustOverMax() throws Exception {
            assertEquals(2, invokeCalculateSample(1001, 500));
        }

        @Test
        @DisplayName("should return sample factor of 2 when largest dimension is 2000")
        void shouldReturnTwoWhenDimensionIsDoubleMax() throws Exception {
            assertEquals(2, invokeCalculateSample(2000, 1000));
        }

        @Test
        @DisplayName("should return sample factor of 3 when largest dimension is 2001")
        void shouldReturnThreeWhenDimensionIsJustOverDoubleMax() throws Exception {
            assertEquals(3, invokeCalculateSample(2001, 1000));
        }

        @Test
        @DisplayName("should calculate based on height when height is the largest dimension")
        void shouldCalculateBasedOnHeightWhenLarger() throws Exception {
            assertEquals(2, invokeCalculateSample(400, 1500));
        }

        @Test
        @DisplayName("should return sample factor of 1 when dimensions are 0")
        void shouldReturnOneWhenDimensionsAreZero() throws Exception {
            assertEquals(1, invokeCalculateSample(0, 0));
        }
    }

    private static Path createTestImage(
            Path tempDir, String fileName, int width, int height, String format
    ) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, format, filePath.toFile());
        return filePath;
    }
}