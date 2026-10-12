package prism.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Listened Unit Tests")
class TestListened {

    @Nested
    @DisplayName("Constructors & Getters/Setters")
    class ConstructorAndPropertyTests {

        @Test
        @DisplayName("should instantiate via no-args constructor with null properties")
        void shouldInstantiateViaNoArgsConstructor() {
            Listened listened = new Listened();
            assertNotNull(listened);
            assertNull(listened.getSender());
            assertNull(listened.getCommand());
        }

        @Test
        @DisplayName("should instantiate via all-args constructor setting sender and command")
        void shouldInstantiateViaAllArgsConstructor() {
            Listened listened = new Listened("daemon-socket", "listen image.png");
            assertNotNull(listened);
            assertEquals("daemon-socket", listened.getSender());
            assertEquals("listen image.png", listened.getCommand());
        }

        @Test
        @DisplayName("should get and set sender property")
        void shouldGetAndSetSender() {
            Listened listened = new Listened();
            listened.setSender("client-123");
            assertEquals("client-123", listened.getSender());
        }

        @Test
        @DisplayName("should get and set command property")
        void shouldGetAndSetCommand() {
            Listened listened = new Listened();
            listened.setCommand("status --verbose");
            assertEquals("status --verbose", listened.getCommand());
        }

        @Test
        @DisplayName("should allow setting null sender and command")
        void shouldAllowSettingNullSenderAndCommand() {
            Listened listened = new Listened("initial-sender", "initial-command");
            listened.setSender(null);
            listened.setCommand(null);

            assertNull(listened.getSender());
            assertNull(listened.getCommand());
        }
    }

    @Nested
    @DisplayName("extractFirstImageFileExt")
    class ExtractFirstImageFileExtTests {

        @Test
        @DisplayName("should extract png extension from command")
        void shouldExtractPngExtension() {
            Listened listened = new Listened("client", "process /path/to/image.png");
            assertEquals("png", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract jpg extension from command")
        void shouldExtractJpgExtension() {
            Listened listened = new Listened("client", "process photo.jpg");
            assertEquals("jpg", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract jpeg extension from command")
        void shouldExtractJpegExtension() {
            Listened listened = new Listened("client", "process picture.jpeg");
            assertEquals("jpeg", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract webp extension from command")
        void shouldExtractWebpExtension() {
            Listened listened = new Listened("client", "process graphic.webp");
            assertEquals("webp", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract extension case-insensitively when supported by ActiveImageType")
        void shouldExtractExtensionCaseInsensitively() {
            Listened listened = new Listened("client", "process IMAGE.PNG");
            assertEquals("PNG", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return first matching extension when command contains multiple image files")
        void shouldReturnFirstMatchingExtensionWhenMultipleImagesExist() {
            Listened listened = new Listened("client", "diff first.png second.jpg third.webp");
            assertEquals("png", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should skip non-image files and return first active image extension")
        void shouldSkipNonImageFilesBeforeImageFile() {
            Listened listened = new Listened("client", "run document.pdf notes.txt photo.jpg");
            assertEquals("jpg", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when command contains only non-image files")
        void shouldReturnEmptyStringWhenOnlyNonImageFilesPresent() {
            Listened listened = new Listened("client", "read document.txt manual.pdf");
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when command has tokens without extension")
        void shouldReturnEmptyStringWhenTokensHaveNoExtension() {
            Listened listened = new Listened("client", "status --flag argument");
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when token ends with a trailing dot")
        void shouldReturnEmptyStringWhenTokenEndsWithTrailingDot() {
            Listened listened = new Listened("client", "process image.");
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string for dotfiles without extension")
        void shouldReturnEmptyStringForDotfileWithoutExtension() {
            Listened listened = new Listened("client", "cat .gitignore");
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract extension from file with multiple dots")
        void shouldExtractExtensionFromMultiDotFilename() {
            Listened listened = new Listened("client", "archive.backup.png");
            assertEquals("png", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should extract extension from quoted token with spaces")
        void shouldExtractExtensionFromQuotedTokenWithSpaces() {
            Listened listened = new Listened("client", "convert \"my vacation photo.jpg\" --out out.txt");
            assertEquals("jpg", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when command is null")
        void shouldReturnEmptyStringWhenCommandIsNull() {
            Listened listened = new Listened("client", null);
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when command is empty string")
        void shouldReturnEmptyStringWhenCommandIsEmpty() {
            Listened listened = new Listened("client", "");
            assertEquals("", listened.extractFirstImageFileExt());
        }

        @Test
        @DisplayName("should return empty string when command contains only whitespace")
        void shouldReturnEmptyStringWhenCommandIsWhitespace() {
            Listened listened = new Listened("client", "   \t  \n  ");
            assertEquals("", listened.extractFirstImageFileExt());
        }
    }

    @Nested
    @DisplayName("extractCmdFile")
    class ExtractCmdFileTests {

        @Test
        @DisplayName("should extract matching file name for given extension")
        void shouldExtractMatchingFileNameForGivenExtension() {
            Listened listened = new Listened("client", "process /home/user/image.png --fast");
            assertEquals("/home/user/image.png", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should extract quoted file name token with whitespace")
        void shouldExtractQuotedFileNameToken() {
            Listened listened = new Listened("client", "process \"/home/user/my image.jpg\"");
            assertEquals("/home/user/my image.jpg", listened.extractCmdFile("jpg"));
        }

        @Test
        @DisplayName("should extract first matching file when multiple tokens have requested extension")
        void shouldExtractFirstMatchingFileWhenMultipleMatch() {
            Listened listened = new Listened("client", "combine first.png second.png");
            assertEquals("first.png", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should extract correct file among multiple tokens with different extensions")
        void shouldExtractCorrectFileAmongMultipleExtensions() {
            Listened listened = new Listened("client", "convert doc.pdf image.png archive.zip");
            assertEquals("image.png", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should return empty string when no token matches requested extension")
        void shouldReturnEmptyStringWhenNoTokenMatchesExtension() {
            Listened listened = new Listened("client", "process photo.jpg");
            assertEquals("", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should return empty string when extension is empty string")
        void shouldReturnEmptyStringWhenExtensionIsEmpty() {
            Listened listened = new Listened("client", "process photo.jpg");
            assertEquals("", listened.extractCmdFile(""));
        }

        @Test
        @DisplayName("should return empty string when extension contains only whitespace")
        void shouldReturnEmptyStringWhenExtensionIsWhitespace() {
            Listened listened = new Listened("client", "process photo.jpg");
            assertEquals("", listened.extractCmdFile("   "));
        }

        @Test
        @DisplayName("should throw NullPointerException when extension argument is null")
        void shouldThrowExceptionWhenExtensionIsNull() {
            Listened listened = new Listened("client", "process photo.jpg");
            assertThrows(NullPointerException.class, () -> listened.extractCmdFile(null));
        }

        @Test
        @DisplayName("should return empty string when command is null")
        void shouldReturnEmptyStringWhenCommandIsNull() {
            Listened listened = new Listened("client", null);
            assertEquals("", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should return empty string when command is empty string")
        void shouldReturnEmptyStringWhenCommandIsEmpty() {
            Listened listened = new Listened("client", "");
            assertEquals("", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should return empty string when command is whitespace")
        void shouldReturnEmptyStringWhenCommandIsWhitespace() {
            Listened listened = new Listened("client", "   ");
            assertEquals("", listened.extractCmdFile("png"));
        }

        @Test
        @DisplayName("should return empty string when command tokens lack extensions")
        void shouldReturnEmptyStringWhenTokensLackExtensions() {
            Listened listened = new Listened("client", "run program without files");
            assertEquals("", listened.extractCmdFile("png"));
        }
    }
}