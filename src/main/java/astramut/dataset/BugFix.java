package astramut.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BugFix(
    @JsonProperty("bugType")              String bugType,
    @JsonProperty("sourceBeforeFix")      String sourceBeforeFix,
    @JsonProperty("sourceAfterFix")       String sourceAfterFix
) {
    public boolean hasSourceCode() {
        return sourceBeforeFix != null && !sourceBeforeFix.isBlank()
                && sourceAfterFix != null && !sourceAfterFix.isBlank();
    }
}