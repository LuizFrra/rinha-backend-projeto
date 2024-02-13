package luiz.rinha.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import luiz.rinha.backend.models.transaction.TransactionRequestPayload;
import luiz.rinha.backend.models.transaction.TransactionResponsePayload;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class AddTransactionUseCase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static public final String resource = "/clientes/{id}/transacoes";

    public static void execute(Context ctx) {
        if(!isValidRequest(ctx)) {
            return;
        }

        int clientId = Integer.parseInt(ctx.pathParam("id"));
        TransactionRequestPayload payload = getPayload(ctx);
        assert payload != null;

        TransactionResponsePayload response = Database.execute((conn) -> {
            try {
                PreparedStatement st = conn.prepareStatement(Queries.PROCESS_TRANSACTION);
                st.setInt(1, clientId);
                st.setInt(2, payload.value());
                st.setString(3, payload.type());
                st.setString(4, payload.description());
                ResultSet rs = st.executeQuery();
                return TransactionResponsePayload.from(rs);
            } catch (SQLException e) {
                return null;
            }
        });

        if (response.limit() == -1) {
            ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
        } else if (response.limit() == 0 && response.balance() == 0) {
            ctx.status(HttpStatus.NOT_FOUND);
        } else {
            ctx.status(HttpStatus.OK);
            ctx.json(response);
        }
    }

    private static TransactionRequestPayload getPayload(Context ctx) {
        try {
            return objectMapper.readValue(ctx.bodyAsBytes(), TransactionRequestPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isValidRequest(Context ctx) {
        try {
            Integer.parseInt(ctx.pathParam("id"));
            HashMap<String, Object> bodyAsMap = objectMapper.readValue(ctx.bodyAsBytes(), HashMap.class);
            Object valueTransaction = bodyAsMap.get("valor");

            if (!(valueTransaction instanceof Integer)) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return false;
            }

            Integer value = (Integer) bodyAsMap.get("valor");
            String type = (String) bodyAsMap.get("tipo");
            String description = (String) bodyAsMap.get("descricao");

            if (value == null || type == null || description == null ||
                    description.isBlank() || description.length() > 10
                    || !(type.equals("c") || type.equals("d"))
            ) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT);
                return false;
            }

            return true;
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return false;
        }
    }
}
