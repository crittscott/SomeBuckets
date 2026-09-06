package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared formatting, classification vocabulary, and file output for the {@code /sb} diagnostic
 * commands. Every finding reaches the operator through {@link #feedback} and the written report; none
 * is logged.
 */
public final class DiagnosticReport {
    private DiagnosticReport() {}

    /** Classification of one report entry, most benign first. */
    public enum Status { OK, SUSPECT, FALLBACK, MISSING, ERROR }

    /**
     * One report entry. {@code detail} lines print indented under the id; {@code notes} print as
     * {@code - reason} bullets. Blank strings in either list are dropped.
     */
    public record Row(String id, Status status, List<String> detail, List<String> notes) {}

    public static boolean isProblem(Status status) {
        return status != Status.OK;
    }

    /**
     * Writes {@code <configDir>/somebuckets/<fileName>}, overwriting any previous run: a header, a
     * {@code == PROBLEMS ==} section of every non-OK row, then {@code == ALL ==}.
     *
     * @return the written file path
     */
    public static Path write(String fileName, String title, List<String> summary, List<Row> rows)
            throws IOException {
        Path dir = DiagnosticsSupport.get().configDir().resolve(SomeBuckets.MODID);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("Some Buckets — ").append(title).append('\n');
        sb.append("generated ").append(Instant.now())
                .append(" · ").append(DiagnosticsSupport.get().loaderName())
                .append(" · MC ").append(SharedConstants.getCurrentVersion().getName()).append('\n');
        for (String line : summary) sb.append(line).append('\n');
        sb.append('\n');

        sb.append("== PROBLEMS ==\n");
        boolean anyProblem = false;
        for (Row row : rows) {
            if (isProblem(row.status())) {
                append(sb, row);
                anyProblem = true;
            }
        }
        if (!anyProblem) sb.append("(none)\n");
        sb.append('\n');

        sb.append("== ALL ==\n");
        for (Row row : rows) append(sb, row);

        Files.writeString(file, sb.toString());
        return file;
    }

    /**
     * The lines to send back to the command source: a one-line count summary, an inline list of the
     * first {@code cap} problem ids, and the report path.
     */
    public static List<Component> feedback(String label, List<Row> rows, String tail, Path file, int cap) {
        Map<Status, Integer> counts = counts(rows);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format(
                "[Some Buckets] %s: %d entries — %d ok, %d suspect, %d fallback, %d missing, %d error.%s",
                label, rows.size(), counts.get(Status.OK), counts.get(Status.SUSPECT),
                counts.get(Status.FALLBACK), counts.get(Status.MISSING), counts.get(Status.ERROR),
                tail.isEmpty() ? "" : " " + tail)));

        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (isProblem(row.status())) problems.add(row.id());
        }
        if (!problems.isEmpty()) {
            String shown = String.join(", ", problems.subList(0, Math.min(cap, problems.size())));
            String more = problems.size() > cap ? " (+" + (problems.size() - cap) + " more)" : "";
            lines.add(Component.literal("Problems: " + shown + more));
        }
        lines.add(Component.literal("Full report: " + relativize(file)));
        return lines;
    }

    public static Map<Status, Integer> counts(List<Row> rows) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) counts.put(status, 0);
        for (Row row : rows) counts.merge(row.status(), 1, Integer::sum);
        return counts;
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static String argb(int color) {
        return String.format("#%08X", color);
    }

    /** Whether every channel is at or below 12/255 - the visible outcome of a color-crushing tint. */
    public static boolean nearBlack(int rgb) {
        return ((rgb >>> 16) & 0xFF) <= 12 && ((rgb >>> 8) & 0xFF) <= 12 && (rgb & 0xFF) <= 12;
    }

    /** Whether the three channels are equal, i.e. the color carries no hue. */
    public static boolean noHue(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r == g && g == b;
    }

    private static void append(StringBuilder sb, Row row) {
        sb.append(pad(row.status().name())).append(row.id()).append('\n');
        for (String line : row.detail()) {
            if (!line.isBlank()) sb.append("         ").append(line).append('\n');
        }
        for (String note : row.notes()) {
            if (!note.isBlank()) sb.append("         - ").append(note).append('\n');
        }
    }

    private static String pad(String status) {
        return status.length() >= 9 ? status + " " : status + " ".repeat(9 - status.length());
    }

    private static String relativize(Path file) {
        try {
            return DiagnosticsSupport.get().configDir().getParent().relativize(file)
                    .toString().replace('\\', '/');
        } catch (RuntimeException ignored) {
            return file.toString().replace('\\', '/');
        }
    }
}
