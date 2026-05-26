package astramut.mutation;

public record Mutant(
        String id,
        String operatorName,
        String sourceName,
        String mutatedSource,
        int occurrenceIndex,
        int lineNumber
) {
    public Mutant(
            String id,
            String operatorName,
            String sourceName,
            String mutatedSource,
            int occurrenceIndex
    ) {
        this(id, operatorName, sourceName, mutatedSource, occurrenceIndex, -1);
    }
}
