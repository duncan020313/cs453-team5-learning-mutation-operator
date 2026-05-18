package astramut.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HierarchicalClusterer {

    private final int maxHolesPerPattern;

    public HierarchicalClusterer(int maxHolesPerPattern) {
        this.maxHolesPerPattern = maxHolesPerPattern;
    }

    public List<Cluster> cluster(List<EditPattern> concretes) {
        List<Cluster> clusters = new ArrayList<>(concretes.size());
        for (EditPattern e : concretes) {
            List<EditPattern> members = new ArrayList<>();
            members.add(e);
            clusters.add(new Cluster(e, members));
        }

        while (clusters.size() > 1) {
            int bestI = -1, bestJ = -1;
            EditPattern bestAU = null;
            // Getafix §4.2.3 tiebreaker: fewer unbound-RHS holes wins over fewer total holes.
            int bestUnbound = Integer.MAX_VALUE;
            int bestHoles = Integer.MAX_VALUE;

            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    EditPattern au = AntiUnifier.antiUnify(
                            clusters.get(i).representative(),
                            clusters.get(j).representative());
                    // Skip degenerate AUs (root is a hole) — they can't be rewrite rules
                    // and would occlude valid merges at the same hole count.
                    if (au.before() instanceof Hole || au.after() instanceof Hole) continue;
                    int unbound = au.unboundAfterHoleCount();
                    int holes = au.holeCount();
                    boolean better = unbound < bestUnbound
                            || (unbound == bestUnbound && holes < bestHoles);
                    if (better) {
                        bestUnbound = unbound;
                        bestHoles = holes;
                        bestI = i;
                        bestJ = j;
                        bestAU = au;
                    }
                }
            }

            if (bestAU == null || bestHoles > maxHolesPerPattern) break;

            Cluster ci = clusters.get(bestI);
            Cluster cj = clusters.get(bestJ);
            List<EditPattern> merged = new ArrayList<>(ci.members().size() + cj.members().size());
            merged.addAll(ci.members());
            merged.addAll(cj.members());
            clusters.remove(bestJ);
            clusters.remove(bestI);
            clusters.add(new Cluster(bestAU, merged));
        }
        return clusters;
    }

    public record Cluster(EditPattern representative, List<EditPattern> members) {
        public Cluster {
            Objects.requireNonNull(representative);
            members = List.copyOf(members);
        }
    }
}
