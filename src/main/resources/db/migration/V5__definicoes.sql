-- Definições editáveis no /admin. Só há linha quando alguém alterou o valor; sem linha vale o application.yml.
CREATE TABLE definicao (
    chave        VARCHAR(60)  PRIMARY KEY,
    valor        VARCHAR(20)  NOT NULL,
    alterado_em  TIMESTAMPTZ  NOT NULL,
    alterado_por BIGINT       REFERENCES utilizador_gestao (id)
);

-- Uma linha por alteração: valor anterior (o efetivo nesse momento) e valor novo.
CREATE TABLE definicao_auditoria (
    id             BIGSERIAL    PRIMARY KEY,
    chave          VARCHAR(60)  NOT NULL,
    valor_anterior VARCHAR(20)  NOT NULL,
    valor_novo     VARCHAR(20)  NOT NULL,
    alterado_em    TIMESTAMPTZ  NOT NULL,
    alterado_por   BIGINT       REFERENCES utilizador_gestao (id)
);
CREATE INDEX ix_definicao_auditoria ON definicao_auditoria (alterado_em DESC, id DESC);
