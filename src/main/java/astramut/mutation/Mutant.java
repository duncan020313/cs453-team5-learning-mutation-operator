package astramut.mutation;

public record Mutant(
        String id,
        String operatorName,
        String sourceName,
        String mutatedSource,
        int occurrenceIndex
) {
}