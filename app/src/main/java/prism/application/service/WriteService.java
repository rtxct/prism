package prism.application.service;

import lombok.extern.java.Log;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import prism.application.component.ImageReader;
import prism.application.component.color.ColorWriter;
import prism.application.component.color.RoleSelector;
import prism.configuration.adapter.WriterAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.domain.model.Prism;
import prism.domain.vo.ColorCluster;
import prism.domain.vo.ColorScale;
import prism.domain.vo.Oklch;
import prism.domain.vo.Roles;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Application service responsible for extracting prominent color clusters and assembling
 * the full {@link Prism} spectrum from them.
 */
@Log
public class WriteService {

    /**
     * Lazy supplier for the application configuration context holding user preferences.
     */
    private final Supplier<? extends AbstractServiceContextConfiguration> appExecutionContext;

    /**
     * Lazy supplier for the color writing service, resolving from the AppContext.
     */
    private final Supplier<ColorWriter> colorWriter;

    /**
     * Lazy supplier for the image reading port, resolving from the AppContext.
     */
    private final Supplier<ImageReader> imageReader;

    /**
     * Lazy supplier for the role selector utility class, resolving from the AppContext.
     */
    private final Supplier<RoleSelector> roleSelector;

    /**
     * Constructs a new {@code WriteService}, initializing its dependencies lazily via the
     * global {@link AppContext}.
     *
     * @param appExecutionContext a supplier providing the configuration context (e.g. from
     *                            CLI arguments)
     */
    public WriteService(@NonNull Supplier<? extends AbstractServiceContextConfiguration> appExecutionContext) {
        this.appExecutionContext = appExecutionContext;

        this.colorWriter = AppContext
                .getClassLazy(ColorWriter.class);
        this.imageReader = AppContext
                .getClassLazy(ImageReader.class);
        this.roleSelector = AppContext.
                getClassLazy(RoleSelector.class);
    }

    /**
     * Reads an image from the given file path and groups its pixels into color clusters.
     *
     * @param pathToImage the file path to the image
     * @return clusters sorted by descending share of the image, or {@code null} if an error
     * occurs
     */
    public @Nullable List<ColorCluster> processImage(@NonNull String pathToImage) {
        try {
            return this.imageReader
                    .get().readClusters(pathToImage);
        } catch (IOException e) {
            log.log(Level.SEVERE, ("Error reading image " +
                    "file from path %s").formatted(pathToImage), e);

            return null;
        }
    }

    /**
     * Assembles a complete {@link Prism} spectrum from the image's color clusters.
     * <p>
     * Picks a base color for each role (see {@link RoleSelector}) and expands each one into
     * a 10-step scale on a shared lightness scale (see {@link ColorWriter}).
     *
     * @param clusters the image's color clusters
     * @return a fully populated {@link Prism} instance
     */
    public @NonNull Prism getPrism(@NonNull List<ColorCluster> clusters) {
        Roles roles = roleSelector.get().select(clusters);

        return Prism.builder()
                .lux(this.toScale(roles.lux()))
                .core(this.toScale(roles.core()))
                .wave(this.toScale(roles.wave()))
                .flare(this.toScale(roles.flare()))
                .spark(this.toScale(roles.spark()))
                .build();
    }

    /**
     * Converts a base OKLCH color into a 10-step {@link ColorScale}.
     *
     * @param base the base OKLCH color
     * @return the generated color scale
     */
    private @NonNull ColorScale toScale(@NonNull Oklch base) {
        Integer[] spectrum = this.colorWriter.get().calculateSpectrum(base);
        return ColorScale.fromColorArray(this.formatColors(spectrum));
    }

    /**
     * Formats an array of packed {@code 0xRRGGBB} colors into string representations. The
     * order is kept as given (the spectrum is already ordered from lightest to darkest).
     *
     * @param colors an array of 24-bit RGB colors
     * @return an array of formatted color strings
     * @throws IllegalArgumentException if an unsupported type is provided
     */
    public @NonNull String[] formatColors(@NonNull Integer[] colors) {
        WriterAdapter adapter = this.appExecutionContext.get().getWriterAdapter();

        String type = WriterAdapter.RGB;
        if (Objects.nonNull(adapter)) {
            type = adapter.getType();
        }

        if (!WriterAdapter.HEX.equalsIgnoreCase(type) && !WriterAdapter.RGB.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(
                    "Invalid color type: %s. Expected hex or rgb.".formatted(type));
        }

        boolean hex = WriterAdapter.HEX.equalsIgnoreCase(type);
        String[] result = new String[colors.length];

        for (int i = 0; i < colors.length; i++) {
            int r = (colors[i] >> 16) & 0xFF;
            int g = (colors[i] >> 8) & 0xFF;
            int b = colors[i] & 0xFF;

            result[i] = hex
                    ? "#%02X%02X%02X".formatted(r, g, b)
                    : "rgb(%d, %d, %d)".formatted(r, g, b);
        }

        return result;
    }
}