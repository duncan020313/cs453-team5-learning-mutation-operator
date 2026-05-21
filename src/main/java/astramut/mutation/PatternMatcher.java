package astramut.mutation;

import astramut.learn.Hole;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import java.util.HashMap;
import java.util.Map;

public final class PatternMatcher {
    private PatternMatcher() {
    }

    public static MatchResult match(TreePattern pattern, TreePattern target) {
        Map<String, TreePattern> bindings = new HashMap<>();
        boolean matched = matchInto(pattern, target, bindings);
        return new MatchResult(matched, bindings);
    }

    private static boolean matchInto(
            TreePattern pattern,
            TreePattern target,
            Map<String, TreePattern> bindings
    ) {
        if (pattern instanceof Hole h) {
            TreePattern previous = bindings.get(h.id());

            if (previous == null) {
                bindings.put(h.id(), target);
                return true;
            }

            return previous.equals(target);
        }

        if (!(pattern instanceof TreeNode p)) {
            return false;
        }

        if (!(target instanceof TreeNode t)) {
            return false;
        }

        if (!typeMatches(p.type(), t.type())) {
            return false;
        }

        if (!labelMatches(p.label(), t.label())) {
            return false;
        }

        if (p.children().size() != t.children().size()) {
            return false;
        }

        for (int i = 0; i < p.children().size(); i++) {
            if (!matchInto(p.children().get(i), t.children().get(i), bindings)) {
                return false;
            }
        }

        return true;
    }

    private static boolean typeMatches(String patternType, String targetType) {
        if (patternType.equals(targetType)) {
            return true;
        }

        if (isInfix(patternType) && isInfix(targetType)) return true;
        if (isName(patternType) && isName(targetType)) return true;
        if (isLiteral(patternType) && isLiteral(targetType)) return true;
        if (isMethodCall(patternType) && isMethodCall(targetType)) return true;
        if (isBlock(patternType) && isBlock(targetType)) return true;
        if (isIf(patternType) && isIf(targetType)) return true;
        if (isExpressionStatement(patternType) && isExpressionStatement(targetType)) return true;
        if (isReturn(patternType) && isReturn(targetType)) return true;
        if (isThrow(patternType) && isThrow(targetType)) return true;
        if (isLoop(patternType) && isLoop(targetType)) return true;
        if (isSwitch(patternType) && isSwitch(targetType)) return true;
        if (isTry(patternType) && isTry(targetType)) return true;
        if (isLambda(patternType) && isLambda(targetType)) return true;
        if (isClassDeclaration(patternType) && isClassDeclaration(targetType)) return true;
        if (isMethodDeclaration(patternType) && isMethodDeclaration(targetType)) return true;
        if (isConstructorDeclaration(patternType) && isConstructorDeclaration(targetType)) return true;

        return false;
    }

    private static boolean labelMatches(String patternLabel, String targetLabel) {
        if (patternLabel == null || patternLabel.isBlank()) {
            return true;
        }

        return patternLabel.equals(targetLabel);
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

    private static boolean isLoop(String type) {
        return type.equals("ForStatement")
                || type.equals("ForStmt")
                || type.equals("ForEachStatement")
                || type.equals("ForEachStmt")
                || type.equals("ForeachStatement")
                || type.equals("WhileStatement")
                || type.equals("WhileStmt")
                || type.equals("DoStatement")
                || type.equals("DoStmt");
    }

    private static boolean isSwitch(String type) {
        return type.equals("SwitchStatement")
                || type.equals("SwitchStmt")
                || type.equals("SwitchEntry");
    }

    private static boolean isTry(String type) {
        return type.equals("TryStatement")
                || type.equals("TryStmt")
                || type.equals("CatchClause")
                || type.equals("FinallyBlock");
    }

    private static boolean isLambda(String type) {
        return type.equals("LambdaExpression")
                || type.equals("LambdaExpr");
    }

    private static boolean isClassDeclaration(String type) {
        return type.equals("ClassOrInterfaceDeclaration")
                || type.equals("ClassDeclaration")
                || type.equals("TypeDeclaration");
    }

    private static boolean isMethodDeclaration(String type) {
        return type.equals("MethodDeclaration");
    }

    private static boolean isConstructorDeclaration(String type) {
        return type.equals("ConstructorDeclaration");
    }

    public record MatchResult(boolean matched, Map<String, TreePattern> bindings) {
        public MatchResult {
            bindings = Map.copyOf(bindings);
        }
    }
}
