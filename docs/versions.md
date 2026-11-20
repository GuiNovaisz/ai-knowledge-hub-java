# Pesquisa técnica — 20/11/2026

A preferência da missão foi conferida em documentação oficial e resolução de artefatos no Maven Central. Os artefatos GA de Boot/AI efetivamente resolveram e compilaram; não foram substituídos por milestone, RC ou snapshot.

| Componente | Escolha | Evidência/decisão |
|---|---|---|
| Java | alvo 21 LTS | `java.version=21`; CI Temurin 21; ambiente local só tem Temurin 25.0.5 LTS e compilou com `--release 21` |
| Spring Boot | 4.2.0 GA | [requisitos oficiais](https://docs.spring.io/spring-boot/system-requirements.html); Java 21 suportado |
| Spring AI | 2.1.0 GA | [anúncio GA](https://spring.io/blog/2026/11/20/spring-ai-2-1-0-GA-available-now/), construído sobre Boot 4.2 |
| PostgreSQL | major 17 suportado | [política de versões](https://www.postgresql.org/support/versioning/); imagem oficial do projeto pgvector para pg17 |
| pgvector | 0.8.7 | [repositório oficial](https://github.com/pgvector/pgvector), [anúncio PostgreSQL](https://www.postgresql.org/about/news/pgvector-087-released-3392/) |
| Imagem DB | `pgvector/pgvector:0.8.7-pg17-bookworm` | [tags do mantenedor](https://hub.docker.com/r/pgvector/pgvector/tags); sem latest; minor do PostgreSQL é registrado pelo relatório de execução |
| Maven | 3.9.16 | wrapper only-script 3.3.4, distribuição Maven Central |
| PDFBox | 3.0.8 | [metadata Maven oficial](https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox/maven-metadata.xml); escolhido após conferir versão disponível, pois página de pesquisa ainda mostrava 3.0.6 |
| springdoc | 3.2.0 | [documentação oficial](https://springdoc.org/); starter WebMVC API, sem UI/MCP |
| Flyway, Micrometer, Testcontainers, JDBC | geridos pelo BOM Boot 4.2.0 | versões transitivas reproduzíveis via `./mvnw dependency:tree` |

O tag de imagem fixa a versão da extensão e a família PostgreSQL, mas não é digest imutável; o patch de PostgreSQL pode mudar se o mantenedor republicar o tag. O relatório captura `server_version` e `extversion`. Digest pinning é uma melhoria futura se exigida reprodutibilidade binária entre máquinas.

## Integrações e APIs verificadas

- [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) e [embedding](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html): starter `spring-ai-starter-model-ollama`; abstrações `ChatModel`/`EmbeddingModel`; pull automático desabilitado.
- [PGvector integration](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html): `PgVectorStore` está disponível. O projeto usa SQL pgvector próprio para demonstrar publicação transacional, FK composta e isolamento verificável. Não afirma usar `PgVectorStore` no runtime; Flyway é o único dono do schema.
- [ETL/readers/chunking](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html): `PagePdfDocumentReader`, Tika reader e `TokenTextSplitter` avaliados. PDFBox direto permite validar criptografia, limitar páginas e preservar metadata com controle explícito. Chunker próprio usa orçamento de caracteres e fronteiras por página/parágrafo, com overlap documentado; não é token splitter.
- [RAG APIs 2.1](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html): pipeline modular disponível, incluindo `DecisionDocumentFilter`/`DecisionDocumentReranker`. Mantidos como opções futuras após medir custo e benefício; nenhuma cadeia Advisor oculta o pipeline atual.
- [Evaluation APIs](https://docs.spring.io/spring-ai/reference/api/testing.html): `EvaluationRequest` e `RelevancyEvaluator` utilizados no profile opcional. O import resolvido em 2.1 é `org.springframework.ai.chat.evaluation.RelevancyEvaluator`. Fact checking com modelo dedicado foi avaliado e não adicionado como dependência obrigatória.
- [qwen3:1.7b](https://ollama.com/library/qwen3:1.7b) e [nomic-embed-text:v1.5](https://ollama.com/library/nomic-embed-text): disponíveis no catálogo Ollama. Configuráveis por ambiente; tamanho reduzido para demonstração, sem alegação de qualidade validada neste hardware.

O provider de embedding aplica prefixos `search_query:` e `search_document:` adequados ao Nomic selecionado. A matriz de dimensão é validada em cada chamada antes de persistir ou consultar. Trocar modelo exige revisão dos prefixos, threshold, versão de embedding e reindexação.
