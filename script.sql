CREATE TABLE transacoes (
    transacao_id SERIAL PRIMARY KEY,
    transacao_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    limite INT,
    valor INT,
    valor_apos_transacao INT,
    transacao_type VARCHAR(1),
    descricao VARCHAR(10),
    client_id INT
);

CREATE INDEX idx_client_id ON transacoes (client_id DESC);


INSERT INTO transacoes (limite, valor, valor_apos_transacao, client_id, transacao_type) VALUES
(100000, 0, 0, 1, 's'),
(80000, 0, 0, 2, 's'),
(1000000, 0, 0, 3, 's'),
(10000000, 0, 0, 4, 's'),
(500000, 0, 0, 5, 's');