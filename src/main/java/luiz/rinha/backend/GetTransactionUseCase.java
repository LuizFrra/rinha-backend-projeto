package luiz.rinha.backend;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import luiz.rinha.backend.models.extract.Extract;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GetTransactionUseCase {
    static public final String resource = "/clientes/{id}/extrato";

    public static void execute(Context ctx) {
        if (!isValidRequest(ctx)) {
            return;
        }

        Extract extract = Database.execute((conn) -> {
            try {
                int clientId = Integer.parseInt(ctx.pathParam("id"));
                PreparedStatement st = conn.prepareStatement(Queries.GET_TRANSACTION);
                st.setInt(1, clientId);
                ResultSet rs = st.executeQuery();
                return Extract.from(rs);
            } catch (SQLException e) {
                return null;
            }
        });

        if (extract == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        ctx.json(extract);
    }


    public static boolean isValidRequest(Context ctx) {
        String clientIdAsStr = ctx.pathParam("id");
        try {
            Integer.valueOf(clientIdAsStr);
            return true;
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return false;
        }
    }
}
