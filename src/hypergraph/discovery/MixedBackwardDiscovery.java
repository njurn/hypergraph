package hypergraph.discovery;

import hypergraph.common.Const;
import hypergraph.mss.MinimalSourceSet;
import hypergraph.mss.NaiveBuilder;
import hypergraph.traversal.BackwardTraversal;
import hypergraph.util.Log;
import org.neo4j.graphdb.Node;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Hyunjun on 2015-05-17.
 */
public class MixedBackwardDiscovery extends NaiveBuilder implements BackwardDiscovery {
    Set<Long> hit;

    public MixedBackwardDiscovery() {
        super();
        hit = new HashSet<>();
    }

    @Override
    public MinimalSourceSet findMinimal(Set<Node> target) {
        // Find super source set
        Set<Node> start = new HashSet<>();
        BackwardTraversal bt = new BackwardTraversal(node -> {
            hit.add(node.getId());
            if (node.hasLabel(Const.LABEL_STARTABLE)) {
                start.add(node);
            }
        });
        bt.traverse(target);

        // Build temporal mss from start
        // only for on the hitting set
        compute(start, (node) -> { return hit.contains(node.getId()); });

        // print mss
        for (Map.Entry<Long, MinimalSourceSet> entry : mssMap.entrySet()) {
            Long id = entry.getKey();
            MinimalSourceSet mss = entry.getValue();
            Log.debug("MSS(" + id + ") = " + mss.toString());
        }

        MinimalSourceSet result = null;
        for (Node t : target) {
            MinimalSourceSet mss = mssMap.get(t.getId());
            if (mss == null) {
                return new MinimalSourceSet();
            }

            if (result == null) {
                result = mss;
            } else {
                result = result.cartesian(mss);
            }
        }

        return result;
    }
}
