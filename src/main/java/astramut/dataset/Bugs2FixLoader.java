package astramut.dataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads parallel buggy/fixed Java method pairs from Bugs2Fix-style text files
 *  ({@code train.buggy-fixed.buggy} + {@code train.buggy-fixed.fixed}). */
public final class Bugs2FixLoader {

    public List<BugFix> load(Path buggyFile, Path fixedFile) throws IOException {
        List<String> buggy = Files.readAllLines(buggyFile, StandardCharsets.UTF_8);
        List<String> fixed = Files.readAllLines(fixedFile, StandardCharsets.UTF_8);
        if (buggy.size() != fixed.size()) {
            throw new IOException("buggy and fixed line counts differ: "
                    + buggy.size() + " vs " + fixed.size());
        }
        List<BugFix> out = new ArrayList<>(buggy.size());
        for (int i = 0; i < buggy.size(); i++) {
            String b = buggy.get(i).trim();
            String f = fixed.get(i).trim();
            if (b.isEmpty() || f.isEmpty() || b.equals(f)) continue;
            out.add(new BugFix("BUGS2FIX", b, f));
        }
        return out;
    }
}
