package astramut.mutation;

import astramut.learn.EditPattern;

import java.util.List;
import java.util.function.Predicate;

public interface MutationOperator {
    String name();

    EditPattern pattern();

    List<Mutant> generateMutants(String sourceCode, String sourceName, int maxMutants);

    default List<Mutant> generateMutants(
            String sourceCode,
            String sourceName,
            int maxMutants,
            Predicate<Mutant> mutantFilter
    ) {
        if (maxMutants <= 0) {
            return List.of();
        }
        return generateMutants(sourceCode, sourceName, Integer.MAX_VALUE).stream()
                .filter(mutantFilter)
                .limit(maxMutants)
                .toList();
    }

    default String apply(String sourceCode) {
        List<Mutant> mutants = generateMutants(sourceCode, "<memory>", 1);
        if (mutants.isEmpty()) {
            return sourceCode;
        }
        return mutants.get(0).mutatedSource();
    }
}
