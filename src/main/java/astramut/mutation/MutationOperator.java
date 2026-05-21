package astramut.mutation;

import astramut.learn.EditPattern;

import java.util.List;

public interface MutationOperator {
    String name();

    EditPattern pattern();

    List<Mutant> generateMutants(String sourceCode, String sourceName, int maxMutants);

    default String apply(String sourceCode) {
        List<Mutant> mutants = generateMutants(sourceCode, "<memory>", 1);
        if (mutants.isEmpty()) {
            return sourceCode;
        }
        return mutants.get(0).mutatedSource();
    }
}