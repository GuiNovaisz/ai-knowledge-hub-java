# Segurança e risco residual

Esta fundação foi desenhada para demonstração local sem autenticação, com bind em loopback. KB isolation é um limite de consulta e integridade, **não um mecanismo de autorização**. Um serviço multiusuário precisa autenticação, associação de principal à KB, quotas, proteção de rede e gestão de segredo próprios.

## Upload e recursos

- Limite HTTP 5 MiB por arquivo, 6 MiB por request. Texto extraído: 200.000 caracteres, até 100 páginas e 300 chunks.
- Só TXT/MD UTF-8 e PDF. A extensão é conferida contra MIME declarado e conteúdo: assinatura `%PDF-`, decoder UTF-8 estrito e rejeição de controles binários em texto. MIME ausente/desconhecido é rejeitado.
- Filename com separadores, `..`, controles ou tamanho excessivo é rejeitado; caracteres restantes são saneados. Não é convertido em caminho local.
- PDFBox lê texto por página, recusa PDF criptografado, interrompe extração ao exceder caracteres e não executa JavaScript, links, anexos ou OCR. PDFs vazios/corrompidos falham.
- Queries até 2.000 caracteres, topK até 20, candidatos até 40 por ramo; contexto limitado; resposta do modelo até 12.000 caracteres, até 20 claims de 1.500 caracteres.

Limites de bytes/páginas/texto reduzem abuso, mas **não são sandbox de parser**. PDFs comprimidos patológicos podem consumir CPU/RAM antes de atingir os limites de extração; execute com orçamento de memória (por exemplo `java -Xmx512m`) em ambiente local confiável. Isolamento de parsing em processo/container, timeout de parsing e quotas globais ficam como trabalho futuro antes de upload público não confiável. O Compose limita a memória do banco a 512 MiB. Nenhuma alegação de proteção completa contra decompression bombs.

## Prompt injection: mitigated

1. Evidência é dado não confiável, não instrução de sistema.
2. Policy de sistema, evidência e pergunta têm mensagens distintas.
3. Filtro determinístico descarta chunks com padrões explícitos de instrução maliciosa em PT/EN.
4. Source IDs são criados somente após filtros e orçamento; KB é verificada novamente.
5. Quotes são os textos reais dos chunks, e citações desconhecidas invalidam a resposta inteira com 502.
6. Chat não recebe tools, filesystem, shell, rede ou ações.
7. Sem evidência elegível, o backend se abstém antes da geração.

O filtro pode ter falsos positivos e falsos negativos. Pode perder ataques ofuscados, multilíngues ou indiretos e pode descartar texto legítimo que descreve ataques. É uma defesa complementar. Um modelo ainda pode seguir uma instrução maliciosa não detectada ou produzir uma afirmação não suportada usando um source ID válido. Proveniência não é prova automática de entailment.

O corpus versiona uma instrução maliciosa e uma fonte legítima de uptime. Os testes determinísticos provam que o chunk malicioso não chega ao chat. A avaliação real mede comportamento do modelo separadamente.

## APIs novas avaliadas

Spring AI 2.1 fornece `DecisionDocumentFilter` e `DecisionDocumentReranker`. Não foram habilitados nesta baseline: adicionariam chamadas probabilísticas por documento e dependência de um juiz cuja calibração ainda não foi medida neste corpus. A documentação permite fail-open em alguns cenários; um perfil futuro voltado a segurança deve optar por fail-closed e testar falhas/recusas explicitamente. O port `DocumentReranker` permite introduzir e medir um reranker depois.

## Segredos e dados

Nenhuma chave cloud é necessária ou versionada. Credencial padrão do Compose é exclusivamente de desenvolvimento local. Originais, chunks e embeddings são dados persistidos no volume PostgreSQL e devem receber proteção e backup coerentes com o corpus. O projeto não promete criptografia por aplicação, eliminação de PII ou exclusão completa nesta milestone. Não inclua documentos pessoais nos fixtures públicos.
