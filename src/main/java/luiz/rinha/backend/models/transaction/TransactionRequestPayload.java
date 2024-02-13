package luiz.rinha.backend.models.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionRequestPayload(
        @JsonProperty("valor")
        int value,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("descricao")
        String description
) {
}
