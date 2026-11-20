# Review — v0.1.0 Grounded RAG Foundation

Versão do projeto: **0.1.0-dev**. Este arquivo registra o escopo revisável, não representa promoção para release.

**Estado: READY FOR REVIEW.** O [CI com Java 21](https://github.com/GuiNovaisz/ai-knowledge-hub-java/actions/runs/44806252910) passou no código `e8d1f8c`: 17 unitários e 10 integrações, zero falhas/erros/skips. PostgreSQL 17.12 e pgvector 0.8.7 reais. O [relatório medido](baseline-report.md) foi recuperado do artifact dessa execução.

Build inicial: Temurin 25.0.5 LTS com alvo `--release 21`, unitários e empacotamento aprovados; integração inicial executada no CI. Na missão seguinte, Docker local e Ollama foram disponibilizados em segundo plano: os 27 testes passaram também localmente e a [avaliação real](real-model-evaluation.md) completou 10/10 casos após correções restritas de geração. A baseline determinística original permanece separada e inalterada.

## Implementado

- Java 21 target, Boot 4.2.0, AI 2.1.0, wrappers, Compose, Flyway e Actions.
- KB explícita, upload TXT/MD/PDF, metadata, SHA-256 e idempotência concorrente.
- Parser/normalização/chunker/embedding separados; publicação atômica, reindex e token de tentativa.
- pgvector exato, FTS e RRF, filtro por KB/documentId e metadata de retrieval.
- Evidence filtering, contexto limitado e diverso, abstention antes do chat.
- Structured claims, IDs controlados e quotes do backend; contrato de conflito.
- Erros categorizados, Micrometer, ECS, correlationId e OpenAPI.
- Corpus/golden, métricas, unitários, Testcontainers e avaliação Spring AI opt-in.

## Critério para READY FOR REVIEW

`./mvnw verify` precisa passar com Java 21 e Docker, incluindo os gates de pgvector real. O relatório medido deve estar anexado ao PR ou versionado após execução. Se o ambiente impedir qualquer gate, registrar o impedimento explicitamente; não apresentar a missão como completa.

O profile de modelo real é avaliação complementar opcional e não bloqueia build. Até ser executado, a qualidade semântica real, calibragem de threshold e comportamento com conflitos devem permanecer como limitações declaradas.

## Fora desta milestone

Autenticação/RBAC, serviço público, frontend, OCR, exclusão, ANN tuning, reindex distribuído com heartbeat, sandbox forte de PDF, cloud profile, reranker aprendido e agente/tools.

## Git

Genealogia esperada: commit inicial MIT em `main`; `dev` deriva desse commit; `feature/grounded-rag-foundation` deriva de `dev` e contém implementação. PR contra `dev` para revisão. Não efetuar merge, tag ou GitHub Release nesta missão.
