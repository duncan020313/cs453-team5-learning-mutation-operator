package astramut.cli;

import astramut.dataset.BugFix;
import astramut.dataset.ManySStuBsLoader;
import astramut.diff.GumTreeDiffEngine;
import astramut.learn.GumTreeDiff;
import astramut.learn.Hole;
import astramut.learn.LearnedModel;
import astramut.learn.LearnedPattern;
import astramut.learn.PatternFormatter;
import astramut.learn.PatternLearner;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code train <datasetPath>} — load ManySStuBs4J, diff every fix, learn patterns. Use {@code --per-bug-type} for the 16 SStuB categories. Use {@code --out} to persist as JSON. */
public class LearnCommand {

    public int run(String[] args) {
        Options o;
        try {
            o = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[train] " + e.getMessage());
            return printUsage();
        }
        if (o.datasetPath == null) return printUsage();

        ManySStuBsLoader loader = new ManySStuBsLoader();
        List<BugFix> bugs;
        try {
            bugs = loader.loadWithSourceCode(o.datasetPath);
        } catch (IOException e) {
            System.err.println("[train] failed to load dataset: " + e.getMessage());
            return 1;
        }
        System.out.printf("[train] dataset has %d bug-fixes with source%n", bugs.size());

        List<RunResult> results = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        if (o.perBugType) {
            Map<String, List<BugFix>> byType = new TreeMap<>();
            for (BugFix b : bugs) byType.computeIfAbsent(b.bugType(), k -> new ArrayList<>()).add(b);
            System.out.printf("[train] %d bug types found%n", byType.size());
            for (Map.Entry<String, List<BugFix>> e : byType.entrySet()) {
                System.out.printf("[train]   %s : %d%n", e.getKey(), e.getValue().size());
            }
            for (Map.Entry<String, List<BugFix>> e : byType.entrySet()) {
                List<BugFix> cohort = e.getValue();
                if (o.limit > 0 && cohort.size() > o.limit) cohort = cohort.subList(0, o.limit);
                System.out.println();
                System.out.println("════════════════════════════════════════════════");
                System.out.printf("══ bugType = %s  (cohort size = %d) ══%n", e.getKey(), cohort.size());
                System.out.println("════════════════════════════════════════════════");
                results.add(runOne(o, e.getKey(), cohort));
            }
        } else {
            results.add(runOne(o, o.bugType == null ? "all" : o.bugType, filter(bugs, o.bugType, o.limit)));
        }
        long totalMs = System.currentTimeMillis() - t0;
        System.out.printf("%n[train] total run time: %.1fs%n", totalMs / 1000.0);

        if (o.outPath != null) {
            try {
                writeJson(o.outPath, o, results, totalMs);
                System.out.printf("[train] wrote patterns to %s%n", o.outPath);
            } catch (IOException ex) {
                System.err.println("[train] failed to write json: " + ex.getMessage());
                return 1;
            }
        }
        return 0;
    }

    private RunResult runOne(Options o, String label, List<BugFix> cohort) {
        GumTreeDiffEngine engine = new GumTreeDiffEngine();
        List<GumTreeDiff> diffs = new ArrayList<>(cohort.size());
        int failures = 0;
        long t0 = System.currentTimeMillis();
        for (BugFix b : cohort) {
            try {
                diffs.add(diffWithFallback(engine, b.sourceBeforeFix(), b.sourceAfterFix()));
            } catch (Exception ex) {
                failures++;
            }
        }
        long diffMs = System.currentTimeMillis() - t0;
        System.out.printf("[%s] diffed %d in %.1fs (failures=%d)%n",
                label, diffs.size(), diffMs / 1000.0, failures);

        t0 = System.currentTimeMillis();
        LearnedModel model = new PatternLearner(o.minSupport, o.maxHoles).learn(diffs);
        long learnMs = System.currentTimeMillis() - t0;
        System.out.printf("[%s] learned %d patterns in %.1fs%n",
                label, model.patterns().size(), learnMs / 1000.0);

        int show = Math.min(o.top, model.patterns().size());
        for (int i = 0; i < show; i++) {
            LearnedPattern p = model.patterns().get(i);
            System.out.printf("[%2d] support=%-5d  spec=%.3f  score=%.3f%n     %s%n",
                    i, p.support(), p.specificity(), p.score(),
                    PatternFormatter.format(p.pattern()));
        }
        return new RunResult(label, cohort.size(), diffs.size(), failures, learnMs, model.patterns());
    }

    private static List<BugFix> filter(List<BugFix> bugs, String bugType, int limit) {
        List<BugFix> out = bugs;
        if (bugType != null) {
            out = new ArrayList<>();
            for (BugFix b : bugs) if (bugType.equals(b.bugType())) out.add(b);
        }
        if (limit > 0 && out.size() > limit) out = out.subList(0, limit);
        return out;
    }

    /** Statement wrap (engine default) fails for bare expressions. Retry with expression-context wrap. */
    private static GumTreeDiff diffWithFallback(GumTreeDiffEngine engine, String before, String after)
            throws IOException {
        GumTreeDiff d = engine.diff(before, after);
        if (!d.actions().isEmpty()) return d;
        String wb = "Object _v_ = (" + before + ");";
        String wa = "Object _v_ = (" + after + ");";
        try {
            GumTreeDiff alt = engine.diff(wb, wa);
            if (!alt.actions().isEmpty()) return alt;
        } catch (Exception ignored) { }
        return d;
    }

    private static void writeJson(Path out, Options o, List<RunResult> runs, long totalMs) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dataset", o.datasetPath.toString());
        meta.put("limit", o.limit);
        meta.put("minSupport", o.minSupport);
        meta.put("maxHoles", o.maxHoles);
        meta.put("topPerRun", o.top);
        meta.put("totalRunMs", totalMs);
        meta.put("timestamp", Instant.now().toString());
        root.put("meta", meta);

        List<Map<String, Object>> runList = new ArrayList<>();
        for (RunResult r : runs) {
            Map<String, Object> rj = new LinkedHashMap<>();
            rj.put("label", r.label());
            rj.put("cohortSize", r.cohortSize());
            rj.put("diffSuccesses", r.diffSuccesses());
            rj.put("diffFailures", r.diffFailures());
            rj.put("learnTimeMs", r.learnTimeMs());
            rj.put("patternCount", r.patterns().size());
            List<Map<String, Object>> ps = new ArrayList<>();
            for (int i = 0; i < r.patterns().size(); i++) {
                LearnedPattern p = r.patterns().get(i);
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("rank", i);
                pm.put("support", p.support());
                pm.put("specificity", p.specificity());
                pm.put("score", p.score());
                pm.put("before", treeToJson(p.pattern().before()));
                pm.put("after", treeToJson(p.pattern().after()));
                ps.add(pm);
            }
            rj.put("patterns", ps);
            runList.add(rj);
        }
        root.put("runs", runList);

        out.getParent().toFile().mkdirs();
        new ObjectMapper().writeValue(out.toFile(), root);
    }

    /** {@code Hole → {"hole": id}}, {@code TreeNode → {"type","label","children":[...]}}. */
    private static Object treeToJson(TreePattern p) {
        if (p instanceof Hole h) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hole", h.id());
            return m;
        }
        TreeNode n = (TreeNode) p;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", n.type());
        m.put("label", n.label());
        List<Object> kids = new ArrayList<>(n.children().size());
        for (TreePattern c : n.children()) kids.add(treeToJson(c));
        m.put("children", kids);
        return m;
    }

    private int printUsage() {
        System.out.println("Usage: astramut train <datasetPath> [--bug-type T] [--per-bug-type] "
                + "[--min-support N] [--max-holes M] [--limit K] [--top N] [--out path.json]");
        System.out.println("  defaults: min-support=2, max-holes=4, top=20, no bug-type filter, no limit");
        System.out.println("  --per-bug-type: run pattern learning per SStuB category (use with --limit)");
        System.out.println("  --out: dump ALL learned patterns per run as compact JSON (--top affects console only)");
        return 0;
    }

    record RunResult(String label, int cohortSize, int diffSuccesses, int diffFailures,
                     long learnTimeMs, List<LearnedPattern> patterns) {}

    static final class Options {
        Path datasetPath;
        Path outPath;
        String bugType;
        boolean perBugType;
        int minSupport = PatternLearner.DEFAULT_MIN_SUPPORT;
        int maxHoles = PatternLearner.DEFAULT_MAX_HOLES;
        int limit = 0;
        int top = 20;

        static Options parse(String[] args) {
            Options o = new Options();
            Map<String, String> flags = new LinkedHashMap<>();
            List<String> positional = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.startsWith("--")) {
                    String key = a.substring(2);
                    if ("per-bug-type".equals(key)) {
                        o.perBugType = true;
                        continue;
                    }
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value for " + a);
                    }
                    flags.put(key, args[++i]);
                } else {
                    positional.add(a);
                }
            }
            if (!positional.isEmpty()) o.datasetPath = Path.of(positional.get(0));
            if (flags.containsKey("bug-type")) o.bugType = flags.get("bug-type");
            if (flags.containsKey("min-support")) o.minSupport = Integer.parseInt(flags.get("min-support"));
            if (flags.containsKey("max-holes")) o.maxHoles = Integer.parseInt(flags.get("max-holes"));
            if (flags.containsKey("limit")) o.limit = Integer.parseInt(flags.get("limit"));
            if (flags.containsKey("top")) o.top = Integer.parseInt(flags.get("top"));
            if (flags.containsKey("out")) o.outPath = Path.of(flags.get("out"));
            return o;
        }
    }
}
