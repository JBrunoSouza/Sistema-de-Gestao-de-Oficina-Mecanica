# Evolução: OS rejeitada, usuários, UC04 e UC05

Referência funcional: `Documento_de_requisitos_Engrenar.pdf` deste repositório, conforme orientação do usuário. Não representa validação de critérios adicionais do Trello.

## OS rejeitada

Na ficha da OS, gerente ou atendente escolhe **Reabrir para revisão** ou **Cancelar e devolver peças**, informando motivo. Revisão volta a Aberta, preserva diagnóstico/itens e reservas, limpa a decisão anterior e exige nova aprovação. Cancelamento muda para Cancelada, devolve peças e libera o veículo para outra OS. A transação bloqueia a OS; tentativas repetidas ou concorrentes não devolvem estoque duas vezes. O histórico preserva motivo, responsável, decisão e itens anteriores.

## Usuários e senhas

O menu **Usuários**, exclusivo do gerente, cadastra nome, login e perfil. **Alterar minha senha** exige senha atual e confirmação da nova. Senhas novas têm 12 a 128 caracteres e usam PBKDF2-HMAC-SHA256, 600.000 iterações e salt aleatório de 16 bytes. Login válido atualiza automaticamente hashes SHA-256 antigos, preservando compatibilidade com contas existentes.

O gerente seleciona uma conta e confirma sua própria senha para emitir um código aleatório. O usuário informa esse código em **Esqueci minha senha**. Código expira em 15 minutos, é armazenado apenas como hash e consumido atomicamente. Reemissão ou troca de senha invalida o código anterior. Não há envio de e-mail; recuperação depende de um gerente com acesso. As contas de demonstração continuam disponíveis em bancos novos e devem ter suas senhas trocadas para uso próprio.

## Critérios conferidos

| Documento | Implementação e validação |
|---|---|
| UC04: selecionar OS e exibir reclamação | Ficha da OS com reclamação e diagnóstico; consulta autenticada |
| UC04: diagnóstico obrigatório | Gravação valida texto; inclusão/conclusão de serviço exige diagnóstico |
| UC04: serviços recomendados e valores | Inclusão na OS aberta, quantidades/valores validados e subtotal |
| UC04: conclusão e observações opcionais | Conclusão após aprovação, observações persistidas e histórico consultável |
| UC04: impedir edição encerrada | Estado da OS e perfil verificados no serviço, além da interface |
| UC05: entrada com nome, preço e quantidade | Novo nome cadastra peça; nome existente soma entrada e atualiza preço |
| UC05: selecionar OS, peça e consumo | Consumo vincula item e baixa saldo na mesma transação |
| UC05: estoque insuficiente | Rejeita operação sem alteração parcial; informa saldo disponível |
| UC05: persistência e permissões | Transações, bloqueio de saldo, autorização e histórico de movimentações |

Preço atualizado de uma peça não altera o preço já registrado nos itens da OS. Não foram acrescentados requisitos de fornecedores ou estoque mínimo, ausentes desses casos de uso no documento.

## Banco e reprodução

`database/evolution.sql` adiciona observações, histórico e códigos de recuperação de forma idempotente na inicialização, sem apagar dados. As migrações anteriores permanecem. Execute `mvn clean -Ppostgres-check package` para testes com H2, interface JavaFX real e PostgreSQL temporário, além de gerar o JAR. Os testes não acessam a base de uso do grupo.

Cobertura nova: cancelamento concorrente e repetido, revisão, permissões, rollback, hashes com salts distintos, troca/recuperação/expiração/reemissão, diagnóstico, observações persistentes e entradas/consumos de estoque. A validação PostgreSQL também percorre revisão, cancelamento, cadastro, recuperação e reinicialização.

Resultado em 23/09/2026: **64 testes, zero falhas, zero erros, zero ignorados**, incluindo quatro testes em PostgreSQL 15.19. `mvn -B -Ppostgres-check package` concluiu com `BUILD SUCCESS` e gerou `target/engrenar-1.0.0.jar`. Alterações locais; não aplicadas à base do grupo nem publicadas no GitHub.

Referência de armazenamento: [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
