package astramut.mutation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Weighted-sampling of magic numeric values from the corpus distribution in catalogue meta. */
public final class MagicValueSampler {
    private final List<String> values;
    private final long[] cumulative;
    private final long total;
    private final Random rng;

    public MagicValueSampler(List<Entry> distribution, long seed) {
        this.values = new ArrayList<>(distribution.size());
        this.cumulative = new long[distribution.size()];
        long running = 0;
        for (int i = 0; i < distribution.size(); i++) {
            Entry e = distribution.get(i);
            values.add(e.value);
            running += e.count;
            cumulative[i] = running;
        }
        this.total = running;
        this.rng = new Random(seed);
    }

    /** Weighted sample by count: probability of returning value v is v.count / total. */
    public String sample() {
        if (total == 0 || values.isEmpty()) return "2";   // safe default
        long r = (long) (rng.nextDouble() * total);
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] <= r) lo = mid + 1; else hi = mid;
        }
        return values.get(lo);
    }

    public int size() { return values.size(); }
    public long total() { return total; }

    /** Reads {@code meta.magicValueDistribution} from catalogue JSON; empty sampler if absent. */
    public static MagicValueSampler loadFromCatalogue(Path catalogueJson, long seed) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(catalogueJson.toFile());
        JsonNode dist = root.path("meta").path("magicValueDistribution");
        List<Entry> entries = new ArrayList<>();
        if (dist.isArray()) {
            for (JsonNode e : dist) {
                entries.add(new Entry(e.path("value").asText(), e.path("count").asLong()));
            }
        }
        return new MagicValueSampler(entries, seed);
    }

    /** Fallback empty distribution — produces "2" deterministically. */
    public static MagicValueSampler empty() {
        return new MagicValueSampler(List.of(new Entry("2", 1)), 0);
    }

    public record Entry(String value, long count) {}
}
