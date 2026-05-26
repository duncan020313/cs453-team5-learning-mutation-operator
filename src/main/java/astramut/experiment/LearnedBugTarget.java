package astramut.experiment;

import java.util.List;

record LearnedBugTarget(String project, int bugId, List<LearnedOperatorSet> operatorSets) {
  LearnedBugTarget {
    operatorSets = List.copyOf(operatorSets);
  }
}
