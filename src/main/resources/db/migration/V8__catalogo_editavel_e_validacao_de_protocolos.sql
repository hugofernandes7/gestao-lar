-- Catálogo de condições editável (criar, renomear, desativar) e validação dos protocolos pela enfermagem.
-- Um protocolo só chega ao tablet depois de validado; qualquer edição volta a pô-lo "por validar".

ALTER TABLE condicao_protocolo
    ADD COLUMN ativa        BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN validado_por BIGINT      REFERENCES utilizador_gestao (id),
    ADD COLUMN validado_em  TIMESTAMPTZ;

-- Os textos de partida (V7) são genéricos: ficam marcados como demonstração e por validar.
UPDATE condicao_protocolo SET protocolo = '[DEMO] ' || protocolo WHERE protocolo NOT LIKE '[DEMO]%';
UPDATE condicao_protocolo SET aviso = '[DEMO] ' || aviso WHERE aviso IS NOT NULL AND aviso NOT LIKE '[DEMO]%';
