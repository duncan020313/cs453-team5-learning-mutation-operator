package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.io.IOException;

public class ExperimentRunner {
  private static final String COMMAND = "pitest-score";

  public int run(String[] args) {
    if (args.length == 0 || "-h".equals(args[0]) || "--help".equals(args[0])) {
      printUsage();
      return 0;
    }
    if (!COMMAND.equals(args[0])) {
      System.err.println("Unknown experiment command: " + args[0]);
      printUsage();
      return 2;
    }
    if (args.length > 1 && ("-h".equals(args[1]) || "--help".equals(args[1]))) {
      printUsage();
      return 0;
    }

    PitestScoreOptions options;
    try {
      options = PitestScoreOptions.parse(dropFirst(args));
    } catch (IllegalArgumentException e) {
      System.err.println("[error] " + e.getMessage());
      printUsage();
      return 2;
    }

    try {
      return new PitestScoreExperiment(options).run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("[error] Experiment interrupted");
      return 1;
    } catch (IOException
        | CommandFailedException
        | ExperimentSetupException
        | SummaryFormatException e) {
      System.err.println("[error] " + e.getMessage());
      return 1;
    }
  }

  private void printUsage() {
    System.out.println("Usage: astramut experiment pitest-score [options]");
    System.out.println("Options:");
    System.out.println(
        "  --projects <p1,p2>        Defects4J project ids. Default: defects4j pids");
    System.out.println(
        "  --bugs <ids>              Bug ids, e.g. 1,2,10..15. Default: active bugs");
    System.out.println("  --work-dir <dir>          Default: data/defects4j-pitest/work");
    System.out.println("  --out-dir <dir>           Default: data/defects4j-pitest/results");
    System.out.println("  --threads <n>             PIT worker threads. Default: 1");
    System.out.println("  --bug-threads <n>         Concurrent Defects4J bugs. Default: 1");
    System.out.println("  --timeout-factor <float>  PIT timeout factor. Default: 1.25");
    System.out.println("  Runs PIT mutator variants: DEFAULTS and ALL");
    System.out.println("  --resume                  Skip successful existing rows. Default: true");
    System.out.println("  --no-resume               Re-run all requested bugs");
    System.out.println("  --keep-workdirs           Keep checked-out Defects4J work directories");
  }

  private static String[] dropFirst(String[] args) {
    String[] result = new String[args.length - 1];
    System.arraycopy(args, 1, result, 0, result.length);
    return result;
  }
}
