package astramut.mutation;

import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class JavaParserTreeAdapter {
    private JavaParserTreeAdapter() {
    }

    public static TreePattern toPattern(Node node) {
        if (node instanceof Statement statement) {
            return statementToPattern(statement);
        }

        if (node instanceof Expression expression) {
            return expressionToPattern(expression);
        }

        if (node instanceof BodyDeclaration<?> bodyDeclaration) {
            return bodyDeclarationToPattern(bodyDeclaration);
        }

        return new TreeNode(node.getClass().getSimpleName(), node.toString(), List.of());
    }

    private static TreePattern bodyDeclarationToPattern(BodyDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classDecl) {
            List<TreePattern> children = new ArrayList<>();
            for (BodyDeclaration<?> member : classDecl.getMembers()) {
                children.add(bodyDeclarationToPattern(member));
            }
            return new TreeNode("ClassOrInterfaceDeclaration", classDecl.getNameAsString(), children);
        }

        if (declaration instanceof MethodDeclaration methodDecl) {
            List<TreePattern> children = new ArrayList<>();
            methodDecl.getBody().ifPresent(body -> children.add(statementToPattern(body)));
            return new TreeNode("MethodDeclaration", methodDecl.getDeclarationAsString(false, false, false), children);
        }

        if (declaration instanceof ConstructorDeclaration constructorDecl) {
            return new TreeNode(
                    "ConstructorDeclaration",
                    constructorDecl.getDeclarationAsString(false, false, false),
                    List.of(statementToPattern(constructorDecl.getBody()))
            );
        }

        return new TreeNode(declaration.getClass().getSimpleName(), declaration.toString(), List.of());
    }

    private static TreePattern statementToPattern(Statement statement) {
        if (statement instanceof BlockStmt blockStmt) {
            List<TreePattern> children = new ArrayList<>();
            for (Statement child : blockStmt.getStatements()) {
                children.add(statementToPattern(child));
            }
            return new TreeNode("Block", "", children);
        }

        if (statement instanceof IfStmt ifStmt) {
            List<TreePattern> children = new ArrayList<>();
            children.add(expressionToPattern(ifStmt.getCondition()));
            children.add(statementToPattern(ifStmt.getThenStmt()));
            ifStmt.getElseStmt().ifPresent(elseStmt -> children.add(statementToPattern(elseStmt)));
            return new TreeNode("IfStatement", "", children);
        }

        if (statement instanceof WhileStmt whileStmt) {
            return new TreeNode("WhileStatement", "", List.of(
                    expressionToPattern(whileStmt.getCondition()),
                    statementToPattern(whileStmt.getBody())
            ));
        }

        if (statement instanceof DoStmt doStmt) {
            return new TreeNode("DoStatement", "", List.of(
                    statementToPattern(doStmt.getBody()),
                    expressionToPattern(doStmt.getCondition())
            ));
        }

        if (statement instanceof ForStmt forStmt) {
            List<TreePattern> children = new ArrayList<>();
            children.add(new TreeNode("FOR_INIT", "", expressionsToPatterns(forStmt.getInitialization())));
            children.add(forStmt.getCompare().map(JavaParserTreeAdapter::expressionToPattern)
                    .orElseGet(() -> new TreeNode("EMPTY", "", List.of())));
            children.add(new TreeNode("FOR_UPDATE", "", expressionsToPatterns(forStmt.getUpdate())));
            children.add(statementToPattern(forStmt.getBody()));
            return new TreeNode("ForStatement", "", children);
        }

        if (statement instanceof ForEachStmt forEachStmt) {
            return new TreeNode("ForEachStatement", forEachStmt.getVariable().toString(), List.of(
                    expressionToPattern(forEachStmt.getIterable()),
                    statementToPattern(forEachStmt.getBody())
            ));
        }

        if (statement instanceof SwitchStmt switchStmt) {
            List<TreePattern> children = new ArrayList<>();
            children.add(expressionToPattern(switchStmt.getSelector()));
            for (SwitchEntry entry : switchStmt.getEntries()) {
                children.add(switchEntryToPattern(entry));
            }
            return new TreeNode("SwitchStatement", "", children);
        }

        if (statement instanceof TryStmt tryStmt) {
            List<TreePattern> children = new ArrayList<>();
            children.add(statementToPattern(tryStmt.getTryBlock()));
            for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                children.add(new TreeNode(
                        "CatchClause",
                        catchClause.getParameter().toString(),
                        List.of(statementToPattern(catchClause.getBody()))
                ));
            }
            tryStmt.getFinallyBlock().ifPresent(finallyBlock ->
                    children.add(new TreeNode("FinallyBlock", "", List.of(statementToPattern(finallyBlock))))
            );
            return new TreeNode("TryStatement", "", children);
        }

        if (statement instanceof ExpressionStmt expressionStmt) {
            return new TreeNode("ExpressionStatement", "", List.of(expressionToPattern(expressionStmt.getExpression())));
        }

        if (statement instanceof ReturnStmt returnStmt) {
            List<TreePattern> children = new ArrayList<>();
            returnStmt.getExpression().ifPresent(expr -> children.add(expressionToPattern(expr)));
            return new TreeNode("ReturnStatement", "", children);
        }

        if (statement instanceof ThrowStmt throwStmt) {
            return new TreeNode("ThrowStatement", "", List.of(expressionToPattern(throwStmt.getExpression())));
        }

        if (statement instanceof BreakStmt breakStmt) {
            return new TreeNode("BreakStatement", breakStmt.getLabel().map(Object::toString).orElse(""), List.of());
        }

        if (statement instanceof ContinueStmt continueStmt) {
            return new TreeNode("ContinueStatement", continueStmt.getLabel().map(Object::toString).orElse(""), List.of());
        }

        if (statement instanceof EmptyStmt) {
            return new TreeNode("EmptyStatement", "", List.of());
        }

        return new TreeNode(statement.getClass().getSimpleName(), statement.toString(), List.of());
    }

    private static TreePattern switchEntryToPattern(SwitchEntry entry) {
        List<TreePattern> children = new ArrayList<>();
        children.add(new TreeNode("SWITCH_LABELS", "", expressionsToPatterns(entry.getLabels())));
        for (Statement statement : entry.getStatements()) {
            children.add(statementToPattern(statement));
        }
        return new TreeNode("SwitchEntry", entry.getType().name(), children);
    }

    private static List<TreePattern> expressionsToPatterns(List<Expression> expressions) {
        return expressions.stream()
                .map(JavaParserTreeAdapter::expressionToPattern)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static TreePattern expressionToPattern(Expression expression) {
        if (expression instanceof BinaryExpr binaryExpr) {
            return new TreeNode("InfixExpression", OperatorToken.fromBinaryOperator(binaryExpr.getOperator()), List.of(
                    expressionToPattern(binaryExpr.getLeft()),
                    expressionToPattern(binaryExpr.getRight())
            ));
        }

        if (expression instanceof MethodCallExpr methodCallExpr) {
            List<TreePattern> children = new ArrayList<>();

            methodCallExpr.getScope().ifPresent(scope ->
                    children.add(new TreeNode("SCOPE", "", List.of(expressionToPattern(scope))))
            );

            children.add(new TreeNode("SimpleName", methodCallExpr.getNameAsString(), List.of()));

            List<TreePattern> args = new ArrayList<>();
            for (Expression arg : methodCallExpr.getArguments()) {
                args.add(expressionToPattern(arg));
            }
            children.add(new TreeNode("ARGS", "", args));

            return new TreeNode("MethodInvocation", "", children);
        }

        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            return new TreeNode("FieldAccessExpr", fieldAccessExpr.getNameAsString(), List.of(
                    expressionToPattern(fieldAccessExpr.getScope())
            ));
        }

        if (expression instanceof ObjectCreationExpr objectCreationExpr) {
            List<TreePattern> args = new ArrayList<>();
            for (Expression arg : objectCreationExpr.getArguments()) {
                args.add(expressionToPattern(arg));
            }
            return new TreeNode("ObjectCreationExpr", objectCreationExpr.getTypeAsString(), args);
        }

        if (expression instanceof LambdaExpr lambdaExpr) {
            String params = lambdaExpr.getParameters().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            Node body = lambdaExpr.getBody();
            TreePattern bodyPattern = body instanceof Statement statement
                    ? statementToPattern(statement)
                    : expressionToPattern((Expression) body);
            return new TreeNode("LambdaExpression", params, List.of(bodyPattern));
        }

        if (expression instanceof NameExpr nameExpr) {
            return new TreeNode("SimpleName", nameExpr.getNameAsString(), List.of());
        }

        if (expression instanceof NullLiteralExpr) {
            return new TreeNode("Literal", "null", List.of());
        }

        if (expression instanceof BooleanLiteralExpr booleanLiteralExpr) {
            return new TreeNode("Literal", String.valueOf(booleanLiteralExpr.getValue()), List.of());
        }

        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return new TreeNode("Literal", integerLiteralExpr.getValue(), List.of());
        }

        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return new TreeNode("Literal", longLiteralExpr.getValue(), List.of());
        }

        if (expression instanceof DoubleLiteralExpr doubleLiteralExpr) {
            return new TreeNode("Literal", doubleLiteralExpr.getValue(), List.of());
        }

        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return new TreeNode("Literal", "'" + charLiteralExpr.getValue() + "'", List.of());
        }

        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return new TreeNode("Literal", "\"" + stringLiteralExpr.getValue() + "\"", List.of());
        }

        if (expression instanceof EnclosedExpr enclosedExpr) {
            return new TreeNode("ParenthesizedExpression", "", List.of(expressionToPattern(enclosedExpr.getInner())));
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            return new TreeNode("UnaryExpression", unaryExpr.getOperator().asString(), List.of(
                    expressionToPattern(unaryExpr.getExpression())
            ));
        }

        if (expression instanceof AssignExpr assignExpr) {
            return new TreeNode("Assignment", assignExpr.getOperator().asString(), List.of(
                    expressionToPattern(assignExpr.getTarget()),
                    expressionToPattern(assignExpr.getValue())
            ));
        }

        return new TreeNode(expression.getClass().getSimpleName(), expression.toString(), List.of());
    }
}
