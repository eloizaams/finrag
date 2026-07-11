CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    filename    VARCHAR(255) NOT NULL,
    chunk_count INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documents_user_id ON documents (user_id);

CREATE TABLE chunks (
    id          UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536) NOT NULL
);

CREATE INDEX idx_chunks_document_id ON chunks (document_id);
