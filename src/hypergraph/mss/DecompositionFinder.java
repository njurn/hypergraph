package hypergraph.mss;

import hypergraph.common.Const;
import hypergraph.common.HypergraphDatabase;
import hypergraph.util.Log;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * Find all MSS recovering from decomposed MSS
 *
 * Created by Hyunjun on 2015-04-30.
 */
public class DecompositionFinder implements MinimalSourceSetFinder {
    private static GraphDatabaseService graphDb;
    private Set<Long> reconstructed;

    public DecompositionFinder() {
        graphDb = HypergraphDatabase.getGraphDatabase();
        reconstructed = new HashSet<>();
    }

    public MinimalSourceSet findWithSampling(Node target) {
        MinimalSourceSet mss = getMinimalSourceSet(target);
        MinimalSourceSet result = new MinimalSourceSet();

        for (Set<Long> set : mss.getSourceSets()) {
            boolean decomposed = false;
            for (Long s : set) {
                Node v = graphDb.getNodeById(s);
                if (v.hasProperty(Const.PROP_DECOMPOSED)) {
                    decomposed = true;
                    Log.debug("decomposed node!");
                    break;
                }
            }
            if (!decomposed && set.size() < 10) {
                result.getSourceSets().add(set);
            }
        }

        return result;
    }

    @Override
    public MinimalSourceSet find(Node target) {
        MinimalSourceSet mss = getMinimalSourceSet(target);

        // Naive implementation
        long decomposedId = needReconstruction(mss);
        while (decomposedId > 0) {
            mss = reconstruct(mss);
            decomposedId = needReconstruction(mss);
        }

        return mss;
    }

    private MinimalSourceSet getMinimalSourceSet(Node target) {
        if (!target.hasProperty(Const.PROP_MSS))
            return new MinimalSourceSet();
        return MinimalSourceSet.valueOf((String) target.getProperty(Const.PROP_MSS));
    }

    private MinimalSourceSet reconstruct(MinimalSourceSet mss, long decomposedId) {
        /*
        A = {a, gf, h}
        mss = {Abc, Abe, ADe}
        mss = A cartesian {bc, be, De}
         */
        MinimalSourceSet mss1 = new MinimalSourceSet(); // mss containing decomposedId
        MinimalSourceSet mss2 = new MinimalSourceSet(); // others (does not need to be join with A)

        for (Set<Long> s : mss.getSourceSets()) {
            if (s.contains(decomposedId)) {
                Set<Long> t = new HashSet<>(s);
                t.remove(decomposedId);
                mss1.getSourceSets().add(t);
            } else {
                mss2.getSourceSets().add(s);
            }
        }

        if (mss1.cardinality() != 0) {
            Node d = graphDb.getNodeById(decomposedId);
            MinimalSourceSet mss3 = computeMinimalSourceSet(d); // A in example
            mss3.removeContains(decomposedId);
            mss2.addAll(mss3.cartesian(mss1));
        }

        return mss2;
    }

    private MinimalSourceSet reconstruct(MinimalSourceSet mss) {
        MinimalSourceSet recon = new MinimalSourceSet(mss);

        for (Set<Long> s : mss.getSourceSets()) {
            for (Long nodeId : s) {
                Node v = graphDb.getNodeById(nodeId);

                // if decomposed
                if (v.hasLabel(Const.LABEL_HYPERNODE)) {

                    // check already reconstructed
                    if (reconstructed.contains(nodeId)) {
                        recon.removeContains(nodeId);
                        return recon;
                    }

                    reconstructed.add(nodeId);

                    if (s.size() == 1) {
                        MinimalSourceSet mssV = computeMinimalSourceSet(v);
                        recon.addAll(mssV);
                        recon.removeContains(v.getId());
                        return recon;
                    }

                    else {
                        return reconstruct(mss, v.getId());
                    }
                }
            }
        }

        return recon;
    }

    private long needReconstruction(MinimalSourceSet mss) {
//        Log.debug("call needReconstruction of mss(" + mss.cardinality() + "):");
//        Log.debug(mss.toString());

        for (Set<Long> s : mss.getSourceSets()) {
            for (Long nodeId : s) {
                Node v = graphDb.getNodeById(nodeId);
                if (v.hasLabel(Const.LABEL_HYPERNODE)) {
//                    Log.debug("needs recon at " + nodeId);
//                    Log.debug("of " + s);
                    return nodeId;
                }
            }
        }
//        Log.debug("reconstructed!");
        return -1;
    }

    private MinimalSourceSet computeMinimalSourceSet(Node hypernode) {
        MinimalSourceSet mss = null;
        Iterable<Relationship> rels = hypernode.getRelationships(Direction.INCOMING, Const.REL_FROM_SOURCE);
        for (Relationship rel : rels) {
            Node s = rel.getStartNode();
            if (mss == null) {
                mss = getMinimalSourceSet(s);
            } else {
                mss = mss.cartesian(getMinimalSourceSet(s));
            }
        }
//        Log.debug("computeMinimalSourceSet");
//        Log.debug("hypernode mss len = " + mss.cardinality());
//        Log.debug(mss.toString());

        return mss;

        // Get from saved result
//        return getMinimalSourceSet(hypernode);
    }
}
