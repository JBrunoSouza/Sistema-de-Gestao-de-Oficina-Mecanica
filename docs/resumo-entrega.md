# Resumo completo da entrega

Branch: `codex/correcoes-relatorios`.
Base analisada: `dbf2a60739ee900c4543ce143e77b90f899e0304`.
Referência funcional: documento de requisitos do repositório, conforme solicitado.

## Acesso e inicialização

- Migração 1 → 2 cria contas demonstrativas ausentes em bancos antigos sem substituir contas, senhas ou dados existentes.
- Consultas exigem autenticação e operações verificam o perfil do usuário.
- Navegação e atalhos respeitam permissões; logout remove os atalhos da sessão anterior.
- Script Windows aguarda o PostgreSQL ficar saudável e chama o Maven corretamente.
- Empacotamento do JAR preserva os registros dos drivers JDBC.

## Relatórios

- Acesso exclusivo ao gerente.
- Filtros combinados por período inclusivo de entrada, status, cliente ou placa.
- Quantidade de ordens, quantidade de fechadas e recebido somente nas OS fechadas do resultado.
- Exportação PDF com os mesmos dados e filtros exibidos, cabeçalhos, paginação, totais e quebra de textos longos.
- Alterar filtros bloqueia exportação até reaplicar a consulta.
- Consulta e exportação em segundo plano; gravação em arquivo temporário antes de substituir o destino.

## Ordens rejeitadas

- Gerente e atendente podem reabrir para revisão ou cancelar, informando motivo.
- Revisão preserva diagnóstico e itens, mantém peças e veículo vinculados, limpa a decisão anterior e exige nova aprovação.
- Cancelamento devolve peças ao estoque, libera o veículo e preserva os itens e o histórico.
- Transações e bloqueios impedem devolução duplicada em cancelamentos repetidos ou concorrentes.
- Histórico registra responsável, motivo, orçamento e itens anteriores.

## Usuários e senhas

- Menu de usuários exclusivo do gerente, com cadastro de nome, login, senha e perfil.
- Troca da própria senha exige a senha atual e confirmação da nova.
- Novas senhas têm 12 a 128 caracteres, PBKDF2-HMAC-SHA256 com 600.000 iterações e salt aleatório.
- Hashes SHA-256 antigos são atualizados automaticamente após login válido.
- Gerente confirma sua senha para emitir um código aleatório de recuperação.
- Código vale 15 minutos, tem uso único e apenas seu hash fica armazenado.
- Reemissão e troca de senha invalidam códigos anteriores.
- Recuperação disponível na tela de login; assistida pelo gerente, sem envio de e-mail.

## UC04 — Diagnóstico e serviços

- Critérios conferidos contra o PDF do repositório.
- Diagnóstico obrigatório para inclusão e conclusão de serviços.
- Serviços com valores e conclusão após aprovação do orçamento.
- Observações opcionais de execução persistidas e disponíveis no histórico.
- Validações de estado e perfil também na camada de serviço.

## UC05 — Peças e estoque

- Entrada cadastra peça nova ou soma quantidade e atualiza preço de uma peça com o mesmo nome.
- Atualização de preço não modifica valores já registrados em itens de OS.
- Consumo vincula peça à OS e baixa saldo na mesma transação.
- Estoque insuficiente rejeita a operação sem gravações parciais.
- Reposição, consumo e devolução registram movimentações e saldo resultante.

## Persistência, documentação e testes

- Script idempotente adiciona observações, eventos da OS, movimentações de estoque e recuperação de senha, sem apagar dados.
- README atualizado e documentos específicos de validação adicionados.
- Validação final: **64 testes, zero falhas, zero erros e zero ignorados**, incluindo quatro testes com PostgreSQL real 15.19 e testes com JavaFX real.
- Comando: `mvn -B -Ppostgres-check package`; resultado `BUILD SUCCESS`.
- Pacote gerado: `target/engrenar-1.0.0.jar` (artefato de build, não versionado).
- Testes usam bancos isolados; nenhuma migração foi aplicada à base de uso do grupo.

## Commits de implementação

- `fad6194`: correções de acesso, migração e relatórios com PDF.
- `ba63b86`: resolução de OS rejeitada, usuários e senhas, complementos de UC04/UC05.

Detalhes e limites: [relatórios e acesso](validacao-correcoes-relatorios.md) e [evolução funcional](validacao-evolucao.md). O documento da primeira entrega descreve limites históricos que foram resolvidos na segunda.

## Correção posterior — permissões exclusivas

Conforme esclarecimento do grupo: somente mecânico registra diagnóstico e inclui, remove ou conclui serviços. Somente gerente (administrador) cadastra, repõe, consome ou remove peças e consulta movimentações. O atendente não executa essas operações. Interface e camada de serviço aplicam as mesmas regras; remover item depende do tipo selecionado. A devolução automática no cancelamento continua sendo parte da transação de cancelamento da OS, sem conceder edição manual de estoque ao atendente.

Testes de fluxo foram ajustados para alternar explicitamente entre os perfis responsáveis. Testes adicionais exercitam bloqueios, preservação dos dados após negativa e botões da ficha para os três perfis.

Validação da correção: `mvn -B -Ppostgres-check package` passou com 67 testes (incluindo PostgreSQL real). Após acrescentar as três verificações de interface por perfil, `mvn -B -Dtest=JavaFxAuditTest test` passou com os 10 testes de interface. Nenhuma falha, erro ou teste ignorado. São 70 casos distintos validados entre as duas execuções.
