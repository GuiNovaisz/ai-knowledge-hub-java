# AI Knowledge Hub Java

**0.1.0-dev · Grounded RAG Foundation**

**READY FOR REVIEW** · [PR #1 → dev](https://github.com/GuiNovaisz/ai-knowledge-hub-java/pull/1) · [verificação e limites](docs/review.md) · [baseline medida](docs/baseline-report.md)

Backend Java de conhecimento com pipeline explícito de ingestão e RAG: documentos → parsing → normalização → chunks → embeddings → PostgreSQL/pgvector → retrieval → filtros → contexto → geração → validação de citações.

Uma pergunta sem evidência suficiente recebe `INSUFFICIENT_EVIDENCE` antes de chamar o chat. As citações são derivadas dos chunks selecionados pelo backend. O modelo não fornece quotes nem IDs de documentos livremente.

## Execução local

Requisitos: JDK 21 LTS, Docker com daemon ativo e Ollama. O Maven Wrapper baixa Maven 3.9.16. Nenhuma chave ou API paga é necessária.

```powershell
docker compose up -d --wait
ollama pull nomic-embed-text:v1.5
ollama pull qwen3:1.7b
.\mvnw.cmd spring-boot:run
```

Ollama deve estar disponível em `http://localhost:11434`. Não há download automático de modelos no startup. O chat sugerido ocupa aproximadamente 1,4 GB de download e o embedding aproximadamente 274 MB; RAM e latência dependem do contexto, da quantização e do hardware. Nenhum benchmark de hardware foi realizado.

No Linux/macOS: `chmod +x mvnw && ./mvnw spring-boot:run`. A API escuta em `127.0.0.1:8080`. OpenAPI: `/v3/api-docs`; saúde: `/actuator/health`; métricas: `/actuator/prometheus`.

**A API não tem autenticação.** Knowledge bases isolam o corpus solicitado na consulta, mas não representam autorização de acesso: qualquer cliente local pode escolher qualquer KB. Não exponha a API ou o PostgreSQL publicamente. O projeto não oferece RBAC, upload remoto por URL ou ações ao modelo.

## Exemplo

```powershell
$base = 'http://127.0.0.1:8080/api/v1/knowledge-bases'
$kb = Invoke-RestMethod $base -Method Post -ContentType 'application/json' -Body '{"name":"Manual","description":"Demonstração"}'
$file = Get-Item 'evaluation/corpus/returns.md'
Invoke-RestMethod "$base/$($kb.id)/documents" -Method Post -Form @{file=$file}
$body = @{query='What is the refund window?';topK=5} | ConvertTo-Json
Invoke-RestMethod "$base/$($kb.id)/query" -Method Post -ContentType 'application/json' -Body $body
```

`topK` é obrigatório, entre 1 e 20. Upload é síncrono; sua resposta contém o estado do documento. Uma falha de parsing, embedding ou indexação retorna a identidade em `FAILED` e `failureReason`, permitindo retry explícito. Dados inválidos rejeitados antes da reserva recebem 4xx. Consulte [contratos](docs/api.md).

## Verificação e avaliação

```powershell
.\mvnw.cmd test     # unitários; nenhum modelo/DB externo
.\mvnw.cmd verify   # unitários + Testcontainers, exige Docker ativo
```

`verify` nunca ignora silenciosamente os testes de banco por falta de Docker. Usa pgvector real, Flyway e embeddings/chat determinísticos. Gera `target/evaluation/baseline.md` comparando VECTOR, LEXICAL e HYBRID em todas as perguntas answerable do corpus versionado, com casos negativos medidos separadamente. Veja [avaliação](docs/evaluation.md).

Para a avaliação opcional com Ollama real, use um banco de desenvolvimento e modelos já instalados:

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/ai-knowledge-hub-java-0.1.0-dev.jar --spring.profiles.active=evaluation
```

Esse job cria duas KBs de avaliação novas, preserva o corpus existente, processa os dez casos e grava `target/evaluation/real-model.json`; não limpa o banco. O relatório inclui Recall/Precision/MRR/Hit Rate por caso/estratégia, gates de answerability/citações e `RelevancyEvaluator` do Spring AI como juiz probabilístico complementar. Erros e gates falsos aparecem no relatório e devem ser revisados; a execução do job não significa que os gates passaram.

## Configuração

| Variável | Padrão | Finalidade |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/knowledgehub` | Banco |
| `DB_USER` | `knowledgehub` | Usuário local |
| `DB_PASSWORD` | `local-development-only` | Segredo por ambiente |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Provider local |
| `AI_CHAT_MODEL` | `qwen3:1.7b` | Chat |
| `AI_EMBEDDING_MODEL` | `nomic-embed-text:v1.5` | Embedding 768 dimensões |
| `SERVER_ADDRESS` | `127.0.0.1` | Bind local |
| `HUB_MINIMUM_SCORE` | `0.65` | Threshold experimental de cosseno |
| `HUB_PROVIDER_TIMEOUT_SECONDS` | `60` | Deadline por chamada |
| `HUB_CONTEXT_CHARACTERS` | `6000` | Orçamento de evidência |

Os demais limites estão em `application.yml`. O limite HTTP é 5 MiB por arquivo/6 MiB por request. Não basta trocar o nome do embedding: altere explicitamente `hub.embedding-version`, mantenha a dimensão do schema e reindexe os documentos. Outra dimensão exige migração e reindexação; a v0.1 falha na validação para dimensões diferentes de 768. Não misture versões semânticas de embedding no mesmo corpus ativo.

O domínio conhece somente `EmbeddingProvider`, `GroundedChat`, `ChunkRetriever` e `DocumentReranker`. Os adapters usam `EmbeddingModel` e `ChatModel` do Spring AI. Outro provider requer um adapter/configuração próprio e prefixos adequados ao modelo; nenhum profile cloud é entregue nesta milestone.

## Decisões e limites

- Busca exata de cosseno como baseline; nenhuma alegação de ganho de ANN.
- FTS PostgreSQL `simple`, candidatos separados e RRF `Σ 1/(60+rank)` disponível no endpoint técnico. Geração usa VECTOR até avaliação com modelos reais justificar outra escolha.
- `DocumentReranker` tem baseline identidade; não existe reranker aprendido habilitado ou ganho medido de reranking.
- Chunks por página, parágrafo/word boundary, 1.200 caracteres, overlap de até 180. Orçamento de contexto por caracteres, conservador e independente de tokenizer; não é uma contagem exata de tokens.
- Citações válidas demonstram proveniência. Não provam que toda afirmação é semanticamente suportada. Conflitos em linguagem natural dependem da geração e são avaliados separadamente.
- Prompt injection é **mitigated**, com risco residual; veja [segurança](docs/security.md).
- Sem OCR, exclusão, UI, agentes, tools ou loops autônomos nesta versão.

Veja [arquitetura](docs/architecture.md), [pesquisa de versões](docs/versions.md), [avaliação](docs/evaluation.md) e [checklist de revisão](docs/review.md). Licença MIT.
