package luiz.rinha.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

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

    static HikariDataSource ds = null;

    public static void main(String[] args) {
        ds = new HikariDataSource();
        String dbConnectionString = "jdbc:postgresql://db:5432/rinha?user=rinha&password=rinha";
        ds.setJdbcUrl(dbConnectionString);

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

        try (Connection conn = ds.getConnection()) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(String.format("""
                            SELECT transacao_time, valor, transacao_type, descricao, limite, valor_apos_transacao
                            FROM public.transacoes
                            WHERE client_id = %d
                            ORDER BY transacao_id DESC
                            LIMIT 11
                            """, clientId));

            Map<String, Object> extrato = new HashMap<>();
            Integer total = null;
            int limite = 0;
            List<Map<String, Object>> transacoes = new ArrayList<>();
            while (rs.next() && transacoes.size() <= 10) {
                String tipo = rs.getString("transacao_type");
                if (!"s".equals(tipo)) {
                    if (total == null) {
                        total = rs.getInt("valor_apos_transacao");
                    }
                    int valorTransacao = rs.getInt("valor");
                    Map<String, Object> transacao = new HashMap<>();
                    transacao.put("realizado_em", rs.getTimestamp("transacao_time").toString()+"Z");
                    transacao.put("valor", valorTransacao);
                    transacao.put("tipo", rs.getString("transacao_type"));
                    transacao.put("descricao", rs.getString("descricao"));
                    transacoes.add(transacao);
                }
                limite = rs.getInt("limite");
            }

            if (limite == 0) {
                ctx.status(HttpStatus.NOT_FOUND);
                return;
            }

            if (total == null) {
                total = 0;
            }

            Map<String, Object> saldo = new HashMap<>();
            saldo.put("limite", limite);
            saldo.put("total", total);
            saldo.put("data_extrato", currentDate(0));

            extrato.put("ultimas_transacoes", transacoes);
            extrato.put("saldo", saldo);

            ctx.result(objectMapper.writeValueAsString(extrato));
        } catch (SQLException | JsonProcessingException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
            Object valueUntype = bodyAsMap.get("valor");
            if (!(valueUntype instanceof Integer)) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return;
            }
            value = (Integer) bodyAsMap.get("valor");
            type = (String) bodyAsMap.get("tipo");
            description = (String) bodyAsMap.get("descricao");
            if (value == null || type == null || description == null ||
                    type.isBlank() || description.isBlank() || description.length() > 10
                    || !(type.equals("c") || type.equals("d"))
            ) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return;
            }
        } catch (Exception ignored) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        // UPDATE HERE
        try {
            Map<String, Object> result = doTransactionWithFunction(clientId, value, type, description);
            if (result == null) {
                ctx.status(HttpStatus.NOT_FOUND);
                return;
            }
            String resultAsStr = objectMapper.writeValueAsString(result);
            if (resultAsStr.contains("unprocess")) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return;
            }
            ctx.result(resultAsStr);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    static Map<String, Object> doTransactionWithFunction(Integer clientId, Integer value, String type, String description) {
        try (Connection conn = ds.getConnection()) {
            String functionCall = String.format(
                    "SELECT * FROM process_transaction(%d, %d, '%s', '%s')", clientId, value, type, description
            );
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(functionCall);
            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();
                int limite = rs.getInt("client_limit");
                int saldo = rs.getInt("client_balance");

                if (limite == -1 && saldo == -1) {
                    result.put("unprocess", 422);
                    return result;
                } else if (limite == 0 && saldo == 0) {
                    return null;
                }

                result.put("limite", limite);
                result.put("saldo", saldo);
                return result;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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
}