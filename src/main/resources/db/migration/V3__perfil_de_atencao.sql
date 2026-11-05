-- Perfil de atenção e "Sobre mim" de cada utente (1:1). Campos estruturados, exceto onde o texto livre é indicado.
CREATE TABLE utente_perfil (
    utente_id         BIGINT       PRIMARY KEY REFERENCES utente (id) ON DELETE CASCADE,

    -- Atenção
    alergias          VARCHAR(200),                       -- texto curto; NULL = sem alergias registadas
    mobilidade        VARCHAR(20)  NOT NULL
        CHECK (mobilidade IN ('AUTONOMO', 'BENGALA', 'ANDARILHO', 'CADEIRA_RODAS', 'ACAMADO')),
    transferencia     INTEGER      CHECK (transferencia IN (1, 2)),   -- nº de pessoas necessárias; NULL = n/a
    risco_queda       VARCHAR(10)  NOT NULL CHECK (risco_queda IN ('BAIXO', 'MEDIO', 'ALTO')),
    textura           VARCHAR(30)  NOT NULL
        CHECK (textura IN ('NORMAL', 'MOLE', 'PASTOSA', 'LIQUIDOS_ESPESSADOS')),
    dieta             VARCHAR(120),                       -- texto curto
    ajuda_a_comer     BOOLEAN      NOT NULL DEFAULT FALSE,
    ouve_mal_esq      BOOLEAN      NOT NULL DEFAULT FALSE,
    ouve_mal_dir      BOOLEAN      NOT NULL DEFAULT FALSE,
    usa_oculos        BOOLEAN      NOT NULL DEFAULT FALSE,
    protese_auditiva  BOOLEAN      NOT NULL DEFAULT FALSE,
    protese_dentaria  BOOLEAN      NOT NULL DEFAULT FALSE,
    desorientado      BOOLEAN      NOT NULL DEFAULT FALSE,
    avisos            VARCHAR(200),                       -- avisos especiais, texto curto, opcional

    -- Sobre mim (só leitura no tablet)
    tratamento        VARCHAR(200),                       -- como gosta de ser tratado
    gostos            TEXT,                               -- gostos e temas de conversa (texto livre)

    revisto_em        TIMESTAMPTZ  NOT NULL,
    revisto_por       BIGINT       REFERENCES utilizador_gestao (id)
);

-- Cada gravação no /admin guarda uma versão completa (como em medicacao_plano_auditoria).
CREATE TABLE utente_perfil_auditoria (
    id            BIGSERIAL    PRIMARY KEY,
    utente_id     BIGINT       NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
    snapshot_json JSONB        NOT NULL,
    alterado_em   TIMESTAMPTZ  NOT NULL,
    alterado_por  BIGINT       REFERENCES utilizador_gestao (id)
);
CREATE INDEX ix_perfil_auditoria_utente ON utente_perfil_auditoria (utente_id, alterado_em, id);
