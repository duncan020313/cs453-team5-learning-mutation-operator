package astramut.experiment;

final class MutationTotals {
  private int generated;
  private int killed;
  private int survived;
  private int noCoverage;

  int generated() {
    return generated;
  }

  int killed() {
    return killed;
  }

  int survived() {
    return survived;
  }

  int noCoverage() {
    return noCoverage;
  }

  void incrementGenerated() {
    generated++;
  }

  void incrementKilled() {
    killed++;
  }

  void incrementSurvived() {
    survived++;
  }

  void incrementNoCoverage() {
    noCoverage++;
  }

  void add(MutationTotals other) {
    generated += other.generated;
    killed += other.killed;
    survived += other.survived;
    noCoverage += other.noCoverage;
  }

  double mutationScore() {
    return generated == 0 ? 1.0 : killed / (double) generated;
  }
}
