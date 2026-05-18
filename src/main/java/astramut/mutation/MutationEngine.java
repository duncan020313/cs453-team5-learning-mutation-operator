package astramut.mutation;

import astramut.learn.LearnedModel;
import astramut.learn.LearnedPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MutationEngine {
    private final List<MutationOperator> operators;

    public MutationEngine(List<MutationOperator> operators) {
        this.operators = List.copyOf(operators);
    }

    public static MutationEngine fromLearnedModel(
            LearnedModel model,
            int maxOperators,
            int minSupport,
            double minSpecificity
    ) {
        List<LearnedPattern> selectedPatterns = model.patterns().stream()
                .filter(p -> p.support() >= minSupport)
                .filter(p -> p.specificity() >= minSpecificity)
                .sorted(Comparator.comparingDouble(LearnedPattern::score).reversed())
                .limit(maxOperators)
                .toList();

        List<MutationOperator> operators = new ArrayList<>();
        for (int i = 0; i < selectedPatterns.size(); i++) {
            operators.add(new LearnedMutationOperator(selectedPatterns.get(i), i));
        }

        return new MutationEngine(operators);
    }

    public List<MutationOperator> operators() {
        return operators;
    }

    public List<Mutant> generateMutants(
            String sourceCode,
            String sourceName,
            int maxMutantsPerOperator
    ) {
        List<Mutant> mutants = new ArrayList<>();

        for (MutationOperator operator : operators) {
            mutants.addAll(operator.generateMutants(
                    sourceCode,
                    sourceName,
                    maxMutantsPerOperator
            ));
        }

        return mutants;
    }
}