# Roteiro de apresentação — entrega parcial

Abra o JAR compilado antes de desconectar da internet. Reserve aproximadamente 8 minutos. Use uma base de demonstração separada conforme o README; os exemplos são dados fictícios persistidos no H2.

## 1. Veículo

1. Entre em **Veículos → Cadastrar veículo**. Clique em **Cliente não localizado? Cadastrar cliente**.
2. Cadastre nome, telefone e e-mail. Ao retornar, o cliente estará selecionado.
3. Informe placa `JKL2M34`, marca Volkswagen, modelo Polo, ano 2023 e quilometragem `-1`. Confirme: o campo fica destacado e nada é salvo.
4. Corrija para `12000` e salve. O veículo aparece na lista.
5. Repita o cadastro com a mesma placa, mesmo em minúsculas ou com hífen. O sistema bloqueia e exibe o veículo existente. Feche o formulário.

## 2. Abertura de OS

1. Entre em **Ordens de serviço → Abrir OS**. Mostre os atalhos de cadastro de cliente e veículo.
2. Selecione o novo cliente e o Polo; confira placa/modelo/km.
3. Deixe a reclamação ou o responsável vazio e confirme para demonstrar o bloqueio.
4. Preencha reclamação, data `dd/mm/aaaa`, km e responsável. Confirme e anote o número automático da OS, com status **Aberta**.
5. Tente abrir outra OS para esse veículo. O sistema informa o número da OS ativa e bloqueia. Feche o formulário e abra a ficha da OS criada.

## 3. Orçamento

1. Abra **Orçamento** da OS nova: sem itens, geração e decisão estão bloqueadas.
2. Em **Diagnóstico e itens**, informe e salve o diagnóstico. Adicione “Revisão preventiva”, quantidade 2, valor unitário `120,50`.
3. Adicione 1 “Filtro de óleo” de `35,90`. O saldo cai de 12 para 11. Tente quantidade superior ao saldo para mostrar o bloqueio.
4. Confira serviços `R$ 241,00`, peças `R$ 35,90`, total `R$ 276,90`.
5. Informe responsável e clique em **Aprovado**. Confira status, data/hora e nome registrados.
6. Para rejeição separadamente, abra **OS-00002** dos exemplos, adicione “Alinhamento” de `90,00` e registre **Rejeitado** com responsável.
7. Feche e reabra o aplicativo usando a mesma pasta: registros e decisões permanecem no banco.

## Apoio opcional

Na OS aprovada, marque os serviços como concluídos. Abra **Fechamento**, selecione pagamento, confirme recebimento e informe retirada. O sistema fecha a OS e permite novo atendimento para aquele veículo. **Relatórios** mostra listagem atual e faturamento das OS fechadas.

Declare: login/perfis, relatórios completos, revisões/cancelamento e acabamento integral do Figma são próximos passos. A entrega demonstra os três fluxos prioritários com dados reais e cenários alternativos.
