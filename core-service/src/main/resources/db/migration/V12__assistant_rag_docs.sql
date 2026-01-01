-- V12__assistant_rag_docs.sql
-- RAG docs storage (Help Center) for AI assistant

CREATE SCHEMA IF NOT EXISTS assistant;

-- pgvector extension (provided by pgvector/pgvector docker image)
CREATE EXTENSION IF NOT EXISTS vector;

-- Be robust to pgvector installed into different schemas (e.g. assistant/public)
SET search_path = assistant, public;

CREATE TABLE IF NOT EXISTS assistant.rag_document (
    id UUID PRIMARY KEY NOT NULL,
    doc_key VARCHAR(256) NOT NULL,
    lang VARCHAR(8) NOT NULL,
    slug VARCHAR(512) NOT NULL,
    title VARCHAR(256) NOT NULL,
    module VARCHAR(64),
    audience VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner VARCHAR(64),
    last_updated DATE,
    source_path TEXT NOT NULL,
    doc_version VARCHAR(64),
    doc_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_assistant_rag_document_doc_key_lang UNIQUE (doc_key, lang)
);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_document_slug
    ON assistant.rag_document(slug);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_document_lang_status_audience
    ON assistant.rag_document(lang, status, audience);

CREATE TABLE IF NOT EXISTS assistant.rag_chunk (
    id BIGSERIAL PRIMARY KEY,
    doc_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    heading_path VARCHAR(512),
    chunk_text TEXT NOT NULL,
    chunk_hash CHAR(64) NOT NULL,
    char_count INT NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assistant_rag_chunk_doc FOREIGN KEY (doc_id) REFERENCES assistant.rag_document(id) ON DELETE CASCADE,
    CONSTRAINT uk_assistant_rag_chunk_doc_chunk_index UNIQUE (doc_id, chunk_index)
);

-- NOTE: ivfflat index requires ANALYZE after data load for best results.
-- For small datasets it is optional, but we create it to prepare for retrieval.
CREATE INDEX IF NOT EXISTS idx_assistant_rag_chunk_embedding_cosine
    ON assistant.rag_chunk USING ivfflat (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_chunk_doc_id
    ON assistant.rag_chunk(doc_id);
