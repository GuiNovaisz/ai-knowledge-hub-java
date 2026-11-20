# Avaliação reproduzível

## Dataset

Os nove documentos de `evaluation/corpus/` são fictícios e escritos para este projeto. Os dez casos de `golden-questions.json` cobrem evidência explícita, paráfrase, resposta distribuída, código exato, distractor, ausência, conflito, injection e isolamento. Cada caso registra KB, documentos/chunks relevantes, answerability e citações esperadas.

Os identificadores de chunk no golden são lógicos (`filename:chunkIndex`), pois UUIDs reais variam a cada ingestão. O benchmark desta versão é **document-level**, deduplicando o primeiro aparecimento de cada documento. Não alega métricas de chunk-level. Nenhuma pergunta é removida para favorecer uma estratégia.

## Métricas

Para cada pergunta answerable com conjunto relevante R e ranking deduplicado nos primeiros K documentos:

- Recall@K = documentos relevantes recuperados / `|R|`.
- Precision@K = documentos relevantes recuperados / `K`; posições vazias contam como não relevantes.
- Reciprocal rank = `1/rank` do primeiro relevante, ou zero; MRR é sua média.
- Hit rate = fração de perguntas com pelo menos um relevante nos primeiros K.

O relatório usa médias macro, K=3. Casos sem documentos relevantes não entram nos denominadores de recall, pois `|R|=0`; são avaliados por abstention, ausência de fontes e isolamento. Thresholds e orçamento são gates posteriores ao retrieval técnico.

A execução aprovada está preservada em [baseline-report.md](baseline-report.md), com link do CI e commit de código. Na fixture medida, VECTOR e HYBRID empataram e LEXICAL obteve MRR maior. Isso não calibra um modelo real nem comprova superioridade geral; mantém-se a baseline vetorial conservadora e hybrid somente para comparação técnica.

## Dois níveis separados

`./mvnw verify` executa PostgreSQL/pgvector real via Testcontainers. Os embeddings são um test double explícito de 768 dimensões, com conceitos conhecidos e vetores ortogonais. O chat é um test double que copia fontes e expõe status determinístico para conflito. Isso prova SQL, fusão, estados, proveniência e lógica de gates; **não prova semântica real, fidelidade de geração ou resistência geral do modelo a injection**.

O teste percorre todos os casos answerable para VECTOR/LEXICAL/HYBRID e escreve `target/evaluation/baseline.md` com valores medidos, modelo fixture e versões de banco/extensão. Não há comparação de reranking aprendido: o port usa identidade e não representa uma estratégia adicional.

O profile `evaluation` roda Ollama real em todas as perguntas, grava os rankings e métricas por caso, registra gates e chama `RelevancyEvaluator` complementarmente para respostas com citações. O modelo juiz atual é o chat configurado; isso introduz viés de autoavaliação. O relatório armazena resultados de juiz separados dos gates, sem transformá-los em uma prova determinística. Um juiz dedicado pode ser acrescentado depois. Erros são preservados por caso.

## Gates de CI

- Constraints e consulta de isolamento por KB, incluindo filtro de documentId de outra KB.
- Pergunta de segredo somente em B feita em A → nenhuma evidência suficiente e nenhum chat.
- Pergunta sem resposta → abstention antes da geração.
- Citation ID desconhecido/KB incorreta → resposta inválida; quote vem do chunk.
- Falha no segundo embedding → FAILED e zero chunks parcialmente publicados.
- Falha no commit de ativação → rollback da versão inteira.
- Retry/reindex troca versão sem misturar chunks; compare-and-set impede tentativa obsoleta.
- Uploads concorrentes do mesmo checksum produzem uma identidade e uma indexação.
- Chunk injection é excluído; o fixture não recebe sua instrução; documento legítimo segue utilizável.
- Conflito requer duas fontes e status explícito no contrato; o fixture prova o contrato, não detecção semântica universal.
- PDF real, UTF-8, corrupção, limites, dimension mismatch e timeout de provider.

## Escolha da baseline

VECTOR é a estratégia de geração inicial por simplicidade e porque o benchmark sintético não justifica preferência de qualidade por hybrid. FTS/hybrid ficam disponíveis para comparação pelo endpoint técnico. RRF usa constante 60 documentada, sem pesos ajustados ao corpus. Reavaliar com embeddings reais e todos os casos antes de promover hybrid ou adicionar reranker. Nenhuma melhoria de performance ou qualidade é alegada sem relatório correspondente.
