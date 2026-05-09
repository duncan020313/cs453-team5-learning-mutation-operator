package astramut.learn;

public final class PatternFormatter {
    private PatternFormatter() {}

    public static String format(EditPattern e) {
        return format(e.before()) + "  ↦  " + format(e.after());
    }

    public static String format(TreePattern p) {
        StringBuilder sb = new StringBuilder();
        append(p, sb);
        return sb.toString();
    }

    private static void append(TreePattern p, StringBuilder sb) {
        if (p instanceof Hole h) {
            sb.append(h.id());
            return;
        }
        TreeNode n = (TreeNode) p;
        sb.append(n.label());
        if (!n.value().isEmpty()) sb.append("(").append(n.value()).append(")");
        if (n.children().isEmpty()) return;
        sb.append("[");
        for (int i = 0; i < n.children().size(); i++) {
            if (i > 0) sb.append(", ");
            TreeNode.ChildSlot s = n.children().get(i);
            sb.append(s.location()).append("=");
            append(s.child(), sb);
        }
        sb.append("]");
    }
}
