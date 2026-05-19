package astramut.experiment;

import astramut.experiment.ExperimentTypes.MethodRange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaSourceMethodParser {
  private static final Pattern METHOD_NAME =
      Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{}]+)?$");
  private static final Set<String> NON_METHOD_NAMES =
      Set.of("if", "for", "while", "switch", "catch", "new", "return", "throw", "assert");

  List<MethodRange> parseMethods(Path source) throws IOException {
    List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
    List<MethodRange> methods = new ArrayList<>();
    StringBuilder pending = new StringBuilder();
    int pendingStart = -1;
    MethodRange current = null;
    int methodDepth = 0;
    boolean inBlockComment = false;

    for (int i = 0; i < lines.size(); i++) {
      StripResult stripResult = stripLine(lines.get(i), inBlockComment);
      inBlockComment = stripResult.inBlockComment();
      String stripped = stripResult.line().trim();
      int lineNumber = i + 1;
      if (stripped.isEmpty() || stripped.startsWith("@")) {
        continue;
      }

      if (current == null) {
        if (pendingStart < 0) {
          pendingStart = lineNumber;
        }
        pending.append(' ').append(stripped);
        int openBrace = stripped.indexOf('{');
        if (openBrace >= 0) {
          String signature =
              pending.substring(
                  0, pending.indexOf("{") >= 0 ? pending.indexOf("{") : pending.length());
          Optional<String> name = methodName(signature);
          if (name.isPresent()) {
            current = new MethodRange(name.get(), pendingStart, lineNumber);
            methodDepth = count(lines.get(i), '{') - count(lines.get(i), '}');
            if (methodDepth <= 0) {
              methods.add(current.withEnd(lineNumber));
              current = null;
            }
          }
          pending = new StringBuilder();
          pendingStart = -1;
        } else if (stripped.endsWith(";")) {
          pending = new StringBuilder();
          pendingStart = -1;
        }
      } else {
        methodDepth += count(lines.get(i), '{') - count(lines.get(i), '}');
        if (methodDepth <= 0) {
          methods.add(current.withEnd(lineNumber));
          current = null;
        }
      }
    }
    return methods;
  }

  Set<String> findMethodsByBackwardScan(Path source, Set<Integer> changedLines) throws IOException {
    List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
    Set<String> names = new TreeSet<>();
    for (int changedLine : changedLines) {
      int upperBound = Math.min(changedLine, lines.size());
      int lowerBound = Math.max(1, upperBound - 200);
      for (int lineNumber = upperBound; lineNumber >= lowerBound; lineNumber--) {
        String stripped = stripLineStateless(lines.get(lineNumber - 1)).trim();
        if (!looksLikeDeclarationLine(stripped)) {
          continue;
        }
        Optional<String> name = methodName(stripped.replace("{", ""));
        if (name.isPresent()) {
          names.add(name.get());
          break;
        }
      }
    }
    return names;
  }

  private Optional<String> methodName(String signature) {
    String normalized = signature.replaceAll("\\s+", " ").trim();
    if (!normalized.contains("(") || normalized.contains("=")) {
      return Optional.empty();
    }
    Matcher matcher = METHOD_NAME.matcher(normalized);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String name = matcher.group(1);
    if (NON_METHOD_NAMES.contains(name)) {
      return Optional.empty();
    }
    return Optional.of(name);
  }

  private boolean looksLikeDeclarationLine(String line) {
    return line.contains("(")
        && line.contains(")")
        && line.contains("{")
        && (line.contains(" public ")
            || line.startsWith("public ")
            || line.contains(" protected ")
            || line.startsWith("protected ")
            || line.contains(" private ")
            || line.startsWith("private ")
            || line.contains(" static ")
            || line.startsWith("static ")
            || line.contains(" final ")
            || line.startsWith("final ")
            || line.contains(" synchronized ")
            || line.startsWith("synchronized "));
  }

  private StripResult stripLine(String line, boolean inBlockComment) {
    StringBuilder result = new StringBuilder();
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char current = line.charAt(i);
      char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
      if (inBlockComment) {
        if (current == '*' && next == '/') {
          inBlockComment = false;
          i++;
        }
        continue;
      }
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
        result.append(' ');
        continue;
      }
      if (current == '/' && next == '/') {
        break;
      }
      if (current == '/' && next == '*') {
        inBlockComment = true;
        i++;
        continue;
      }
      if (current == '"') {
        inString = true;
        result.append(' ');
        continue;
      }
      result.append(current);
    }
    return new StripResult(result.toString(), inBlockComment);
  }

  private String stripLineStateless(String line) {
    return stripLine(line, false).line();
  }

  private int count(String line, char c) {
    int total = 0;
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == c) {
        total++;
      }
    }
    return total;
  }

  private record StripResult(String line, boolean inBlockComment) {}
}
