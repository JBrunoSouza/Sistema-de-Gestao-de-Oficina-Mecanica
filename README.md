# Engrenar

Entrega parcial de Engenharia de Software II — UNIVASF, 2026.2. Desktop Java 17+, Swing e H2 em arquivo local. Os três fluxos prioritários usam persistência real: cadastrar veículo, abrir OS e elaborar/registrar orçamento.

## Executar

Desenvolvimento: **JDK 17+ e Maven 3.9+**.

```sh
mvn clean compile exec:java
```

Para gerar o executável com todas as dependências:

```sh
mvn clean package
java -jar target/engrenar-1.0.0.jar
```

No Windows, após compilar, dê dois cliques em `iniciar.cmd`. Para levar a outro computador, copie o JAR e tenha Java 17+ instalado. A primeira compilação baixa dependências; **o JAR pronto funciona offline, sem Maven, Docker ou instalação de banco**. Os arquivos Docker existentes são da estrutura anterior e não são usados nesta entrega.

## Dados locais

- Banco criado automaticamente em `data/engrenar.mv.db`, relativo à pasta de execução. Execute sempre da mesma pasta para usar a mesma base.
- Primeira abertura: 3 clientes, 3 veículos, 3 peças e 4 OS — Aberta, Aprovado, Rejeitado e Fechada. Exemplos não são reinseridos ao reiniciar.
- Schema: `database/schema.sql`. Dados fictícios: `database/seed.sql`.
- Backup: feche o aplicativo e copie a pasta `data`. Use somente uma instância por arquivo de banco.
- Base independente para demonstração: `java -Dengrenar.dataDir=data-demo -jar target/engrenar-1.0.0.jar`.

## Entrega funcional

| Fluxo | Disponível |
|---|---|
| Cadastrar veículo | Campos obrigatórios, cliente existente, km não negativo, placa única normalizada; exibe o veículo duplicado; abre cadastro de cliente e retorna com ele selecionado. |
| Abrir OS | Cliente e veículo correspondentes, reclamação, data válida, km e responsável; número automático, status Aberta; bloqueia OS ativa informando número; atalhos para cadastros. |
| Orçamento | Itens reais, subtotais e total com `BigDecimal`; bloqueio sem itens; aprovação/rejeição com data/hora, responsável e total persistidos. |
| Apoio | Cadastro de clientes, diagnóstico, serviços, cadastro/reposição/consumo de peças com saldo validado, remoção de item com devolução ao estoque, conclusão de serviço e fechamento com pagamento/retirada. |

Os formulários marcam em vermelho o primeiro campo inválido e mantêm os valores para correção. Na ficha da OS, registre itens em **Diagnóstico e itens**, depois abra **Orçamento**. Quantidades são inteiras; valores aceitam `120,50` ou `120.50`. Datas: `dd/mm/aaaa`. `Esc` fecha cadastros; `Ctrl+O` abre OS e `Ctrl+V` abre veículos.

## Limites desta entrega

- Login, perfis e hash de senhas ainda não implementados. Responsáveis são informados nominalmente, sem identidade autenticada.
- Diagnóstico/serviços simplificados: texto de diagnóstico e marcação de conclusão; sem observações separadas, histórico ou edição de cadastros.
- Relatório mostra a lista atual e faturamento de OS fechadas; sem filtro de período, exportação ou teste com 100 mil registros.
- Uma decisão de orçamento congela itens/valores. Revisão, cancelamento e reabertura não implementados. **OS rejeitada continua ativa**, bloqueando outra OS para o veículo e mantendo peças vinculadas. Fechamento exige aprovação e serviços concluídos.
- Estoque sem fornecedores, mínimo ou histórico independente de movimentações. Pagamento é registro presencial, sem integração financeira.
- Interface escura/laranja com telas simplificadas; não é reprodução exata do Figma.

## Verificar e desenvolver

```sh
mvn test
# Testes adicionais do Swing real; requer sessão gráfica:
mvn -Dengrenar.uiTest=true test
```

Testes usam H2 real e bases isoladas, incluindo arquivo em disco, reinicialização, cenários alternativos, transações e abertura concorrente. Testes de interface geram imagens em `target/screenshots`. Nenhum teste usa a pasta `data` da aplicação.

Camadas em `src/main/java/br/edu/univasf/engrenar`: `model` (dados), `dao` (JDBC/transações), `service` (regras) e `view` (Swing). SQL com integridade referencial e parâmetros JDBC; migração futura exige adaptar driver, inicialização e diferenças de dialeto.

Veja `docs/roteiro-apresentacao.md` para a demonstração e `docs/escopo-e-fontes.md` para rastreabilidade.
