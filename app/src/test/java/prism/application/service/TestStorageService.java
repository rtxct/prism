package prism.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.configuration.adapter.StorageAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.domain.model.Storage;
import prism.infrastructure.filesystem.FileDataWriter;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService Unit Tests")
class TestStorageService {

    @Mock
    private FileDataWriter mockDataWriter;

    @Mock
    private AbstractServiceContextConfiguration mockExecutionContext;

    @Captor
    private ArgumentCaptor<Storage> storageCaptor;

    @BeforeEach
    void setUp() throws Exception {
        resetAppContext();
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

    private StorageService createInitializedService() {
        AppContext.initialize(mockDataWriter);
        return new StorageService();
    }

    @Nested
    @DisplayName("Constructor & AppContext Integration")
    class ConstructorAndContextTests {

        @Test
        @DisplayName("should construct successfully when AppContext is initialized")
        void shouldConstructSuccessfullyWhenContextInitialized() {
            StorageService service = createInitializedService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("should not evaluate AppContext dependency during construction")
        void shouldNotEvaluateAppContextDuringConstruction() {
            // AppContext uninitialized; constructor does not evaluate lazy supplier
            StorageService service = new StorageService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("should catch and log gracefully when store is invoked without AppContext initialization")
        void shouldHandleStoreWithoutAppContextGracefully() {
            StorageService service = new StorageService();

            // dataWriter.get() throws IllegalStateException which is caught and logged
            assertDoesNotThrow(() -> service.store("/home/user/sample.png"));
        }

        @Test
        @DisplayName("should catch and log gracefully when FileDataWriter is not registered in AppContext")
        void shouldHandleStoreWithoutFileDataWriterGracefully() {
            AppContext.initialize(new Object());
            StorageService service = new StorageService();

            assertDoesNotThrow(() -> service.store("/home/user/sample.png"));
        }
    }

    @Nested
    @DisplayName("Path Parsing & Storage Model Construction")
    class PathParsingTests {

        @Test
        @DisplayName("should extract fileName and parent directory from absolute file path")
        void shouldExtractFileNameAndParentFromAbsolutePath() {
            StorageService service = createInitializedService();

            service.store("/home/user/wallpapers/aurora.png");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals("aurora.png", captured.getFile());
            assertEquals("/home/user/wallpapers", captured.getDir());
        }

        @Test
        @DisplayName("should extract fileName and empty directory string from bare file name")
        void shouldExtractFileNameAndEmptyDirFromBareFileName() {
            StorageService service = createInitializedService();

            service.store("landscape.jpg");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals("landscape.jpg", captured.getFile());
            assertEquals("", captured.getDir());
        }

        @Test
        @DisplayName("should extract fileName and relative directory path from relative file path")
        void shouldExtractFileNameAndRelativeDirFromRelativePath() {
            StorageService service = createInitializedService();

            service.store("assets/images/logo.webp");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals("logo.webp", captured.getFile());
            assertEquals("assets/images", captured.getDir());
        }

        @Test
        @DisplayName("should extract fileName and root directory for file located at root")
        void shouldExtractFileNameAndRootDirForRootFile() {
            StorageService service = createInitializedService();

            service.store("/root_image.png");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals("root_image.png", captured.getFile());
            assertEquals("/", captured.getDir());
        }

        @Test
        @DisplayName("should preserve hidden files and dot-prefixed directory paths")
        void shouldPreserveHiddenDirectoriesAndFiles() {
            StorageService service = createInitializedService();

            service.store("/home/user/.config/prism/.hidden_bg.png");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals(".hidden_bg.png", captured.getFile());
            assertEquals("/home/user/.config/prism", captured.getDir());
        }

        @Test
        @DisplayName("should correctly handle file names containing multiple extension dots")
        void shouldHandleMultipleExtensionsInFileName() {
            StorageService service = createInitializedService();

            service.store("/var/tmp/backup.image.tar.gz");

            verify(mockDataWriter, times(1)).writeStorage(storageCaptor.capture());
            Storage captured = storageCaptor.getValue();
            assertNotNull(captured);
            assertEquals("backup.image.tar.gz", captured.getFile());
            assertEquals("/var/tmp", captured.getDir());
        }

        @Test
        @DisplayName("should throw NullPointerException when null file path is supplied")
        void shouldThrowNullPointerExceptionWhenPathIsNull() {
            StorageService service = createInitializedService();

            assertThrows(
                    NullPointerException.class,
                    () -> service.store(null)
            );

            verify(mockDataWriter, never()).writeStorage(any());
        }
    }

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should catch and log SEVERE when FileDataWriter throws RuntimeException")
        void shouldCatchAndLogRuntimeExceptionFromWriter() {
            StorageService service = createInitializedService();
            doThrow(new RuntimeException("Disk I/O failure"))
                    .when(mockDataWriter).writeStorage(any());

            assertDoesNotThrow(() -> service.store("/path/to/test.png"));
            verify(mockDataWriter, times(1)).writeStorage(any());
        }

        @Test
        @DisplayName("should catch and log SEVERE when FileDataWriter throws IllegalStateException")
        void shouldCatchAndLogIllegalStateExceptionFromWriter() {
            StorageService service = createInitializedService();
            doThrow(new IllegalStateException("Storage configuration missing"))
                    .when(mockDataWriter).writeStorage(any());

            assertDoesNotThrow(() -> service.store("/path/to/test.png"));
            verify(mockDataWriter, times(1)).writeStorage(any());
        }

        @Test
        @DisplayName("should catch and log SEVERE when FileDataWriter throws IllegalArgumentException")
        void shouldCatchAndLogIllegalArgumentExceptionFromWriter() {
            StorageService service = createInitializedService();
            doThrow(new IllegalArgumentException("Invalid path argument"))
                    .when(mockDataWriter).writeStorage(any());

            assertDoesNotThrow(() -> service.store("/path/to/test.png"));
            verify(mockDataWriter, times(1)).writeStorage(any());
        }
    }

    @Nested
    @DisplayName("End-to-End Integration with Real FileDataWriter")
    class EndToEndIntegrationTests {

        @Test
        @DisplayName("should serialize and write Storage JSON to file using real FileDataWriter")
        void shouldWriteStorageJsonUsingRealWriter(@TempDir Path tempDir) throws Exception {
            StorageAdapter storageAdapter = new StorageAdapter();
            storageAdapter.setDirectory(tempDir.toString());
            storageAdapter.setFile("storage_output.json");
            when(mockExecutionContext.getStorageAdapter()).thenReturn(storageAdapter);

            FileDataWriter realWriter = new FileDataWriter(() -> mockExecutionContext);
            AppContext.initialize(realWriter);

            StorageService service = new StorageService();
            service.store("/home/photos/vacation.png");

            Path expectedStorageFile = tempDir.resolve("storage_output.json");
            assertTrue(Files.exists(expectedStorageFile), "Storage output file must exist");

            String jsonContent = Files.readString(expectedStorageFile);
            assertTrue(jsonContent.contains("\"file\":\"vacation.png\""));
            assertTrue(jsonContent.contains("\"dir\":\"/home/photos\""));
        }

        @Test
        @DisplayName("should serialize bare file name with empty dir using real FileDataWriter")
        void shouldWriteBareFileNameStorageUsingRealWriter(@TempDir Path tempDir) throws Exception {
            StorageAdapter storageAdapter = new StorageAdapter();
            storageAdapter.setDirectory(tempDir.toString());
            storageAdapter.setFile("bare_storage.json");
            when(mockExecutionContext.getStorageAdapter()).thenReturn(storageAdapter);

            FileDataWriter realWriter = new FileDataWriter(() -> mockExecutionContext);
            AppContext.initialize(realWriter);

            StorageService service = new StorageService();
            service.store("standalone.jpg");

            Path expectedStorageFile = tempDir.resolve("bare_storage.json");
            assertTrue(Files.exists(expectedStorageFile), "Bare storage output file must exist");

            String jsonContent = Files.readString(expectedStorageFile);
            assertTrue(jsonContent.contains("\"file\":\"standalone.jpg\""));
            assertTrue(jsonContent.contains("\"dir\":\"\""));
        }
    }
}