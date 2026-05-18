package astramut.diff;

import astramut.learn.GumTreeAction;
import astramut.learn.GumTreeDiff;
import astramut.learn.GumTreeMatch;
import astramut.learn.GumTreeNode;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
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

        GumTreeNode srcTree = toGumTreeNode(beforeRoot);
        GumTreeNode dstTree = toGumTreeNode(afterRoot);
        List<GumTreeMatch> matches = toMatches(mappings);
        List<GumTreeAction> ourActions = toActions(actions);

        return new GumTreeDiff(srcTree, dstTree, matches, ourActions);
    }

    // conversion helpers
    private static GumTreeNode toGumTreeNode(Tree tree) {
        List<GumTreeNode> children = new ArrayList<>(tree.getChildren().size());
        for (Tree child : tree.getChildren()) {
            children.add(toGumTreeNode(child));
        }
        return new GumTreeNode(
                tree.getType().name,
                tree.getLabel(),
                tree.getPos(),
                tree.getLength(),
                children
        );
    }


    private static List<GumTreeMatch> toMatches(MappingStore mappings) {
        List<GumTreeMatch> result = new ArrayList<>();
        for (Mapping m : mappings) {
            result.add(new GumTreeMatch(
                    m.first.toString(),
                    m.second.toString()
            ));
        }
        return result;
    }

    private static List<GumTreeAction> toActions(List<Action> actions) {
        List<GumTreeAction> result = new ArrayList<>();
        for (Action a : actions) {
            if (a instanceof Update u) {
                result.add(new GumTreeAction.UpdateNode(u.getNode().toString(), u.getValue()));
            } else if (a instanceof Insert i) {
                result.add(new GumTreeAction.InsertNode(
                        i.getNode().toString(), i.getParent().toString(), i.getPosition()));
            } else if (a instanceof Delete d) {
                result.add(new GumTreeAction.DeleteNode(d.getNode().toString()));
            } else if (a instanceof TreeInsert ti) {
                result.add(new GumTreeAction.InsertTree(
                        ti.getNode().toString(), ti.getParent().toString(), ti.getPosition()));
            } else if (a instanceof TreeDelete td) {
                result.add(new GumTreeAction.DeleteTree(td.getNode().toString()));
            } else if (a instanceof Move mv) {
                result.add(new GumTreeAction.MoveTree(
                        mv.getNode().toString(), mv.getParent().toString(), mv.getPosition()));
            }
        }
        return result;
    }

    private static String wrap(String snippet) {
        return String.format(CU_TEMPLATE, snippet);
    }
}