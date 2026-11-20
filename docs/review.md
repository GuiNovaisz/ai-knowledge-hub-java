# Review — v0.1.0 Grounded RAG Foundation

Versão Maven/OpenAPI: **0.1.0**. Este registro reúne os gates da fundação e a preparação de release; as evidências históricas abaixo permanecem identificadas por execução/commit.

**Fundação validada.** O [PR #1](https://github.com/GuiNovaisz/ai-knowledge-hub-java/pull/1) foi integrado em dev por merge commit `30b789b95c2e0aed789ee0415b0cc6d996874e3d`, sem conflitos. A validação pré-merge repetiu os 17 unitários e 10 integrações, zero falhas/erros/skips, com PostgreSQL/pgvector real. O [relatório determinístico](baseline-report.md) mantém os resultados e a proveniência da execução original.

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

## Critérios de validação

`./mvnw verify` precisa passar com Java 21 e Docker, incluindo os gates de pgvector real. O relatório medido deve estar anexado ao PR ou versionado após execução. Se o ambiente impedir qualquer gate, registrar o impedimento explicitamente; não apresentar a missão como completa.

O profile de modelo real é avaliação complementar opt-in e não bloqueia build. A execução real, seus resultados e limitações estão preservados no [relatório](real-model-evaluation.md); Ollama não é requisito do CI.

## Fora desta milestone

Preparação v0.1.0: `mvnw.cmd verify` repetiu 17 unitários + 10 integrações, zero failures/errors/skips. Clean-start Compose em projeto/volume/rede exclusivos confirmou PostgreSQL 17.12 saudável, pgvector 0.8.7, Flyway V1 aplicada, corpus vazio, startup do JAR 0.1.0, health UP e OpenAPI 0.1.0. A asserção de versão do teste OpenAPI foi promovida sem alterar a quantidade de testes. CI cobre pushes de release/main além dos gates existentes. Não houve alteração funcional RAG ou repetição ritual da avaliação Ollama.

Autenticação/RBAC, serviço público, frontend, OCR, exclusão, ANN tuning, reindex distribuído com heartbeat, sandbox forte de PDF, cloud profile, reranker aprendido e agente/tools.

## Git

Genealogia: commit inicial MIT em `main`; feature integrada integralmente em `dev` pelo PR #1; preparação `release/0.1.0 → dev → main` por PRs e merge commits. A tag e a publicação v0.1.0 dependem dos gates da missão de release. Consulte as [notas](releases/v0.1.0.md) e o histórico de PRs para o estado publicado, sem confundir as execuções históricas da fundação com a promoção de versão.
