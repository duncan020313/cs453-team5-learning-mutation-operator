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

class LearnedMutationOperatorTest {

    @Test
    void mutatesScopedMethodCallReceiverAndArguments() {
        EditPattern fixPattern = new EditPattern(
                expressionStatement(methodCall(
                        receiver(simpleName("backup")),
                        "foo",
                        simpleName("b"),
                        simpleName("a")
                )),
                expressionStatement(methodCall(
                        receiver(simpleName("service")),
                        "foo",
                        simpleName("a"),
                        simpleName("b")
                ))
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    Service service;
                    Service backup;

                    void f(int a, int b) {
                        service.foo(a, b);
                    }

                    static class Service {
                        void foo(int x, int y) {
                        }
                    }
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        String mutated = mutants.get(0).mutatedSource();

        assertTrue(mutated.contains("backup.foo(b, a);"), mutated);
    }

    @Test
    void mutatesWhileStatementBody() {
        TreePattern condition = hole("?cond");

        EditPattern fixPattern = new EditPattern(
                whileStatement(
                        condition,
                        block(expressionStatement(methodCall(null, "skip")))
                ),
                whileStatement(
                        condition,
                        block(expressionStatement(methodCall(null, "tick")))
                )
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    void f(int x) {
                        while (x > 0) {
                            tick();
                        }
                    }

                    void tick() {
                    }

                    void skip() {
                    }
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        String mutated = mutants.get(0).mutatedSource();

        assertTrue(mutated.contains("while (x > 0)"), mutated);
        assertTrue(mutated.contains("skip();"), mutated);
        assertFalse(mutated.contains("tick();"), mutated);
    }

    @Test
    void reordersStatementsInsideLargerBlock() {
        EditPattern fixPattern = new EditPattern(
                block(
                        expressionStatement(methodCall(null, "b")),
                        expressionStatement(methodCall(null, "a"))
                ),
                block(
                        expressionStatement(methodCall(null, "a")),
                        expressionStatement(methodCall(null, "b"))
                )
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

                    void pre() {
                    }

                    void a() {
                    }

                    void b() {
                    }

                    void post() {
                    }
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        String mutated = mutants.get(0).mutatedSource();

        int pre = mutated.indexOf("pre();");
        int b = mutated.indexOf("b();");
        int a = mutated.indexOf("a();");
        int post = mutated.indexOf("post();");

        assertTrue(pre >= 0, mutated);
        assertTrue(b >= 0, mutated);
        assertTrue(a >= 0, mutated);
        assertTrue(post >= 0, mutated);

        assertTrue(pre < b, mutated);
        assertTrue(b < a, mutated);
        assertTrue(a < post, mutated);
    }

    @Test
    void mutatesInfixOperatorByLearnedOperatorNode() {
        EditPattern fixPattern = new EditPattern(
                infixOperator("!="),
                infixOperator("==")
        );

        LearnedMutationOperator operator = operatorFrom(fixPattern);

        String source = """
                class Example {
                    boolean same(int x, int y) {
                        return x == y;
                    }
                }
                """;

        List<Mutant> mutants = operator.generateMutants(source, "Example.java", 10);

        assertFalse(mutants.isEmpty());
        String mutated = mutants.get(0).mutatedSource();

        assertTrue(mutated.contains("return x != y;"), mutated);
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

    private static TreePattern simpleName(String name) {
        return new TreeNode("SimpleName", name, List.of());
    }

    private static TreePattern receiver(TreePattern expression) {
        return new TreeNode("METHOD_INVOCATION_RECEIVER", "", List.of(expression));
    }

    private static TreePattern arguments(TreePattern... args) {
        return new TreeNode("METHOD_INVOCATION_ARGUMENTS", "", List.of(args));
    }

    private static TreePattern methodCall(TreePattern receiver, String name, TreePattern... args) {
        List<TreePattern> children = new java.util.ArrayList<>();

        if (receiver != null) {
            children.add(receiver);
        }

        children.add(simpleName(name));
        children.add(arguments(args));

        return new TreeNode("MethodInvocation", "", children);
    }

    private static TreePattern expressionStatement(TreePattern expression) {
        return new TreeNode("ExpressionStatement", "", List.of(expression));
    }

    private static TreePattern block(TreePattern... statements) {
        return new TreeNode("Block", "", List.of(statements));
    }

    private static TreePattern whileStatement(TreePattern condition, TreePattern body) {
        return new TreeNode("WhileStatement", "", List.of(condition, body));
    }

    private static TreePattern infixOperator(String operator) {
        return new TreeNode("INFIX_EXPRESSION_OPERATOR", operator, List.of());
    }
}