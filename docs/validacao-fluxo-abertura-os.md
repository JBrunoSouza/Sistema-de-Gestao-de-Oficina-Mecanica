# Abertura de OS conforme os diagramas da apresentação

Referência: apresentação Canva DAHVqgagu90, páginas 9–11, consultada em 25/09/2026.

1. O funcionário solicita a abertura e seleciona cliente e veículo.
2. Ao continuar, se não houver cliente selecionado, o sistema abre o cadastro de cliente. Se faltar veículo, abre o cadastro de veículo vinculado ao cliente. Após salvar, a seleção é atualizada; cancelar mantém a etapa inicial sem criar OS.
3. A seleção é validada no banco: existência, vínculo cliente/veículo e ausência de OS ativa. Uma OS ativa gera mensagem com seu número e impede a liberação do formulário.
4. Somente após a validação aparecem reclamação, data, quilometragem, responsável e confirmação. Trocar cliente ou veículo oculta esses campos e exige nova validação.
5. Ao confirmar, os pré-requisitos são revalidados e os campos obrigatórios conferidos. A gravação transacional também repete a verificação para impedir duplicidade concorrente; gera número, status Aberta e confirmação.

A pré-validação não cria nem reserva uma OS. Os testes cobrem bloqueio por OS ativa antes da exibição dos campos, mudança de seleção, encaminhamento aos cadastros, campos obrigatórios e abertura bem-sucedida. Os dados usados nos testes são isolados em H2; os cadastros locais do usuário não são alterados.
