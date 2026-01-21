-- V18__assistant_rag_rebuild.sql
-- Rebuild RAG schema for Spring AI (parent table + FTS)

CREATE SCHEMA IF NOT EXISTS assistant;

-- Drop legacy RAG tables (will be re-ingested).
DROP TABLE IF EXISTS assistant.rag_chunk;
DROP TABLE IF EXISTS assistant.rag_document;

-- Parent table for Small-to-Big retrieval.
CREATE TABLE IF NOT EXISTS assistant.rag_parent (
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
    heading_path VARCHAR(512),
    parent_index INT NOT NULL DEFAULT 0,
    parent_text TEXT NOT NULL,
    char_count INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Requires jieba_cfg (pg_jieba) and english configs to exist.
    fts_zh tsvector GENERATED ALWAYS AS (to_tsvector('jieba_cfg', coalesce(parent_text, ''))) STORED,
    fts_en tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(parent_text, ''))) STORED,
    CONSTRAINT uk_assistant_rag_parent_doc_lang_heading UNIQUE (doc_key, lang, heading_path, parent_index)
);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_parent_slug
    ON assistant.rag_parent(slug);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_parent_lang_status_audience
    ON assistant.rag_parent(lang, status, audience);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_parent_doc_key_lang
    ON assistant.rag_parent(doc_key, lang);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_parent_fts_zh
    ON assistant.rag_parent USING gin (fts_zh);

CREATE INDEX IF NOT EXISTS idx_assistant_rag_parent_fts_en
    ON assistant.rag_parent USING gin (fts_en);
