package astramut.cli;

import astramut.experiment.ExperimentRunner;

public class AstraMutCommand {
    public int run(String[] args) {
        if (args.length == 0) {
            return printUsage();
        }

        String[] sub = new String[args.length - 1];
        System.arraycopy(args, 1, sub, 0, sub.length);

        return switch (args[0]) {
            case "experiment" -> new ExperimentRunner().run(sub);
            case "train" -> new LearnCommand().run(sub);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield printUsage();
            }
        };
    }

    private int printUsage() {
        System.out.println("Usage: astramut <train|mutate|experiment> [options]");
        System.out.println("       astramut train <datasetPath> [--bug-type T] [--min-support N] [--max-holes M] [--limit K] [--top N]");
        System.out.println("       astramut experiment pitest-score [options]");
        return 0;
    }
}
