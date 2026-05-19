package astramut.learn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

public final class HierarchicalClusterer {

    public static final int DEFAULT_MAX_BUCKET_SIZE = 1500;

    private final int maxHolesPerPattern;
    private final int maxBucketSize;

    public HierarchicalClusterer(int maxHolesPerPattern) {
        this(maxHolesPerPattern, DEFAULT_MAX_BUCKET_SIZE);
    }

    public HierarchicalClusterer(int maxHolesPerPattern, int maxBucketSize) {
        this.maxHolesPerPattern = maxHolesPerPattern;
        this.maxBucketSize = maxBucketSize;
    }

    public List<Cluster> cluster(List<EditPattern> concretes) {
        // Bucket by (before-root sig, after-root sig); cross-bucket AUs root to Hole anyway.
        Map<String, List<Cluster>> buckets = new LinkedHashMap<>();
        for (EditPattern e : concretes) {
            String key = rootSig(e);
            buckets.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new Cluster(e, List.of(e)));
        }

        List<Cluster> all = new ArrayList<>();
        for (List<Cluster> bucket : buckets.values()) {
            if (bucket.size() <= maxBucketSize) {
                all.addAll(clusterBucket(bucket));
                continue;
            }
            // Bucket too large: random partition into sub-buckets.
            List<Cluster> shuffled = new ArrayList<>(bucket);
            Collections.shuffle(shuffled, new Random(0));
            for (int i = 0; i < shuffled.size(); i += maxBucketSize) {
                int end = Math.min(i + maxBucketSize, shuffled.size());
                all.addAll(clusterBucket(new ArrayList<>(shuffled.subList(i, end))));
            }
        }
        return dedup(all);
    }

    private List<Cluster> clusterBucket(List<Cluster> initial) {
        Set<Cluster> alive = Collections.newSetFromMap(new IdentityHashMap<>());
        alive.addAll(initial);

        // Tie-break by insertion seq so initial (i,j)-lex order wins ties.
        PriorityQueue<PairEntry> heap = new PriorityQueue<>(
                Comparator.comparingInt((PairEntry p) -> p.unbound)
                        .thenComparingInt(p -> p.holes)
                        .thenComparingInt(p -> p.seq));

        int[] seq = {0};
        for (int i = 0; i < initial.size(); i++) {
            for (int j = i + 1; j < initial.size(); j++) {
                addPair(initial.get(i), initial.get(j), heap, seq);
            }
        }

        while (alive.size() > 1) {
            PairEntry best = heap.poll();
            if (best == null) break;
            if (!alive.contains(best.a) || !alive.contains(best.b)) continue;
            if (best.holes > maxHolesPerPattern) break;

            alive.remove(best.a);
            alive.remove(best.b);
            List<EditPattern> merged = new ArrayList<>(
                    best.a.members().size() + best.b.members().size());
            merged.addAll(best.a.members());
            merged.addAll(best.b.members());
            Cluster newC = new Cluster(best.au, merged);

            for (Cluster other : alive) {
                addPair(newC, other, heap, seq);
            }
            alive.add(newC);
        }
        return new ArrayList<>(alive);
    }

    /** Merge clusters with structurally identical reps (modulo hole renaming) — recovers patterns split by sub-bucketing. */
    private static List<Cluster> dedup(List<Cluster> clusters) {
        Map<String, List<EditPattern>> members = new LinkedHashMap<>();
        Map<String, EditPattern> rep = new LinkedHashMap<>();
        for (Cluster c : clusters) {
            String sig = canonicalSig(c.representative());
            members.computeIfAbsent(sig, k -> new ArrayList<>()).addAll(c.members());
            rep.putIfAbsent(sig, c.representative());
        }
        List<Cluster> out = new ArrayList<>(members.size());
        for (Map.Entry<String, List<EditPattern>> e : members.entrySet()) {
            out.add(new Cluster(rep.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    /** Canonicalize hole ids left-to-right across before+after so equivalent rewrites collapse. */
    private static String canonicalSig(EditPattern p) {
        Map<String, String> rename = new HashMap<>();
        int[] next = {0};
        StringBuilder sb = new StringBuilder();
        appendCanon(p.before(), rename, next, sb);
        sb.append("↦");
        appendCanon(p.after(), rename, next, sb);
        return sb.toString();
    }

    private static void appendCanon(TreePattern t, Map<String, String> rename, int[] next, StringBuilder sb) {
        if (t instanceof Hole h) {
            sb.append(rename.computeIfAbsent(h.id(), k -> "?" + next[0]++));
            return;
        }
        TreeNode n = (TreeNode) t;
        sb.append(n.type()).append('(').append(n.label()).append(")[");
        for (int i = 0; i < n.children().size(); i++) {
            if (i > 0) sb.append(',');
            appendCanon(n.children().get(i), rename, next, sb);
        }
        sb.append(']');
    }

    private static void addPair(Cluster a, Cluster b, PriorityQueue<PairEntry> heap, int[] seq) {
        EditPattern au = AntiUnifier.antiUnify(a.representative(), b.representative());
        if (au.before() instanceof Hole || au.after() instanceof Hole) return;
        heap.add(new PairEntry(a, b, au,
                au.unboundAfterHoleCount(), au.holeCount(), seq[0]++));
    }

    private static String rootSig(EditPattern e) {
        return sigOf(e.before()) + "→" + sigOf(e.after());
    }

    private static String sigOf(TreePattern p) {
        if (p instanceof Hole) return "?";
        TreeNode n = (TreeNode) p;
        return n.type() + "(" + n.label() + ")/" + n.children().size();
    }

    private record PairEntry(Cluster a, Cluster b, EditPattern au,
                             int unbound, int holes, int seq) {}

    public record Cluster(EditPattern representative, List<EditPattern> members) {
        public Cluster {
            Objects.requireNonNull(representative);
            members = List.copyOf(members);
        }
    }
}
