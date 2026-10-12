package prism.infrastructure.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.configuration.adapter.StorageAdapter;
import prism.configuration.adapter.WriterAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.domain.model.Prism;
import prism.domain.model.Storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileDataWriter Unit Tests")
class TestFileDataWriter {

    @Mock
    private AbstractServiceContextConfiguration context;

    @Mock
    private Supplier<AbstractServiceContextConfiguration> contextSupplier;

    private FileDataWriter writer;

    @BeforeEach
    public void setUp() {
        lenient().when(contextSupplier.get()).thenReturn(context);
        writer = new FileDataWriter(contextSupplier);
    }

    @Nested
    @DisplayName("writeStorage")
    class WriteStorageTests {

        @Test
        @DisplayName("should throw IllegalStateException when storage adapter is null")
        void shouldThrowIllegalStateExceptionWhenStorageAdapterIsNull() {
            when(context.getStorageAdapter()).thenReturn(null);
            Storage storage = new Storage("file.txt", "/dir");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> writer.writeStorage(storage)
            );
            assertEquals("Storage configuration not present", exception.getMessage());
            verify(context).getStorageAdapter();
        }

        @Test
        @DisplayName("should write storage data as JSON to configured file")
        void shouldWriteStorageDataToJsonFile(@TempDir Path tempDir) throws IOException {
            StorageAdapter adapter = new StorageAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("storage.json");
            when(context.getStorageAdapter()).thenReturn(adapter);

            Storage storage = new Storage("photo.png", "/images");
            writer.writeStorage(storage);

            Path writtenFile = tempDir.resolve("storage.json");
            assertTrue(Files.exists(writtenFile));

            String content = Files.readString(writtenFile);
            assertTrue(content.contains("\"file\":\"photo.png\""));
            assertTrue(content.contains("\"dir\":\"/images\""));
            verify(context).getStorageAdapter();
        }

        @Test
        @DisplayName("should throw RuntimeException when storage file cannot be written")
        void shouldThrowRuntimeExceptionWhenStorageFileCannotBeWritten(@TempDir Path tempDir) throws IOException {
            StorageAdapter adapter = new StorageAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("existing_dir");
            when(context.getStorageAdapter()).thenReturn(adapter);

            Files.createDirectory(tempDir.resolve("existing_dir"));

            Storage storage = new Storage("photo.png", "/images");

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> writer.writeStorage(storage)
            );
            assertEquals("Error while writing to storage file", exception.getMessage());
            assertNotNull(exception.getCause());
            verify(context).getStorageAdapter();
        }
    }

    @Nested
    @DisplayName("writeSpectrum")
    class WriteSpectrumTests {

        @Test
        @DisplayName("should throw IllegalStateException when writer adapter is null")
        void shouldThrowIllegalStateExceptionWhenWriterAdapterIsNull() {
            when(context.getWriterAdapter()).thenReturn(null);
            Prism prism = mock(Prism.class);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> writer.writeSpectrum(prism)
            );
            assertEquals("Writing configuration not present", exception.getMessage());
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should write spectrum file when file does not exist")
        void shouldWriteSpectrumFileWhenDoesNotExist(@TempDir Path tempDir) throws IOException {
            WriterAdapter adapter = new WriterAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("spectrum.spec");
            when(context.getWriterAdapter()).thenReturn(adapter);

            Prism prism = mock(Prism.class);
            when(prism.getId()).thenReturn("ID: test-palette-123");
            when(prism.getFormatted()).thenReturn("lux100::#111111\n");

            writer.writeSpectrum(prism);

            Path spectrumFile = tempDir.resolve("spectrum.spec");
            assertTrue(Files.exists(spectrumFile));

            String content = Files.readString(spectrumFile);
            assertTrue(content.contains("# Warning: This file is automatically generated by Prism"));
            assertTrue(content.contains("ID: test-palette-123"));
            assertTrue(content.contains("lux100::#111111"));
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should skip writing when target file already exists with matching prism ID")
        void shouldSkipWritingWhenFileExistsWithMatchingId(@TempDir Path tempDir) throws IOException {
            WriterAdapter adapter = new WriterAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("spectrum.spec");
            when(context.getWriterAdapter()).thenReturn(adapter);

            Prism prism = mock(Prism.class);
            when(prism.getId()).thenReturn("ID: matching-id");

            Path spectrumFile = tempDir.resolve("spectrum.spec");
            String customContent = "ID: matching-id\nCUSTOM_CONTENT_SHOULD_NOT_BE_OVERWRITTEN\n";
            Files.writeString(spectrumFile, customContent);

            writer.writeSpectrum(prism);

            String contentAfter = Files.readString(spectrumFile);
            assertEquals(customContent, contentAfter);
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should overwrite spectrum file when existing file has different prism ID")
        void shouldOverwriteWhenExistingFileHasDifferentId(@TempDir Path tempDir) throws IOException {
            WriterAdapter adapter = new WriterAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("spectrum.spec");
            when(context.getWriterAdapter()).thenReturn(adapter);

            Prism newPrism = mock(Prism.class);
            when(newPrism.getId()).thenReturn("ID: new-id");
            when(newPrism.getFormatted()).thenReturn("core100::#222222\n");

            Path spectrumFile = tempDir.resolve("spectrum.spec");
            Files.writeString(spectrumFile, "ID: old-id\nold_content\n");

            writer.writeSpectrum(newPrism);

            String contentAfter = Files.readString(spectrumFile);
            assertTrue(contentAfter.contains("ID: new-id"));
            assertTrue(contentAfter.contains("core100::#222222"));
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should overwrite spectrum file when existing file has no ID header")
        void shouldOverwriteWhenExistingFileHasNoIdHeader(@TempDir Path tempDir) throws IOException {
            WriterAdapter adapter = new WriterAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("spectrum.spec");
            when(context.getWriterAdapter()).thenReturn(adapter);

            Prism prism = mock(Prism.class);
            when(prism.getId()).thenReturn("ID: created-id");
            when(prism.getFormatted()).thenReturn("spark100::#333333\n");

            Path spectrumFile = tempDir.resolve("spectrum.spec");
            Files.writeString(spectrumFile, "Arbitrary content without ID marker\nline2\n");

            writer.writeSpectrum(prism);

            String contentAfter = Files.readString(spectrumFile);
            assertTrue(contentAfter.contains("ID: created-id"));
            assertTrue(contentAfter.contains("spark100::#333333"));
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should throw RuntimeException when reading existing spectrum file fails")
        void shouldThrowRuntimeExceptionWhenReadingExistingSpectrumFails(@TempDir Path tempDir) throws IOException {
            WriterAdapter adapter = new WriterAdapter();
            adapter.setDirectory(tempDir.toString());
            adapter.setFile("spectrum_dir");
            when(context.getWriterAdapter()).thenReturn(adapter);

            Files.createDirectory(tempDir.resolve("spectrum_dir"));

            Prism prism = mock(Prism.class);

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> writer.writeSpectrum(prism)
            );

            assertEquals("Error while validating active configuration", exception.getMessage());
            assertNotNull(exception.getCause());
            verify(context).getWriterAdapter();
        }

        @Test
        @DisplayName("should throw RuntimeException when temp file cannot be created")
        void shouldThrowRuntimeExceptionWhenTempFileCreationFails(@TempDir Path tempDir) throws IOException {
            Path readOnlyDir = Files.createDirectory(tempDir.resolve("readonly_output"));
            Path targetFile = readOnlyDir.resolve("output.spec");

            // Create target file first with a different ID so assertFileAndDirExistence succeeds without trying to create files
            Files.writeString(targetFile, "ID: different-id\n");

            File dirFile = readOnlyDir.toFile();
            if (dirFile.setWritable(false, false)) {
                WriterAdapter adapter = new WriterAdapter();
                adapter.setDirectory(readOnlyDir.toString());
                adapter.setFile("output.spec");
                when(context.getWriterAdapter()).thenReturn(adapter);

                Prism prism = mock(Prism.class);
                when(prism.getId()).thenReturn("ID: active-id");

                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> writer.writeSpectrum(prism)
                );

                assertEquals("Error while validating active configuration", exception.getMessage());
                verify(context).getWriterAdapter();
            }
        }
    }
}