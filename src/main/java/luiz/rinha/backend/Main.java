package luiz.rinha.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.postgresql.ds.PGConnectionPoolDataSource;
import org.postgresql.ds.PGPooledConnection;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    static ObjectMapper objectMapper = new ObjectMapper();
    static Connection conn = null;

    static PGPooledConnection pooledConnection = null;

    static void createClientTable() {
        String createTableTransaction = """
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
                """;

        String initialValues = """
                       INSERT INTO transacoes (limite, valor, valor_apos_transacao, client_id, transacao_type) VALUES
                       (100000, 0, 0, 1, 's'),
                       (80000, 0, 0, 2, 's'),
                       (1000000, 0, 0, 3, 's'),
                       (10000000, 0, 0, 4, 's'),
                       (500000, 0, 0, 5, 's');
                """;
        try (Connection connection = pooledConnection.getConnection()) {
            Statement st = connection.createStatement();
            st.execute(createTableTransaction);
            st.execute(initialValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws SQLException, InterruptedException {
        Thread.sleep(5000);
        String dbConnectionString = "jdbc:postgresql://db:5432/rinha?user=rinha&password=rinha&maxConnections=10";
        conn = DriverManager.getConnection(dbConnectionString);
        pooledConnection = new PGPooledConnection(
                conn, true
        );
        createClientTable();
        Javalin app = Javalin.create(config -> {
                    config.useVirtualThreads = true;
                }).post("/clientes/{id}/transacoes", Main::postTransaction)
                .get("/clientes/{id}/extrato", Main::getExtract)
                .start(8080);
    }

    public static void getExtract(Context ctx) {
        String clientIdAsStr = ctx.pathParam("id");
        Integer clientId = null;
        try {
            clientId = Integer.valueOf(clientIdAsStr);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        try (Connection conn = pooledConnection.getConnection()) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(String.format("""
                            SELECT transacao_time, valor, transacao_type, descricao, limite
                            FROM public.transacoes
                            WHERE client_id = %d
                            ORDER BY transacao_id DESC
                            """, clientId));

            Map<String, Object> extrato = new HashMap<>();
            int total = 0;
            int limite = 0;
            List<Map<String, Object>> transacoes = new ArrayList<>();
            while (rs.next()) {
                String tipo = rs.getString("transacao_type");
                if (!"s".equals(tipo)) {
                    int valorTransacao = rs.getInt("valor");
                    Map<String, Object> transacao = new HashMap<>();
                    transacao.put("realizado_em", rs.getTimestamp("transacao_time").toString()+"Z");
                    transacao.put("valor", valorTransacao);
                    transacao.put("tipo, ", rs.getString("transacao_type"));
                    transacao.put("descricao", rs.getString("descricao"));
                    total += valorTransacao;
                    transacoes.add(transacao);
                }
                limite = rs.getInt("limite");
            }

            if (limite == 0) {
                ctx.status(HttpStatus.NOT_FOUND);
                return;
            }

            Map<String, Object> saldo = new HashMap<>();
            saldo.put("limite", limite);
            saldo.put("saldo", total);
            saldo.put("data_extrato", currentDate(0));

            extrato.put("transacoes", transacoes);
            extrato.put("saldo", saldo);

            ctx.result(objectMapper.writeValueAsString(extrato));
        } catch (SQLException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String currentDate(int timestamp) {
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        if (timestamp > 0) {
            Instant instant = Instant.ofEpochSecond(timestamp);
            zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
        return zonedDateTime.format(formatter);
    }

    public static void postTransaction(Context ctx) {
        String clientIdAsStr = ctx.pathParam("id");
        Map<String, Object> bodyAsMap = null;

        Integer value = null;
        String type = null;
        String description = null;
        Integer clientId = null;

        try {
            clientId = Integer.valueOf(clientIdAsStr);
            bodyAsMap = objectMapper.readValue(ctx.bodyAsBytes(), HashMap.class);
            value = (Integer) bodyAsMap.get("valor");
            type = (String) bodyAsMap.get("tipo");
            description = (String) bodyAsMap.get("descricao");
            if (value == null || type == null || description == null ||
                    type.isBlank() || description.isBlank() || description.length() > 10
            ) {
                ctx.status(HttpStatus.BAD_REQUEST);
                return;
            }
        } catch (Exception ignored) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        // UPDATE HERE
        try {
            Map result = doTransaction(clientId, value, type, description);
            String resultAsStr = objectMapper.writeValueAsString(result);
            if (resultAsStr.contains("unprocess")) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return;
            }
            ctx.result(resultAsStr);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    static Map<String, Object> doTransaction(Integer clientId, Integer value, String type, String description) {
        try (Connection conn = pooledConnection.getConnection()) {
            conn.setAutoCommit(false);
            value = Math.abs(value);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                    String.format("""
                                   SELECT limite, valor_apos_transacao
                                           FROM transacoes
                                           WHERE client_id = %d
                                           ORDER BY transacao_id DESC
                                           LIMIT 1
                                           FOR UPDATE
                            """, clientId)
            );

            if (rs.next()) {
                int limit = rs.getInt("limite");
                int currentValue = rs.getInt("valor_apos_transacao");
                int newValue = Integer.MAX_VALUE;

                if ("c".equals(type)) {
                    newValue = currentValue + value;
                } else if ("d".equals(type)) {
                    newValue = currentValue - value;
                    value *= -1;
                }

                if ("d".equals(type)) {
                    if (newValue < 0 && (newValue < (limit * -1))) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("unprocess", 422);
                        conn.commit();
                        return result;
                    }
                }

                st = conn.createStatement();
                st.execute(String.format("""
                            INSERT INTO transacoes (limite, valor, valor_apos_transacao, client_id, descricao, transacao_type)
                            VALUES (%d, %d, %d, %d, '%s', '%s')
                        """, limit, value, newValue, clientId, description, type));

                conn.commit();
                Map<String, Object> result = new HashMap<>();
                result.put("limite", limit);
                result.put("saldo", newValue);
                return result;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}