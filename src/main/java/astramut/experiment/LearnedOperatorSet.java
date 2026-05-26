package astramut.experiment;

enum LearnedOperatorSet {
  LEARNED_TOP_1000("top1000", 1000, "top-1000"),
  LEARNED_TOP_100("top100", 100, "top-100");

  static final java.util.List<LearnedOperatorSet> DEFAULT_SETS =
      java.util.List.of(LEARNED_TOP_1000, LEARNED_TOP_100);

  private final String optionName;
  private final int limit;
  private final String directoryName;

  LearnedOperatorSet(String optionName, int limit, String directoryName) {
    this.optionName = optionName;
    this.limit = limit;
    this.directoryName = directoryName;
  }

  int limit() {
    return limit;
  }

  String directoryName() {
    return directoryName;
  }

  static LearnedOperatorSet parse(String value) {
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "");
    for (LearnedOperatorSet set : values()) {
      if (set.optionName.equals(normalized)) {
        return set;
      }
    }
    throw new IllegalArgumentException("Invalid learned preset: " + value);
  }
}
