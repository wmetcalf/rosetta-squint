package io.github.wmetcalf.rosettasquint.hash.testkit;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class SpecPath {
    public static final Path SPEC = Paths.get("..", "spec").toAbsolutePath().normalize();
    public static final Path FIXTURES = SPEC.resolve("fixtures");
    public static final Path DECODED = SPEC.resolve("decoded");
    public static final Path LANCZOS_CASES = SPEC.resolve("lanczos_cases");
    public static final Path GOLDENS_JSON = SPEC.resolve("goldens.json");
    public static final Path GRAYSCALE_CASES = SPEC.resolve("grayscale_cases.json");
    public static final Path HSV_CASES = SPEC.resolve("hsv_cases.json");
    public static final Path DCT_CASES = SPEC.resolve("dct_cases.json");
    public static final Path HAAR_CASES = SPEC.resolve("haar_cases.json");
    public static final Path DB4_CASES = SPEC.resolve("db4_cases.json");
    public static final Path GAUSSIAN_BLUR_CASES = SPEC.resolve("gaussian_blur_cases.json");
    public static final Path MEDIAN_FILTER_CASES = SPEC.resolve("median_filter_cases.json");
    public static final Path SEGMENTATION_CASES = SPEC.resolve("segmentation_cases.json");

    private SpecPath() {}
}
