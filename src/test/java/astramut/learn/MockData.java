package astramut.learn;

import java.util.ArrayList;
import java.util.List;

/** Hand-crafted {@link GumTreeDiff} fixtures shaped like {@code gumtree textdiff -f JSON} output. */
final class MockData {
    private MockData() {}

    /** if (var <op> null) <body>  →  if (var <flippedOp> null) <body> */
    static GumTreeDiff nullCheckFlip(String varName, String op, String flippedOp, BodyPair body) {
        GumTreeNode srcVar = GumTreeNode.leaf("Identifier", varName, 1, 1);
        GumTreeNode srcNull = GumTreeNode.leaf("Literal", "null", 2, 1);
        GumTreeNode srcBin = new GumTreeNode("InfixExpression", op, 3, 1, List.of(srcVar, srcNull));
        GumTreeNode srcIf = new GumTreeNode("IfStatement", "", 4, 1, List.of(srcBin, body.src()));

        GumTreeNode dstVar = GumTreeNode.leaf("Identifier", varName, 11, 1);
        GumTreeNode dstNull = GumTreeNode.leaf("Literal", "null", 12, 1);
        GumTreeNode dstBin = new GumTreeNode("InfixExpression", flippedOp, 13, 1, List.of(dstVar, dstNull));
        GumTreeNode dstIf = new GumTreeNode("IfStatement", "", 14, 1, List.of(dstBin, body.dst()));

        List<GumTreeMatch> matches = new ArrayList<>();
        matches.add(new GumTreeMatch(srcIf.identifier(), dstIf.identifier()));
        matches.add(new GumTreeMatch(srcBin.identifier(), dstBin.identifier()));
        matches.add(new GumTreeMatch(srcVar.identifier(), dstVar.identifier()));
        matches.add(new GumTreeMatch(srcNull.identifier(), dstNull.identifier()));
        matches.addAll(body.matches());

        List<GumTreeAction> actions = List.of(
                new GumTreeAction.UpdateNode(srcBin.identifier(), flippedOp));

        return new GumTreeDiff(srcIf, dstIf, matches, actions);
    }

    /** int <var> = <other> <op> 1;  →  int <var> = <other> <op> 0; */
    static GumTreeDiff literalToZero(String varName, String otherName, String op) {
        GumTreeNode srcOther = GumTreeNode.leaf("Identifier", otherName, 1, 1);
        GumTreeNode srcLit = GumTreeNode.leaf("Literal", "1", 2, 1);
        GumTreeNode srcBin = new GumTreeNode("InfixExpression", op, 3, 1, List.of(srcOther, srcLit));
        GumTreeNode srcVar = GumTreeNode.leaf("Identifier", varName, 4, 1);
        GumTreeNode srcDecl = new GumTreeNode("VariableDeclaration", "int", 5, 1, List.of(srcVar, srcBin));

        GumTreeNode dstOther = GumTreeNode.leaf("Identifier", otherName, 11, 1);
        GumTreeNode dstLit = GumTreeNode.leaf("Literal", "0", 12, 1);
        GumTreeNode dstBin = new GumTreeNode("InfixExpression", op, 13, 1, List.of(dstOther, dstLit));
        GumTreeNode dstVar = GumTreeNode.leaf("Identifier", varName, 14, 1);
        GumTreeNode dstDecl = new GumTreeNode("VariableDeclaration", "int", 15, 1, List.of(dstVar, dstBin));

        List<GumTreeMatch> matches = List.of(
                new GumTreeMatch(srcDecl.identifier(), dstDecl.identifier()),
                new GumTreeMatch(srcVar.identifier(), dstVar.identifier()),
                new GumTreeMatch(srcBin.identifier(), dstBin.identifier()),
                new GumTreeMatch(srcOther.identifier(), dstOther.identifier()),
                new GumTreeMatch(srcLit.identifier(), dstLit.identifier()));

        List<GumTreeAction> actions = List.of(
                new GumTreeAction.UpdateNode(srcLit.identifier(), "0"));

        return new GumTreeDiff(srcDecl, dstDecl, matches, actions);
    }

    static BodyPair leafBody(String type, int srcPos, int dstPos) {
        GumTreeNode src = GumTreeNode.leaf(type, "", srcPos, 1);
        GumTreeNode dst = GumTreeNode.leaf(type, "", dstPos, 1);
        return new BodyPair(src, dst, List.of(new GumTreeMatch(src.identifier(), dst.identifier())));
    }

    /** assert(name1, name2) — same shape across diffs, differing leaves. */
    static BodyPair richBody(String name1, String name2, int srcBase, int dstBase) {
        GumTreeNode srcName = GumTreeNode.leaf("Identifier", "assert", srcBase, 1);
        GumTreeNode srcArg1 = GumTreeNode.leaf("Identifier", name1, srcBase + 1, 1);
        GumTreeNode srcArg2 = GumTreeNode.leaf("Identifier", name2, srcBase + 2, 1);
        GumTreeNode src = new GumTreeNode("MethodCall", "", srcBase + 3, 1,
                List.of(srcName, srcArg1, srcArg2));

        GumTreeNode dstName = GumTreeNode.leaf("Identifier", "assert", dstBase, 1);
        GumTreeNode dstArg1 = GumTreeNode.leaf("Identifier", name1, dstBase + 1, 1);
        GumTreeNode dstArg2 = GumTreeNode.leaf("Identifier", name2, dstBase + 2, 1);
        GumTreeNode dst = new GumTreeNode("MethodCall", "", dstBase + 3, 1,
                List.of(dstName, dstArg1, dstArg2));

        List<GumTreeMatch> matches = List.of(
                new GumTreeMatch(src.identifier(), dst.identifier()),
                new GumTreeMatch(srcName.identifier(), dstName.identifier()),
                new GumTreeMatch(srcArg1.identifier(), dstArg1.identifier()),
                new GumTreeMatch(srcArg2.identifier(), dstArg2.identifier()));
        return new BodyPair(src, dst, matches);
    }

    record BodyPair(GumTreeNode src, GumTreeNode dst, List<GumTreeMatch> matches) {}
}
