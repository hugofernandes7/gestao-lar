-- Etapa 7: condições de saúde, protocolos "O que fazer" e registo de acessos ao separador Saúde.
-- O nome da condição (diagnóstico) só se mostra no separador Saúde (com PIN) e no /admin. No tablet, fora da Saúde,
-- aparece apenas o "aviso de atuação" (sem o nome da condição).

-- Catálogo fixo de condições, com o protocolo e o aviso editáveis no /admin.
-- protocolo: um passo por linha. aviso: frase curta de atuação, sem o nome da condição (pode ser vazio).
CREATE TABLE condicao_protocolo (
    codigo         VARCHAR(40)  PRIMARY KEY,
    nome           VARCHAR(80)  NOT NULL,
    protocolo      TEXT         NOT NULL,
    aviso          VARCHAR(200),
    ordem          INTEGER      NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    atualizado_por BIGINT       REFERENCES utilizador_gestao (id)
);

-- Condições de cada utente.
CREATE TABLE utente_condicao (
    id              BIGSERIAL    PRIMARY KEY,
    utente_id       BIGINT       NOT NULL REFERENCES utente (id),
    condicao        VARCHAR(40)  NOT NULL REFERENCES condicao_protocolo (codigo),
    detalhe         VARCHAR(200),
    desde           DATE,
    ativa           BOOLEAN      NOT NULL DEFAULT TRUE,
    notas           TEXT,
    criado_em       TIMESTAMPTZ  NOT NULL,
    criado_por      BIGINT       REFERENCES utilizador_gestao (id),
    atualizado_em   TIMESTAMPTZ  NOT NULL,
    atualizado_por  BIGINT       REFERENCES utilizador_gestao (id)
);
CREATE INDEX ix_utente_condicao_utente ON utente_condicao (utente_id);

-- Quem abriu o separador Saúde de quem, e quando (uma linha por abertura).
CREATE TABLE acesso_saude (
    id             BIGSERIAL    PRIMARY KEY,
    funcionario_id BIGINT       NOT NULL REFERENCES funcionario (id),
    utente_id      BIGINT       NOT NULL REFERENCES utente (id),
    data_hora      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_acesso_saude_data ON acesso_saude (data_hora DESC, id DESC);

-- Textos de partida: genéricos, escritos para um lar. A enfermagem deve rever e ajustar cada um em /admin → Protocolos
-- antes de qualquer uso real. Nenhum nomeia medicamentos.
INSERT INTO condicao_protocolo (codigo, nome, protocolo, aviso, ordem, atualizado_em) VALUES
('DIABETES_INSULINA', 'Diabetes (com insulina)',
 E'Não saltar refeições: confirmar que comeu.\nSuores frios, tremores, fome súbita, confusão ou sonolência fora do normal: pode ser açúcar baixo.\nSe estiver consciente e a engolir bem: dar 3 pacotes de açúcar ou meio copo de sumo e avisar a enfermagem de imediato.\nSe não estiver consciente ou não conseguir engolir: não dar nada pela boca; chamar a enfermagem e o 112.\nNo banho, ver os pés e a pele; avisar se houver feridas ou vermelhidão.\nAnotar a hora e o que aconteceu.',
 'Suores, tremores ou confusão: dar açúcar e avisar a enfermagem. Não deixar saltar refeições.', 1, now()),
('DIABETES_ORAL', 'Diabetes (comprimidos)',
 E'Seguir a dieta indicada no perfil; evitar doces fora das refeições.\nMuita sede, muita urina, cansaço invulgar ou confusão: avisar a enfermagem.\nSuores frios ou tremores: dar 3 pacotes de açúcar ou meio copo de sumo e avisar a enfermagem.\nNo banho, ver os pés e a pele; avisar se houver feridas ou vermelhidão.',
 'Seguir a dieta; muita sede ou muita urina: avisar a enfermagem.', 2, now()),
('ANTICOAGULADO', 'Anticoagulado',
 E'Qualquer queda ou pancada na cabeça, mesmo sem queixas: avisar a enfermagem de imediato.\nSangramento: pressionar com um pano limpo durante 10 minutos; se não parar, chamar o 112.\nFezes escuras, urina com sangue, sangue ao tossir ou nódoas negras sem causa: avisar a enfermagem.\nBarbear só com máquina elétrica; evitar picadas e pancadas.\nNão dar anti-inflamatórios nem aspirina sem indicação da enfermagem.',
 'Risco de hemorragia: qualquer queda, pancada ou sangramento — avisar a enfermagem de imediato.', 3, now()),
('AVC_SEQUELAS', 'AVC (sequelas)',
 E'Boca descaída, braço ou perna sem força, fala arrastada ou confusão súbita: chamar o 112 e anotar a hora a que começou.\nNas transferências, apoiar do lado mais fraco; não puxar pelo braço afetado.\nRefeições sentado e direito, com tempo; atenção a engasgamento e a comida que fica na boca.\nDar tempo para falar e para responder; uma pergunta de cada vez.',
 'Lado mais fraco: apoiar desse lado e dar tempo para falar e engolir.', 4, now()),
('ENFARTE', 'Enfarte do miocárdio (antecedente)',
 E'Dor ou aperto no peito, falta de ar, suores frios, enjoo ou dor no braço ou na mandíbula: parar a atividade e sentar.\nChamar a enfermagem de imediato; se não passar em poucos minutos ou piorar, chamar o 112.\nNão deixar a pessoa sozinha. Anotar a hora a que começou.',
 'Dor ou aperto no peito, falta de ar ou suores frios: sentar e chamar a enfermagem de imediato.', 5, now()),
('INSUF_CARDIACA', 'Insuficiência cardíaca',
 E'Pernas elevadas quando está sentado.\nFalta de ar deitado, tosse à noite, pernas inchadas ou aumento rápido de peso: avisar a enfermagem.\nSeguir a dieta e os líquidos indicados pela enfermagem.\nFalta de ar intensa ou lábios arroxeados: sentar, chamar a enfermagem e o 112.',
 'Falta de ar, pernas inchadas ou aumento rápido de peso: avisar a enfermagem.', 6, now()),
('EPILEPSIA', 'Epilepsia',
 E'Manter a calma e anotar a hora a que começou.\nAfastar objetos e proteger a cabeça. Não segurar a pessoa e não pôr nada na boca.\nQuando parar, deitar de lado e ficar ao lado até recuperar.\nSe durar mais de 5 minutos, repetir ou a pessoa não recuperar: chamar o 112.\nAvisar sempre a enfermagem.',
 'Se tiver uma convulsão: não segurar, não pôr nada na boca, proteger a cabeça e avisar a enfermagem.', 7, now()),
('DEMENCIA', 'Demência',
 E'Aproximar-se de frente, chamar pelo nome e falar devagar, com voz calma.\nUma instrução de cada vez; não contrariar de frente.\nAgitação: baixar o ruído, acompanhar, tentar atividade calma que a pessoa goste.\nVigiar saídas do quarto e da casa, sobretudo ao fim da tarde e à noite.\nMudança súbita de comportamento pode ser dor ou infeção: avisar a enfermagem.',
 'Pode ficar confusa ou agitada: falar devagar e com calma, chamar pelo nome e vigiar saídas.', 8, now()),
('DPOC_OXIGENIO', 'DPOC com oxigénio',
 E'Oxigénio no débito indicado pela enfermagem; nunca alterar.\nSem chamas, cigarros ou fontes de calor perto do oxigénio.\nFalta de ar: sentar, ligeiramente inclinado para a frente, e avisar a enfermagem.\nLábios arroxeados, confusão ou muita sonolência: chamar a enfermagem e o 112.\nVer o tubo e as orelhas (pele magoada) de cada vez que se apoia.',
 'Usa oxigénio: sem chamas nem calor por perto. Falta de ar: sentar e avisar a enfermagem.', 9, now()),
('PACEMAKER', 'Pacemaker',
 E'Tonturas, desmaio ou pulso muito lento: sentar ou deitar e avisar a enfermagem.\nNão pôr ímanes nem aparelhos de eletroterapia sobre o peito.\nO cartão do pacemaker acompanha a pessoa em consultas e na urgência.',
 'Não aplicar ímanes nem aparelhos de eletroterapia sobre o peito.', 10, now()),
('OUTRA', 'Outra condição',
 E'Seguir as instruções escritas pela enfermagem nas notas desta condição.\nEm caso de dúvida, avisar a enfermagem.',
 NULL, 11, now());
