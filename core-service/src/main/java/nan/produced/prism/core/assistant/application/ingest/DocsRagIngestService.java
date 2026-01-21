package nan.produced.prism.core.assistant.application.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.application.rag.RagMetadataKeys;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantRagParentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocsRagIngestService {

    private final AssistantRagProperties properties;
    private final VectorStore vectorStore;
    private final AssistantRagParentRepository parentRepository;

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

            List<MarkdownRagChunker.ParentChunk> parents = MarkdownRagChunker.splitParents(
                    doc.body(),
                    fm.title(),
                    properties.parent().maxChars(),
                    properties.parent().overlapChars()
            );

            if (parents.isEmpty()) {
                log.warn("[assistant.rag.ingest] skip empty doc: path={}, slug={}", doc.sourcePath(), fm.slug());
                continue;
            }

            String docHash = Hashing.sha256Hex(doc.body());
            LocalDate lastUpdated = parseLocalDateOrNull(fm.lastUpdated());
            List<AssistantRagParentRepository.ParentRow> parentRows = new ArrayList<>(parents.size());
            List<Document> childDocs = new ArrayList<>();
            int nextChunkIndex = 0;

            for (MarkdownRagChunker.ParentChunk parent : parents) {
                UUID parentId = UUID.randomUUID();
                String parentText = parent.text();
                int parentCharCount = parentText != null ? parentText.length() : 0;

                parentRows.add(new AssistantRagParentRepository.ParentRow(
                        parentId,
                        fm.docKey(),
                        fm.lang(),
                        fm.slug(),
                        fm.title(),
                        fm.module(),
                        fm.audience(),
                        fm.status(),
                        fm.owner(),
                        lastUpdated,
                        normalizeSourcePath(doc.sourcePath()),
                        properties.ingest().docVersion(),
                        docHash,
                        parent.headingPath(),
                        parent.parentIndex(),
                        parentText,
                        parentCharCount
                ));

                List<MarkdownRagChunker.ChildChunk> children = MarkdownRagChunker.splitChildren(
                        parentText,
                        properties.chunk().maxChars(),
                        properties.chunk().overlapChars(),
                        nextChunkIndex
                );

                for (MarkdownRagChunker.ChildChunk child : children) {
                    Map<String, Object> metadata = buildMetadata(
                            fm,
                            doc,
                            docHash,
                            lastUpdated,
                            properties.ingest().docVersion(),
                            parentId,
                            parent,
                            child
                    );
                    childDocs.add(Document.builder()
                            .id(UUID.randomUUID().toString())
                            .text(child.text())
                            .metadata(metadata)
                            .build());
                }
                if (!children.isEmpty()) {
                    nextChunkIndex = children.get(children.size() - 1).chunkIndex() + 1;
                }
            }

            parentRepository.replaceParents(fm.docKey(), fm.lang(), parentRows);
            deleteVectorChunksForDoc(fm.docKey(), fm.lang());
            if (!childDocs.isEmpty()) {
                vectorStore.add(childDocs);
            }

            docsIngested++;
            chunksIngested += childDocs.size();

            log.info("[assistant.rag.ingest] upserted: slug={}, lang={}, parents={}, chunks={}, updatedAt={}",
                    fm.slug(), fm.lang(), parentRows.size(), childDocs.size(), OffsetDateTime.now());
        }

        return new IngestResult(docsIngested, chunksIngested, System.currentTimeMillis() - startedAt);
    }

    private void deleteVectorChunksForDoc(String docKey, String lang) {
        if (!StringUtils.hasText(docKey) || !StringUtils.hasText(lang)) {
            return;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        var expr = builder.and(
                builder.eq(RagMetadataKeys.DOC_KEY, docKey),
                builder.eq(RagMetadataKeys.LANG, lang)
        );
        vectorStore.delete(expr.build());
    }

    private static Map<String, Object> buildMetadata(MarkdownDocLoader.FrontMatter fm,
                                                     MarkdownDocLoader.LoadedDoc doc,
                                                     String docHash,
                                                     LocalDate lastUpdated,
                                                     String docVersion,
                                                     UUID parentId,
                                                     MarkdownRagChunker.ParentChunk parent,
                                                     MarkdownRagChunker.ChildChunk child) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(RagMetadataKeys.DOC_KEY, fm.docKey());
        metadata.put(RagMetadataKeys.LANG, fm.lang());
        metadata.put(RagMetadataKeys.SLUG, fm.slug());
        metadata.put(RagMetadataKeys.TITLE, fm.title());
        metadata.put(RagMetadataKeys.MODULE, fm.module());
        metadata.put(RagMetadataKeys.AUDIENCE, fm.audience());
        metadata.put(RagMetadataKeys.STATUS, fm.status());
        metadata.put(RagMetadataKeys.OWNER, fm.owner());
        if (lastUpdated != null) {
            metadata.put(RagMetadataKeys.LAST_UPDATED, lastUpdated.toString());
        }
        metadata.put(RagMetadataKeys.SOURCE_PATH, normalizeSourcePath(doc.sourcePath()));
        metadata.put(RagMetadataKeys.DOC_VERSION, docVersion);
        metadata.put(RagMetadataKeys.DOC_HASH, docHash);
        metadata.put(RagMetadataKeys.PARENT_ID, parentId.toString());
        metadata.put(RagMetadataKeys.PARENT_INDEX, parent.parentIndex());
        metadata.put(RagMetadataKeys.HEADING_PATH, parent.headingPath());
        metadata.put(RagMetadataKeys.CHUNK_INDEX, child.chunkIndex());
        metadata.put(RagMetadataKeys.CHAR_COUNT, child.text() != null ? child.text().length() : 0);
        return metadata;
    }

    private static String normalizeSourcePath(Path sourcePath) {
        if (sourcePath == null) {
            return "";
        }
        return sourcePath.toString().replace('\\', '/');
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
