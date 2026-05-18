package astramut.mutation;

import astramut.learn.EditPattern;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LearnedMutationOperator implements MutationOperator {
    private final LearnedPattern learnedPattern;
    private final EditPattern mutationPattern;
    private final String name;

    public LearnedMutationOperator(LearnedPattern learnedPattern, int index) {
        this.learnedPattern = learnedPattern;

        EditPattern fixPattern = learnedPattern.pattern();

        this.mutationPattern = new EditPattern(
                fixPattern.after(),
                fixPattern.before()
        );

        this.name = "learned-" + index
                + "-support-" + learnedPattern.support()
                + "-score-" + String.format("%.3f", learnedPattern.score());
    }

    public LearnedPattern learnedPattern() {
        return learnedPattern;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public EditPattern pattern() {
        return mutationPattern;
    }

    @Override
    public List<Mutant> generateMutants(String sourceCode, String sourceName, int maxMutants) {
        if (maxMutants <= 0) {
            return List.of();
        }

        Optional<BinaryExpr.Operator> replacementOperator = replacementBinaryOperator();
        if (replacementOperator.isEmpty()) {
            return List.of();
        }

        CompilationUnit original;
        try {
            original = StaticJavaParser.parse(sourceCode);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to parse Java source: " + sourceName, e);
        }

        List<BinaryExpr> candidates = original.findAll(BinaryExpr.class);
        List<Integer> matchedIndexes = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            BinaryExpr candidate = candidates.get(i);
            TreePattern candidatePattern = toPattern(candidate);

            PatternMatcher.MatchResult result = PatternMatcher.match(mutationPattern.before(), candidatePattern);
            if (result.matched()) {
                matchedIndexes.add(i);
            }
        }

        List<Mutant> mutants = new ArrayList<>();
        for (int i = 0; i < matchedIndexes.size() && mutants.size() < maxMutants; i++) {
            int occurrenceIndex = matchedIndexes.get(i);

            CompilationUnit cloned = original.clone();
            List<BinaryExpr> clonedBinaryExpressions = cloned.findAll(BinaryExpr.class);

            if (occurrenceIndex >= clonedBinaryExpressions.size()) {
                continue;
            }

            BinaryExpr target = clonedBinaryExpressions.get(occurrenceIndex);
            if (target.getOperator() == replacementOperator.get()) {
                continue;
            }

            target.setOperator(replacementOperator.get());

            String mutantId = name + "-occurrence-" + occurrenceIndex;
            mutants.add(new Mutant(
                    mutantId,
                    name,
                    sourceName,
                    cloned.toString(),
                    occurrenceIndex
            ));
        }

        return mutants;
    }

    private Optional<BinaryExpr.Operator> replacementBinaryOperator() {
        if (!(mutationPattern.after() instanceof TreeNode afterNode)) {
            return Optional.empty();
        }

        if (!afterNode.type().equals("InfixExpression")) {
            return Optional.empty();
        }

        return OperatorToken.toBinaryOperator(afterNode.label());
    }

    private static TreePattern toPattern(Expression expression) {
        if (expression instanceof BinaryExpr b) {
            return new TreeNode(
                    "InfixExpression",
                    OperatorToken.fromBinaryOperator(b.getOperator()),
                    List.of(toPattern(b.getLeft()), toPattern(b.getRight()))
            );
        }

        if (expression instanceof NameExpr n) {
            return new TreeNode("SimpleName", n.getNameAsString(), List.of());
        }

        if (expression instanceof NullLiteralExpr) {
            return new TreeNode("NullLiteral", "null", List.of());
        }

        if (expression instanceof BooleanLiteralExpr b) {
            return new TreeNode("BooleanLiteral", String.valueOf(b.getValue()), List.of());
        }

        if (expression instanceof IntegerLiteralExpr i) {
            return new TreeNode("NumberLiteral", i.getValue(), List.of());
        }

        if (expression instanceof LongLiteralExpr l) {
            return new TreeNode("NumberLiteral", l.getValue(), List.of());
        }

        if (expression instanceof DoubleLiteralExpr d) {
            return new TreeNode("NumberLiteral", d.getValue(), List.of());
        }

        if (expression instanceof CharLiteralExpr c) {
            return new TreeNode("CharacterLiteral", c.getValue(), List.of());
        }

        if (expression instanceof StringLiteralExpr s) {
            return new TreeNode("StringLiteral", s.getValue(), List.of());
        }

        return new TreeNode(expression.getClass().getSimpleName(), expression.toString(), List.of());
    }

    @SuppressWarnings("unused")
    private static TreePattern toPattern(Node node) {
        if (node instanceof Expression e) {
            return toPattern(e);
        }

        if (node instanceof SimpleName s) {
            return new TreeNode("SimpleName", s.asString(), List.of());
        }

        return new TreeNode(node.getClass().getSimpleName(), node.toString(), List.of());
    }
}