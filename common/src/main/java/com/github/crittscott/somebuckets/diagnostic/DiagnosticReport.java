package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.client.ClientPlatform;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import javax.annotation.Nullable;
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
    private static final int CHAT_PROBLEM_LIMIT = 15;
    private static final int NEAR_BLACK_CHANNEL_MAX = 12;
    private static final int STATUS_COLUMN_WIDTH = 9;
    private static final String DETAIL_INDENT = " ".repeat(STATUS_COLUMN_WIDTH);

    private DiagnosticReport() {}

    /** Classification of one report entry, most benign first. */
    public enum Status { OK, SUSPECT, FALLBACK, MISSING, ERROR }

    /**
     * One report entry. {@code detail} lines print indented under the id; {@code notes} print as
     * {@code - reason} bullets. Blank strings in either list are dropped.
     */
    public record Row(String id, Status status, List<String> detail, List<String> notes) {}

    /** Returns whether {@code status} belongs in the report's {@code PROBLEMS} section. */
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
        Path dir = ClientPlatform.configDir().resolve(SomeBuckets.MODID);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("Some Buckets — ").append(title).append('\n');
        sb.append("generated ").append(Instant.now())
                .append(" · ").append(ClientPlatform.loaderName())
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
     * first problem ids, and the report path.
     */
    public static List<Component> feedback(String label, List<Row> rows, Path file) {
        return feedback(label, rows, null, file);
    }

    /** Adds an optional translated suffix to the standard feedback summary. */
    public static List<Component> feedback(String label, List<Row> rows, @Nullable Component tail, Path file) {
        Map<Status, Integer> counts = counts(rows);
        List<Component> lines = new ArrayList<>();
        Component summary = Component.translatable("commands.somebuckets.sb.summary",
                label, rows.size(), counts.get(Status.OK), counts.get(Status.SUSPECT),
                counts.get(Status.FALLBACK), counts.get(Status.MISSING), counts.get(Status.ERROR));
        if (tail != null) summary = summary.copy().append(" ").append(tail);
        lines.add(summary);

        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (isProblem(row.status())) problems.add(row.id());
        }
        if (!problems.isEmpty()) {
            String shown = String.join(", ", problems.subList(
                    0, Math.min(CHAT_PROBLEM_LIMIT, problems.size())));
            lines.add(problems.size() > CHAT_PROBLEM_LIMIT
                    ? Component.translatable("commands.somebuckets.sb.problems_more", shown,
                            problems.size() - CHAT_PROBLEM_LIMIT)
                    : Component.translatable("commands.somebuckets.sb.problems", shown));
        }
        lines.add(Component.translatable("commands.somebuckets.sb.report_path", relativize(file)));
        return lines;
    }

    /** Counts rows by every status, including zero-valued statuses. */
    public static Map<Status, Integer> counts(List<Row> rows) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) counts.put(status, 0);
        for (Row row : rows) counts.merge(row.status(), 1, Integer::sum);
        return counts;
    }

    /** Formats the low 24 bits of {@code rgb} as {@code #RRGGBB}. */
    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Formats all 32 bits of {@code color} as {@code #AARRGGBB}. */
    public static String argb(int color) {
        return String.format("#%08X", color);
    }

    /** Whether every channel is near zero - the visible outcome of a color-crushing tint. */
    public static boolean nearBlack(int rgb) {
        return ARGB.red(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && ARGB.green(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && ARGB.blue(rgb) <= NEAR_BLACK_CHANNEL_MAX;
    }

    /** Whether the three channels are equal, i.e. the color carries no hue. */
    public static boolean noHue(int rgb) {
        return ARGB.red(rgb) == ARGB.green(rgb) && ARGB.green(rgb) == ARGB.blue(rgb);
    }

    private static void append(StringBuilder sb, Row row) {
        sb.append(pad(row.status().name())).append(row.id()).append('\n');
        for (String line : row.detail()) {
            if (!line.isBlank()) sb.append(DETAIL_INDENT).append(line).append('\n');
        }
        for (String note : row.notes()) {
            if (!note.isBlank()) sb.append(DETAIL_INDENT).append("- ").append(note).append('\n');
        }
    }

    private static String pad(String status) {
        return status.length() >= STATUS_COLUMN_WIDTH
                ? status + " "
                : status + " ".repeat(STATUS_COLUMN_WIDTH - status.length());
    }

    private static String relativize(Path file) {
        try {
            return ClientPlatform.configDir().getParent().relativize(file)
                    .toString().replace('\\', '/');
        } catch (RuntimeException ignored) {
            return file.toString().replace('\\', '/');
        }
    }
}
