package luiz.rinha.backend.models.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentBalanceExtract(
        int total,
        @JsonProperty("limite")
        int limit,
        @JsonProperty("data_extrato")
        String dateExtract) {
}
