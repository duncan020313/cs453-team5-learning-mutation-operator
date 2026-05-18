package astramut.cli;

import astramut.learn.EditPattern;
import astramut.learn.Hole;
import astramut.learn.LearnedPattern;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;
import astramut.mutation.LearnedMutationOperator;
import astramut.mutation.Mutant;
import astramut.mutation.MutationOperator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

public class AstraMutCommand {
    public int run(String[] args) {
        if (args.length == 0) {
            return printUsage();
        }

        return switch (args[0]) {
            case "mutate-demo" -> runMutateDemo(args);
            case "train", "mutate", "experiment" -> notImplemented(args[0]);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield printUsage();
            }
        };
    }

    private int runMutateDemo(String[] args) {
        Path input = null;
        Path outputDir = Path.of("mutants");
        int maxMutants = 10;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --input");
                        return 2;
                    }
                    input = Path.of(args[++i]);
                }
                case "--output" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --output");
                        return 2;
                    }
                    outputDir = Path.of(args[++i]);
                }
                case "--max-mutants" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Missing value for --max-mutants");
                        return 2;
                    }
                    maxMutants = Integer.parseInt(args[++i]);
                }
                default -> {
                    System.err.println("Unknown option for mutate-demo: " + args[i]);
                    return 2;
                }
            }
        }

        if (input == null) {
            System.err.println("Missing required option: --input");
            return 2;
        }

        try {
            String sourceCode = Files.readString(input, StandardCharsets.UTF_8);

            MutationOperator operator = demoEqualsToNotEqualsOperator();

            List<Mutant> mutants = operator.generateMutants(
                    sourceCode,
                    input.getFileName().toString(),
                    maxMutants
            );

            Files.createDirectories(outputDir);

            for (int i = 0; i < mutants.size(); i++) {
                Path out = outputDir.resolve("mutant-" + i + ".java");
                Files.writeString(out, mutants.get(i).mutatedSource(), StandardCharsets.UTF_8);
                System.out.println("Generated " + out + " by " + mutants.get(i).operatorName());
            }

            System.out.println("Total mutants: " + mutants.size());
            return 0;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            System.err.println("Mutation failed: " + e.getMessage());
            return 1;
        }
    }

    private MutationOperator demoEqualsToNotEqualsOperator() {
        TreePattern left = new Hole("?h0");
        TreePattern right = new Hole("?h1");

        TreePattern buggy = new TreeNode(
                "InfixExpression",
                "!=",
                List.of(left, right)
        );

        TreePattern fixed = new TreeNode(
                "InfixExpression",
                "==",
                List.of(left, right)
        );

        EditPattern fixPattern = new EditPattern(buggy, fixed);

        LearnedPattern learnedPattern = new LearnedPattern(
                fixPattern,
                1,
                1.0,
                List.of(fixPattern)
        );

        return new LearnedMutationOperator(learnedPattern, 0);
    }

    private int notImplemented(String command) {
        System.out.println("Command is not implemented yet: " + command);
        System.out.println("For mutation smoke test, use:");
        System.out.println("  astramut mutate-demo --input Example.java --output mutants");
        return 0;
    }

    private int printUsage() {
        System.out.println("Usage:");
        System.out.println("  astramut <train|mutate|experiment> [options]");
        System.out.println("  astramut mutate-demo --input <Java file> [--output <dir>] [--max-mutants <n>]");
        return 0;
    }
}