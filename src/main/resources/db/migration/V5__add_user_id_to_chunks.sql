-- Desnormaliza user_id em chunks para que o filtro por usuário fique na mesma
-- tabela do índice HNSW: com o predicado na tabela JOINada, a busca ANN gera
-- candidatos em ordem global e o filtro de tenant só é aplicado depois, o que
-- degrada o recall conforme o corpus cresce.
ALTER TABLE chunks ADD COLUMN user_id UUID;

UPDATE chunks c
SET user_id = d.user_id
FROM documents d
WHERE d.id = c.document_id;

ALTER TABLE chunks ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE chunks ADD CONSTRAINT fk_chunks_user_id FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_chunks_user_id ON chunks (user_id);
