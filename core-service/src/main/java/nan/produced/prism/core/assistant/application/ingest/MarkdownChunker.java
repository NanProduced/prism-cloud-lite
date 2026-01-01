package nan.produced.prism.core.assistant.application.ingest;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownChunker {

    private MarkdownChunker() {
    }

    public record Chunk(int chunkIndex, String headingPath, String text) {
    }

    public static List<Chunk> chunk(String markdownBody, String title, int maxChars, int overlapChars) {
        String normalized = (markdownBody == null ? "" : markdownBody).replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<Section> sections = splitByHeadings(normalized, title);
        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Section section : sections) {
            List<String> pieces = splitByMaxChars(section.content(), maxChars, overlapChars);
            for (String piece : pieces) {
                String text = (section.headingPath().isBlank() ? piece : section.headingPath() + "\n\n" + piece).trim();
                if (!text.isBlank()) {
                    chunks.add(new Chunk(chunkIndex++, section.headingPath(), text));
                }
            }
        }
        return chunks;
    }

    private record Section(String headingPath, String content) {
    }

    private static List<Section> splitByHeadings(String body, String title) {
        List<Section> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeadingPath = title == null ? "" : ("# " + title).trim();

        boolean inCodeFence = false;
        for (String line : body.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence;
            }

            if (!inCodeFence && isHeadingLine(trimmed)) {
                if (!current.isEmpty()) {
                    out.add(new Section(currentHeadingPath, current.toString().trim()));
                    current.setLength(0);
                }
                currentHeadingPath = trimmed;
                continue;
            }

            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            out.add(new Section(currentHeadingPath, current.toString().trim()));
        }

        return out;
    }

    private static boolean isHeadingLine(String line) {
        if (line == null) {
            return false;
        }
        if (!(line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") || line.startsWith("#### "))) {
            return false;
        }
        return line.length() >= 3;
    }

    private static List<String> splitByMaxChars(String text, int maxChars, int overlapChars) {
        String normalized = (text == null ? "" : text).trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (maxChars <= 0 || normalized.length() <= maxChars) {
            return List.of(normalized);
        }

        int safeOverlap = Math.max(0, Math.min(overlapChars, maxChars / 2));
        List<String> out = new ArrayList<>();

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxChars);
            String piece = normalized.substring(start, end).trim();
            if (!piece.isBlank()) {
                out.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - safeOverlap);
        }

        return out;
    }
}

