CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE knowledge_base (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL, description varchar(1000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE document (
 id uuid PRIMARY KEY, kb_id uuid NOT NULL REFERENCES knowledge_base(id),
 filename varchar(180) NOT NULL, media_type varchar(100) NOT NULL,
 checksum char(64) NOT NULL, original bytea NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('UPLOADED','PROCESSING','READY','FAILED')),
 failure_reason varchar(60), created_at timestamptz NOT NULL DEFAULT now(), indexed_at timestamptz,
 active_version uuid, attempt_version uuid, attempt_at timestamptz, embedding_version varchar(120),
 UNIQUE(kb_id, checksum), UNIQUE(kb_id,id)
);
CREATE TABLE chunk (
 id uuid PRIMARY KEY, kb_id uuid NOT NULL, document_id uuid NOT NULL,
 ingestion_version uuid NOT NULL, chunk_index int NOT NULL, page int, heading varchar(300),
 content text NOT NULL, embedding vector(768) NOT NULL,
 embedding_provider varchar(40) NOT NULL, embedding_model varchar(120) NOT NULL,
 embedding_version varchar(120) NOT NULL, dimensions int NOT NULL CHECK(dimensions=768),
 search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
 FOREIGN KEY(kb_id,document_id) REFERENCES document(kb_id,id),
 UNIQUE(document_id,ingestion_version,chunk_index)
);
CREATE INDEX chunk_kb ON chunk(kb_id,document_id,ingestion_version);
CREATE INDEX chunk_lexical ON chunk USING gin(search_vector);
-- Exact cosine search is intentional: no approximate index in the small-corpus baseline.
