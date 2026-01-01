package nan.produced.prism.core.assistant.application.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import nan.produced.prism.core.assistant.infrastructure.embeddings.OpenAiEmbeddingClient;
import nan.produced.prism.core.assistant.infrastructure.persistence.RagDocsRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocsRagIngestService {

    private final AssistantRagProperties properties;
    private final OpenAiEmbeddingClient embeddingClient;
    private final RagDocsRepository repository;

    public record IngestResult(int docsIngested, int chunksIngested, long elapsedMs) {
    }

    public IngestResult ingestHelpDocs() {
        long startedAt = System.currentTimeMillis();

        List<MarkdownDocLoader.LoadedDoc> docs = new ArrayList<>();
        List<Path> roots = resolveExistingRoots(properties.ingest().helpRoots());
        if (roots.isEmpty()) {
            log.warn("[assistant.rag.ingest] no docs roots found; configuredRoots={}", properties.ingest().helpRoots());
        } else {
            log.info("[assistant.rag.ingest] resolvedRoots={}", roots);
        }
        for (Path root : roots) {
            docs.addAll(MarkdownDocLoader.loadAllMarkdown(root));
        }

        docs.sort(Comparator.comparing(d -> d.frontMatter().slug()));

        int maxDocs = properties.ingest().maxDocs();
        if (maxDocs > 0 && docs.size() > maxDocs) {
            docs = docs.subList(0, maxDocs);
        }

        if (docs.isEmpty()) {
            log.warn("[assistant.rag.ingest] loadedDocs=0 (check working directory + helpRoots, or filters: requireAudience={}, requireStatus={})",
                    properties.ingest().requireAudience(), properties.ingest().requireStatus());
        }

        int docsIngested = 0;
        int chunksIngested = 0;

        for (var doc : docs) {
            var fm = doc.frontMatter();
            if (!properties.ingest().requireAudience().equalsIgnoreCase(fm.audience())) {
                continue;
            }
            if (!properties.ingest().requireStatus().equalsIgnoreCase(fm.status())) {
                continue;
            }

            List<MarkdownChunker.Chunk> chunks = MarkdownChunker.chunk(
                    doc.body(),
                    fm.title(),
                    properties.chunk().maxChars(),
                    properties.chunk().overlapChars()
            );

            if (chunks.isEmpty()) {
                log.warn("[assistant.rag.ingest] skip empty doc: path={}, slug={}", doc.sourcePath(), fm.slug());
                continue;
            }

            List<RagDocsRepository.ChunkRow> chunkRows = embedChunks(chunks);

            UUID docId = repository.upsertDocument(new RagDocsRepository.UpsertDocumentParams(
                    UUID.randomUUID(),
                    fm.docKey(),
                    fm.lang(),
                    fm.slug(),
                    fm.title(),
                    fm.module(),
                    fm.audience(),
                    fm.status(),
                    fm.owner(),
                    parseLocalDateOrNull(fm.lastUpdated()),
                    doc.sourcePath().toString().replace('\\', '/'),
                    properties.ingest().docVersion(),
                    Hashing.sha256Hex(doc.body())
            ));

            repository.replaceChunks(docId, chunkRows);

            docsIngested++;
            chunksIngested += chunkRows.size();

            log.info("[assistant.rag.ingest] upserted: slug={}, lang={}, chunks={}, updatedAt={}",
                    fm.slug(), fm.lang(), chunkRows.size(), OffsetDateTime.now());
        }

        return new IngestResult(docsIngested, chunksIngested, System.currentTimeMillis() - startedAt);
    }

    private static List<Path> resolveExistingRoots(List<String> configuredRoots) {
        if (configuredRoots == null || configuredRoots.isEmpty()) {
            return List.of();
        }

        Set<Path> out = new LinkedHashSet<>();
        for (String root : configuredRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }

            List<Path> candidates = new ArrayList<>();
            candidates.add(Path.of(root));
            if (root.startsWith("../") || root.startsWith("..\\")) {
                candidates.add(Path.of(root.substring(3)));
            } else {
                candidates.add(Path.of("..").resolve(root));
            }

            Path resolved = null;
            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    resolved = candidate.toAbsolutePath().normalize();
                    break;
                }
            }

            if (resolved == null) {
                log.warn("[assistant.rag.ingest] docs root not found: root={}, candidates={}", root, candidates);
                continue;
            }
            out.add(resolved);
        }

        return List.copyOf(out);
    }

    private List<RagDocsRepository.ChunkRow> embedChunks(List<MarkdownChunker.Chunk> chunks) {
        int batchSize = properties.embedding().batchSize();
        List<RagDocsRepository.ChunkRow> rows = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(chunks.size(), i + batchSize);
            List<MarkdownChunker.Chunk> batch = chunks.subList(i, end);
            List<String> inputs = batch.stream().map(MarkdownChunker.Chunk::text).toList();
            List<float[]> embeddings = embeddingClient.embedAll(inputs);

            if (embeddings.size() != batch.size()) {
                throw new IllegalStateException("Embeddings size mismatch: expected=%s actual=%s"
                        .formatted(batch.size(), embeddings.size()));
            }

            for (int j = 0; j < batch.size(); j++) {
                var chunk = batch.get(j);
                float[] vector = embeddings.get(j);
                rows.add(new RagDocsRepository.ChunkRow(
                        chunk.chunkIndex(),
                        chunk.headingPath(),
                        chunk.text(),
                        Hashing.sha256Hex(chunk.text()),
                        chunk.text().length(),
                        VectorLiterals.toPgVectorLiteral(vector)
                ));
            }
        }

        return rows;
    }

    private static LocalDate parseLocalDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            DateTimeFormatter yamlDateFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
            return ZonedDateTime.parse(trimmed, yamlDateFormatter).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        throw new DateTimeParseException("Unsupported date format (expect yyyy-MM-dd)", trimmed, 0);
    }
}
