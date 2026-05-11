package astramut.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


public class ManySStuBsLoader {
    private final ObjectMapper mapper = new ObjectMapper();

    public List<BugFix> load(Path datasetPath) throws IOException {
        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, BugFix.class);
        return mapper.readValue(datasetPath.toFile(), listType);
    }

    public List<BugFix> loadWithSourceCode(Path datasetPath) throws IOException {
        return load(datasetPath).stream().filter(BugFix::hasSourceCode).collect(Collectors.toList());
    }

    public List<BugFix> loadByBugType(Path datasetPath, String bugType) throws IOException {
        return loadWithSourceCode(datasetPath).stream()
                .filter(b -> bugType.equals(b.bugType()))
                .collect(Collectors.toList());
    }
}