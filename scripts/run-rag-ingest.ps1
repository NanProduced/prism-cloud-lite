$ErrorActionPreference = "Stop"

Write-Host "[rag-ingest] Starting one-shot docs ingestion..." -ForegroundColor Cyan

if (-not $env:SPRING_DATASOURCE_URL) {
  Write-Host "[rag-ingest] Missing env: SPRING_DATASOURCE_URL" -ForegroundColor Yellow
}
if (-not $env:SPRING_DATASOURCE_USERNAME) {
  Write-Host "[rag-ingest] Missing env: SPRING_DATASOURCE_USERNAME" -ForegroundColor Yellow
}
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
  Write-Host "[rag-ingest] Missing env: SPRING_DATASOURCE_PASSWORD" -ForegroundColor Yellow
}

if (-not $env:ASSISTANT_RAG_EMBEDDING_BASE_URL) {
  $env:ASSISTANT_RAG_EMBEDDING_BASE_URL = "http://127.0.0.1:7997"
}
if (-not $env:ASSISTANT_RAG_EMBEDDING_MODEL) {
  $env:ASSISTANT_RAG_EMBEDDING_MODEL = "BAAI/bge-m3"
}
if (-not $env:ASSISTANT_RAG_DOC_VERSION) {
  $env:ASSISTANT_RAG_DOC_VERSION = "dev"
}

Write-Host "[rag-ingest] Using profile: rag-ingest (web-application-type=none)" -ForegroundColor Cyan
Write-Host "[rag-ingest] Embeddings: $env:ASSISTANT_RAG_EMBEDDING_BASE_URL model=$env:ASSISTANT_RAG_EMBEDDING_MODEL" -ForegroundColor Cyan

$env:SPRING_PROFILES_ACTIVE = "rag-ingest"

Push-Location (Join-Path $PSScriptRoot "..")
try {
  .\mvnw.cmd -pl core-service -DskipTests spring-boot:run
} finally {
  Pop-Location
}
