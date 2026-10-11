package prism.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FileUtils Unit Tests")
class TestFileUtils {

    @Nested
    @DisplayName("isValidDirectory")
    class IsValidDirectoryTests {

        @Test
        @DisplayName("should return false when path string is null")
        void shouldReturnFalseWhenPathIsNull() {
            assertFalse(FileUtils.isValidDirectory(null));
        }

        @Test
        @DisplayName("should return false when path string contains invalid path characters")
        void shouldReturnFalseWhenPathContainsInvalidCharacters() {
            assertFalse(FileUtils.isValidDirectory("invalid\0path"));
        }

        @Test
        @DisplayName("should return false when directory does not exist")
        void shouldReturnFalseWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("non_existent_dir");
            assertFalse(FileUtils.isValidDirectory(nonExistent.toString()));
        }

        @Test
        @DisplayName("should return false when path points to a regular file instead of a directory")
        void shouldReturnFalseWhenPathIsRegularFile(@TempDir Path tempDir) throws IOException {
            Path regularFile = Files.createFile(tempDir.resolve("regular_file.txt"));
            assertFalse(FileUtils.isValidDirectory(regularFile.toString()));
        }

        @Test
        @DisplayName("should return false when directory is not writable")
        void shouldReturnFalseWhenDirectoryIsNotWritable(@TempDir Path tempDir) throws IOException {
            Path readOnlyDir = Files.createDirectory(tempDir.resolve("readonly_dir"));
            File file = readOnlyDir.toFile();
            if (file.setWritable(false, false)) {
                assertFalse(FileUtils.isValidDirectory(readOnlyDir.toString()));
            }
        }

        @Test
        @DisplayName("should return true when path points to an existing writable directory")
        void shouldReturnTrueWhenDirectoryIsExistingAndWritable(@TempDir Path tempDir) {
            assertTrue(FileUtils.isValidDirectory(tempDir.toString()));
        }
    }

    @Nested
    @DisplayName("buildPath")
    class BuildPathTests {

        @Test
        @DisplayName("should concatenate dir and file without slashes")
        void shouldConcatenateDirAndFileWithoutSlashes() {
            assertEquals("foo/bar", FileUtils.buildPath("foo", "bar"));
        }

        @Test
        @DisplayName("should concatenate when dir has trailing slash")
        void shouldConcatenateWhenDirHasTrailingSlash() {
            assertEquals("foo/bar", FileUtils.buildPath("foo/", "bar"));
        }

        @Test
        @DisplayName("should concatenate when file has leading slash")
        void shouldConcatenateWhenFileHasLeadingSlash() {
            assertEquals("foo/bar", FileUtils.buildPath("foo", "/bar"));
        }

        @Test
        @DisplayName("should concatenate when dir has trailing slash and file has leading slash")
        void shouldConcatenateWhenBothHaveSlashes() {
            assertEquals("foo/bar", FileUtils.buildPath("foo/", "/bar"));
        }

        @Test
        @DisplayName("should handle empty dir string")
        void shouldHandleEmptyDirString() {
            assertEquals("/bar", FileUtils.buildPath("", "bar"));
        }

        @Test
        @DisplayName("should handle empty file string")
        void shouldHandleEmptyFileString() {
            assertEquals("foo/", FileUtils.buildPath("foo", ""));
        }

        @Test
        @DisplayName("should handle both dir and file as single slash")
        void shouldHandleBothAsSingleSlash() {
            assertEquals("/", FileUtils.buildPath("/", "/"));
        }

        @Test
        @DisplayName("should concatenate nested directory and file path")
        void shouldConcatenateNestedDirectoryAndFilePath() {
            assertEquals("/var/log/syslog", FileUtils.buildPath("/var/log", "syslog"));
        }

        @Test
        @DisplayName("should concatenate multi-level subpaths")
        void shouldConcatenateMultiLevelSubpaths() {
            assertEquals("a/b/c/d", FileUtils.buildPath("a/b/", "/c/d"));
        }
    }

    @Nested
    @DisplayName("assertFileAndDirExistence")
    class AssertFileAndDirExistenceTests {

        @Test
        @DisplayName("should create both parent directory and file when neither exists")
        void shouldCreateParentDirectoryAndFileWhenNeitherExists(@TempDir Path tempDir) {
            Path targetFile = tempDir.resolve("nested/dir/new_file.txt");

            FileUtils.assertFileAndDirExistence(targetFile);

            assertTrue(Files.isDirectory(targetFile.getParent()));
            assertTrue(Files.isRegularFile(targetFile));
        }

        @Test
        @DisplayName("should create file when parent directory already exists")
        void shouldCreateFileWhenParentDirectoryAlreadyExists(@TempDir Path tempDir) {
            Path targetFile = tempDir.resolve("new_file.txt");

            FileUtils.assertFileAndDirExistence(targetFile);

            assertTrue(Files.isRegularFile(targetFile));
        }

        @Test
        @DisplayName("should be idempotent and not fail when both parent directory and file already exist")
        void shouldNotFailWhenBothDirectoryAndFileAlreadyExist(@TempDir Path tempDir) throws IOException {
            Path targetFile = tempDir.resolve("existing_file.txt");
            Files.writeString(targetFile, "existing content");

            FileUtils.assertFileAndDirExistence(targetFile);

            assertTrue(Files.isRegularFile(targetFile));
            assertEquals("existing content", Files.readString(targetFile));
        }

        @Test
        @DisplayName("should create deeply nested directories and file")
        void shouldCreateDeeplyNestedDirectoriesAndFile(@TempDir Path tempDir) {
            Path deepTarget = tempDir.resolve("level1/level2/level3/level4/deep.txt");

            FileUtils.assertFileAndDirExistence(deepTarget);

            assertTrue(Files.isDirectory(deepTarget.getParent()));
            assertTrue(Files.isRegularFile(deepTarget));
        }

        @Test
        @DisplayName("should throw RuntimeException when filePath has no parent directory")
        void shouldThrowRuntimeExceptionWhenFilePathHasNoParent() {
            Path pathWithoutParent = Path.of("root_file.txt");

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> FileUtils.assertFileAndDirExistence(pathWithoutParent)
            );

            assertEquals("Error while asserting file and directory existence", exception.getMessage());
            assertInstanceOf(NullPointerException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw RuntimeException when parent path is an existing regular file")
        void shouldThrowRuntimeExceptionWhenParentPathIsRegularFile(@TempDir Path tempDir) throws IOException {
            Path parentAsFile = Files.createFile(tempDir.resolve("parent_is_file"));
            Path invalidTarget = parentAsFile.resolve("child_file.txt");

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> FileUtils.assertFileAndDirExistence(invalidTarget)
            );

            assertEquals("Error while asserting file and directory existence", exception.getMessage());
            assertNotNull(exception.getCause());
        }
    }
}