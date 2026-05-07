package astramut.cli;

public class AstraMutCommand {
    public int run(String[] args) {
        if (args.length == 0) {
            return printUsage();
        }

        System.out.println("Command is not implemented yet: " + args[0]);
        return 0;
    }

    private int printUsage() {
        System.out.println("Usage: astramut <train|mutate|experiment> [options]");
        return 0;
    }
}
