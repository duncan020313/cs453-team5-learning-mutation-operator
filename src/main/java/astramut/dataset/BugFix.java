package astramut.dataset;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BugFix(
    @JsonProperty("bugType")                                       String bugType,
    @JsonProperty("sourceBeforeFix") @JsonAlias({"before"})        String sourceBeforeFix,
    @JsonProperty("sourceAfterFix")  @JsonAlias({"after"})         String sourceAfterFix
) {
    public boolean hasSourceCode() {
        return sourceBeforeFix != null && !sourceBeforeFix.isBlank()
                && sourceAfterFix != null && !sourceAfterFix.isBlank();
    }
}