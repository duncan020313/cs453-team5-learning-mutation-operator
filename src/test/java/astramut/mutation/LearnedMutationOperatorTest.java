package astramut.mutation;

import astramut.learn.EditPattern;
import astramut.learn.Hole;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnedMutationOperatorImprovementTest {
    @Test
    void mutatesScopedMethodCallReceiverAndArguments() {
        EditPattern fixPattern = new EditPattern(
                scopedMethodCall(name("oldObj"), "foo", literal("1")),
                scopedMethodCall(name("newObj"), "foo", literal("0"))
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    void f(Service oldObj, Service newObj) {
                        newObj.foo(0);
                    }
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        assertTrue(mutants.get(0).mutatedSource().contains("oldObj.foo(1);"));
    }

    @Test
    void mutatesWhileStatementBody() {
        EditPattern fixPattern = new EditPattern(
                whileStmt(hole("?cond"), block(expressionStatement(methodCall("unsafe")))),
                whileStmt(hole("?cond"), block(expressionStatement(methodCall("safe"))))
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    void f(boolean ok) {
                        while (ok) {
                            safe();
                        }
                    }
                    void safe() {}
                    void unsafe() {}
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        assertTrue(mutants.get(0).mutatedSource().contains("unsafe();"));
    }

    @Test
    void reordersStatementsInsideLargerBlock() {
        TreePattern a = expressionStatement(methodCall("a"));
        TreePattern b = expressionStatement(methodCall("b"));

        EditPattern fixPattern = new EditPattern(
                block(b, a),
                block(a, b)
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    void f() {
                        pre();
                        a();
                        b();
                        post();
                    }
                    void pre() {}
                    void a() {}
                    void b() {}
                    void post() {}
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());

        boolean foundMove = mutants.stream()
                .map(Mutant::mutatedSource)
                .anyMatch(src -> src.indexOf("pre();") < src.indexOf("b();")
                        && src.indexOf("b();") < src.indexOf("a();")
                        && src.indexOf("a();") < src.indexOf("post();"));

        assertTrue(foundMove);
    }

    private static LearnedMutationOperator operatorFrom(EditPattern fixPattern) {
        LearnedPattern learnedPattern = new LearnedPattern(
                fixPattern,
                2,
                1.0,
                List.of(fixPattern, fixPattern)
        );

        return new LearnedMutationOperator(learnedPattern, 0);
    }

    private static TreePattern hole(String id) {
        return new Hole(id);
    }

    private static TreePattern name(String value) {
        return new TreeNode("SimpleName", value, List.of());
    }

    private static TreePattern literal(String value) {
        return new TreeNode("Literal", value, List.of());
    }

    private static TreePattern methodCall(String name, TreePattern... args) {
        return new TreeNode("MethodInvocation", "", List.of(
                new TreeNode("SimpleName", name, List.of()),
                new TreeNode("ARGS", "", List.of(args))
        ));
    }

    private static TreePattern scopedMethodCall(TreePattern scope, String name, TreePattern... args) {
        return new TreeNode("MethodInvocation", "", List.of(
                new TreeNode("SCOPE", "", List.of(scope)),
                new TreeNode("SimpleName", name, List.of()),
                new TreeNode("ARGS", "", List.of(args))
        ));
    }

    private static TreePattern whileStmt(TreePattern condition, TreePattern body) {
        return new TreeNode("WhileStatement", "", List.of(condition, body));
    }

    private static TreePattern expressionStatement(TreePattern expression) {
        return new TreeNode("ExpressionStatement", "", List.of(expression));
    }

    private static TreePattern block(TreePattern... statements) {
        return new TreeNode("Block", "", List.of(statements));
    }
}
