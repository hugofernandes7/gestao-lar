-- Etapa 9: vigilância pós-queda. Cada queda cria um plano com N verificações (por defeito +1 h, +4 h, +8 h, +24 h; os
-- intervalos vêm de lar.vigilancia.intervalos-horas e ficam gravados em prevista_em no momento da queda).
CREATE TABLE vigilancia_plano (
    id               BIGSERIAL   PRIMARY KEY,
    registo_queda_id BIGINT      NOT NULL UNIQUE REFERENCES registo (id),
    utente_id        BIGINT      NOT NULL REFERENCES utente (id),
    criado_em        TIMESTAMPTZ NOT NULL,
    cancelado        BOOLEAN     NOT NULL DEFAULT FALSE      -- a queda foi anulada: já não há nada a verificar
);
CREATE INDEX ix_vigilancia_plano_utente ON vigilancia_plano (utente_id);

CREATE TABLE vigilancia_verificacao (
    id                   BIGSERIAL   PRIMARY KEY,
    plano_id             BIGINT      NOT NULL REFERENCES vigilancia_plano (id),
    ordem                INTEGER     NOT NULL CHECK (ordem >= 1),
    prevista_em          TIMESTAMPTZ NOT NULL,
    feita_em             TIMESTAMPTZ,
    registo_id           BIGINT      UNIQUE REFERENCES registo (id),   -- o registo VIGILANCIA criado ao fazer
    consciente_orientado BOOLEAN,
    dor                  VARCHAR(10) CHECK (dor IN ('SEM_DOR', 'LIGEIRA', 'MODERADA', 'FORTE')),
    move_como_habitual   VARCHAR(12) CHECK (move_como_habitual IN ('SIM', 'DIFICULDADE', 'NAO_SE_MOVE')),
    alerta               BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (plano_id, ordem),
    -- feita => as 3 respostas (nunca por defeito) e o registo
    CONSTRAINT ck_verificacao_feita CHECK (
        (feita_em IS NULL AND registo_id IS NULL AND consciente_orientado IS NULL AND dor IS NULL AND move_como_habitual IS NULL)
        OR (feita_em IS NOT NULL AND registo_id IS NOT NULL AND consciente_orientado IS NOT NULL AND dor IS NOT NULL AND move_como_habitual IS NOT NULL))
);
