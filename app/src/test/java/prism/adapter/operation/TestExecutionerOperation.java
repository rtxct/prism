package prism.adapter.operation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.input.Command;
import prism.adapter.cli.procedure.ProcedureCaller;
import prism.application.component.ColorCache;
import prism.application.service.WriteService;
import prism.configuration.adapter.ParameterAdapter;
import prism.configuration.context.AppContext;
import prism.configuration.context.ExecutionerContext;
import prism.domain.exception.ArgumentNotFoundException;
import prism.domain.model.Prism;
import prism.domain.vo.ColorCluster;
import prism.infrastructure.filesystem.FileDataWriter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExecutionerOperation Unit Tests")
class TestExecutionerOperation {

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetAppContextAndCleanup() throws Exception {
        shutdownColorCacheSchedulerIfPresent();
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

    private static void shutdownColorCacheSchedulerIfPresent() {
        try {
            Field instanceField = AppContext.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            AppContext context = (AppContext) instanceField.get(null);

            if (context != null) {
                ColorCache cache = context.getClass(ColorCache.class);

                Field schedulerField = ColorCache.class.getDeclaredField("scheduler");
                schedulerField.setAccessible(true);
                ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(cache);
                if (scheduler != null) {
                    scheduler.shutdownNow();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path createTestImage(String filename, Color... colors) throws IOException {
        int width = 10;
        int height = 10;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int colorCount = colors.length;
        int sliceWidth = Math.max(1, width / colorCount);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colorIndex = Math.min(x / sliceWidth, colorCount - 1);
                image.setRGB(x, y, colors[colorIndex].getRGB());
            }
        }

        Path imagePath = tempDir.resolve(filename);
        ImageIO.write(image, "png", imagePath.toFile());
        return imagePath;
    }

    @Nested
    @DisplayName("End-to-End execute() Processing")
    class EndToEndExecutionTests {

        @Test
        @DisplayName("execute successfully processes valid image and writes default RGB spectrum file")
        void execute_withValidImageAndDefaultWriterConfig_successfullyProcessesAndWritesSpectrum() throws IOException {
            Path imagePath = createTestImage("single_color.png", new Color(0x3B, 0x82, 0xF6));
            Path outputDir = tempDir.resolve("out_default");
            Files.createDirectories(outputDir);

            Command[] commands = ProcedureCaller.assertAndGetExecutionerCall(
                    "-w", "-d", outputDir.toString(), "-f", "spectrum.prism",
                    "-p", "-f", imagePath.toString()
            );

            assertDoesNotThrow(() -> ExecutionerOperation.execute(commands));

            Path outputFile = outputDir.resolve("spectrum.prism");
            assertTrue(Files.exists(outputFile), "Output palette file should be generated");
            assertTrue(Files.size(outputFile) > 0, "Output palette file should not be empty");

            String content = Files.readString(outputFile);
            assertTrue(content.contains("# Warning: This file is automatically generated by Prism"));
            assertTrue(content.contains("core100::"));
            assertTrue(content.contains("flare100::"));
            assertTrue(content.contains("wave100::"));
            assertTrue(content.contains("spark100::"));
            assertTrue(content.contains("lux100::"));
        }

        @Test
        @DisplayName("execute successfully writes spectrum using HEX format when configured")
        void execute_withHexFormatOption_writesHexSpectrumFile() throws IOException {
            Path imagePath = createTestImage("hex_sample.png", new Color(0x10, 0xB9, 0x81));
            Path outputDir = tempDir.resolve("out_hex");
            Files.createDirectories(outputDir);

            Command[] commands = ProcedureCaller.assertAndGetExecutionerCall(
                    "-w", "-d", outputDir.toString(), "-f", "hex_palette.prism", "-t", "hex",
                    "-p", "-f", imagePath.toString()
            );

            assertDoesNotThrow(() -> ExecutionerOperation.execute(commands));

            Path outputFile = outputDir.resolve("hex_palette.prism");
            assertTrue(Files.exists(outputFile), "Hex output palette file should exist");
            String content = Files.readString(outputFile);
            assertTrue(content.contains("#"), "Should contain hex '#' color representation");
        }

        @Test
        @DisplayName("execute extracts palette from multi-colored image with distinct roles")
        void execute_withMultiColoredImage_extractsPaletteAndWritesFile() throws IOException {
            Path imagePath = createTestImage("multi.png",
                    new Color(255, 0, 0),
                    new Color(0, 255, 0),
                    new Color(0, 0, 255),
                    new Color(255, 255, 0)
            );
            Path outputDir = tempDir.resolve("out_multi");
            Files.createDirectories(outputDir);

            Command[] commands = ProcedureCaller.assertAndGetExecutionerCall(
                    "-w", "-d", outputDir.toString(), "-f", "multi.prism",
                    "-p", "-f", imagePath.toString()
            );

            assertDoesNotThrow(() -> ExecutionerOperation.execute(commands));

            Path outputFile = outputDir.resolve("multi.prism");
            assertTrue(Files.exists(outputFile));
            assertTrue(Files.size(outputFile) > 0);
        }
    }

    @Nested
    @DisplayName("Constructor and AppStartup Bootstrap Tests")
    class ConstructorAndAppStartupBootstrapTests {

        @Test
        @DisplayName("private constructor initializes commands field and bootstraps AppContext via AppStartup")
        void constructor_initializesCommandsAndBootstrapsAppContext() throws Exception {
            Command[] commands = new Command[]{new Command()};
            ExecutionerContext context = new ExecutionerContext();

            Constructor<ExecutionerOperation> constructor = ExecutionerOperation.class
                    .getDeclaredConstructor(Command[].class, ExecutionerContext.class);
            constructor.setAccessible(true);

            ExecutionerOperation operation = constructor.newInstance(commands, context);

            assertNotNull(operation.getAppContext(), "AppContext should be bootstrapped and accessible");
            assertSame(AppContext.instance(), operation.getAppContext());

            Field commandsField = ExecutionerOperation.class.getDeclaredField("commands");
            commandsField.setAccessible(true);
            Command[] actualCommands = (Command[]) commandsField.get(operation);
            assertSame(commands, actualCommands);

            // Verify essential services are wired inside AppContext
            assertNotNull(operation.getAppContext().getClass(WriteService.class));
            assertNotNull(operation.getAppContext().getClass(FileDataWriter.class));
        }
    }

    @Nested
    @DisplayName("getArgumentFile() Helper Tests via Reflection")
    class GetArgumentFileTests {

        @Test
        @DisplayName("getArgumentFile returns file path when ParameterAdapter is present")
        void getArgumentFile_whenParameterAdapterPresent_returnsFilePath() throws Exception {
            ExecutionerContext context = new ExecutionerContext();
            ParameterAdapter parameterAdapter = new ParameterAdapter();
            parameterAdapter.setFile("/custom/path/image.jpg");
            context.setParameterAdapter(parameterAdapter);

            Method getArgMethod = ExecutionerOperation.class
                    .getDeclaredMethod("getArgumentFile", ExecutionerContext.class);
            getArgMethod.setAccessible(true);

            // Construct operation via reflection to invoke instance method
            Constructor<ExecutionerOperation> constructor = ExecutionerOperation.class
                    .getDeclaredConstructor(Command[].class, ExecutionerContext.class);
            constructor.setAccessible(true);
            ExecutionerOperation operation = constructor.newInstance(new Command[]{}, context);

            String filePath = (String) getArgMethod.invoke(operation, context);
            assertEquals("/custom/path/image.jpg", filePath);
        }

        @Test
        @DisplayName("getArgumentFile throws ArgumentNotFoundException when ParameterAdapter is absent")
        void getArgumentFile_whenParameterAdapterMissing_throwsArgumentNotFoundException() throws Exception {
            ExecutionerContext context = new ExecutionerContext();
            // parameterAdapter is null by default

            Method getArgMethod = ExecutionerOperation.class
                    .getDeclaredMethod("getArgumentFile", ExecutionerContext.class);
            getArgMethod.setAccessible(true);

            Constructor<ExecutionerOperation> constructor = ExecutionerOperation.class
                    .getDeclaredConstructor(Command[].class, ExecutionerContext.class);
            constructor.setAccessible(true);
            ExecutionerOperation operation = constructor.newInstance(new Command[]{}, context);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> getArgMethod.invoke(operation, context)
            );
            assertInstanceOf(ArgumentNotFoundException.class, ite.getCause());
            assertEquals("No file argument found", ite.getCause().getMessage());
        }

        @Test
        @DisplayName("getArgumentFile consumes ParameterAdapter so a subsequent call throws ArgumentNotFoundException")
        void getArgumentFile_consumesParameterAdapterOnFirstRetrieval() throws Exception {
            ExecutionerContext context = new ExecutionerContext();
            ParameterAdapter parameterAdapter = new ParameterAdapter();
            parameterAdapter.setFile("/path/sample.png");
            context.setParameterAdapter(parameterAdapter);

            Method getArgMethod = ExecutionerOperation.class
                    .getDeclaredMethod("getArgumentFile", ExecutionerContext.class);
            getArgMethod.setAccessible(true);

            Constructor<ExecutionerOperation> constructor = ExecutionerOperation.class
                    .getDeclaredConstructor(Command[].class, ExecutionerContext.class);
            constructor.setAccessible(true);
            ExecutionerOperation operation = constructor.newInstance(new Command[]{}, context);

            // First call succeeds
            String path = (String) getArgMethod.invoke(operation, context);
            assertEquals("/path/sample.png", path);

            // Second call throws because ExecutionerContext.getParameterAdapter() reset it to null
            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> getArgMethod.invoke(operation, context)
            );
            assertInstanceOf(ArgumentNotFoundException.class, ite.getCause());
        }
    }

    @Nested
    @DisplayName("ExecutionerContext Integration Tests")
    class ExecutionerContextIntegrationTests {

        @Test
        @DisplayName("updateConfiguration correctly configures WriterAdapter and ParameterAdapter")
        void updateConfiguration_withValidCommands_correctlyConfiguresAdapters() throws IOException {
            Path validDir = tempDir.resolve("out_cfg");
            Files.createDirectories(validDir);

            ExecutionerContext context = new ExecutionerContext();

            Command writeCmd = new Command();
            writeCmd.setCommand(AppCommandType.WRITE);
            writeCmd.add("-d");
            writeCmd.add(validDir.toString());
            writeCmd.add("-f");
            writeCmd.add("custom.prism");
            writeCmd.add("-t");
            writeCmd.add("hex");

            Command paramCmd = new Command();
            paramCmd.setCommand(AppCommandType.PARAMETER);
            paramCmd.add("-f");
            paramCmd.add("/in/photo.png");

            context.updateConfiguration(new Command[]{writeCmd, paramCmd});

            assertNotNull(context.getWriterAdapter());
            assertEquals(validDir.toString(), context.getWriterAdapter().getDirectory());
            assertEquals("custom.prism", context.getWriterAdapter().getFile());
            assertEquals("hex", context.getWriterAdapter().getType());

            ParameterAdapter paramAdapter = context.getParameterAdapter();
            assertNotNull(paramAdapter);
            assertEquals("/in/photo.png", paramAdapter.getFile());
        }

        @Test
        @DisplayName("updateConfiguration throws IllegalArgumentException for unsupported command")
        void updateConfiguration_withUnsupportedCommand_throwsIllegalArgumentException() {
            ExecutionerContext context = new ExecutionerContext();

            Command invalidCmd = new Command();
            invalidCmd.setCommand(AppCommandType.LISTEN);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{invalidCmd})
            );
            assertTrue(ex.getMessage().contains("No configuration found for LISTEN specified value."));
        }
    }

    @Nested
    @DisplayName("WriteService & FileDataWriter Integration Pipeline")
    class PipelineComponentTests {

        @Test
        @DisplayName("validates pipeline steps: processImage -> getPrism -> writeSpectrum")
        void pipelineSteps_executeSuccessfullyTogether() throws Exception {
            Path imagePath = createTestImage("pipeline.png", new Color(0xEF, 0x44, 0x44));
            Path outputDir = tempDir.resolve("out_pipeline");
            Files.createDirectories(outputDir);

            ExecutionerContext context = new ExecutionerContext();

            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("palette.prism");

            ParameterAdapter paramAdapter = new ParameterAdapter();
            paramAdapter.setFile(imagePath.toString());
            context.setParameterAdapter(paramAdapter);

            Constructor<ExecutionerOperation> constructor = ExecutionerOperation.class
                    .getDeclaredConstructor(Command[].class, ExecutionerContext.class);
            constructor.setAccessible(true);
            ExecutionerOperation operation = constructor.newInstance(new Command[]{}, context);

            WriteService writeService = operation.getAppContext().getClass(WriteService.class);
            FileDataWriter writer = operation.getAppContext().getClass(FileDataWriter.class);

            // Step 1: processImage
            List<ColorCluster> clusters = writeService.processImage(imagePath.toString());
            assertNotNull(clusters);
            assertFalse(clusters.isEmpty());

            // Step 2: getPrism
            Prism prism = writeService.getPrism(clusters);
            assertNotNull(prism);
            assertNotNull(prism.getId());
            assertTrue(prism.getFormatted().contains("core"));

            // Step 3: writeSpectrum
            assertDoesNotThrow(() -> writer.writeSpectrum(prism));
            assertTrue(Files.exists(outputDir.resolve("palette.prism")));
        }
    }
}