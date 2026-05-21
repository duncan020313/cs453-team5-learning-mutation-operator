package astramut.cli;

import astramut.learn.EditPattern;
import astramut.learn.Hole;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;
import astramut.mutation.LearnedMutationOperator;
import astramut.mutation.Mutant;
import astramut.mutation.MutationOperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * mutate --model learned/patterns-full.json --input Example.java --output mutants
 *
 * This command loads learned JSON patterns, reverses them through LearnedMutationOperator,
 * applies them to one Java source file, and writes generated mutants to an output directory.
 */
public class MutateCommand {
    public int run(String[] args) {
        Options o;
        try {
            o = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[mutate] " + e.getMessage());
            return printUsage();
        }

        if (o.modelPath == null || o.inputPath == null) {
            return printUsage();
        }

        try {
            String sourceCode = Files.readString(o.inputPath, StandardCharsets.UTF_8);
            List<LearnedPattern> learnedPatterns = loadPatterns(o);

            if (learnedPatterns.isEmpty()) {
                System.out.println("[mutate] no learned patterns selected");
                return 0;
            }

            Files.createDirectories(o.outputDir);

            int operatorIndex = 0;
            int written = 0;
            Set<String> seenMutatedSources = new LinkedHashSet<>();

            for (LearnedPattern learnedPattern : learnedPatterns) {
                if (written >= o.maxMutants) {
                    break;
                }

                MutationOperator operator = new LearnedMutationOperator(learnedPattern, operatorIndex++);

                List<Mutant> mutants = operator.generateMutants(
                        sourceCode,
                        o.inputPath.getFileName().toString(),
                        o.maxMutantsPerOperator
                );

                for (Mutant mutant : mutants) {
                    if (written >= o.maxMutants) {
                        break;
                    }

                    if (mutant.mutatedSource().equals(sourceCode)) {
                        continue;
                    }

                    if (!seenMutatedSources.add(mutant.mutatedSource())) {
                        continue;
                    }

                    Path out = o.outputDir.resolve(String.format("mutant-%05d.java", written));
                    Files.writeString(out, mutant.mutatedSource(), StandardCharsets.UTF_8);

                    System.out.printf(
                            "[mutate] wrote %s  operator=%s  occurrence=%d%n",
                            out,
                            mutant.operatorName(),
                            mutant.occurrenceIndex()
                    );

                    written++;
                }
            }

            System.out.printf("[mutate] selected patterns: %d%n", learnedPatterns.size());
            System.out.printf("[mutate] generated mutants: %d%n", written);
            System.out.printf("[mutate] output dir: %s%n", o.outputDir);
            return 0;

        } catch (IOException e) {
            System.err.println("[mutate] I/O error: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            System.err.println("[mutate] failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private List<LearnedPattern> loadPatterns(Options o) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(o.modelPath.toFile());

        JsonNode runs = root.path("runs");
        if (!runs.isArray()) {
            throw new IllegalArgumentException("model JSON must contain array field: runs");
        }

        List<LearnedPattern> selected = new ArrayList<>();

        for (JsonNode run : runs) {
            String label = run.path("label").asText("");
            int cohortSize = run.path("cohortSize").asInt(0);

            if (o.bugType != null && !o.bugType.equals(label)) {
                continue;
            }

            JsonNode patterns = run.path("patterns");
            if (!patterns.isArray()) {
                continue;
            }

            for (JsonNode patternJson : patterns) {
                int support = patternJson.path("support").asInt(0);
                double specificity = patternJson.path("specificity").asDouble(0.0);

                if (support < o.minSupport) {
                    continue;
                }

                if (specificity < o.minSpecificity) {
                    continue;
                }

                if (o.minCohortRatio > 0.0) {
                    if (cohortSize <= 0) {
                        continue;
                    }
                    double ratio = (double) support / (double) cohortSize;
                    if (ratio < o.minCohortRatio) {
                        continue;
                    }
                }

                TreePattern before = treeFromJson(patternJson.path("before"));
                TreePattern after = treeFromJson(patternJson.path("after"));

                EditPattern editPattern = new EditPattern(before, after);

                LearnedPattern learnedPattern = new LearnedPattern(
                        editPattern,
                        support,
                        specificity,
                        List.of(editPattern)
                );

                selected.add(learnedPattern);

                if (selected.size() >= o.maxOperators) {
                    return selected;
                }
            }
        }

        return selected;
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

    private int printUsage() {
        System.out.println("Usage:");
        System.out.println("  astramut mutate --model <patterns-full.json> --input <Java file> --output <dir> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --model <path>                       learned pattern JSON");
        System.out.println("  --input <path>                       Java source file to mutate");
        System.out.println("  --output <dir>                       output directory for mutants, default: mutants");
        System.out.println("  --bug-type <label>                   optional run label filter, e.g. CHANGE_OPERATOR");
        System.out.println("  --max-operators <n>                  maximum learned patterns to try, default: 250");
        System.out.println("  --max-mutants-per-operator <n>       mutants generated per operator, default: 1");
        System.out.println("  --max-mutants <n>                    total mutants to write, default: 100");
        System.out.println("  --min-support <n>                    minimum support, default: 20");
        System.out.println("  --min-specificity <x>                minimum specificity, default: 0.2");
        System.out.println("  --min-cohort-ratio <x>               minimum support/cohortSize ratio, default: 0.005");
        return 0;
    }

    static final class Options {
        Path modelPath;
        Path inputPath;
        Path outputDir = Path.of("mutants");

        String bugType;

        int maxOperators = 250;
        int maxMutantsPerOperator = 1;
        int maxMutants = 100;

        int minSupport = 20;
        double minSpecificity = 0.2;
        double minCohortRatio = 0.005;

        static Options parse(String[] args) {
            Options o = new Options();

            for (int i = 0; i < args.length; i++) {
                String a = args[i];

                switch (a) {
                    case "--model" -> {
                        o.modelPath = Path.of(requireValue(args, ++i, a));
                    }
                    case "--input" -> {
                        o.inputPath = Path.of(requireValue(args, ++i, a));
                    }
                    case "--output" -> {
                        o.outputDir = Path.of(requireValue(args, ++i, a));
                    }
                    case "--bug-type" -> {
                        o.bugType = requireValue(args, ++i, a);
                    }
                    case "--max-operators" -> {
                        o.maxOperators = Integer.parseInt(requireValue(args, ++i, a));
                    }
                    case "--max-mutants-per-operator" -> {
                        o.maxMutantsPerOperator = Integer.parseInt(requireValue(args, ++i, a));
                    }
                    case "--max-mutants" -> {
                        o.maxMutants = Integer.parseInt(requireValue(args, ++i, a));
                    }
                    case "--min-support" -> {
                        o.minSupport = Integer.parseInt(requireValue(args, ++i, a));
                    }
                    case "--min-specificity" -> {
                        o.minSpecificity = Double.parseDouble(requireValue(args, ++i, a));
                    }
                    case "--min-cohort-ratio" -> {
                        o.minCohortRatio = Double.parseDouble(requireValue(args, ++i, a));
                    }
                    default -> throw new IllegalArgumentException("unknown option: " + a);
                }
            }

            if (o.maxOperators <= 0) {
                throw new IllegalArgumentException("--max-operators must be positive");
            }

            if (o.maxMutantsPerOperator <= 0) {
                throw new IllegalArgumentException("--max-mutants-per-operator must be positive");
            }

            if (o.maxMutants <= 0) {
                throw new IllegalArgumentException("--max-mutants must be positive");
            }

            return o;
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + optionName);
            }
            return args[index];
        }
    }
}
