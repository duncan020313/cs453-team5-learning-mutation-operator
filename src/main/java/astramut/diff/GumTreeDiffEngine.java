package astramut.diff;

import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class GumTreeDiffEngine {

    private static final String CU_TEMPLATE =
            "class _Wrapper_ { void _m_() { %s } }";

    static {
        Run.initGenerators();
    }
    
    public GumTreeDiff diff(String beforeSourceCode, String afterSourceCode) throws IOException {
        String wrappedBefore = wrap(beforeSourceCode);
        String wrappedAfter  = wrap(afterSourceCode);

        TreeGenerator generator = new JdtTreeGenerator();

        TreeContext beforeCtx = generator.generateFrom().reader(new StringReader(wrappedBefore));
        TreeContext afterCtx  = generator.generateFrom().reader(new StringReader(wrappedAfter));

        Tree beforeRoot = beforeCtx.getRoot();
        Tree afterRoot  = afterCtx.getRoot();

        Matcher matcher = new CompositeMatchers.ClassicGumtree();
        MappingStore mappings = matcher.match(beforeRoot, afterRoot);

        EditScriptGenerator scriptGen = new SimplifiedChawatheScriptGenerator();
        List<Action> actions = scriptGen.computeActions(mappings).asList();

        return new GumTreeDiff(beforeSourceCode, afterSourceCode, beforeRoot, afterRoot, actions, mappings);
    }

    private static String wrap(String snippet) {
        return String.format(CU_TEMPLATE, snippet);
    }
}