package luiz.rinha.backend;

public class Queries {
    public static final String GET_TRANSACTION = """
            SELECT transacao_time, valor, transacao_type, descricao, limite, valor_apos_transacao
            FROM public.transacoes
            WHERE client_id = ?
            ORDER BY transacao_id DESC
            LIMIT 11
            """;

    public static final String PROCESS_TRANSACTION = """
            SELECT * FROM process_transaction(?, ?, ?, ?)
            """;
}
