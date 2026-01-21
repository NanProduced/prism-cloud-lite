package nan.produced.prism.core.assistant.application.ingest;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownRagChunker {

    private MarkdownRagChunker() {
    }

    public record ParentChunk(String headingPath, int parentIndex, String text) {
    }

    public record ChildChunk(int chunkIndex, String text) {
    }

    public static List<ParentChunk> splitParents(String markdownBody,
                                                 String title,
                                                 int maxChars,
                                                 int overlapChars) {
        String normalized = normalize(markdownBody);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<Section> sections = splitByHeadings(normalized, title);
        int parentLevel = resolveParentLevel(sections);

        List<ParentChunk> parents = new ArrayList<>();
        ParentBuilder current = null;
        for (Section section : sections) {
            if (section.level() == parentLevel) {
                if (current != null && StringUtils.hasText(current.text())) {
                    parents.addAll(current.toChunks(maxChars, overlapChars));
                }
                current = new ParentBuilder(section.heading());
                current.append(section);
                continue;
            }

            if (current == null) {
                current = new ParentBuilder(section.heading());
            }
            current.append(section);
        }

        if (current != null && StringUtils.hasText(current.text())) {
            parents.addAll(current.toChunks(maxChars, overlapChars));
        }

        return List.copyOf(parents);
    }

    public static List<ChildChunk> splitChildren(String parentText,
                                                 int maxChars,
                                                 int overlapChars,
                                                 int startIndex) {
        String normalized = normalize(parentText);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> pieces = splitByMaxChars(normalized, maxChars, overlapChars);
        List<ChildChunk> children = new ArrayList<>(pieces.size());
        int idx = startIndex;
        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (!trimmed.isBlank()) {
                children.add(new ChildChunk(idx++, trimmed));
            }
        }
        return children;
    }

    private record Section(int level, String heading, String content) {
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).replace("\r\n", "\n").trim();
    }

    private static List<Section> splitByHeadings(String body, String title) {
        List<Section> out = new ArrayList<>();
        String currentHeading = StringUtils.hasText(title) ? "# " + title.trim() : "";
        int currentLevel = StringUtils.hasText(title) ? 1 : 0;
        StringBuilder current = new StringBuilder();

        boolean inCodeFence = false;
        for (String line : body.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence;
            }

            if (!inCodeFence && isHeadingLine(trimmed)) {
                if (!current.isEmpty()) {
                    out.add(new Section(currentLevel, currentHeading, current.toString().trim()));
                    current.setLength(0);
                }
                currentHeading = trimmed;
                currentLevel = headingLevel(trimmed);
                continue;
            }

            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            out.add(new Section(currentLevel, currentHeading, current.toString().trim()));
        }

        return out;
    }

    private static int resolveParentLevel(List<Section> sections) {
        boolean hasH2 = sections.stream().anyMatch(s -> s.level() == 2);
        if (hasH2) {
            return 2;
        }
        boolean hasH3 = sections.stream().anyMatch(s -> s.level() == 3);
        if (hasH3) {
            return 3;
        }
        return 1;
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

    private static int headingLevel(String line) {
        if (line == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '#') {
                count++;
            } else {
                break;
            }
        }
        return count;
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

    private static final class ParentBuilder {
        private final String headingPath;
        private final StringBuilder body = new StringBuilder();
        private int index = 0;

        private ParentBuilder(String headingPath) {
            this.headingPath = StringUtils.hasText(headingPath) ? headingPath.trim() : "";
        }

        private void append(Section section) {
            if (section == null) {
                return;
            }
            String heading = section.heading();
            String content = section.content();
            if (StringUtils.hasText(heading)) {
                body.append(heading.trim()).append('\n');
            }
            if (StringUtils.hasText(content)) {
                body.append('\n').append(content.trim()).append('\n');
            }
        }

        private String text() {
            return body.toString().trim();
        }

        private List<ParentChunk> toChunks(int maxChars, int overlapChars) {
            List<String> pieces = splitByMaxChars(text(), maxChars, overlapChars);
            List<ParentChunk> chunks = new ArrayList<>(pieces.size());
            for (String piece : pieces) {
                if (StringUtils.hasText(piece)) {
                    chunks.add(new ParentChunk(headingPath, index++, piece.trim()));
                }
            }
            return chunks;
        }
    }
}
