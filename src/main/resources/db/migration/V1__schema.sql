CREATE TABLE utente (
    id        BIGSERIAL PRIMARY KEY,
    nome      VARCHAR(120) NOT NULL,
    quarto    VARCHAR(10)  NOT NULL,
    foto_path VARCHAR(255),
    ativo     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE utilizador_gestao (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL CHECK (perfil IN ('ENFERMEIRO', 'GERONTOLOGO'))
);

CREATE TABLE medicacao_plano (
    id             BIGSERIAL PRIMARY KEY,
    utente_id      BIGINT       NOT NULL REFERENCES utente (id),
    medicamento    VARCHAR(120) NOT NULL,
    dose           VARCHAR(60)  NOT NULL,
    horario        VARCHAR(10)  NOT NULL CHECK (horario IN ('MANHA', 'ALMOCO', 'JANTAR', 'DEITAR')),
    observacoes    TEXT,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ  NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    atualizado_por BIGINT       REFERENCES utilizador_gestao (id)
);
CREATE INDEX ix_plano_utente ON medicacao_plano (utente_id) WHERE ativo;

CREATE TABLE medicacao_plano_auditoria (
    id            BIGSERIAL PRIMARY KEY,
    plano_id      BIGINT      NOT NULL REFERENCES medicacao_plano (id),
    snapshot_json JSONB       NOT NULL,
    alterado_em   TIMESTAMPTZ NOT NULL,
    alterado_por  BIGINT      REFERENCES utilizador_gestao (id)
);
CREATE INDEX ix_auditoria_plano ON medicacao_plano_auditoria (plano_id, alterado_em DESC);

CREATE TABLE registo (
    id                  BIGSERIAL PRIMARY KEY,
    utente_id           BIGINT      NOT NULL REFERENCES utente (id),
    tipo                VARCHAR(10) NOT NULL CHECK (tipo IN ('BANHO', 'QUEDA', 'DOR', 'TOMA')),
    detalhe             VARCHAR(30),
    nota                TEXT,
    -- Texto do que foi registado (ex.: "Paracetamol 1 g"), para o histórico sobreviver a edições do plano.
    descricao           VARCHAR(255),
    medicacao_plano_id  BIGINT      REFERENCES medicacao_plano (id),
    data_hora           TIMESTAMPTZ NOT NULL,
    -- Dia civil em Europe/Lisbon, calculado pela aplicação (necessário para os índices únicos).
    dia                 DATE        NOT NULL,
    anulado             BOOLEAN     NOT NULL DEFAULT FALSE,
    anulado_em          TIMESTAMPTZ,
    -- NULL quando anulado no tablet (sem identificação); preenchido quando anulado no /admin.
    anulado_por         BIGINT      REFERENCES utilizador_gestao (id),
    CONSTRAINT ck_toma_tem_plano CHECK (tipo <> 'TOMA' OR medicacao_plano_id IS NOT NULL),
    CONSTRAINT ck_anulado_tem_data CHECK (NOT anulado OR anulado_em IS NOT NULL)
);

-- Regras de negócio garantidas pela base de dados (protege contra toques duplos / dois tablets):
CREATE UNIQUE INDEX ux_banho_utente_dia
    ON registo (utente_id, dia) WHERE tipo = 'BANHO' AND NOT anulado;
CREATE UNIQUE INDEX ux_toma_plano_dia
    ON registo (medicacao_plano_id, dia) WHERE tipo = 'TOMA' AND NOT anulado;

CREATE INDEX ix_registo_utente_dia ON registo (utente_id, dia);
CREATE INDEX ix_registo_ocorrencias ON registo (utente_id, data_hora DESC)
    WHERE tipo IN ('QUEDA', 'DOR') AND NOT anulado;
