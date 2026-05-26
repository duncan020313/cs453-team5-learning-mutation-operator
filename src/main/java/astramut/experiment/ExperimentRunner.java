package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.io.IOException;

public class ExperimentRunner {
  private static final String PIT_COMMAND = "pitest-score";
  private static final String LEARNED_COMMAND = "learned-score";

  public int run(String[] args) {
    if (args.length == 0 || "-h".equals(args[0]) || "--help".equals(args[0])) {
      printUsage();
      return 0;
    }
    if (!PIT_COMMAND.equals(args[0]) && !LEARNED_COMMAND.equals(args[0])) {
      System.err.println("Unknown experiment command: " + args[0]);
      printUsage();
      return 2;
    }
    if (args.length > 1 && ("-h".equals(args[1]) || "--help".equals(args[1]))) {
      printUsage();
      return 0;
    }

    try {
      if (PIT_COMMAND.equals(args[0])) {
        PitestScoreOptions options = PitestScoreOptions.parse(dropFirst(args));
        return new PitestScoreExperiment(options).run();
      }
      LearnedScoreOptions options = LearnedScoreOptions.parse(dropFirst(args));
      return new LearnedScoreExperiment(options).run();
    } catch (IllegalArgumentException e) {
      System.err.println("[error] " + e.getMessage());
      printUsage();
      return 2;
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
    System.out.println("Usage:");
    System.out.println("  astramut experiment pitest-score [options]");
    System.out.println("  astramut experiment learned-score [options]");
    System.out.println();
    System.out.println("PIT options:");
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
    System.out.println();
    System.out.println("Learned options:");
    System.out.println(
        "  --projects <p1,p2>        Defects4J project ids. Default: defects4j pids");
    System.out.println(
        "  --bugs <ids>              Bug ids, e.g. 1,2,10..15. Default: active bugs");
    System.out.println("  --work-dir <dir>          Default: data/defects4j-learned/work");
    System.out.println("  --out-dir <dir>           Default: data/defects4j-learned/results");
    System.out.println("  --bug-threads <n>         Concurrent Defects4J bugs. Default: 1");
    System.out.println("  --model-archive <path>    Default: learned_260520.tar.gz");
    System.out.println("  --model-entry <path>      Default: learned/patterns-full.json");
    System.out.println("  --preset <csv>            top1000,top100. Default: top1000,top100");
    System.out.println("  --bug-type <label>        Optional learned run label filter");
    System.out.println("  --min-support <n>         Default: 2");
    System.out.println("  --min-specificity <x>     Default: 0.0");
    System.out.println("  --min-cohort-ratio <x>    Default: 0.0");
    System.out.println("  --mutant-timeout-seconds <n>    Default: 300");
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
