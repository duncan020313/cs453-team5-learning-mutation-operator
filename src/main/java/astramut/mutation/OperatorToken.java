package astramut.mutation;

import com.github.javaparser.ast.expr.BinaryExpr;

import java.util.Optional;

public final class OperatorToken {
    private OperatorToken() {
    }

    public static Optional<BinaryExpr.Operator> toBinaryOperator(String token) {
        return switch (token) {
            case "==" -> Optional.of(BinaryExpr.Operator.EQUALS);
            case "!=" -> Optional.of(BinaryExpr.Operator.NOT_EQUALS);
            case "<" -> Optional.of(BinaryExpr.Operator.LESS);
            case "<=" -> Optional.of(BinaryExpr.Operator.LESS_EQUALS);
            case ">" -> Optional.of(BinaryExpr.Operator.GREATER);
            case ">=" -> Optional.of(BinaryExpr.Operator.GREATER_EQUALS);
            case "+" -> Optional.of(BinaryExpr.Operator.PLUS);
            case "-" -> Optional.of(BinaryExpr.Operator.MINUS);
            case "*" -> Optional.of(BinaryExpr.Operator.MULTIPLY);
            case "/" -> Optional.of(BinaryExpr.Operator.DIVIDE);
            case "%" -> Optional.of(BinaryExpr.Operator.REMAINDER);
            case "&&" -> Optional.of(BinaryExpr.Operator.AND);
            case "||" -> Optional.of(BinaryExpr.Operator.OR);
            case "&" -> Optional.of(BinaryExpr.Operator.BINARY_AND);
            case "|" -> Optional.of(BinaryExpr.Operator.BINARY_OR);
            case "^" -> Optional.of(BinaryExpr.Operator.XOR);
            case "<<" -> Optional.of(BinaryExpr.Operator.LEFT_SHIFT);
            case ">>" -> Optional.of(BinaryExpr.Operator.SIGNED_RIGHT_SHIFT);
            case ">>>" -> Optional.of(BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT);
            default -> Optional.empty();
        };
    }

    public static String fromBinaryOperator(BinaryExpr.Operator operator) {
        return operator.asString();
    }
}