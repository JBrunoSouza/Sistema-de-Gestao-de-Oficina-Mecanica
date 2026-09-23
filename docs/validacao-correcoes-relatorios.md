# Correções de acesso e relatórios — 23/09/2026

Base: `dbf2a60739ee900c4543ce143e77b90f899e0304` da main.

## Entrega

- Migração 1 → 2 provisiona as contas demonstrativas ausentes no banco antigo. O marcador é bloqueado durante a migração; contas, senhas e dados existentes são preservados. Contas não são recriadas a cada reinício depois de concluir a migração.
- Consultas de negócio exigem autenticação. Os perfis mantêm as leituras necessárias à ficha de OS; navegação em cadastros e relatórios tem verificação própria.
- Ctrl+V não é registrado para mecânico. Logout remove os aceleradores da Scene. Formulários de cadastros não abrem para o perfil mecânico; edição de diagnóstico/itens respeita o papel e estado da OS.
- Relatórios para gerente com período de entrada inclusivo, status e pesquisa por cliente/placa. Consultas parametrizadas, pesquisa literal de `%`/`_`, total recebido apenas das OS fechadas filtradas e estado vazio explícito.
- PDF A4 paisagem com filtros, totais, cabeçalhos repetidos, paginação e quebra de nomes longos. O arquivo usa a mesma fotografia dos dados exibidos. Alterar filtros desabilita a exportação até reaplicar.
- Consulta/exportação executam em segundo plano. Gravação do PDF utiliza arquivo temporário antes de substituir o destino para preservar um arquivo anterior em caso de falha na geração.
- Inicialização pelo script aguarda a saúde do PostgreSQL. O JAR mescla os registros de serviços JDBC para preservar os drivers H2 e PostgreSQL.

## Evidência de validação

Ambiente Windows, Java 21.0.8, Maven 3.9.11, release de compilação 17.

`mvn -B clean -Ppostgres-check package`: **52 testes, zero falhas, zero erros, zero ignorados**.

| Bateria | Testes | Resultado |
|---|---:|---|
| Regras de negócio recuperadas | 26 | Passaram |
| Autenticação, migração e permissões | 10 | Passaram |
| JavaFX: login, perfis, atalhos, logout e filtros do relatório | 7 | Passaram |
| Relatórios, PDF e preservação de contas na migração | 6 | Passaram |
| PostgreSQL real 15.19 | 3 | Passaram |

Os quatro testes que falhavam na auditoria anterior agora passam. O teste PostgreSQL percorre cadastro, abertura, diagnóstico, itens, decisão, conclusão, pagamento/fechamento, liberação do veículo e relatório filtrado; também testa upgrade, preservação da senha existente, rollback e restrição de perfil.

Depois do ajuste exclusivo do empacotamento JDBC, o JAR foi novamente gerado e testado isoladamente, sem depender do classpath do Maven: descoberta do driver PostgreSQL, inicialização H2 temporária, login, relatório filtrado e PDF passaram.

A tela foi renderizada e inspecionada. O PDF de teste tem 75 ordens com nomes longos, 13 páginas, total de R$ 9.037,50; os testes conferem a última OS e o cabeçalho em todas as páginas. Primeira e última páginas renderizadas foram inspecionadas sem sobreposição/cortes.

## Como reproduzir

```sh
mvn clean test
mvn clean -Ppostgres-check test
mvn package
```

Os testes JavaFX precisam de sessão gráfica. O perfil PostgreSQL baixa binários pelo Maven, cria bancos temporários em porta livre e encerra o servidor ao terminar. Não usa a instância da aplicação nem requer Docker.

Para executar a aplicação com o banco do projeto, use `iniciar.cmd` com Docker Desktop ativo, ou inicie o banco com Docker Compose, aguarde ficar saudável e rode `mvn javafx:run`.

## Limites conhecidos na entrega anterior

Os dois primeiros itens abaixo foram resolvidos na [evolução subsequente](validacao-evolucao.md).

- As contas e hashes SHA-256 demonstrativos foram mantidos para compatibilidade. Gestão de usuários, recuperação e troca de senha permanecem fora desta entrega.
- A OS rejeitada ainda exige uma decisão futura sobre cancelamento/revisão/liberação de peças e veículo. O comportamento anterior foi preservado; não foi criado um fluxo de cancelamento sem critérios de negócio.
- Os relatórios usam a **data de entrada**, não a data de recebimento. O campo "valor decidido" é vazio até a decisão de orçamento, e ordens não fechadas não compõem o recebido.
- Testes de PostgreSQL real passaram; o comando Docker Compose e uma instalação em outro computador não foram executados neste ambiente. O script requer Compose com suporte a `--wait`.
- Não foi feito ensaio com 100 mil registros. O PDF utiliza fontes padrão, com acentos portugueses; caracteres fora do conjunto suportado são representados por `?`.

## Referências técnicas

- [Apache PDFBox 3 — API e migração](https://pdfbox.apache.org/3.0/migration.html).
- [Zonky Embedded PostgreSQL — testes com servidor real](https://github.com/zonkyio/embedded-postgres).

Esta entrega está em uma branch local; não foi publicada no GitHub nem aplicada à base de uso do grupo.
