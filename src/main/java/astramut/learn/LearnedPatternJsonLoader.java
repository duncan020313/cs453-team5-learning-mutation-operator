package astramut.learn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LearnedPatternJsonLoader {
  private final ObjectMapper mapper = new ObjectMapper();

  public List<LearnedPatternEntry> selectTop(Path modelJson, Selection selection, int limit)
      throws IOException {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return load(modelJson, selection).stream().limit(limit).toList();
  }

  public List<LearnedPatternEntry> load(Path modelJson, Selection selection) throws IOException {
    JsonNode root = mapper.readTree(modelJson.toFile());
    JsonNode runs = root.path("runs");
    if (!runs.isArray()) {
      throw new IllegalArgumentException("model JSON must contain array field: runs");
    }

    List<LearnedPatternEntry> entries = new ArrayList<>();
    for (JsonNode run : runs) {
      String label = run.path("label").asText("");
      if (selection.bugType() != null && !selection.bugType().equals(label)) {
        continue;
      }
      int cohortSize = run.path("cohortSize").asInt(0);
      JsonNode patterns = run.path("patterns");
      if (!patterns.isArray()) {
        continue;
      }

      for (JsonNode patternJson : patterns) {
        int support = patternJson.path("support").asInt(0);
        double specificity = patternJson.path("specificity").asDouble(0.0);
        if (support < selection.minSupport() || specificity < selection.minSpecificity()) {
          continue;
        }
        if (selection.minCohortRatio() > 0.0) {
          if (cohortSize <= 0 || support / (double) cohortSize < selection.minCohortRatio()) {
            continue;
          }
        }

        TreePattern before = treeFromJson(patternJson.path("before"));
        TreePattern after = treeFromJson(patternJson.path("after"));
        EditPattern editPattern = new EditPattern(before, after);
        LearnedPattern learnedPattern =
            new LearnedPattern(editPattern, support, specificity, List.of(editPattern));
        double score =
            patternJson.has("score")
                ? patternJson.path("score").asDouble()
                : learnedPattern.score();
        int rank = patternJson.path("rank").asInt(-1);
        entries.add(new LearnedPatternEntry(label, cohortSize, rank, score, learnedPattern));
      }
    }

    return entries.stream()
        .sorted(
            Comparator.comparingDouble(LearnedPatternEntry::score)
                .reversed()
                .thenComparing(LearnedPatternEntry::bugType)
                .thenComparingInt(LearnedPatternEntry::rank))
        .toList();
  }

  private static TreePattern treeFromJson(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      throw new IllegalArgumentException("invalid tree node in model JSON");
    }

    if (node.has("hole")) {
      return new Hole(node.get("hole").asText());
    }

    String type = node.path("type").asText(null);
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("tree node is missing required field: type");
    }

    String label = node.path("label").asText("");
    List<TreePattern> children = new ArrayList<>();
    JsonNode childArray = node.path("children");
    if (childArray.isArray()) {
      for (JsonNode child : childArray) {
        children.add(treeFromJson(child));
      }
    }

    return new TreeNode(type, label, children);
  }

  public record Selection(
      String bugType, int minSupport, double minSpecificity, double minCohortRatio) {
    public Selection {
      if (minSupport < 0) {
        throw new IllegalArgumentException("minSupport must be >= 0");
      }
      if (minSpecificity < 0.0) {
        throw new IllegalArgumentException("minSpecificity must be >= 0.0");
      }
      if (minCohortRatio < 0.0) {
        throw new IllegalArgumentException("minCohortRatio must be >= 0.0");
      }
    }
  }
}
