package luiz.rinha.backend.models.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public record Extract(
        @JsonProperty("saldo")
        CurrentBalanceExtract balance,
        @JsonProperty("ultimas_transacoes")
        List<LastTransactions> lastTransactions
) {
    public static Extract from(ResultSet rs) throws SQLException {
        Integer total = null;
        Integer limit = null;
        boolean userExist = false;
        List<LastTransactions> transactions = new ArrayList<>();

        while (rs.next() && transactions.size() < 10) {
            userExist = true;
            if (limit == null) {
                limit = rs.getInt("limite");
            }
            String typeTransaction = rs.getString("transacao_type");
            if (!"s".equals(typeTransaction)) {
                if (total == null) {
                    total = rs.getInt("valor_apos_transacao");
                }
                int valueTransaction = rs.getInt("valor");
                String timeTransaction = rs.getTimestamp("transacao_time").toString() + "Z";
                String descriptionTransaction = rs.getString("descricao");
                transactions.add(
                        new LastTransactions(valueTransaction, typeTransaction, descriptionTransaction, timeTransaction)
                );
            }
        }

        if (userExist && total == null) {
            total = 0;
        }

        if (!userExist) {
            return null;
        }

        CurrentBalanceExtract currentBalance = new CurrentBalanceExtract(total, limit, currentDate());
        return new Extract(currentBalance, transactions);
    }

    private static String currentDate() {
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
        return zonedDateTime.format(formatter);
    }
}

