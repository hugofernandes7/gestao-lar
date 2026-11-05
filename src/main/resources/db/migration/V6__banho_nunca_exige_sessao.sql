-- Regra definitiva: o banho nunca exige sessão de PIN (só ocorrências e medicação). A definição deixa de existir;
-- se alguma instalação a tinha guardada, apaga-se o valor (o histórico em definicao_auditoria mantém-se).
DELETE FROM definicao WHERE chave = 'registo.exige-sessao.banho';
