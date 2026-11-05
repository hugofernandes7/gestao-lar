-- Funcionárias do lar. O id é técnico (nunca mostrado nem usado para entrar); quem entra usa o número + PIN.
CREATE TABLE funcionario (
    id        BIGSERIAL    PRIMARY KEY,
    numero    INTEGER      NOT NULL UNIQUE CHECK (numero BETWEEN 1 AND 9999),   -- nunca é reutilizado
    nome      VARCHAR(120) NOT NULL,
    pin_hash  VARCHAR(100) NOT NULL,                                            -- BCrypt
    ativa     BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ  NOT NULL
);

-- Todas as tentativas de acesso por PIN no tablet (certas e erradas). numero_tentado é texto: guarda também
-- números que não existem, para os bloqueios se aplicarem de igual modo a números existentes e inexistentes.
CREATE TABLE tentativa_acesso (
    id             BIGSERIAL    PRIMARY KEY,
    numero_tentado VARCHAR(20)  NOT NULL,
    data           TIMESTAMPTZ  NOT NULL,
    sucesso        BOOLEAN      NOT NULL,
    bloqueada      BOOLEAN      NOT NULL DEFAULT FALSE,    -- recusada por haver um bloqueio ativo (o PIN nem foi avaliado)
    CONSTRAINT ck_tentativa_bloqueada CHECK (NOT (sucesso AND bloqueada))
);
CREATE INDEX ix_tentativa_numero ON tentativa_acesso (numero_tentado, data);
CREATE INDEX ix_tentativa_data ON tentativa_acesso (data);

-- Bloqueios: por número (5 PINs errados: 5 min) ou global do tablet (20 falhas seguidas: 15 min).
CREATE TABLE bloqueio_acesso (
    id     BIGSERIAL   PRIMARY KEY,
    ambito VARCHAR(10) NOT NULL CHECK (ambito IN ('NUMERO', 'GLOBAL')),
    numero VARCHAR(20),
    inicio TIMESTAMPTZ NOT NULL,
    fim    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_bloqueio_numero CHECK ((ambito = 'NUMERO') = (numero IS NOT NULL)),
    CONSTRAINT ck_bloqueio_fim CHECK (fim > inicio)
);
CREATE INDEX ix_bloqueio_ambito ON bloqueio_acesso (ambito, numero, fim);

-- Quem fez o registo (NULL = sem sessão de PIN no momento do registo). Usado em banho, queda, dor e toma.
ALTER TABLE registo ADD COLUMN realizado_por BIGINT REFERENCES funcionario (id);
