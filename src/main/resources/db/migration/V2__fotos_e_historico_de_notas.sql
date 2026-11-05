-- Fotos dos utentes passam a viver na base de dados (um só sítio para copiar/repor; sem volume de ficheiros).
CREATE TABLE utente_foto (
    utente_id     BIGINT PRIMARY KEY REFERENCES utente (id) ON DELETE CASCADE,
    conteudo      BYTEA        NOT NULL,
    content_type  VARCHAR(60)  NOT NULL,
    -- Hash do conteúdo, usado como ETag (o browser só volta a descarregar se a foto mudar).
    etag          VARCHAR(64)  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL
);

ALTER TABLE utente DROP COLUMN foto_path;

-- Cada versão da nota de uma queda/dor (a nota pode ser acrescentada, editada e removida mais tarde).
-- nota NULL = a nota foi removida nessa altura.
CREATE TABLE registo_nota_historico (
    id          BIGSERIAL PRIMARY KEY,
    registo_id  BIGINT      NOT NULL REFERENCES registo (id) ON DELETE CASCADE,
    nota        TEXT,
    alterado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_nota_historico_registo ON registo_nota_historico (registo_id, alterado_em, id);

-- Notas que já existiam passam a ter a sua primeira versão.
INSERT INTO registo_nota_historico (registo_id, nota, alterado_em)
SELECT id, nota, data_hora FROM registo WHERE nota IS NOT NULL;
