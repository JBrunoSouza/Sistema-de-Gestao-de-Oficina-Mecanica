# Preparação da apresentação

## Compatibilidade da versão

Comparação: versão anterior `8fc3278` e correção de interface `355cde2`.

- `pom.xml`, dependências, `docker-compose.yml`, `iniciar.cmd`, scripts SQL e conexão do banco permanecem iguais entre essas versões.
- Compilação com `--release 17`; classe MainWindow verificada com major version 61 (Java 17). Isso verifica o alvo de compilação, não substitui uma execução em JDK 17.
- Ambiente efetivamente usado: Windows 11 x64, JDK 21.0.8, Maven 3.9.11 e PostgreSQL 15.19 local.
- Docker não está disponível no terminal desta máquina. A configuração Docker foi comparada, mas a inicialização via Docker não foi executada nesta revisão.
- As mudanças de abertura não alteram o esquema nem exigem apagar/recriar o banco. Testes usam bases isoladas.

## Resultado da validação em 25/09/2026

`mvn -B -Ppostgres-check test`: **76 testes, zero falhas, zero erros, zero ignorados**. Inclui 12 testes JavaFX e 4 de integração PostgreSQL 15.19 (abertura/fechamento, relatórios, migração, preservação de credenciais, rejeição/cancelamento, permissões e rollback). O JAR foi gerado com sucesso por `mvn -B -DskipTests package` após os testes da alteração de código.

## Antes da apresentação

1. Deixe o banco ativo em localhost:5430 e abra o JAR atualizado. Na instalação Docker, use `iniciar.cmd`; com PostgreSQL local já configurado, use `java -jar target/engrenar-1.0.0.jar`.
2. Use somente uma janela do sistema para evitar confundir sessões e versões.
3. Atendente: `ana`; mecânico: `carlos`; gerente: `admin`. Senha inicial de demonstração: `123456` (se não foi alterada).
4. Para repetir uma abertura bem-sucedida, use um veículo sem OS ativa. Cada confirmação grava dados reais no banco local; não reutilize um veículo com atendimento ativo para esse cenário.
5. Compile e teste antes, com dependências disponíveis. Durante a apresentação, abra o JAR já preparado; não dependa de baixar dependências pela internet.

## Demonstração de abertura de OS

1. Entrar como atendente e acessar Ordens de serviço → Abrir OS.
2. Mostrar que inicialmente só aparecem cliente e veículo.
3. Selecionar veículo com OS ativa e clicar Continuar: o sistema informa o número e bloqueia o formulário.
4. Selecionar veículo sem OS ativa e clicar Continuar: aparecem reclamação, data, quilometragem e responsável.
5. Confirmar sem preencher a reclamação: mostrar a validação sem gerar OS.
6. Preencher os campos e confirmar: mostrar número automático e status Aberta.
7. Para demonstrar cadastro ausente, Continuar sem seleção abre o cadastro correspondente. Cancelar o cadastro não cria OS.

Fala sugerida: “Antes de registrar a ordem, o sistema verifica se cliente e veículo estão cadastrados e se já existe um atendimento ativo. Só então libera os dados da ordem. Na confirmação, valida os campos e grava a OS com número próprio e status Aberta.”

## Perguntas que podem surgir

- **Onde ficam os dados?** No PostgreSQL local configurado. O Git guarda o código; os cadastros feitos durante a apresentação não são enviados ao Git.
- **Por que o atendente não escreve o diagnóstico?** A implementação reserva diagnóstico e execução de serviços ao mecânico; estoque ao gerente. Todos os perfis autenticados visualizam OS.
- **Por que não fecha a OS?** Precisa de orçamento aprovado, serviços concluídos, pagamento recebido confirmado e data/forma de pagamento válidas.
- **Onde está o total no fechamento?** Na aba Orçamento; a aba Fechamento registra pagamento e retirada.
- **Valida contato?** E-mail tem validação básica de formato; não há confirmação por e-mail/SMS. Telefone é obrigatório, sem validação completa de DDD/número.
- **Está tudo provado?** A revisão cobre os testes registrados e a abertura modelada nos slides 9–11. Não representa certificação de todos os cenários possíveis, desempenho com 100 mil registros ou execução em todos os sistemas operacionais.
