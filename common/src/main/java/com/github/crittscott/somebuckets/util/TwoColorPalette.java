package com.github.crittscott.somebuckets.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Reduces straight-alpha ARGB pixels to two sampled colors using opacity-weighted Oklab clustering. */
public final class TwoColorPalette {
    private static final double CONVERGENCE_DISTANCE_SQUARED = 1.0e-12;
    private static final int MAX_ITERATIONS = 32;

    private TwoColorPalette() {}

    /** Opaque sampled colors, with the more populous cluster first. */
    public record Colors(int primary, int secondary) {}

    private record Lab(double lightness, double redGreen, double yellowBlue) {
        double distanceSquared(Lab other) {
            double dl = lightness - other.lightness;
            double da = redGreen - other.redGreen;
            double db = yellowBlue - other.yellowBlue;
            return dl * dl + da * da + db * db;
        }
    }

    private record Point(int rgb, long weight, Lab lab) {}

    /** Returns no palette for an entirely transparent image. Black, white, and gray are valid colors. */
    public static Optional<Colors> extract(int[] pixels) {
        Map<Integer, Long> histogram = new TreeMap<>();
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha != 0) histogram.merge(pixel & 0xFFFFFF, (long) alpha, Long::sum);
        }
        if (histogram.isEmpty()) return Optional.empty();
        List<Point> points = new ArrayList<>(histogram.size());
        histogram.forEach((rgb, weight) -> points.add(new Point(rgb, weight, toLab(rgb))));
        Point dominant = points.getFirst();
        for (Point point : points) {
            if (point.weight() > dominant.weight()) dominant = point;
        }
        Lab first = dominant.lab();
        Lab second = first;
        double furthest = 0;
        for (Point point : points) {
            double score = point.weight() * point.lab().distanceSquared(first);
            if (score > furthest) {
                furthest = score;
                second = point.lab();
            }
        }
        if (furthest == 0) return Optional.of(new Colors(opaque(dominant.rgb()), opaque(dominant.rgb())));

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[][] sums = new double[2][4];
            for (Point point : points) {
                int group = group(point.lab(), first, second);
                sums[group][0] += point.lab().lightness() * point.weight();
                sums[group][1] += point.lab().redGreen() * point.weight();
                sums[group][2] += point.lab().yellowBlue() * point.weight();
                sums[group][3] += point.weight();
            }
            if (sums[0][3] == 0 || sums[1][3] == 0) break;
            Lab nextFirst = mean(sums[0]);
            Lab nextSecond = mean(sums[1]);
            double movement = first.distanceSquared(nextFirst) + second.distanceSquared(nextSecond);
            first = nextFirst;
            second = nextSecond;
            if (movement <= CONVERGENCE_DISTANCE_SQUARED) break;
        }

        Point[] nearest = new Point[2];
        double[] distances = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        long[] weights = new long[2];
        for (Point point : points) {
            int group = group(point.lab(), first, second);
            weights[group] += point.weight();
            double distance = point.lab().distanceSquared(group == 0 ? first : second);
            if (distance < distances[group] || (distance == distances[group]
                    && (nearest[group] == null || point.weight() > nearest[group].weight()))) {
                nearest[group] = point;
                distances[group] = distance;
            }
        }
        int primaryGroup = weights[1] > weights[0] ? 1 : 0;
        Point primary = nearest[primaryGroup];
        Point secondary = nearest[1 - primaryGroup];
        if (primary == null) primary = dominant;
        if (secondary == null) secondary = primary;
        return Optional.of(new Colors(opaque(primary.rgb()), opaque(secondary.rgb())));
    }

    private static int group(Lab point, Lab first, Lab second) {
        return point.distanceSquared(first) <= point.distanceSquared(second) ? 0 : 1;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static Lab mean(double[] sum) {
        return new Lab(sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3]);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /* Bjorn Ottosson's Oklab transform: https://bottosson.github.io/posts/oklab/ */
    private static Lab toLab(int rgb) {
        double red = linear((rgb >>> 16) & 255);
        double green = linear((rgb >>> 8) & 255);
        double blue = linear(rgb & 255);
        double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new Lab(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }
}
