package prism.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.application.component.ImageReader;
import prism.application.component.color.ColorWriter;
import prism.application.component.color.RoleSelector;
import prism.configuration.adapter.WriterAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.domain.model.Prism;
import prism.domain.vo.ColorCluster;
import prism.domain.vo.Oklch;
import prism.domain.vo.Roles;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WriteService Unit Tests")
class TestWriteService {

    @Mock
    private AbstractServiceContextConfiguration mockExecutionContext;

    @Mock
    private ImageReader mockImageReader;

    @Mock
    private ColorWriter mockColorWriter;

    @Mock
    private RoleSelector mockRoleSelector;

    private static final Integer[] SAMPLE_10_SPECTRUM = new Integer[]{
            0xFFFFFF, 0xEEEEEE, 0xDDDDDD, 0xCCCCCC, 0xBBBBBB,
            0xAAAAAA, 0x888888, 0x666666, 0x333333, 0x000000
    };

    private static final Oklch SAMPLE_LUX = new Oklch(0.55, 0.012, 210.0);
    private static final Oklch SAMPLE_CORE = new Oklch(0.60, 0.180, 210.0);
    private static final Oklch SAMPLE_WAVE = new Oklch(0.52, 0.288, 210.0);
    private static final Oklch SAMPLE_FLARE = new Oklch(0.88, 0.180, 210.0);
    private static final Oklch SAMPLE_SPARK = new Oklch(0.65, 0.090, 360.0);
    private static final Roles SAMPLE_ROLES = new Roles(
            SAMPLE_LUX, SAMPLE_CORE, SAMPLE_WAVE, SAMPLE_FLARE, SAMPLE_SPARK
    );

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

    private WriteService createInitializedService() {
        AppContext.initialize(mockColorWriter, mockImageReader, mockRoleSelector);
        return new WriteService(() -> mockExecutionContext);
    }

    @Nested
    @DisplayName("Constructor & AppContext Integration")
    class ConstructorAndContextTests {

        @Test
        @DisplayName("should construct successfully with valid context configuration supplier")
        void shouldConstructSuccessfullyWithSupplier() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            assertNotNull(service);
        }

        @Test
        @DisplayName("should not evaluate AppContext dependencies at construction time")
        void shouldNotEvaluateAppContextAtConstructionTime() {
            // AppContext is uninitialized, but constructor does not fail
            WriteService service = new WriteService(() -> mockExecutionContext);
            assertNotNull(service);
        }

        @Test
        @DisplayName("should throw IllegalStateException when processImage is called before AppContext initialization")
        void shouldThrowExceptionWhenProcessImageCalledWithoutAppContext() {
            WriteService service = new WriteService(() -> mockExecutionContext);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.processImage("image.png")
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException when getPrism is called before AppContext initialization")
        void shouldThrowExceptionWhenGetPrismCalledWithoutAppContext() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.getPrism(clusters)
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException when ImageReader is not registered in AppContext")
        void shouldThrowExceptionWhenImageReaderNotRegistered() {
            AppContext.initialize(mockColorWriter, mockRoleSelector);
            WriteService service = new WriteService(() -> mockExecutionContext);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.processImage("image.png")
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + ImageReader.class.getName()));
        }

        @Test
        @DisplayName("should throw IllegalStateException when RoleSelector is not registered in AppContext")
        void shouldThrowExceptionWhenRoleSelectorNotRegistered() {
            AppContext.initialize(mockColorWriter, mockImageReader);
            WriteService service = new WriteService(() -> mockExecutionContext);
            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.getPrism(clusters)
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + RoleSelector.class.getName()));
        }

        @Test
        @DisplayName("should throw IllegalStateException when ColorWriter is not registered in AppContext")
        void shouldThrowExceptionWhenColorWriterNotRegistered() {
            AppContext.initialize(mockImageReader, mockRoleSelector);
            WriteService service = new WriteService(() -> mockExecutionContext);
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.getPrism(clusters)
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + ColorWriter.class.getName()));
        }
    }

    @Nested
    @DisplayName("processImage")
    class ProcessImageTests {

        @Test
        @DisplayName("should return color clusters when image reading succeeds")
        void shouldReturnClustersWhenReadingSucceeds() throws Exception {
            WriteService service = createInitializedService();
            List<ColorCluster> expectedClusters = List.of(
                    new ColorCluster(SAMPLE_CORE, 0.7),
                    new ColorCluster(SAMPLE_FLARE, 0.3)
            );
            when(mockImageReader.readClusters("path/to/test.png")).thenReturn(expectedClusters);

            List<ColorCluster> actualClusters = service.processImage("path/to/test.png");

            assertNotNull(actualClusters);
            assertEquals(2, actualClusters.size());
            assertEquals(expectedClusters, actualClusters);
            verify(mockImageReader, times(1)).readClusters("path/to/test.png");
        }

        @Test
        @DisplayName("should pass exact file path to ImageReader")
        void shouldPassExactPathToImageReader() throws Exception {
            WriteService service = createInitializedService();
            String exactPath = "/var/custom/wallpapers/aurora.webp";
            when(mockImageReader.readClusters(exactPath)).thenReturn(List.of());

            service.processImage(exactPath);

            verify(mockImageReader, times(1)).readClusters(exactPath);
        }

        @Test
        @DisplayName("should return empty list when ImageReader yields no color clusters")
        void shouldReturnEmptyListWhenNoClustersFound() throws Exception {
            WriteService service = createInitializedService();
            when(mockImageReader.readClusters("transparent.png")).thenReturn(List.of());

            List<ColorCluster> result = service.processImage("transparent.png");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return null and log error when ImageReader throws IOException")
        void shouldReturnNullWhenImageReaderThrowsIOException() throws Exception {
            WriteService service = createInitializedService();
            when(mockImageReader.readClusters("unreadable.jpg"))
                    .thenThrow(new IOException("File not found or corrupted"));

            List<ColorCluster> result = service.processImage("unreadable.jpg");

            assertNull(result);
            verify(mockImageReader, times(1)).readClusters("unreadable.jpg");
        }

        @Test
        @DisplayName("should propagate unchecked runtime exception thrown by ImageReader")
        void shouldPropagateRuntimeExceptionFromImageReader() throws Exception {
            WriteService service = createInitializedService();
            when(mockImageReader.readClusters("error.png"))
                    .thenThrow(new IllegalStateException("Native decoder failed"));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.processImage("error.png")
            );

            assertEquals("Native decoder failed", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("formatColors")
    class FormatColorsTests {

        @Test
        @DisplayName("should default to RGB format when WriterAdapter is null")
        void shouldDefaultToRgbWhenWriterAdapterIsNull() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            when(mockExecutionContext.getWriterAdapter()).thenReturn(null);

            String[] formatted = service.formatColors(new Integer[]{0xFF0000});

            assertArrayEquals(new String[]{"rgb(255, 0, 0)"}, formatted);
        }

        @Test
        @DisplayName("should format colors as RGB when WriterAdapter type is 'rgb'")
        void shouldFormatAsRgbWhenTypeIsRgb() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType("rgb");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            String[] formatted = service.formatColors(new Integer[]{0x00FF00});

            assertArrayEquals(new String[]{"rgb(0, 255, 0)"}, formatted);
        }

        @Test
        @DisplayName("should format colors as RGB when WriterAdapter type is 'RGB' (case-insensitive)")
        void shouldFormatAsRgbWhenTypeIsUpperCaseRgb() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType("RGB");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            String[] formatted = service.formatColors(new Integer[]{0x0000FF});

            assertArrayEquals(new String[]{"rgb(0, 0, 255)"}, formatted);
        }

        @Test
        @DisplayName("should format colors as HEX when WriterAdapter type is 'hex'")
        void shouldFormatAsHexWhenTypeIsHex() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            String[] formatted = service.formatColors(new Integer[]{0xFF0000});

            assertArrayEquals(new String[]{"#FF0000"}, formatted);
        }

        @Test
        @DisplayName("should format colors as HEX when WriterAdapter type is 'HEX' (case-insensitive)")
        void shouldFormatAsHexWhenTypeIsUpperCaseHex() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType("HEX");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            String[] formatted = service.formatColors(new Integer[]{0x1A2B3C});

            assertArrayEquals(new String[]{"#1A2B3C"}, formatted);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when WriterAdapter type is unsupported")
        void shouldThrowExceptionWhenTypeIsUnsupported() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType("cmyk");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.formatColors(new Integer[]{0x112233})
            );

            assertEquals("Invalid color type: cmyk. Expected hex or rgb.", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when WriterAdapter type is null")
        void shouldThrowExceptionWhenTypeIsNull() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter adapter = new WriterAdapter();
            adapter.setType(null);
            when(mockExecutionContext.getWriterAdapter()).thenReturn(adapter);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.formatColors(new Integer[]{0x112233})
            );

            assertEquals("Invalid color type: null. Expected hex or rgb.", ex.getMessage());
        }

        @Test
        @DisplayName("should return empty array when colors input is empty")
        void shouldReturnEmptyArrayWhenColorsInputIsEmpty() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            when(mockExecutionContext.getWriterAdapter()).thenReturn(null);

            String[] formatted = service.formatColors(new Integer[0]);

            assertNotNull(formatted);
            assertEquals(0, formatted.length);
        }

        @Test
        @DisplayName("should correctly format black (0x000000) in both RGB and HEX")
        void shouldCorrectlyFormatBlack() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter rgbAdapter = new WriterAdapter();
            rgbAdapter.setType("rgb");
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");

            when(mockExecutionContext.getWriterAdapter()).thenReturn(rgbAdapter);
            assertArrayEquals(new String[]{"rgb(0, 0, 0)"}, service.formatColors(new Integer[]{0x000000}));

            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);
            assertArrayEquals(new String[]{"#000000"}, service.formatColors(new Integer[]{0x000000}));
        }

        @Test
        @DisplayName("should correctly format white (0xFFFFFF) in both RGB and HEX")
        void shouldCorrectlyFormatWhite() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter rgbAdapter = new WriterAdapter();
            rgbAdapter.setType("rgb");
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");

            when(mockExecutionContext.getWriterAdapter()).thenReturn(rgbAdapter);
            assertArrayEquals(new String[]{"rgb(255, 255, 255)"}, service.formatColors(new Integer[]{0xFFFFFF}));

            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);
            assertArrayEquals(new String[]{"#FFFFFF"}, service.formatColors(new Integer[]{0xFFFFFF}));
        }

        @Test
        @DisplayName("should correctly zero-pad single-digit hex channel values")
        void shouldCorrectlyZeroPadHexChannels() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);

            // R=5, G=9, B=12 (0x0C) -> 0x05090C
            int packed = (5 << 16) | (9 << 8) | 12;
            String[] formatted = service.formatColors(new Integer[]{packed});

            assertArrayEquals(new String[]{"#05090C"}, formatted);
        }

        @Test
        @DisplayName("should preserve order and length across multiple packed color values")
        void shouldPreserveOrderAndLength() {
            WriteService service = new WriteService(() -> mockExecutionContext);
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);

            Integer[] colors = new Integer[]{0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00};
            String[] expected = new String[]{"#FF0000", "#00FF00", "#0000FF", "#FFFF00"};

            String[] actual = service.formatColors(colors);

            assertArrayEquals(expected, actual);
        }

        @Test
        @DisplayName("should throw NullPointerException when execution context supplier returns null")
        void shouldThrowExceptionWhenSupplierReturnsNull() {
            WriteService service = new WriteService(() -> null);

            assertThrows(
                    NullPointerException.class,
                    () -> service.formatColors(new Integer[]{0xFF0000})
            );
        }
    }

    @Nested
    @DisplayName("getPrism")
    class GetPrismTests {

        @Test
        @DisplayName("should assemble Prism successfully with RGB color format")
        void shouldAssemblePrismSuccessfullyWithRgb() {
            WriteService service = createInitializedService();
            WriterAdapter rgbAdapter = new WriterAdapter();
            rgbAdapter.setType("rgb");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(rgbAdapter);
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));
            Prism prism = service.getPrism(clusters);

            assertNotNull(prism);
            String formatted = prism.getFormatted();
            assertTrue(formatted.contains("lux100::rgb("));
            assertTrue(formatted.contains("core100::rgb("));
            assertTrue(formatted.contains("wave100::rgb("));
            assertTrue(formatted.contains("flare100::rgb("));
            assertTrue(formatted.contains("spark100::rgb("));
        }

        @Test
        @DisplayName("should assemble Prism successfully with HEX color format")
        void shouldAssemblePrismSuccessfullyWithHex() {
            WriteService service = createInitializedService();
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));
            Prism prism = service.getPrism(clusters);

            assertNotNull(prism);
            String formatted = prism.getFormatted();
            assertTrue(formatted.contains("lux100::#FFFFFF"));
            assertTrue(formatted.contains("core100::#FFFFFF"));
            assertTrue(formatted.contains("wave100::#FFFFFF"));
            assertTrue(formatted.contains("flare100::#FFFFFF"));
            assertTrue(formatted.contains("spark100::#FFFFFF"));
        }

        @Test
        @DisplayName("should pass provided clusters directly to RoleSelector")
        void shouldPassClustersToRoleSelector() {
            WriteService service = createInitializedService();
            when(mockExecutionContext.getWriterAdapter()).thenReturn(new WriterAdapter());
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(SAMPLE_CORE, 0.6),
                    new ColorCluster(SAMPLE_FLARE, 0.4)
            );

            service.getPrism(clusters);

            verify(mockRoleSelector, times(1)).select(clusters);
        }

        @Test
        @DisplayName("should invoke ColorWriter calculateSpectrum for all five palette roles")
        void shouldInvokeColorWriterForEveryRole() {
            WriteService service = createInitializedService();
            when(mockExecutionContext.getWriterAdapter()).thenReturn(new WriterAdapter());
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));
            service.getPrism(clusters);

            verify(mockColorWriter, times(1)).calculateSpectrum(SAMPLE_LUX);
            verify(mockColorWriter, times(1)).calculateSpectrum(SAMPLE_CORE);
            verify(mockColorWriter, times(1)).calculateSpectrum(SAMPLE_WAVE);
            verify(mockColorWriter, times(1)).calculateSpectrum(SAMPLE_FLARE);
            verify(mockColorWriter, times(1)).calculateSpectrum(SAMPLE_SPARK);
            verify(mockColorWriter, times(5)).calculateSpectrum(any(Oklch.class));
        }

        @Test
        @DisplayName("should populate all ten shades (100 to 1000) for each role in generated Prism")
        void shouldPopulateAllTenShadesForEachRole() {
            WriteService service = createInitializedService();
            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));
            Prism prism = service.getPrism(clusters);
            String formatted = prism.getFormatted();

            for (String role : List.of("lux", "core", "wave", "flare", "spark")) {
                for (int level = 100; level <= 1000; level += 100) {
                    assertTrue(formatted.contains(role + level + "::"),
                            "Missing shade " + role + level + " in Prism output");
                }
            }
        }

        @Test
        @DisplayName("should produce a valid non-empty identifier from generated Prism")
        void shouldProduceValidPrismIdentifier() {
            WriteService service = createInitializedService();
            when(mockExecutionContext.getWriterAdapter()).thenReturn(new WriterAdapter());
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(SAMPLE_10_SPECTRUM);

            List<ColorCluster> clusters = List.of(new ColorCluster(SAMPLE_CORE, 1.0));
            Prism prism = service.getPrism(clusters);

            assertNotNull(prism.getId());
            assertTrue(prism.getId().startsWith("ID: "));
        }

        @Test
        @DisplayName("should propagate exception when RoleSelector fails")
        void shouldPropagateExceptionWhenRoleSelectorFails() {
            WriteService service = createInitializedService();
            when(mockRoleSelector.select(any()))
                    .thenThrow(new IllegalArgumentException("The image has no visible pixels"));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.getPrism(List.of())
            );

            assertEquals("The image has no visible pixels", ex.getMessage());
        }

        @Test
        @DisplayName("should propagate exception when ColorWriter calculateSpectrum fails")
        void shouldPropagateExceptionWhenColorWriterFails() {
            WriteService service = createInitializedService();
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any()))
                    .thenThrow(new RuntimeException("Color conversion failure"));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> service.getPrism(List.of(new ColorCluster(SAMPLE_CORE, 1.0)))
            );

            assertEquals("Color conversion failure", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IndexOutOfBoundsException when ColorWriter returns fewer than ten shades")
        void shouldThrowExceptionWhenSpectrumHasFewerThanTenShades() {
            WriteService service = createInitializedService();
            when(mockExecutionContext.getWriterAdapter()).thenReturn(new WriterAdapter());
            when(mockRoleSelector.select(any())).thenReturn(SAMPLE_ROLES);
            when(mockColorWriter.calculateSpectrum(any())).thenReturn(new Integer[]{0xFF0000, 0x00FF00});

            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> service.getPrism(List.of(new ColorCluster(SAMPLE_CORE, 1.0)))
            );
        }

        @Test
        @DisplayName("should assemble Prism end-to-end using real RoleSelector and ColorWriter components")
        void shouldAssemblePrismEndToEndWithRealComponents() {
            RoleSelector realRoleSelector = new RoleSelector();
            ColorWriter realColorWriter = new ColorWriter();
            AppContext.initialize(realColorWriter, mockImageReader, realRoleSelector);

            WriterAdapter hexAdapter = new WriterAdapter();
            hexAdapter.setType("hex");
            when(mockExecutionContext.getWriterAdapter()).thenReturn(hexAdapter);

            WriteService service = new WriteService(() -> mockExecutionContext);

            List<ColorCluster> clusters = List.of(
                    new ColorCluster(new Oklch(0.55, 0.16, 210.0), 0.55),
                    new ColorCluster(new Oklch(0.85, 0.22, 45.0), 0.25),
                    new ColorCluster(new Oklch(0.35, 0.12, 220.0), 0.15),
                    new ColorCluster(new Oklch(0.70, 0.19, 320.0), 0.05)
            );

            Prism prism = service.getPrism(clusters);

            assertNotNull(prism);
            String formatted = prism.getFormatted();
            assertTrue(formatted.contains("lux100::#"));
            assertTrue(formatted.contains("core100::#"));
            assertTrue(formatted.contains("wave100::#"));
            assertTrue(formatted.contains("flare100::#"));
            assertTrue(formatted.contains("spark100::#"));

            String id = prism.getId();
            assertNotNull(id);
            assertTrue(id.startsWith("ID: "));
        }
    }
}