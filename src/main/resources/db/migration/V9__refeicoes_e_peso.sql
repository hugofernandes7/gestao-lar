-- Etapa 8: refeições e peso. Alarga o registo aos tipos das Etapas 8-11 (o tipo mais comprido, SINAIS_VITAIS, não cabia
-- em VARCHAR(10)) e acrescenta a refeição ao registo e a tabela do peso.

ALTER TABLE registo DROP CONSTRAINT registo_tipo_check;
ALTER TABLE registo ALTER COLUMN tipo TYPE VARCHAR(20);
ALTER TABLE registo ALTER COLUMN detalhe TYPE VARCHAR(40);
ALTER TABLE registo ADD CONSTRAINT registo_tipo_check CHECK (tipo IN
    ('BANHO', 'QUEDA', 'DOR', 'TOMA', 'REFEICAO', 'HUMOR', 'ATIVIDADE', 'VIGILANCIA', 'PESO', 'SINAIS_VITAIS'));

-- Refeição: qual (PEQUENO_ALMOCO, ALMOCO, LANCHE, JANTAR, CEIA); a quantidade (TUDO, METADE, POUCO, RECUSOU) fica em "detalhe".
ALTER TABLE registo ADD COLUMN refeicao VARCHAR(20);
ALTER TABLE registo ADD CONSTRAINT ck_refeicao_tem_refeicao CHECK (tipo <> 'REFEICAO' OR refeicao IS NOT NULL);

-- Uma refeição de cada tipo por utente por dia (como o banho): aguenta toques duplos e dois tablets.
CREATE UNIQUE INDEX ux_refeicao_utente_dia_tipo
    ON registo (utente_id, dia, refeicao) WHERE tipo = 'REFEICAO' AND NOT anulado;

-- Peso (kg, 1 decimal). A data e quem registou estão no registo.
CREATE TABLE peso (
    id          BIGSERIAL     PRIMARY KEY,
    registo_id  BIGINT        NOT NULL UNIQUE REFERENCES registo (id),
    utente_id   BIGINT        NOT NULL REFERENCES utente (id),
    kg          NUMERIC(4, 1) NOT NULL CHECK (kg BETWEEN 20 AND 250)
);
CREATE INDEX ix_peso_utente ON peso (utente_id);
