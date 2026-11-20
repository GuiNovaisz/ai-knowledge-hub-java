# Contratos HTTP

Prefixo `/api/v1/knowledge-bases`. OpenAPI gerado em `/v3/api-docs`.

| Método/caminho | Entrada | Resultado |
|---|---|---|
| `POST /` | JSON `name` até 120; `description` até 1.000, ambos obrigatórios | 201 com KB |
| `GET /{id}` | UUID | KB ou 404 |
| `POST /{id}/documents` | multipart `file` | documento com READY/FAILED ou identidade existente |
| `GET /{id}/documents` | UUID | até 100 documentos mais recentes |
| `GET /{id}/documents/{documentId}` | UUIDs | metadata/estado; não retorna bytes originais |
| `POST /{id}/documents/{documentId}/reindex` | UUIDs | retry explícito; novo token de versão |
| `POST /{id}/query` | `query`, `topK`, `documentId` opcional | resposta grounded ou abstention |
| `POST /{id}/retrieval` | query + `strategy`: VECTOR/LEXICAL/HYBRID | candidatos inspecionáveis, sem chat |

`query` deve ser não vazia e ter até 2.000 caracteres. `topK` é inteiro obrigatório em 1..20. Campos UUID inválidos e violações de Bean Validation retornam 400. Todos os resultados do documento são limitados por KB.

Resposta de query:

```json
{
  "status": "ANSWERED",
  "answer": "The refund window is 30 days. [S1]",
  "citations": [{
    "sourceId": "S1", "documentId": "<uuid>", "filename": "returns.md",
    "page": 1, "chunkId": "<uuid>", "quote": "<texto real do chunk>"
  }],
  "retrieval": [{"rank": 1, "chunkId": "<uuid>", "documentId": "<uuid>", "filename": "returns.md", "page": 1, "score": 0.8, "rankScore": 0.8}],
  "model": "qwen3:1.7b", "usage": 123, "correlationId": "<uuid>"
}
```

`usage` é total de tokens reportados pelo provider, quando disponível. `model` e `usage` são null quando o chat não foi chamado. Não há chain-of-thought na API. `INSUFFICIENT_EVIDENCE` usa mensagem amigável e citações vazias. `SOURCE_CONFLICT` explica o desacordo e cita pelo menos duas fontes; a identificação semântica depende do modelo.

Scores no endpoint técnico não são garantia de answerability: o gate de threshold, injection e orçamento é posterior. Esse endpoint expõe conteúdo dos chunks e destina-se à demonstração local.

## Erros

Erros têm `{ "code": "...", "correlationId": "..." }`, sem stack trace ou mensagem de provider.

| HTTP | Exemplos |
|---|---|
| 400 | INVALID_INPUT, INVALID_QUERY, INVALID_FILENAME |
| 404 | KB_NOT_FOUND, DOCUMENT_NOT_FOUND |
| 409 | INGESTION_IN_PROGRESS, INGESTION_CONFLICT |
| 413 | UPLOAD_TOO_LARGE, PAGE_LIMIT, CHARACTER_LIMIT, CHUNK_LIMIT |
| 415 | UNSUPPORTED_MEDIA_TYPE, INVALID_UTF8, BINARY_TEXT_REJECTED |
| 422 | EMPTY_DOCUMENT, CORRUPT_PDF, ENCRYPTED_PDF |
| 502 | INVALID_MODEL_RESPONSE, EMBEDDING_DIMENSION_MISMATCH, INVALID_EMBEDDING |
| 503 | MODEL_UNAVAILABLE, EMBEDDING_UNAVAILABLE, RETRIEVAL_FAILURE, STORAGE_UNAVAILABLE, *_BUSY |
| 504 | MODEL_TIMEOUT, EMBEDDING_TIMEOUT |

Após reserva do documento, erros de ingestão são persistidos em `failureReason` e o upload/reindex devolve o recurso FAILED em 200, inclusive timeout de embedding. Os códigos HTTP de provider aplicam-se ao caminho de query. Parsing/recurso inválido detectado depois da reserva também aparece como FAILED. O cliente deve inspecionar o estado; não presumir sucesso de indexação a partir de HTTP 200.

Uploads duplicados devolvem o documento existente, inclusive PROCESSING/FAILED, sem indexar novamente. Reindex explícito em PROCESSING retorna 409 até a tentativa ter mais de 15 minutos; depois pode recuperar a tentativa interrompida. Não há endpoint de exclusão nesta versão.
