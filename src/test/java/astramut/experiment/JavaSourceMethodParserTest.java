package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import astramut.experiment.ExperimentTypes.MethodRange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceMethodParserTest {
  @TempDir Path tempDir;

  @Test
  void parsesAnnotatedAndMultilineMethods() throws IOException {
    Path source = tempDir.resolve("Sample.java");
    Files.writeString(
        source,
        """
        class Sample {
            /* public void hidden() {} */
            @Deprecated
            public void changed(
                    String value) {
                if (value != null) {
                    System.out.println(value);
                }
            }

            private int same() { return 1; }
        }
        """,
        StandardCharsets.UTF_8);

    List<MethodRange> methods = new JavaSourceMethodParser().parseMethods(source);

    assertEquals(List.of("changed", "same"), methods.stream().map(MethodRange::name).toList());
    assertEquals(new MethodRange("changed", 4, 9), methods.get(0));
  }

  @Test
  void backwardScanFindsNearestDeclaration() throws IOException {
    Path source = tempDir.resolve("Sample.java");
    Files.writeString(
        source,
        """
        class Sample {
            public void target() {
                int value = 1;
                value++;
            }
        }
        """,
        StandardCharsets.UTF_8);

    Set<String> methods = new JavaSourceMethodParser().findMethodsByBackwardScan(source, Set.of(4));

    assertEquals(Set.of("target"), methods);
  }
}
