# Engrenar

Entrega parcial de Engenharia de Software II — UNIVASF, 2026.2. Desktop Java 17+, JavaFX e PostgreSQL com Docker. 

## Executar

Desenvolvimento: **JDK 17+ e Maven 3.9+**.

```sh
docker compose up -d
mvn clean compile exec:java
```

Para gerar o executável com todas as dependências:

```sh
mvn clean package
java -jar target/engrenar-1.0.0.jar
```

## Dados locais

- Schema: `database/schema.sql`. Dados fictícios: `database/seed.sql`.

## Entrega funcional

| Fluxo | Disponível |
|---|---|
| Cadastrar veículo | Campos obrigatórios, cliente existente, km não negativo, placa única normalizada; exibe o veículo duplicado; abre cadastro de cliente e retorna com ele selecionado. |
| Abrir OS | Cliente e veículo correspondentes, reclamação, data válida, km e responsável; número automático, status Aberta; bloqueia OS ativa informando número; atalhos para cadastros. |
| Orçamento | Itens reais, subtotais e total; bloqueio sem itens; aprovação/rejeição com data/hora, responsável e total persistidos. |
| Apoio | Cadastro de clientes, diagnóstico, serviços, cadastro/reposição/consumo de peças com saldo validado, remoção de item com devolução ao estoque, conclusão de serviço e fechamento com pagamento/retirada. |

Os formulários marcam em vermelho o primeiro campo inválido e mantêm os valores para correção. Na ficha da OS, registre itens em **Diagnóstico e itens**, depois abra **Orçamento**. Quantidades são inteiras; valores aceitam `120,50` ou `120.50`. Datas: `dd/mm/aaaa`. `Esc` fecha cadastros; `Ctrl+O` abre OS e `Ctrl+V` abre veículos.

## Limites desta entrega

- Login, perfis e hash de senhas ainda não implementados. Responsáveis são informados nominalmente, sem identidade autenticada.
- Diagnóstico/serviços simplificados: texto de diagnóstico e marcação de conclusão; sem observações separadas, histórico ou edição de cadastros.
- Relatório mostra a lista atual e faturamento de OS fechadas; sem filtro de período e exportação.
- Uma decisão de orçamento congela itens/valores. Revisão, cancelamento e reabertura não implementados. **OS rejeitada continua ativa**, bloqueando outra OS para o veículo e mantendo peças vinculadas. Fechamento exige aprovação e serviços concluídos.
- Interface escura/laranja com telas simplificadas;
