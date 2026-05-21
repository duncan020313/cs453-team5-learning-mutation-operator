package astramut.mutation;

import astramut.learn.EditPattern;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.BinaryExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


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

        CompilationUnit original;
        try {
            original = StaticJavaParser.parse(sourceCode);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to parse Java source: " + sourceName, e);
        }

        List<Candidate> candidates = collectCandidates(original, mutationPattern.before());
        List<MatchSite> matchedSites = new ArrayList<>();

        for (Candidate candidate : candidates) {
            TreePattern targetPattern = candidate.toPattern(original);
            if (targetPattern == null) {
                continue;
            }

            PatternMatcher.MatchResult matchResult = PatternMatcher.match(
                    mutationPattern.before(),
                    targetPattern
            );

            if (!matchResult.matched()) {
                continue;
            }

            TreePattern instantiated;
            try {
                instantiated = PatternInstantiator.instantiate(
                        mutationPattern.after(),
                        matchResult.bindings()
                );
            } catch (IllegalStateException e) {
                continue;
            }

            if (candidate.canReplaceWith(original, instantiated)) {
                matchedSites.add(new MatchSite(candidate, instantiated));
            }
        }

        List<Mutant> mutants = new ArrayList<>();
        Set<String> seenSources = new LinkedHashSet<>();

        for (int i = 0; i < matchedSites.size() && mutants.size() < maxMutants; i++) {
            MatchSite site = matchedSites.get(i);

            CompilationUnit cloned = original.clone();
            boolean replaced = site.candidate().replaceIn(cloned, site.replacementPattern());
            if (!replaced) {
                continue;
            }

            String mutatedSource = cloned.toString();
            if (!seenSources.add(mutatedSource)) {
                continue;
            }

            String mutantId = name + "-occurrence-" + site.candidate().globalIndex();

            mutants.add(new Mutant(
                    mutantId,
                    name,
                    sourceName,
                    mutatedSource,
                    site.candidate().globalIndex()
            ));
        }

        return mutants;
    }

    private static List<Candidate> collectCandidates(CompilationUnit compilationUnit, TreePattern matchPattern) {
        List<Candidate> candidates = new ArrayList<>();
        int globalIndex = 0;

        List<BodyDeclaration> declarations = compilationUnit.findAll(BodyDeclaration.class);
        for (int i = 0; i < declarations.size(); i++) {
            candidates.add(new NodeCandidate(CandidateKind.BODY_DECLARATION, i, globalIndex++));
        }

        List<Statement> statements = compilationUnit.findAll(Statement.class);
        for (int i = 0; i < statements.size(); i++) {
            candidates.add(new NodeCandidate(CandidateKind.STATEMENT, i, globalIndex++));
        }

        List<Expression> expressions = compilationUnit.findAll(Expression.class);
        for (int i = 0; i < expressions.size(); i++) {
            candidates.add(new NodeCandidate(CandidateKind.EXPRESSION, i, globalIndex++));
        }

        /*
         * Some learned CHANGE_OPERATOR patterns are not whole InfixExpression patterns.
         They directly target the pseudo node: INFIX_EXPRESSION_OPERATOR("==")
         *
         * JavaParser does not expose the operator as a replaceable AST Node;
         * it is a field inside BinaryExpr. Therefore we add a special candidate
         * that matches and rewrites only BinaryExpr.getOperator().
         */
        if (matchPattern instanceof TreeNode n && n.type().equals("INFIX_EXPRESSION_OPERATOR")) {
            List<BinaryExpr> binaryExprs = compilationUnit.findAll(BinaryExpr.class);
            for (int i = 0; i < binaryExprs.size(); i++) {
                candidates.add(new BinaryOperatorCandidate(i, globalIndex++));
            }
        }

        /*
         * A learned block pattern such as Block[?a, stmt, ?b] -> Block[?a, ?b]
         * should fire even when it appears inside a larger method body: pre(); a(); stmt; b(); post();
         *
         * Node.replace(BlockStmt) can only replace the whole block.
         * This window candidate matches a contiguous statement slice and rewrites only that slice.
         */
        if (matchPattern instanceof TreeNode n && isBlock(n.type())) {
            int windowSize = n.children().size();
            if (windowSize > 0) {
                List<BlockStmt> blocks = compilationUnit.findAll(BlockStmt.class);
                for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                    BlockStmt block = blocks.get(blockIndex);
                    int statementCount = block.getStatements().size();
                    for (int start = 0; start + windowSize <= statementCount; start++) {
                        candidates.add(new BlockWindowCandidate(blockIndex, start, windowSize, globalIndex++));
                    }
                }
            }
        }

        return candidates;
    }

    private interface Candidate {
        int globalIndex();

        TreePattern toPattern(CompilationUnit compilationUnit);

        boolean canReplaceWith(CompilationUnit compilationUnit, TreePattern replacementPattern);

        boolean replaceIn(CompilationUnit compilationUnit, TreePattern replacementPattern);
    }

    private enum CandidateKind {
        BODY_DECLARATION,
        STATEMENT,
        EXPRESSION
    }

    private record NodeCandidate(CandidateKind kind, int indexWithinKind, int globalIndex) implements Candidate {
        @Override
        public TreePattern toPattern(CompilationUnit compilationUnit) {
            Node node = resolve(compilationUnit);
            if (node == null) {
                return null;
            }
            return JavaParserTreeAdapter.toPattern(node);
        }

        @Override
        public boolean canReplaceWith(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            Node target = resolve(compilationUnit);
            return target != null && JavaParserTreeBuilder.buildForContext(replacementPattern, target).isPresent();
        }

        @Override
        public boolean replaceIn(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            Node target = resolve(compilationUnit);
            if (target == null) {
                return false;
            }

            Optional<Node> replacement = JavaParserTreeBuilder.buildForContext(replacementPattern, target);
            return replacement.filter(target::replace).isPresent();
        }

        private Node resolve(CompilationUnit compilationUnit) {
            if (kind == CandidateKind.BODY_DECLARATION) {
                List<BodyDeclaration> declarations = compilationUnit.findAll(BodyDeclaration.class);
                if (indexWithinKind >= declarations.size()) {
                    return null;
                }
                return declarations.get(indexWithinKind);
            }

            if (kind == CandidateKind.STATEMENT) {
                List<Statement> statements = compilationUnit.findAll(Statement.class);
                if (indexWithinKind >= statements.size()) {
                    return null;
                }
                return statements.get(indexWithinKind);
            }

            List<Expression> expressions = compilationUnit.findAll(Expression.class);
            if (indexWithinKind >= expressions.size()) {
                return null;
            }
            return expressions.get(indexWithinKind);
        }
    }

    private record BlockWindowCandidate(
            int blockIndex,
            int start,
            int length,
            int globalIndex
    ) implements Candidate {
        @Override
        public TreePattern toPattern(CompilationUnit compilationUnit) {
            BlockStmt block = resolveBlock(compilationUnit);
            if (block == null || start + length > block.getStatements().size()) {
                return null;
            }

            List<TreePattern> children = new ArrayList<>();
            for (int i = start; i < start + length; i++) {
                children.add(JavaParserTreeAdapter.toPattern(block.getStatements().get(i)));
            }
            return new TreeNode("Block", "", children);
        }

        @Override
        public boolean canReplaceWith(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            return resolveBlock(compilationUnit) != null
                    && JavaParserTreeBuilder.buildStatementList(replacementPattern).isPresent();
        }

        @Override
        public boolean replaceIn(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            BlockStmt block = resolveBlock(compilationUnit);
            if (block == null || start + length > block.getStatements().size()) {
                return false;
            }

            Optional<List<Statement>> replacementStatements = JavaParserTreeBuilder.buildStatementList(replacementPattern);
            if (replacementStatements.isEmpty()) {
                return false;
            }

            for (int i = 0; i < length; i++) {
                block.getStatements().remove(start);
            }

            int insertAt = start;
            for (Statement statement : replacementStatements.get()) {
                block.addStatement(insertAt++, statement);
            }

            return true;
        }

        private BlockStmt resolveBlock(CompilationUnit compilationUnit) {
            List<BlockStmt> blocks = compilationUnit.findAll(BlockStmt.class);
            if (blockIndex >= blocks.size()) {
                return null;
            }
            return blocks.get(blockIndex);
        }
    }

    private record BinaryOperatorCandidate(int indexWithinKind, int globalIndex) implements Candidate {
        @Override
        public int globalIndex() {
            return globalIndex;
        }

        @Override
        public TreePattern toPattern(CompilationUnit compilationUnit) {
            BinaryExpr binaryExpr = resolve(compilationUnit);
            if (binaryExpr == null) {
                return null;
            }

            return new TreeNode(
                    "INFIX_EXPRESSION_OPERATOR",
                    OperatorToken.fromBinaryOperator(binaryExpr.getOperator()),
                    List.of()
            );
        }

        @Override
        public boolean canReplaceWith(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            return resolve(compilationUnit) != null
                    && replacementOperator(replacementPattern).isPresent();
        }

        @Override
        public boolean replaceIn(CompilationUnit compilationUnit, TreePattern replacementPattern) {
            BinaryExpr binaryExpr = resolve(compilationUnit);
            Optional<BinaryExpr.Operator> replacement = replacementOperator(replacementPattern);

            if (binaryExpr == null || replacement.isEmpty()) {
                return false;
            }

            binaryExpr.setOperator(replacement.get());
            return true;
        }

        private BinaryExpr resolve(CompilationUnit compilationUnit) {
            List<BinaryExpr> binaryExprs = compilationUnit.findAll(BinaryExpr.class);
            if (indexWithinKind >= binaryExprs.size()) {
                return null;
            }
            return binaryExprs.get(indexWithinKind);
        }

        private static Optional<BinaryExpr.Operator> replacementOperator(TreePattern replacementPattern) {
            if (!(replacementPattern instanceof TreeNode n)) {
                return Optional.empty();
            }

            if (!n.type().equals("INFIX_EXPRESSION_OPERATOR")) {
                return Optional.empty();
            }

            return OperatorToken.toBinaryOperator(n.label());
        }
    }

    private record MatchSite(Candidate candidate, TreePattern replacementPattern) {
    }

    private static boolean isBlock(String type) {
        return type.equals("Block")
                || type.equals("BlockStmt")
                || type.equals("BlockStatement");
    }
}
