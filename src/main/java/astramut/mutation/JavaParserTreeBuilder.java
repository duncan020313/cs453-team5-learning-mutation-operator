package astramut.mutation;

import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
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
import com.github.javaparser.ast.expr.SimpleName;
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
import java.util.Optional;

public final class JavaParserTreeBuilder {
    private JavaParserTreeBuilder() {
    }

    public static Optional<Node> buildForContext(TreePattern pattern, Node targetContext) {
        if (targetContext instanceof BodyDeclaration<?>) {
            return buildBodyDeclaration(pattern).map(d -> (Node) d);
        }

        if (targetContext instanceof Statement) {
            Optional<Statement> statement = buildStatement(pattern);
            if (statement.isPresent()) {
                return statement.map(s -> (Node) s);
            }

            Optional<Expression> expression = buildExpression(pattern);
            return expression.map(expr -> (Node) new ExpressionStmt(expr));
        }

        if (targetContext instanceof Expression) {
            return buildExpression(pattern).map(expr -> (Node) expr);
        }

        Optional<BodyDeclaration<?>> declaration = buildBodyDeclaration(pattern);
        if (declaration.isPresent()) {
            return declaration.map(d -> (Node) d);
        }

        Optional<Statement> statement = buildStatement(pattern);
        if (statement.isPresent()) {
            return statement.map(s -> (Node) s);
        }

        return buildExpression(pattern).map(expr -> (Node) expr);
    }

    public static Optional<List<Statement>> buildStatementList(TreePattern pattern) {
        if (pattern instanceof TreeNode n && isBlock(n.type())) {
            List<Statement> statements = new ArrayList<>();
            for (TreePattern child : n.children()) {
                Optional<Statement> statement = buildStatement(child);
                if (statement.isEmpty()) {
                    return Optional.empty();
                }
                statements.add(statement.get());
            }
            return Optional.of(statements);
        }

        return buildStatement(pattern).map(List::of);
    }

    public static Optional<BodyDeclaration<?>> buildBodyDeclaration(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n)) {
            return Optional.empty();
        }

        if (!n.label().isBlank()) {
            Optional<BodyDeclaration<?>> parsed = parseBodyDeclaration(n.label());
            if (parsed.isPresent() && n.children().isEmpty()) {
                return parsed;
            }
        }

        String type = n.type();

        if (type.equals("ClassOrInterfaceDeclaration") || type.equals("ClassDeclaration") || type.equals("ClassOrInterfaceType")) {
            String name = n.label().isBlank() ? "GeneratedClass" : n.label().trim().split("\\s+")[0];
            ClassOrInterfaceDeclaration decl = new ClassOrInterfaceDeclaration();
            decl.setName(name.replaceAll("[^A-Za-z0-9_$]", ""));

            for (TreePattern child : n.children()) {
                Optional<BodyDeclaration<?>> member = buildBodyDeclaration(child);
                member.ifPresent(decl::addMember);
            }
            return Optional.of(decl);
        }

        if (type.equals("MethodDeclaration")) {
            if (!n.label().isBlank()) {
                String header = n.label().trim();
                String src = header.endsWith(";") ? header : header + " {}";
                try {
                    MethodDeclaration method = StaticJavaParser.parseBodyDeclaration(src).asMethodDeclaration();
                    if (!n.children().isEmpty()) {
                        Optional<Statement> body = buildStatement(n.children().get(0));
                        if (body.isPresent() && body.get() instanceof BlockStmt blockStmt) {
                            method.setBody(blockStmt);
                        }
                    }
                    return Optional.of(method);
                } catch (RuntimeException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        if (type.equals("ConstructorDeclaration")) {
            if (!n.label().isBlank()) {
                String src = n.label().trim() + " {}";
                try {
                    ConstructorDeclaration constructor = StaticJavaParser.parseBodyDeclaration(src).asConstructorDeclaration();
                    if (!n.children().isEmpty()) {
                        Optional<Statement> body = buildStatement(n.children().get(0));
                        if (body.isPresent() && body.get() instanceof BlockStmt blockStmt) {
                            constructor.setBody(blockStmt);
                        }
                    }
                    return Optional.of(constructor);
                } catch (RuntimeException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        if (!n.label().isBlank()) {
            return parseBodyDeclaration(n.label());
        }

        return Optional.empty();
    }

    public static Optional<Statement> buildStatement(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n)) {
            return Optional.empty();
        }

        String type = n.type();

        if (isBlock(type)) {
            BlockStmt blockStmt = new BlockStmt();
            for (TreePattern child : n.children()) {
                Optional<Statement> statement = buildStatement(child);
                if (statement.isPresent()) {
                    blockStmt.addStatement(statement.get());
                    continue;
                }

                Optional<Expression> expression = buildExpression(child);
                if (expression.isPresent()) {
                    blockStmt.addStatement(new ExpressionStmt(expression.get()));
                    continue;
                }

                return Optional.empty();
            }
            return Optional.of(blockStmt);
        }

        if (isIf(type)) {
            if (n.children().size() < 2 || n.children().size() > 3) {
                return Optional.empty();
            }

            Optional<Expression> condition = buildExpression(n.children().get(0));
            Optional<Statement> thenStmt = buildStatement(n.children().get(1));

            if (condition.isEmpty() || thenStmt.isEmpty()) {
                return Optional.empty();
            }

            IfStmt ifStmt = new IfStmt();
            ifStmt.setCondition(condition.get());
            ifStmt.setThenStmt(thenStmt.get());

            if (n.children().size() == 3) {
                Optional<Statement> elseStmt = buildStatement(n.children().get(2));
                if (elseStmt.isEmpty()) {
                    return Optional.empty();
                }
                ifStmt.setElseStmt(elseStmt.get());
            }

            return Optional.of(ifStmt);
        }

        if (type.equals("WhileStatement") || type.equals("WhileStmt")) {
            if (n.children().size() != 2) {
                return Optional.empty();
            }
            Optional<Expression> condition = buildExpression(n.children().get(0));
            Optional<Statement> body = buildStatement(n.children().get(1));
            if (condition.isEmpty() || body.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new WhileStmt(condition.get(), body.get()));
        }

        if (type.equals("DoStatement") || type.equals("DoStmt")) {
            if (n.children().size() != 2) {
                return Optional.empty();
            }
            Optional<Statement> body = buildStatement(n.children().get(0));
            Optional<Expression> condition = buildExpression(n.children().get(1));
            if (body.isEmpty() || condition.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DoStmt(body.get(), condition.get()));
        }

        if (type.equals("ForStatement") || type.equals("ForStmt")) {
            if (n.children().size() != 4) {
                return Optional.empty();
            }

            Optional<NodeList<Expression>> init = buildExpressionList(n.children().get(0));
            Optional<Expression> compare = buildMaybeExpression(n.children().get(1));
            Optional<NodeList<Expression>> update = buildExpressionList(n.children().get(2));
            Optional<Statement> body = buildStatement(n.children().get(3));

            if (init.isEmpty() || update.isEmpty() || body.isEmpty()) {
                return Optional.empty();
            }

            ForStmt forStmt = new ForStmt();
            forStmt.setInitialization(init.get());
            compare.ifPresent(forStmt::setCompare);
            forStmt.setUpdate(update.get());
            forStmt.setBody(body.get());
            return Optional.of(forStmt);
        }

        if (type.equals("ForEachStatement") || type.equals("ForeachStatement") || type.equals("ForEachStmt")) {
            if (n.children().size() != 2 || n.label().isBlank()) {
                return Optional.empty();
            }
            Optional<Expression> iterable = buildExpression(n.children().get(0));
            Optional<Statement> body = buildStatement(n.children().get(1));
            if (iterable.isEmpty() || body.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new ForEachStmt(
                        StaticJavaParser.parseVariableDeclarationExpr(n.label()),
                        iterable.get(),
                        body.get()
                ));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        if (type.equals("SwitchStatement") || type.equals("SwitchStmt")) {
            if (n.children().isEmpty()) {
                return Optional.empty();
            }
            Optional<Expression> selector = buildExpression(n.children().get(0));
            if (selector.isEmpty()) {
                return Optional.empty();
            }

            SwitchStmt switchStmt = new SwitchStmt();
            switchStmt.setSelector(selector.get());
            NodeList<SwitchEntry> entries = new NodeList<>();
            for (int i = 1; i < n.children().size(); i++) {
                Optional<SwitchEntry> entry = buildSwitchEntry(n.children().get(i));
                if (entry.isEmpty()) {
                    return Optional.empty();
                }
                entries.add(entry.get());
            }
            switchStmt.setEntries(entries);
            return Optional.of(switchStmt);
        }

        if (type.equals("TryStatement") || type.equals("TryStmt")) {
            if (n.children().isEmpty()) {
                return Optional.empty();
            }
            Optional<Statement> tryBlock = buildStatement(n.children().get(0));
            if (tryBlock.isEmpty() || !(tryBlock.get() instanceof BlockStmt blockStmt)) {
                return Optional.empty();
            }

            TryStmt tryStmt = new TryStmt();
            tryStmt.setTryBlock(blockStmt);

            NodeList<CatchClause> catches = new NodeList<>();
            for (int i = 1; i < n.children().size(); i++) {
                TreePattern child = n.children().get(i);
                if (!(child instanceof TreeNode childNode)) {
                    return Optional.empty();
                }

                if (childNode.type().equals("CatchClause")) {
                    if (childNode.children().size() != 1 || childNode.label().isBlank()) {
                        return Optional.empty();
                    }
                    Optional<Statement> catchBody = buildStatement(childNode.children().get(0));
                    if (catchBody.isEmpty() || !(catchBody.get() instanceof BlockStmt catchBlock)) {
                        return Optional.empty();
                    }
                    try {
                        catches.add(new CatchClause(StaticJavaParser.parseParameter(childNode.label()), catchBlock));
                    } catch (RuntimeException ignored) {
                        return Optional.empty();
                    }
                } else if (childNode.type().equals("FinallyBlock")) {
                    if (childNode.children().size() != 1) {
                        return Optional.empty();
                    }
                    Optional<Statement> finallyBody = buildStatement(childNode.children().get(0));
                    if (finallyBody.isEmpty() || !(finallyBody.get() instanceof BlockStmt finallyBlock)) {
                        return Optional.empty();
                    }
                    tryStmt.setFinallyBlock(finallyBlock);
                }
            }
            tryStmt.setCatchClauses(catches);
            return Optional.of(tryStmt);
        }

        if (isExpressionStatement(type)) {
            if (n.children().size() != 1) {
                return Optional.empty();
            }

            return buildExpression(n.children().get(0)).map(ExpressionStmt::new);
        }

        if (isReturn(type)) {
            ReturnStmt returnStmt = new ReturnStmt();

            if (n.children().isEmpty()) {
                return Optional.of(returnStmt);
            }

            if (n.children().size() != 1) {
                return Optional.empty();
            }

            Optional<Expression> expression = buildExpression(n.children().get(0));
            if (expression.isEmpty()) {
                return Optional.empty();
            }

            returnStmt.setExpression(expression.get());
            return Optional.of(returnStmt);
        }

        if (isThrow(type)) {
            if (n.children().size() != 1) {
                return Optional.empty();
            }

            Optional<Expression> expression = buildExpression(n.children().get(0));
            return expression.map(ThrowStmt::new);
        }

        if (type.equals("BreakStatement") || type.equals("BreakStmt")) {
            if (n.label().isBlank()) {
                return Optional.of(new BreakStmt());
            }
            return Optional.of(new BreakStmt(n.label()));
        }

        if (type.equals("ContinueStatement") || type.equals("ContinueStmt")) {
            if (n.label().isBlank()) {
                return Optional.of(new ContinueStmt());
            }
            return Optional.of(new ContinueStmt(n.label()));
        }

        if (type.equals("EmptyStatement") || type.equals("EmptyStmt") || type.equals("EMPTY")) {
            return Optional.of(new EmptyStmt());
        }

        Optional<Expression> expression = buildExpression(pattern);
        return expression.map(ExpressionStmt::new);
    }

    public static Optional<Expression> buildExpression(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n)) {
            return Optional.empty();
        }

        String type = n.type();

        if (isInfix(type)) {
            if (n.children().size() == 2) {
                Optional<BinaryExpr.Operator> operator = OperatorToken.toBinaryOperator(n.label());
                Optional<Expression> left = buildExpression(n.children().get(0));
                Optional<Expression> right = buildExpression(n.children().get(1));

                if (operator.isEmpty() || left.isEmpty() || right.isEmpty()) {
                    return Optional.empty();
                }

                return Optional.of(new BinaryExpr(left.get(), right.get(), operator.get()));
            }

            if (n.children().size() == 3) {
                Optional<Expression> left = buildExpression(n.children().get(0));
                Optional<BinaryExpr.Operator> operator = buildInfixOperator(n.children().get(1));
                Optional<Expression> right = buildExpression(n.children().get(2));

                if (left.isEmpty() || operator.isEmpty() || right.isEmpty()) {
                    return Optional.empty();
                }

                return Optional.of(new BinaryExpr(left.get(), right.get(), operator.get()));
            }

            return Optional.empty();
        }

        if (isName(type)) {
            if (n.label().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new NameExpr(n.label()));
        }

        if (isLiteral(type)) {
            return Optional.of(buildLiteral(n.label()));
        }

        if (isMethodCall(type)) {
            return buildMethodCall(n);
        }

        if (type.equals("FieldAccessExpr") || type.equals("FieldAccess")) {
            if (n.children().size() != 1 || n.label().isBlank()) {
                return Optional.empty();
            }
            Optional<Expression> scope = buildExpression(n.children().get(0));
            return scope.map(expression -> new FieldAccessExpr(expression, n.label()));
        }

        if (type.equals("ObjectCreationExpr") || type.equals("ClassInstanceCreation")) {
            if (n.label().isBlank()) {
                return Optional.empty();
            }

            NodeList<Expression> args = new NodeList<>();
            for (TreePattern child : n.children()) {
                Optional<Expression> arg = buildExpression(child);
                if (arg.isEmpty()) {
                    return Optional.empty();
                }
                args.add(arg.get());
            }

            try {
                ObjectCreationExpr expr = new ObjectCreationExpr();
                expr.setType(StaticJavaParser.parseClassOrInterfaceType(n.label()));
                expr.setArguments(args);
                return Optional.of(expr);
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        if (type.equals("ParenthesizedExpression") || type.equals("EnclosedExpr")) {
            if (n.children().size() != 1) {
                return Optional.empty();
            }

            return buildExpression(n.children().get(0)).map(EnclosedExpr::new);
        }

        if (type.equals("UnaryExpression") || type.equals("UnaryExpr")) {
            if (n.children().size() != 1) {
                return Optional.empty();
            }

            Optional<Expression> expression = buildExpression(n.children().get(0));
            Optional<UnaryExpr.Operator> operator = toUnaryOperator(n.label());

            if (expression.isEmpty() || operator.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new UnaryExpr(expression.get(), operator.get()));
        }

        if (type.equals("Assignment") || type.equals("AssignExpr")) {
            if (n.children().size() != 2) {
                return Optional.empty();
            }

            Optional<Expression> target = buildExpression(n.children().get(0));
            Optional<Expression> value = buildExpression(n.children().get(1));
            Optional<AssignExpr.Operator> operator = toAssignOperator(n.label());

            if (target.isEmpty() || value.isEmpty() || operator.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new AssignExpr(target.get(), value.get(), operator.get()));
        }

        if (type.equals("LambdaExpression") || type.equals("LambdaExpr")) {
            if (n.children().size() != 1) {
                return Optional.empty();
            }

            Optional<Node> body = buildForContext(n.children().get(0), new ExpressionStmt());
            if (body.isEmpty()) {
                return Optional.empty();
            }

            String bodySource = body.get() instanceof ExpressionStmt expressionStmt
                    ? expressionStmt.getExpression().toString()
                    : body.get().toString();

            String lambdaSource = "(" + n.label() + ") -> " + bodySource;
            try {
                return Optional.of(StaticJavaParser.parseExpression(lambdaSource));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        if (!n.label().isBlank()) {
            try {
                return Optional.of(StaticJavaParser.parseExpression(n.label()));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
    
    private static Optional<BinaryExpr.Operator> buildInfixOperator(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n)) {
            return Optional.empty();
        }

        if (!n.type().equals("INFIX_EXPRESSION_OPERATOR")) {
            return Optional.empty();
        }

        return OperatorToken.toBinaryOperator(n.label());
    }

    private static Optional<Expression> buildMethodCall(TreeNode n) {
        MethodCallExpr callExpr = new MethodCallExpr();

        for (TreePattern child : n.children()) {
            if (!(child instanceof TreeNode c)) {
                return Optional.empty();
            }

            if (c.type().equals("METHOD_INVOCATION_RECEIVER")) {
                if (c.children().size() != 1) {
                    return Optional.empty();
                }

                Optional<Expression> scope = buildExpression(c.children().get(0));
                if (scope.isEmpty()) {
                    return Optional.empty();
                }

                callExpr.setScope(scope.get());
                continue;
            }

            if (isName(c.type())) {
                if (c.label().isBlank()) {
                    return Optional.empty();
                }

                callExpr.setName(c.label());
                continue;
            }

            if (c.type().equals("METHOD_INVOCATION_ARGUMENTS")) {
                NodeList<Expression> args = new NodeList<>();

                for (TreePattern argPattern : c.children()) {
                    Optional<Expression> arg = buildExpression(argPattern);
                    if (arg.isEmpty()) {
                        return Optional.empty();
                    }
                    args.add(arg.get());
                }

                callExpr.setArguments(args);
                continue;
            }

            return Optional.empty();
        }

        if (callExpr.getNameAsString().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(callExpr);
    }

    private static Optional<NodeList<Expression>> buildExpressionList(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n)) {
            return Optional.empty();
        }

        if (n.type().equals("EMPTY")) {
            return Optional.of(new NodeList<>());
        }

        NodeList<Expression> expressions = new NodeList<>();
        for (TreePattern child : n.children()) {
            Optional<Expression> expression = buildExpression(child);
            if (expression.isEmpty()) {
                return Optional.empty();
            }
            expressions.add(expression.get());
        }
        return Optional.of(expressions);
    }

    private static Optional<Expression> buildMaybeExpression(TreePattern pattern) {
        if (pattern instanceof TreeNode n && n.type().equals("EMPTY")) {
            return Optional.empty();
        }
        return buildExpression(pattern);
    }

    private static Optional<SwitchEntry> buildSwitchEntry(TreePattern pattern) {
        if (!(pattern instanceof TreeNode n) || !n.type().equals("SwitchEntry") || n.children().isEmpty()) {
            return Optional.empty();
        }

        SwitchEntry entry = new SwitchEntry();
        NodeList<Expression> labels = new NodeList<>();

        TreePattern first = n.children().get(0);
        if (first instanceof TreeNode labelNode && labelNode.type().equals("SWITCH_LABELS")) {
            for (TreePattern labelPattern : labelNode.children()) {
                Optional<Expression> label = buildExpression(labelPattern);
                if (label.isEmpty()) {
                    return Optional.empty();
                }
                labels.add(label.get());
            }
        } else {
            return Optional.empty();
        }

        entry.setLabels(labels);

        NodeList<Statement> statements = new NodeList<>();
        for (int i = 1; i < n.children().size(); i++) {
            Optional<Statement> statement = buildStatement(n.children().get(i));
            if (statement.isEmpty()) {
                return Optional.empty();
            }
            statements.add(statement.get());
        }
        entry.setStatements(statements);
        return Optional.of(entry);
    }

    private static Optional<BodyDeclaration<?>> parseBodyDeclaration(String source) {
        try {
            return Optional.of(StaticJavaParser.parseBodyDeclaration(source));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Expression buildLiteral(String label) {
        if (label == null || label.isBlank()) {
            return new StringLiteralExpr("");
        }

        String value = label.trim();

        if (value.equals("null")) {
            return new NullLiteralExpr();
        }

        if (value.equals("true") || value.equals("false")) {
            return new BooleanLiteralExpr(Boolean.parseBoolean(value));
        }

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return new StringLiteralExpr(value.substring(1, value.length() - 1));
        }

        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 3) {
            return new CharLiteralExpr(value.substring(1, value.length() - 1));
        }

        if (value.matches("-?\\d+")) {
            return new IntegerLiteralExpr(value);
        }

        if (value.matches("-?\\d+[lL]")) {
            return new LongLiteralExpr(value);
        }

        if (value.matches("-?\\d+\\.\\d+([dDfF])?")) {
            return new DoubleLiteralExpr(value);
        }

        return new StringLiteralExpr(value);
    }

    private static boolean isInfix(String type) {
        return type.equals("InfixExpression")
                || type.equals("BinaryExpr")
                || type.equals("BinaryExpression");
    }

    private static boolean isName(String type) {
        return type.equals("SimpleName")
                || type.equals("Identifier")
                || type.equals("NameExpr")
                || type.equals("Name");
    }

    private static boolean isLiteral(String type) {
        return type.equals("Literal")
                || type.equals("NullLiteral")
                || type.equals("BooleanLiteral")
                || type.equals("NumberLiteral")
                || type.equals("StringLiteral")
                || type.equals("CharacterLiteral");
    }

    private static boolean isMethodCall(String type) {
        return type.equals("MethodInvocation")
                || type.equals("MethodCallExpr")
                || type.equals("MethodCall");
    }

    private static boolean isBlock(String type) {
        return type.equals("Block")
                || type.equals("BlockStmt")
                || type.equals("BlockStatement");
    }

    private static boolean isIf(String type) {
        return type.equals("IfStatement")
                || type.equals("IfStmt");
    }

    private static boolean isExpressionStatement(String type) {
        return type.equals("ExpressionStatement")
                || type.equals("ExpressionStmt");
    }

    private static boolean isReturn(String type) {
        return type.equals("ReturnStatement")
                || type.equals("ReturnStmt");
    }

    private static boolean isThrow(String type) {
        return type.equals("ThrowStatement")
                || type.equals("ThrowStmt");
    }

    private static Optional<UnaryExpr.Operator> toUnaryOperator(String token) {
        return switch (token) {
            case "+" -> Optional.of(UnaryExpr.Operator.PLUS);
            case "-" -> Optional.of(UnaryExpr.Operator.MINUS);
            case "!" -> Optional.of(UnaryExpr.Operator.LOGICAL_COMPLEMENT);
            case "~" -> Optional.of(UnaryExpr.Operator.BITWISE_COMPLEMENT);
            case "++" -> Optional.of(UnaryExpr.Operator.PREFIX_INCREMENT);
            case "--" -> Optional.of(UnaryExpr.Operator.PREFIX_DECREMENT);
            default -> Optional.empty();
        };
    }

    private static Optional<AssignExpr.Operator> toAssignOperator(String token) {
        return switch (token) {
            case "=", "" -> Optional.of(AssignExpr.Operator.ASSIGN);
            case "+=" -> Optional.of(AssignExpr.Operator.PLUS);
            case "-=" -> Optional.of(AssignExpr.Operator.MINUS);
            case "*=" -> Optional.of(AssignExpr.Operator.MULTIPLY);
            case "/=" -> Optional.of(AssignExpr.Operator.DIVIDE);
            case "%=" -> Optional.of(AssignExpr.Operator.REMAINDER);
            case "&=" -> Optional.of(AssignExpr.Operator.BINARY_AND);
            case "|=" -> Optional.of(AssignExpr.Operator.BINARY_OR);
            case "^=" -> Optional.of(AssignExpr.Operator.XOR);
            case "<<=" -> Optional.of(AssignExpr.Operator.LEFT_SHIFT);
            case ">>=" -> Optional.of(AssignExpr.Operator.SIGNED_RIGHT_SHIFT);
            case ">>>=" -> Optional.of(AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT);
            default -> Optional.empty();
        };
    }
}
