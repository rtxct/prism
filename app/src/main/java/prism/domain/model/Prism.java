package prism.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import prism.domain.vo.ColorScale;

/**
 * Domain model representing a color palette (spectrum) extracted from an image.
 * <p>
 * A Prism is composed of five {@link ColorScale} roles, each capturing a different tonal
 * purpose within the palette:
 * <ul>
 *   <li><b>Lux</b> – grayscale tones</li>
 *   <li><b>Core</b> – dominant color shade</li>
 *   <li><b>Wave</b> – secondary supporting shade</li>
 *   <li><b>Flare</b> – bright accent contrast</li>
 *   <li><b>Spark</b> – secondary accent sharpening</li>
 * </ul>
 *
 * <p>Instances are persisted with the custom {@value #EXTENSION} file extension.
 */
@Builder
public class Prism {

    /**
     * File custom "prism spectrum" extension.
     */
    public static final String EXTENSION = ".spec";

    /**
     * Grayscale (Lux)
     */
    private @NonNull ColorScale lux;

    /**
     * Dominant color shade (core)
     */
    private @NonNull ColorScale core;

    /**
     * Secondary supporting (Wave)
     */
    private @NonNull ColorScale wave;

    /**
     * Bright accent contrast (Flare)
     */
    private @NonNull ColorScale flare;

    /**
     * Secondary accent sharpening (Spark)
     */
    private @NonNull ColorScale spark;

    /**
     * Returns a formatted multi-line string representation of the prism palette. Each shade
     * is listed with its corresponding role, level, and color value.
     *
     * @return a formatted string of the prism colors
     */
    public String getFormatted() {
        StringBuilder builder = new StringBuilder();

        this.appendType(builder,
                "lux", this.lux.getShades());

        this.appendType(builder,
                "core", this.core.getShades());

        this.appendType(builder,
                "wave", this.wave.getShades());

        this.appendType(builder,
                "flare", this.flare.getShades());

        this.appendType(builder,
                "spark", this.spark.getShades());

        return builder.toString();
    }

    /**
     * Appends a formatted list of colors for a specific role type to the string builder.
     *
     * @param builder the StringBuilder to append to
     * @param type    the name of the color role (e.g., "lux", "core")
     * @param colors  the array of color shades
     */
    private void appendType(StringBuilder builder, String type, String[] colors) {
        for (int i = 0; i < colors.length; i++) {
            builder.append(type)
                    .append((i + 1) * 100)
                    .append("::")
                    .append(colors[i])
                    .append(System.lineSeparator());
        }

        builder.append(System.lineSeparator());

    }

    /**
     * Generates a unique identifier for this prism based on the hash codes of its
     * constituent shades.
     *
     * @return the unique identifier string
     */
    public String getId() {
        return "ID: " + this.getLuxId() +
                "-" +
                this.getCoreId() +
                "-" +
                this.getWaveId() +
                "-" +
                this.getFlareId() +
                "-" +
                this.getSparkId();
    }

    /**
     * Generates a short hash-based identifier for the lux shades.
     *
     * @return the hex string identifier for the lux shades
     */
    private String getLuxId() {
        return this.getShades(this.lux.getShades());
    }

    /**
     * Generates a short hash-based identifier for the core shades.
     *
     * @return the hex string identifier for the core shades
     */
    private String getCoreId() {
        return this.getShades(this.core.getShades());
    }

    /**
     * Generates a short hash-based identifier for the wave shades.
     *
     * @return the hex string identifier for the wave shades
     */
    private String getWaveId() {
        return this.getShades(this.wave.getShades());
    }

    /**
     * Generates a short hash-based identifier for the flare shades.
     *
     * @return the hex string identifier for the flare shades
     */
    private String getFlareId() {
        return this.getShades(this.flare.getShades());
    }

    /**
     * Generates a short hash-based identifier for the spark shades.
     *
     * @return the hex string identifier for the spark shades
     */
    private String getSparkId() {
        return this.getShades(this.spark.getShades());
    }

    /**
     * Calculates a hex string identifier based on the hash code of a given array of
     * shades.
     *
     * @param shades the array of color shades
     * @return the hex string identifier
     */
    private String getShades(String[] shades) {
        return Integer.toHexString(java.util.Arrays.hashCode(shades));
    }
}