package nan.produced.prism.core.assistant.application.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "assistant.rag.ingest", name = "enabled", havingValue = "true")
public class DocsRagIngestApplicationRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final AssistantRagProperties properties;
    private final DocsRagIngestService ingestService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[assistant.rag.ingest] start: roots={}, version={}",
                properties.ingest().helpRoots(), properties.ingest().docVersion());

        var result = ingestService.ingestHelpDocs();
        log.info("[assistant.rag.ingest] done: docs={}, chunks={}, elapsedMs={}",
                result.docsIngested(), result.chunksIngested(), result.elapsedMs());

        if (properties.ingest().exitAfterRun()) {
            log.info("[assistant.rag.ingest] exitAfterRun=true, shutting down");
            int code = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }
}

