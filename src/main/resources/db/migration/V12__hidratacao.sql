-- Hidratação: 3 momentos por dia (manhã, tarde, noite), escala 0–3 guardada em "detalhe" ('0'..'3').
-- Como a refeição, é um registo com um valor por utente, dia e momento; o momento fica na coluna nova "momento".

ALTER TABLE registo DROP CONSTRAINT registo_tipo_check;
ALTER TABLE registo ADD CONSTRAINT registo_tipo_check CHECK (tipo IN
    ('BANHO', 'QUEDA', 'DOR', 'TOMA', 'REFEICAO', 'HUMOR', 'ATIVIDADE', 'VIGILANCIA', 'PESO', 'SINAIS_VITAIS', 'HIDRATACAO'));

ALTER TABLE registo ADD COLUMN momento VARCHAR(10);
ALTER TABLE registo ADD CONSTRAINT ck_hidratacao_momento CHECK (
    tipo <> 'HIDRATACAO' OR (momento IS NOT NULL AND momento IN ('MANHA', 'TARDE', 'NOITE')
                             AND detalhe IS NOT NULL AND detalhe IN ('0', '1', '2', '3')));

-- Um registo de hidratação por utente, dia e momento (aguenta toques duplos e dois tablets).
CREATE UNIQUE INDEX ux_hidratacao_utente_dia_momento
    ON registo (utente_id, dia, momento) WHERE tipo = 'HIDRATACAO' AND NOT anulado;
