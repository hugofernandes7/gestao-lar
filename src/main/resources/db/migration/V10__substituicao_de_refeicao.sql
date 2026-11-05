-- Etapa 8 (correção): a refeição que substitui outra guarda qual substituiu, para que desfazer (ou anular no admin)
-- possa repor a anterior. Migração nova: a V9 já está aplicada e não se edita.
ALTER TABLE registo ADD COLUMN substitui_id BIGINT REFERENCES registo (id);
