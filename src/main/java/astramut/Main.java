package astramut;

import astramut.cli.AstraMutCommand;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new AstraMutCommand().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
