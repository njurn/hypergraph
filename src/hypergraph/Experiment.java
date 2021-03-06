package hypergraph;

import hypergraph.common.Const;
import hypergraph.common.HypergraphDatabase;
import hypergraph.data.Importer;
import hypergraph.data.SimpleImporter;
import hypergraph.discovery.BackwardDiscovery;
import hypergraph.discovery.ForwardDiscovery;
import hypergraph.discovery.IndexedBackwardDiscovery;
import hypergraph.mss.FastDecompositionBuilder;
import hypergraph.mss.MinimalSourceSet;
import hypergraph.mss.MinimalSourceSetBuilder;
import hypergraph.util.Log;
import hypergraph.util.Measure;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by Hyunjun on 2015-06-13.
 */
public class Experiment {

    public static void run() {
        run("e1");
    }

    private static void run(String filename) {
//        HypergraphDatabase.execute(filename + "-import", "db/" + filename, true, () -> {
//            Importer importer = new SimpleImporter("input/" + filename + ".txt");
//            importer.run();
//        });
//
//        HypergraphDatabase.execute(filename + "-build", "db/" + filename, false, () -> {
//            MinimalSourceSetBuilder builder = new FastDecompositionBuilder(512);
//            builder.run();
//        });

        //backwardQuery(filename, 3);
    }

    private static Set<Set<Long>> generateQuery(int numQuery, int size) {
        GraphDatabaseService graphDb = HypergraphDatabase.getGraphDatabase();
        Node meta = graphDb.findNodes(Const.LABEL_META).next();
        int n = (int) meta.getProperty(Const.PROP_COUNT);

        Log.debug(n + " nodes");

        Random random = new Random();

        // create query set
        Set<Set<Long>> querySet = new HashSet<>();

        for (int i = 0; i < numQuery; i++) {
            Set<Long> q = new HashSet<>();
            while (q.size() < size) {
                q.add((long) random.nextInt(n));
            }
            querySet.add(q);
        }

        return querySet;
    }

    private static void forwardQuery(String filename, int sourceSize) {
        HypergraphDatabase.executeTx(filename + "-forward-" + sourceSize, "db/" + filename, false, () -> {
            GraphDatabaseService graphDb = HypergraphDatabase.getGraphDatabase();
            Measure measure = new Measure("Forward Query MSS " + sourceSize);
            Set<Set<Long>> querySet = generateQuery(25, sourceSize);

            for (Set<Long> q : querySet) {
                Set<Node> source = new HashSet<Node>();
                for (Long id : q) {
                    source.add(graphDb.getNodeById(id));
                }

                measure.start();
                ForwardDiscovery discovery = new ForwardDiscovery();
                Set<Node> result = discovery.find(source, (v) -> (true));
                measure.end();
            }
            measure.printStatistic();
        });
    }

    private static void backwardQuery(String filename, int targetSize) {
        backwardQuery(filename, targetSize, IndexedBackwardDiscovery.class);
    }

    private static void backwardQuery(String filename, int targetSize, Class strategy) {
        HypergraphDatabase.executeTx(filename + "-backward-" + targetSize, "db/" + filename, false, () -> {
            GraphDatabaseService graphDb = HypergraphDatabase.getGraphDatabase();
            Measure measure = new Measure("Backward Query MSS " + targetSize);

            Set<Set<Long>> querySet = generateQuery(200, targetSize);
            for (Set<Long> q : querySet) {
                Set<Node> source = new HashSet<Node>();
                for (Long id : q) {
                    source.add(graphDb.getNodeById(id));
                }

                measure.start();
                try {
                    BackwardDiscovery discovery = (BackwardDiscovery) strategy.newInstance();
                    MinimalSourceSet mssIndexed = discovery.findMinimal(source);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                measure.end();
            }
            measure.printStatistic();
        });
    }
}
