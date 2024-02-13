package luiz.rinha.backend.models.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.ResultSet;
import java.sql.SQLException;

public record TransactionResponsePayload(
        @JsonProperty("saldo")
        int balance,
        @JsonProperty("limite")
        int limit
) {
    public static TransactionResponsePayload from(ResultSet rs) {
        try {
            if (rs.next()) {
                int limit = rs.getInt("client_limit");
                int balance = rs.getInt("client_balance");
                return new TransactionResponsePayload(balance, limit);
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }
}
