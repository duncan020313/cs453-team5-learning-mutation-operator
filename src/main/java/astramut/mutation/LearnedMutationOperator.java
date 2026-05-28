package astramut.mutation;

import astramut.learn.EditPattern;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.type.PrimitiveType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;


public final class LearnedMutationOperator implements MutationOperator {
    private final LearnedPattern learnedPattern;
    private final EditPattern mutationPattern;
    private final MagicValueSampler magicSampler;
    private final String name;

    public LearnedMutationOperator(LearnedPattern learnedPattern, int index) {
        this(learnedPattern, index, MagicValueSampler.empty());
    }

    public LearnedMutationOperator(LearnedPattern learnedPattern, int index, MagicValueSampler magicSampler) {
        this.learnedPattern = learnedPattern;

        EditPattern fixPattern = learnedPattern.pattern();

        this.mutationPattern = new EditPattern(
                fixPattern.after(),
                fixPattern.before()
        );

        this.magicSampler = magicSampler != null ? magicSampler : MagicValueSampler.empty();
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
        return generateMutants(sourceCode, sourceName, maxMutants, mutant -> true);
    }

    @Override
    public List<Mutant> generateMutants(
            String sourceCode,
            String sourceName,
            int maxMutants,
            Predicate<Mutant> mutantFilter
    ) {
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
                        matchResult.bindings(),
                        magicSampler
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
            String mutantId = name + "-occurrence-" + site.candidate().globalIndex();

            Mutant mutant = new Mutant(
                    mutantId,
                    name,
                    sourceName,
                    mutatedSource,
                    site.candidate().globalIndex(),
                    site.candidate().lineNumber(original)
            );
            if (!mutantFilter.test(mutant)) {
                continue;
            }
            if (!seenSources.add(mutatedSource)) {
                continue;
            }

            mutants.add(mutant);
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

        // Modifier swap (private↔public↔protected etc.) — JavaParser exposes Modifier as a Node.
        if (matchPattern instanceof TreeNode n && n.type().equals("Modifier")) {
            List<Modifier> mods = compilationUnit.findAll(Modifier.class);
            for (int i = 0; i < mods.size(); i++) {
                candidates.add(new ModifierCandidate(i, globalIndex++));
            }
        }

        // PrimitiveType swap (int↔long↔double, void↔boolean) — Node.replace works on PrimitiveType.
        if (matchPattern instanceof TreeNode n && n.type().equals("PrimitiveType")) {
            List<PrimitiveType> types = compilationUnit.findAll(PrimitiveType.class);
            for (int i = 0; i < types.size(); i++) {
                candidates.add(new PrimitiveTypeCandidate(i, globalIndex++));
            }
        }

        // AssignmentOperator swap (= ↔ += etc.) — like BinaryOperator, operator is a field of AssignExpr.
        if (matchPattern instanceof TreeNode n && n.type().equals("ASSIGNMENT_OPERATOR")) {
            List<AssignExpr> assignments = compilationUnit.findAll(AssignExpr.class);
            for (int i = 0; i < assignments.size(); i++) {
                candidates.add(new AssignmentOperatorCandidate(i, globalIndex++));
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

        int lineNumber(CompilationUnit compilationUnit);
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

        @Override
        public int lineNumber(CompilationUnit compilationUnit) {
            Node target = resolve(compilationUnit);
            return target == null
                    ? -1
                    : target.getRange().map(range -> range.begin.line).orElse(-1);
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

        @Override
        public int lineNumber(CompilationUnit compilationUnit) {
            BlockStmt block = resolveBlock(compilationUnit);
            if (block == null || start >= block.getStatements().size()) {
                return -1;
            }
            return block.getStatements().get(start).getRange().map(range -> range.begin.line).orElse(-1);
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

        @Override
        public int lineNumber(CompilationUnit compilationUnit) {
            BinaryExpr binaryExpr = resolve(compilationUnit);
            return binaryExpr == null
                    ? -1
                    : binaryExpr.getRange().map(range -> range.begin.line).orElse(-1);
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

    private record ModifierCandidate(int indexWithinKind, int globalIndex) implements Candidate {
        @Override public int globalIndex() { return globalIndex; }

        @Override public TreePattern toPattern(CompilationUnit cu) {
            Modifier m = resolve(cu);
            if (m == null) return null;
            return new TreeNode("Modifier", m.getKeyword().asString(), List.of());
        }

        @Override public boolean canReplaceWith(CompilationUnit cu, TreePattern repl) {
            return resolve(cu) != null && replacementKeyword(repl).isPresent();
        }

        @Override public boolean replaceIn(CompilationUnit cu, TreePattern repl) {
            Modifier m = resolve(cu);
            Optional<Modifier.Keyword> kw = replacementKeyword(repl);
            if (m == null || kw.isEmpty()) return false;
            m.setKeyword(kw.get());
            return true;
        }

        @Override public int lineNumber(CompilationUnit cu) {
            Modifier m = resolve(cu);
            return m == null ? -1 : m.getRange().map(r -> r.begin.line).orElse(-1);
        }

        private Modifier resolve(CompilationUnit cu) {
            List<Modifier> all = cu.findAll(Modifier.class);
            return indexWithinKind < all.size() ? all.get(indexWithinKind) : null;
        }

        private static Optional<Modifier.Keyword> replacementKeyword(TreePattern p) {
            if (!(p instanceof TreeNode n) || !n.type().equals("Modifier")) return Optional.empty();
            for (Modifier.Keyword k : Modifier.Keyword.values()) {
                if (k.asString().equals(n.label())) return Optional.of(k);
            }
            return Optional.empty();
        }
    }

    private record PrimitiveTypeCandidate(int indexWithinKind, int globalIndex) implements Candidate {
        @Override public int globalIndex() { return globalIndex; }

        @Override public TreePattern toPattern(CompilationUnit cu) {
            PrimitiveType t = resolve(cu);
            if (t == null) return null;
            return new TreeNode("PrimitiveType", t.getType().asString(), List.of());
        }

        @Override public boolean canReplaceWith(CompilationUnit cu, TreePattern repl) {
            return resolve(cu) != null && replacementType(repl).isPresent();
        }

        @Override public boolean replaceIn(CompilationUnit cu, TreePattern repl) {
            PrimitiveType t = resolve(cu);
            Optional<PrimitiveType.Primitive> p = replacementType(repl);
            if (t == null || p.isEmpty()) return false;
            t.setType(p.get());
            return true;
        }

        @Override public int lineNumber(CompilationUnit cu) {
            PrimitiveType t = resolve(cu);
            return t == null ? -1 : t.getRange().map(r -> r.begin.line).orElse(-1);
        }

        private PrimitiveType resolve(CompilationUnit cu) {
            List<PrimitiveType> all = cu.findAll(PrimitiveType.class);
            return indexWithinKind < all.size() ? all.get(indexWithinKind) : null;
        }

        private static Optional<PrimitiveType.Primitive> replacementType(TreePattern p) {
            if (!(p instanceof TreeNode n) || !n.type().equals("PrimitiveType")) return Optional.empty();
            // Pattern labels: "int", "long", "boolean", "void", "double", "float", "byte", "short", "char"
            for (PrimitiveType.Primitive prim : PrimitiveType.Primitive.values()) {
                if (prim.asString().equals(n.label())) return Optional.of(prim);
            }
            return Optional.empty();
        }
    }

    private record AssignmentOperatorCandidate(int indexWithinKind, int globalIndex) implements Candidate {
        @Override public int globalIndex() { return globalIndex; }

        @Override public TreePattern toPattern(CompilationUnit cu) {
            AssignExpr a = resolve(cu);
            if (a == null) return null;
            return new TreeNode("ASSIGNMENT_OPERATOR", a.getOperator().asString(), List.of());
        }

        @Override public boolean canReplaceWith(CompilationUnit cu, TreePattern repl) {
            return resolve(cu) != null && replacementOp(repl).isPresent();
        }

        @Override public boolean replaceIn(CompilationUnit cu, TreePattern repl) {
            AssignExpr a = resolve(cu);
            Optional<AssignExpr.Operator> op = replacementOp(repl);
            if (a == null || op.isEmpty()) return false;
            a.setOperator(op.get());
            return true;
        }

        @Override public int lineNumber(CompilationUnit cu) {
            AssignExpr a = resolve(cu);
            return a == null ? -1 : a.getRange().map(r -> r.begin.line).orElse(-1);
        }

        private AssignExpr resolve(CompilationUnit cu) {
            List<AssignExpr> all = cu.findAll(AssignExpr.class);
            return indexWithinKind < all.size() ? all.get(indexWithinKind) : null;
        }

        private static Optional<AssignExpr.Operator> replacementOp(TreePattern p) {
            if (!(p instanceof TreeNode n) || !n.type().equals("ASSIGNMENT_OPERATOR")) return Optional.empty();
            for (AssignExpr.Operator op : AssignExpr.Operator.values()) {
                if (op.asString().equals(n.label())) return Optional.of(op);
            }
            return Optional.empty();
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
