package nan.produced.prism.core.assistant.application.ingest;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class MarkdownDocLoader {

    private MarkdownDocLoader() {
    }

    public record FrontMatter(
            String docKey,
            String lang,
            String slug,
            String title,
            String module,
            String audience,
            String status,
            String owner,
            String lastUpdated
    ) {
    }

    public record LoadedDoc(Path sourcePath, FrontMatter frontMatter, String body) {
    }

    public static List<LoadedDoc> loadAllMarkdown(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }

        List<LoadedDoc> docs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".md"))
                    .forEach(path -> docs.add(loadOne(path)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk docs root: " + root, e);
        }
        return docs;
    }

    public static LoadedDoc loadOne(Path path) {
        String raw;
        try {
            raw = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read doc: " + path, e);
        }

        ParsedFrontMatter parsed = parseFrontMatter(raw);
        FrontMatter fm = mapFrontMatter(path, parsed.frontMatterMap());
        return new LoadedDoc(path, fm, parsed.body());
    }

    private record ParsedFrontMatter(Map<String, Object> frontMatterMap, String body) {
    }

    private static ParsedFrontMatter parseFrontMatter(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw is null");
        }

        String normalized = raw.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalStateException("Missing front-matter header (---)");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("Missing front-matter trailer (---)");
        }

        String yamlBlock = normalized.substring(4, end + 1);
        String body = normalized.substring(end + 5);

        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(50);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(yamlBlock);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Front-matter must be a map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> fm = (Map<String, Object>) map;
        return new ParsedFrontMatter(fm, body.trim());
    }

    private static FrontMatter mapFrontMatter(Path path, Map<String, Object> fm) {
        return new FrontMatter(
                requiredString(fm, "docKey", path),
                requiredString(fm, "lang", path),
                requiredString(fm, "slug", path),
                requiredString(fm, "title", path),
                optionalString(fm, "module"),
                requiredString(fm, "audience", path),
                requiredString(fm, "status", path),
                optionalString(fm, "owner"),
                optionalString(fm, "lastUpdated")
        );
    }

    private static String requiredString(Map<String, Object> fm, String key, Path path) {
        String value = optionalString(fm, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required front-matter key '%s' in %s".formatted(key, path));
        }
        return value;
    }

    private static String optionalString(Map<String, Object> fm, String key) {
        Object value = fm.get(key);
        if (value == null) {
            return null;
        }
        return Objects.toString(value, null);
    }
}

