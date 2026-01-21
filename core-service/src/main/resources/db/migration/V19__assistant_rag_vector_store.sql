-- V19__assistant_rag_vector_store.sql
-- Vector store table for Spring AI (child chunks)

CREATE SCHEMA IF NOT EXISTS assistant;

CREATE TABLE IF NOT EXISTS assistant.rag_vector (
    id UUID PRIMARY KEY NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW index for vector similarity (cosine).
CREATE INDEX IF NOT EXISTS idx_assistant_rag_vector_embedding_hnsw
    ON assistant.rag_vector USING hnsw (embedding vector_cosine_ops)
    WITH (m=16, ef_construction=200);
