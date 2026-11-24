-- Ficha de emergência (INEM): dados de identificação que faltavam no utente e que uma equipa de emergência pede.
-- Todos opcionais: a ficha avisa quando faltam em vez de os inventar.

ALTER TABLE utente ADD COLUMN data_nascimento DATE;
ALTER TABLE utente ADD COLUMN sexo VARCHAR(1) CHECK (sexo IN ('F', 'M'));
ALTER TABLE utente ADD COLUMN numero_utente_sns VARCHAR(20);
ALTER TABLE utente ADD COLUMN contacto_nome VARCHAR(120);
ALTER TABLE utente ADD COLUMN contacto_parentesco VARCHAR(60);
ALTER TABLE utente ADD COLUMN contacto_telefone VARCHAR(30);
ALTER TABLE utente ADD COLUMN medico_assistente VARCHAR(120);
-- Texto livre da enfermagem para a equipa de emergência (p. ex. decisões já tomadas, diretivas, situações a ter em conta).
ALTER TABLE utente ADD COLUMN info_emergencia VARCHAR(1000);

-- Cada emissão da ficha fica registada: quem (funcionária no tablet ou utilizador de gestão no /admin), de quem e quando.
CREATE TABLE ficha_emergencia_emitida (
    id             BIGSERIAL   PRIMARY KEY,
    utente_id      BIGINT      NOT NULL REFERENCES utente (id),
    data_hora      TIMESTAMPTZ NOT NULL,
    funcionario_id BIGINT      REFERENCES funcionario (id),
    utilizador_id  BIGINT      REFERENCES utilizador_gestao (id),
    CONSTRAINT ck_ficha_emergencia_quem CHECK ((funcionario_id IS NULL) <> (utilizador_id IS NULL))
);
CREATE INDEX ix_ficha_emergencia_utente ON ficha_emergencia_emitida (utente_id);
