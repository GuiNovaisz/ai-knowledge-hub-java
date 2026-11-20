# Retrieval baseline — deterministic fixture

Dataset: all 10 golden cases; document-level K=3; 8 answerable cases in retrieval macro averages. Two negative cases are evaluated by abstention gates.

Embedding: concept-v1-768, synthetic deterministic test double. Chat: deterministic source-copying fixture. These results validate SQL/fusion mechanics, **not real model semantic quality**.

Database: 17.12 (Debian 17.12-1.pgdg12+1); pgvector: 0.8.7.

| Strategy | Recall@3 | Precision@3 | MRR | Hit rate |
|---|---:|---:|---:|---:|
| VECTOR | 1.0000 | 0.4167 | 0.9375 | 1.0000 |
| LEXICAL | 1.0000 | 0.4167 | 1.0000 | 1.0000 |
| HYBRID | 1.0000 | 0.4167 | 0.9375 | 1.0000 |

All golden answerability/citation/isolation/injection fixture gates passed. No learned reranker is enabled. Vector remains the generation default until real-model evaluation demonstrates a better choice.

Provenance: [GitHub Actions run 44806252910](https://github.com/GuiNovaisz/ai-knowledge-hub-java/actions/runs/44806252910), code commit `e8d1f8c7cfa79cf364d33e3a1333cc51785d6847`, 2026-11-20. The numbers above were copied from the generated artifact, not estimated. Lexical's higher MRR on this tiny synthetic fixture does not establish a real-world quality advantage.
