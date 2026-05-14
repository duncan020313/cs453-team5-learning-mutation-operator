package astramut.learn;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-crafted {@link GumTreeDiff} fixtures shaped exactly like what
 * {@code gumtree textdiff -f JSON} would emit — paired src/dst trees, the
 * matches list, and the edit script.
 *
 * <p>Positions are synthetic and only need to be unique within a single
 * tree; they exist solely to give each node a distinct
 * {@link GumTreeNode#identifier()} string for the matches/actions to
 * reference.
 */
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

        // The only edit GumTree would emit here is the operator update.
        List<GumTreeAction> actions = List.of(
                new GumTreeAction.UpdateNode(srcBin.identifier(), flippedOp));

        return new GumTreeDiff(srcIf, dstIf, matches, actions);
    }

    /** int <var> = <other> <op> 1;  →  int <var> = <other> <op> 0;  (LITERAL_TO_ZERO) */
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

    /**
     * Body fixture for {@link #nullCheckFlip} — the unchanged subtree that
     * sits in the {@code then}-branch. Built once per diff so src and dst
     * positions are distinct and a one-to-one match can be recorded.
     */
    static BodyPair leafBody(String type, int srcPos, int dstPos) {
        GumTreeNode src = GumTreeNode.leaf(type, "", srcPos, 1);
        GumTreeNode dst = GumTreeNode.leaf(type, "", dstPos, 1);
        return new BodyPair(src, dst, List.of(new GumTreeMatch(src.identifier(), dst.identifier())));
    }

    record BodyPair(GumTreeNode src, GumTreeNode dst, List<GumTreeMatch> matches) {}
}
