package astramut.learn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MockData {
    private MockData() {}

    static AstNode node(String id, String label, String value, AstNode.ChildSlot... slots) {
        return new AstNode(id, label, value, List.of(slots));
    }

    static AstNode leaf(String id, String label, String value) {
        return AstNode.leaf(id, label, value);
    }

    static AstNode.ChildSlot slot(String location, AstNode child) {
        return new AstNode.ChildSlot(location, child);
    }

    /** if (var <op> null) <body>  →  if (var <flippedOp> null) <body> */
    static AstDiff nullCheckFlip(String prefix, String varName, String op, String flippedOp, AstNode body) {
        AstNode beforeCond = node(prefix + ":cond", "BinaryExpr", op,
                slot("left", leaf(prefix + ":var", "Id", varName)),
                slot("right", leaf(prefix + ":null", "Literal", "null")));
        AstNode afterCond = node(prefix + ":cond", "BinaryExpr", flippedOp,
                slot("left", leaf(prefix + ":var", "Id", varName)),
                slot("right", leaf(prefix + ":null", "Literal", "null")));
        AstNode beforeIf = node(prefix + ":if", "IfStmt", "",
                slot("cond", beforeCond), slot("then", body));
        AstNode afterIf = node(prefix + ":if", "IfStmt", "",
                slot("cond", afterCond), slot("then", body));
        Map<String, String> map = new HashMap<>();
        map.put(prefix + ":if", prefix + ":if");
        map.put(prefix + ":cond", prefix + ":cond");
        map.put(prefix + ":var", prefix + ":var");
        map.put(prefix + ":null", prefix + ":null");
        addSubtreeIds(body, map);
        return new AstDiff(beforeIf, afterIf, map);
    }

    /** int <var> = <other> <op> 1;  →  int <var> = <other> <op> 0;  (LITERAL_TO_ZERO) */
    static AstDiff literalToZero(String prefix, String varName, String otherName, String op) {
        AstNode beforeRhs = node(prefix + ":bin", "BinaryExpr", op,
                slot("left", leaf(prefix + ":other", "Id", otherName)),
                slot("right", leaf(prefix + ":lit", "Literal", "1")));
        AstNode afterRhs = node(prefix + ":bin", "BinaryExpr", op,
                slot("left", leaf(prefix + ":other", "Id", otherName)),
                slot("right", leaf(prefix + ":lit", "Literal", "0")));
        AstNode beforeDecl = node(prefix + ":decl", "VarDecl", "int",
                slot("name", leaf(prefix + ":var", "Id", varName)),
                slot("init", beforeRhs));
        AstNode afterDecl = node(prefix + ":decl", "VarDecl", "int",
                slot("name", leaf(prefix + ":var", "Id", varName)),
                slot("init", afterRhs));
        Map<String, String> map = new HashMap<>();
        for (String suffix : List.of(":decl", ":var", ":bin", ":other", ":lit")) {
            map.put(prefix + suffix, prefix + suffix);
        }
        return new AstDiff(beforeDecl, afterDecl, map);
    }

    private static void addSubtreeIds(AstNode n, Map<String, String> m) {
        m.put(n.id(), n.id());
        for (AstNode.ChildSlot s : n.children()) addSubtreeIds(s.child(), m);
    }
}
