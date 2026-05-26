package astramut.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearnedPatternJsonLoaderTest {
  @TempDir Path tempDir;

  @Test
  void flattensFiltersAndSortsByGlobalJsonScore() throws IOException {
    Path model = tempDir.resolve("patterns.json");
    Files.writeString(
        model,
        """
        {
          "runs": [
            {
              "label": "CHANGE_OPERATOR",
              "cohortSize": 10,
              "patterns": [
                {
                  "rank": 0,
                  "support": 3,
                  "specificity": 0.5,
                  "score": 2.0,
                  "before": {"type": "SimpleName", "label": "bug", "children": []},
                  "after": {"type": "SimpleName", "label": "fix", "children": []}
                },
                {
                  "rank": 1,
                  "support": 4,
                  "specificity": 0.25,
                  "score": 9.0,
                  "before": {"type": "SimpleName", "label": "high", "children": []},
                  "after": {"type": "SimpleName", "label": "low", "children": []}
                },
                {
                  "rank": 2,
                  "support": 1,
                  "specificity": 1.0,
                  "score": 99.0,
                  "before": {"type": "SimpleName", "label": "skip", "children": []},
                  "after": {"type": "SimpleName", "label": "skip2", "children": []}
                }
              ]
            },
            {
              "label": "SWAP_ARGUMENTS",
              "cohortSize": 100,
              "patterns": [
                {
                  "rank": 0,
                  "support": 20,
                  "specificity": 0.2,
                  "score": 5.0,
                  "before": {"type": "SimpleName", "label": "other", "children": []},
                  "after": {"type": "SimpleName", "label": "other2", "children": []}
                }
              ]
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    LearnedPatternJsonLoader loader = new LearnedPatternJsonLoader();

    List<LearnedPatternEntry> entries =
        loader.load(model, new LearnedPatternJsonLoader.Selection(null, 2, 0.2, 0.2));

    assertEquals(3, entries.size());
    assertEquals("high", ((TreeNode) entries.get(0).pattern().pattern().before()).label());
    assertEquals(9.0, entries.get(0).score());
    assertEquals("SWAP_ARGUMENTS", entries.get(1).bugType());
    assertEquals("bug", ((TreeNode) entries.get(2).pattern().pattern().before()).label());
  }

  @Test
  void selectsTopPresetAfterBugTypeFiltering() throws IOException {
    Path model = tempDir.resolve("patterns.json");
    Files.writeString(
        model,
        """
        {
          "runs": [
            {
              "label": "A",
              "cohortSize": 10,
              "patterns": [
                {
                  "rank": 0,
                  "support": 2,
                  "specificity": 1.0,
                  "score": 20.0,
                  "before": {"type": "SimpleName", "label": "a1", "children": []},
                  "after": {"type": "SimpleName", "label": "a2", "children": []}
                }
              ]
            },
            {
              "label": "B",
              "cohortSize": 10,
              "patterns": [
                {
                  "rank": 0,
                  "support": 2,
                  "specificity": 1.0,
                  "score": 10.0,
                  "before": {"type": "SimpleName", "label": "b1", "children": []},
                  "after": {"type": "SimpleName", "label": "b2", "children": []}
                }
              ]
            }
          ]
        }
        """,
        StandardCharsets.UTF_8);

    LearnedPatternJsonLoader loader = new LearnedPatternJsonLoader();

    List<LearnedPatternEntry> entries =
        loader.selectTop(model, new LearnedPatternJsonLoader.Selection("B", 2, 0.0, 0.0), 100);

    assertEquals(1, entries.size());
    assertEquals("B", entries.get(0).bugType());
    assertEquals("b1", ((TreeNode) entries.get(0).pattern().pattern().before()).label());
  }
}
