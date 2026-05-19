package astramut.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

final class FileUtils {
  private FileUtils() {}

  static void recreateDirectory(Path path) throws IOException {
    deleteRecursively(path);
    Files.createDirectories(path);
  }

  static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var stream = Files.walk(path)) {
      List<Path> paths = stream.sorted((a, b) -> b.compareTo(a)).toList();
      for (Path current : paths) {
        Files.deleteIfExists(current);
      }
    }
  }

  static String safeName(String value) {
    return value.replaceAll("[^A-Za-z0-9_.-]", "_");
  }

  static List<String> nonBlankLines(String value) {
    return value.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
  }

  static List<String> defects4jExportLines(String value) {
    return nonBlankLines(value).stream()
        .filter(line -> !line.startsWith("Running "))
        .filter(line -> !line.equals("OK"))
        .toList();
  }

  static List<String> splitClasspath(String classpath) {
    return List.of(classpath.split(Pattern.quote(System.getProperty("path.separator")))).stream()
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .toList();
  }
}
