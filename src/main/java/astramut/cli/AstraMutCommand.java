package astramut.cli;

import astramut.experiment.ExperimentRunner;

public class AstraMutCommand {
    public int run(String[] args) {
        if (args.length == 0) {
            return printUsage();
        }

        if ("experiment".equals(args[0])) {
            String[] experimentArgs = new String[args.length - 1];
            System.arraycopy(args, 1, experimentArgs, 0, experimentArgs.length);
            return new ExperimentRunner().run(experimentArgs);
        }

        System.err.println("Unknown command: " + args[0]);
        return printUsage();
    }

    private int printUsage() {
        System.out.println("Usage: astramut <train|mutate|experiment> [options]");
        System.out.println("       astramut experiment pitest-score [options]");
        return 0;
    }
}
