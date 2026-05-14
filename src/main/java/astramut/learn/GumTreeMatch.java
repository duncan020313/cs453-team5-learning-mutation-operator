package astramut.learn;

/**
 * One entry of GumTree's {@code matches} array — a pair of
 * {@link GumTreeNode#identifier() node identifier} strings, src ↔ dest.
 */
public record GumTreeMatch(String src, String dest) {}
