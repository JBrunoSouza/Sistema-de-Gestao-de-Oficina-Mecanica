# Escopo e fontes do Engrenar

Base: pedido detalhado desta entrega, `DocumentoRequisitos2026_2.docx` enviado pelo grupo (versão até 11/09/2026, v5) e `Projeto_09 (1).docx` do professor. Ambos foram lidos antes da implementação. A prioridade é UC02, UC03 e UC06; telas de apoio permitem executá-los.

- Repositório: https://github.com/JBrunoSouza/Sistema-de-Gestao-de-Oficina-Mecanica
- Documento online recuperado do histórico: https://docs.google.com/document/d/1L0PujZaA2Wess67LRPREpIqRUzjOUB-7/edit
- Figma recuperado do histórico: https://www.figma.com/design/MO7w5FSEGOXDROfLYJo8UB/Engrenar
- Prazo: código parcialmente funcionando e algumas telas para sexta-feira; ênfase final nos três requisitos destacados.

O conteúdo foi conferido nos DOCX locais e no pedido atual. Não houve nova auditoria do documento online ou de todos os frames do Figma nesta entrega.

| Caso/regra | Implementação | Verificação |
|---|---|---|
| UC02 / RF02 | Cliente obrigatório, placa normalizada, retorno de duplicata, cadastro de cliente aninhado | Banco e interface |
| UC03 / RF03 | Cliente/veículo, número automático, OS Aberta, campos obrigatórios e uma OS ativa | Banco, concorrência e interface |
| UC06 / RF06 | Valores por itens persistidos, recusa sem itens, decisão com data e responsável | Banco e interface |
| RN01 | UNIQUE na placa e validação no serviço | Placas normalizadas/repetidas |
| RN02 | Itens/diagnóstico exigem OS Aberta; conclusão exige Aprovado | Alteração de OS aprovada/fechada bloqueada |
| RN03 | Baixa em transação com bloqueio da peça e validação de saldo | Estoque insuficiente/devolução |
| RN04 | FK do cliente e FK composta veículo/cliente | Cliente inexistente/proprietário incompatível |
| RN05 | Conclusão e fechamento exigem aprovação | Bloqueios e fechamento |

## Decisões da entrega parcial

- Java 17/Swing e H2 em arquivo conforme pedido atual. Docker/PostgreSQL original preservado, mas dispensável nesta versão.
- Serviço concentra validações, valores, estados e transações. UI apresenta erros e navegação.
- `active_order` reserva o veículo até fechamento. Rejeição não equivale a cancelamento: liberar veículo/peças nesse caso requer um fluxo futuro explícito.
- Decisão congela itens para evitar mudanças silenciosas de orçamento aprovado. Revisões com histórico ficam fora da entrega.
- Responsável é texto obrigatório. Autenticação, perfis e hash de senhas permanecem pendentes.
- Número de OS usa identity; lacunas após transações revertidas são permitidas. Exemplos não são mocks.
