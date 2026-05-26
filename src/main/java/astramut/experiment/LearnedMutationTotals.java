package astramut.experiment;

record LearnedMutationTotals(
    int generated,
    int killed,
    int survived,
    int noCoverage,
    int sourceMutants,
    int duplicateMutants,
    int compileFailed,
    int testFailed,
    int timeoutKilled) {
  static Builder builder() {
    return new Builder();
  }

  static LearnedMutationTotals empty() {
    return new LearnedMutationTotals(0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  double mutationScore() {
    return generated == 0 ? 1.0 : killed / (double) generated;
  }

  LearnedMutationTotals add(LearnedMutationTotals other) {
    return new LearnedMutationTotals(
        generated + other.generated,
        killed + other.killed,
        survived + other.survived,
        noCoverage + other.noCoverage,
        sourceMutants + other.sourceMutants,
        duplicateMutants + other.duplicateMutants,
        compileFailed + other.compileFailed,
        testFailed + other.testFailed,
        timeoutKilled + other.timeoutKilled);
  }

  static final class Builder {
    private int generated;
    private int killed;
    private int survived;
    private int sourceMutants;
    private int duplicateMutants;
    private int compileFailed;
    private int testFailed;
    private int timeoutKilled;

    void incrementSourceMutants() {
      sourceMutants++;
    }

    void incrementDuplicateMutants() {
      duplicateMutants++;
    }

    void incrementCompileFailed() {
      compileFailed++;
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

    void incrementTestFailed() {
      testFailed++;
    }

    void incrementTimeoutKilled() {
      timeoutKilled++;
    }

    int sourceMutants() {
      return sourceMutants;
    }

    LearnedMutationTotals build() {
      return new LearnedMutationTotals(
          generated,
          killed,
          survived,
          0,
          sourceMutants,
          duplicateMutants,
          compileFailed,
          testFailed,
          timeoutKilled);
    }
  }
}
