package luiz.rinha.backend.models.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LastTransactions(
        @JsonProperty("valor")
        int value,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("descricao")
        String description,
        @JsonProperty("realizado_em")
        String time
) {
}
