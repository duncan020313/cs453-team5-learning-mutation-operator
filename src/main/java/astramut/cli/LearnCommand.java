package astramut.cli;

import astramut.dataset.BugFix;
import astramut.dataset.Bugs2FixLoader;
import astramut.dataset.ManySStuBsLoader;
import astramut.diff.GumTreeDiffEngine;
import astramut.learn.EditPattern;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        List<BugFix> bugs;
        try {
            if (o.bugs2fixFixedPath != null) {
                bugs = new Bugs2FixLoader().load(o.datasetPath, o.bugs2fixFixedPath);
            } else {
                bugs = new ManySStuBsLoader().loadWithSourceCode(o.datasetPath);
            }
        } catch (IOException e) {
            System.err.println("[train] failed to load dataset: " + e.getMessage());
            return 1;
        }
        System.out.printf("[train] dataset has %d bug-fixes with source%n", bugs.size());

        // Compute corpus magic-value distribution (top-K NumberLiteral frequencies).
        o.magicValueDist = extractMagicValueDistribution(bugs, 50);
        System.out.printf("[train] magic-value distribution: top-%d values cover %d occurrences%n",
                o.magicValueDist.size(),
                o.magicValueDist.stream().mapToInt(e -> (int) e.get("count")).sum());

        // Corpus-common type names: only these stay literal in type contexts; rare ones hole-ify.
        o.commonTypeNames = extractCommonTypeNames(bugs, 100);
        astramut.learn.ConcreteEditExtractor.setCommonTypeNames(o.commonTypeNames);
        System.out.printf("[train] common-type names: top-%d preserved as literal in type contexts%n",
                o.commonTypeNames.size());

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
        boolean methodMode = o.bugs2fixFixedPath != null;
        List<GumTreeDiff> diffs = new ArrayList<>(cohort.size());
        int failures = 0;
        long t0 = System.currentTimeMillis();
        for (BugFix b : cohort) {
            try {
                diffs.add(diffWithFallback(engine, b.sourceBeforeFix(), b.sourceAfterFix(), methodMode));
            } catch (Exception ex) {
                failures++;
            }
        }
        long diffMs = System.currentTimeMillis() - t0;
        System.out.printf("[%s] diffed %d in %.1fs (failures=%d)%n",
                label, diffs.size(), diffMs / 1000.0, failures);

        t0 = System.currentTimeMillis();
        LearnedModel model = new PatternLearner().learn(diffs);
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

    /** Statement wrap (engine default) fails for bare expressions. Retry with expression-context wrap.
     *  In method mode, use the method-level wrap directly with no fallback. */
    private static GumTreeDiff diffWithFallback(GumTreeDiffEngine engine, String before, String after,
                                                boolean methodMode) throws IOException {
        if (methodMode) return engine.diff(before, after, true);
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
        meta.put("topPerRun", o.top);
        meta.put("totalRunMs", totalMs);
        meta.put("timestamp", Instant.now().toString());
        // Corpus magic-number frequencies; engine samples from this for NumberLiteral(__MAGIC__) on RHS.
        if (o.magicValueDist != null) meta.put("magicValueDistribution", o.magicValueDist);
        // Corpus-common type names; types not in this list were hole-ified during extraction.
        if (o.commonTypeNames != null) {
            List<String> sorted = new ArrayList<>(o.commonTypeNames);
            sorted.sort(String::compareTo);
            meta.put("commonTypeNames", sorted);
        }
        root.put("meta", meta);

        // Cross-run dedup: home a sig in the run with highest single-cohort support; sum supports.
        Map<String, MergeAccumulator> sigMap = new LinkedHashMap<>();
        for (int ri = 0; ri < runs.size(); ri++) {
            RunResult r = runs.get(ri);
            for (LearnedPattern p : r.patterns()) {
                String sig = p.pattern().canonicalSignature();
                MergeAccumulator acc = sigMap.computeIfAbsent(sig, k -> new MergeAccumulator());
                acc.contributors.add(new Contribution(ri, r.label(), p));
            }
        }

        // Decide winner per signature and combined support.
        Map<Integer, Map<String, MergedPattern>> homeByRun = new LinkedHashMap<>();
        for (Map.Entry<String, MergeAccumulator> e : sigMap.entrySet()) {
            MergeAccumulator acc = e.getValue();
            acc.contributors.sort(Comparator.<Contribution>comparingInt(c -> c.pattern.support()).reversed());
            Contribution winner = acc.contributors.get(0);
            int combinedSupport = acc.contributors.stream().mapToInt(c -> c.pattern.support()).sum();
            List<String> mergedFrom = acc.contributors.size() > 1
                    ? acc.contributors.stream().skip(1).map(c -> c.runLabel).distinct().toList()
                    : List.of();
            homeByRun.computeIfAbsent(winner.runIndex, k -> new LinkedHashMap<>())
                    .put(e.getKey(), new MergedPattern(winner.pattern, combinedSupport, mergedFrom));
        }

        List<Map<String, Object>> runList = new ArrayList<>();
        for (int ri = 0; ri < runs.size(); ri++) {
            RunResult r = runs.get(ri);
            Map<String, MergedPattern> home = homeByRun.getOrDefault(ri, Map.of());
            List<MergedPattern> kept = new ArrayList<>();
            for (LearnedPattern p : r.patterns()) {
                String sig = p.pattern().canonicalSignature();
                MergedPattern mp = home.get(sig);
                if (mp != null && mp.original == p) kept.add(mp);
            }
            // Re-sort by combined score so ranks reflect deduped supports.
            kept.sort(Comparator.comparingDouble((MergedPattern mp) -> mp.combinedScore()).reversed());

            Map<String, Object> rj = new LinkedHashMap<>();
            rj.put("label", r.label());
            rj.put("cohortSize", r.cohortSize());
            rj.put("diffSuccesses", r.diffSuccesses());
            rj.put("diffFailures", r.diffFailures());
            rj.put("learnTimeMs", r.learnTimeMs());
            rj.put("patternCount", kept.size());
            List<Map<String, Object>> ps = new ArrayList<>();
            for (int i = 0; i < kept.size(); i++) {
                MergedPattern mp = kept.get(i);
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("rank", i);
                pm.put("support", mp.combinedSupport);
                pm.put("specificity", mp.original.specificity());
                pm.put("score", mp.combinedScore());
                if (!mp.mergedFrom.isEmpty()) pm.put("mergedFrom", mp.mergedFrom);
                pm.put("before", treeToJson(mp.original.pattern().before()));
                pm.put("after", treeToJson(mp.original.pattern().after()));
                ps.add(pm);
            }
            rj.put("patterns", ps);
            runList.add(rj);
        }
        root.put("runs", runList);

        out.getParent().toFile().mkdirs();
        new ObjectMapper().writeValue(out.toFile(), root);
    }

    /** Boundary numeric labels are NOT magic; everything else feeds the distribution. */
    private static final Set<String> CANONICAL_NUMERIC_LABELS = Set.of(
            "0", "1", "-1",
            "0.0", "1.0", "-1.0",
            "0f", "1f", "-1f", "0F", "1F", "-1F",
            "0l", "1l", "-1l", "0L", "1L", "-1L",
            "0d", "1d", "-1d", "0D", "1D", "-1D");
    private static final java.util.regex.Pattern NUMERIC_LITERAL_RE =
            java.util.regex.Pattern.compile(
                    "(?<![\\w.])([+-]?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?[fFlLdD]?)(?![\\w])");

    /** Top-K non-boundary numeric literal frequencies across all bug sources, sorted descending. */
    private static List<Map<String, Object>> extractMagicValueDistribution(List<BugFix> bugs, int topK) {
        Map<String, Integer> freq = new HashMap<>();
        for (BugFix b : bugs) {
            countNumerics(b.sourceBeforeFix(), freq);
            countNumerics(b.sourceAfterFix(), freq);
        }
        // Drop canonical (boundary) values — those stay literal in the catalogue.
        for (String c : CANONICAL_NUMERIC_LABELS) freq.remove(c);
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();
    }

    private static void countNumerics(String src, Map<String, Integer> freq) {
        if (src == null || src.length() < 1) return;
        java.util.regex.Matcher m = NUMERIC_LITERAL_RE.matcher(src);
        while (m.find()) {
            freq.merge(m.group(1), 1, Integer::sum);
        }
    }

    /** Identifies PascalCase-style names ≥2 chars (type-name candidates) for the corpus type whitelist. */
    private static final java.util.regex.Pattern TYPE_NAME_RE =
            java.util.regex.Pattern.compile("\\b([A-Z][a-zA-Z][a-zA-Z0-9_]*)\\b");

    /** Abstracted-form placeholders (Bugs2Fix-style {@code TYPE_3, VAR_0, METHOD_1, ...}) — never literal. */
    private static final java.util.regex.Pattern ABSTRACTED_PLACEHOLDER_RE =
            java.util.regex.Pattern.compile("[A-Z]+_\\d+");

    /** Top-K PascalCase identifiers across the corpus — proxy for "common type names". */
    private static Set<String> extractCommonTypeNames(List<BugFix> bugs, int topK) {
        Map<String, Integer> freq = new HashMap<>();
        for (BugFix b : bugs) {
            countTypeNames(b.sourceBeforeFix(), freq);
            countTypeNames(b.sourceAfterFix(), freq);
        }
        return freq.entrySet().stream()
                .filter(e -> !ABSTRACTED_PLACEHOLDER_RE.matcher(e.getKey()).matches())
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void countTypeNames(String src, Map<String, Integer> freq) {
        if (src == null || src.length() < 1) return;
        java.util.regex.Matcher m = TYPE_NAME_RE.matcher(src);
        while (m.find()) freq.merge(m.group(1), 1, Integer::sum);
    }

    private record Contribution(int runIndex, String runLabel, LearnedPattern pattern) {}

    private static final class MergeAccumulator {
        final List<Contribution> contributors = new ArrayList<>();
    }

    private static final class MergedPattern {
        final LearnedPattern original;
        final int combinedSupport;
        final List<String> mergedFrom;
        MergedPattern(LearnedPattern p, int sup, List<String> merged) {
            this.original = p; this.combinedSupport = sup; this.mergedFrom = merged;
        }
        double combinedScore() { return combinedSupport * original.specificity(); }
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
                + "[--limit K] [--top N] [--out path.json]");
        System.out.println("  defaults: top=20, no bug-type filter, no limit");
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
        int limit = 0;
        int top = 20;
        /** Bugs2Fix mode: datasetPath is the buggy text file; this is the parallel fixed file. */
        Path bugs2fixFixedPath;
        /** Populated after corpus scan; passed through to JSON meta. */
        List<Map<String, Object>> magicValueDist;
        /** Populated after corpus scan; passed through to JSON meta and extractor. */
        java.util.Set<String> commonTypeNames;

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
            if (flags.containsKey("bugs2fix")) o.bugs2fixFixedPath = Path.of(flags.get("bugs2fix"));
            if (flags.containsKey("limit")) o.limit = Integer.parseInt(flags.get("limit"));
            if (flags.containsKey("top")) o.top = Integer.parseInt(flags.get("top"));
            if (flags.containsKey("out")) o.outPath = Path.of(flags.get("out"));
            return o;
        }
    }
}
