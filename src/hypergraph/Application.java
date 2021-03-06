package hypergraph;

import hypergraph.common.Const;
import hypergraph.common.HypergraphDatabase;
import hypergraph.data.*;
import hypergraph.discovery.*;
import hypergraph.mss.*;
import hypergraph.traversal.HypergraphTraversal;
import hypergraph.util.Log;
import hypergraph.util.Measure;
import org.neo4j.graphdb.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Main hypergraph.Application
 *
 * Initialize Neo4j as follows
 * http://neo4j.com/docs/stable/tutorials-java-embedded-hello-world.html
 *
 * Created by Hyunjun on 2015-04-15.
 */
public class Application {
    private static GraphDatabaseService graphDb;

    public static void main(String[] args) {
//        Experiment.run();

//        syntheticImport();
//        syntheticQuery();

//        codaImport();
//        codaQuery();

//        keggImport();
//        keggQuery();

        HypergraphDatabase.execute("syn-import", "db/syn", true, () -> {
            Importer importer = new SimpleImporter("input/hypergraph.txt");
            importer.run();

            TargetableBuilder targetableBuilder = new TargetableBuilder();
            GraphDatabaseService graphDb = HypergraphDatabase.getGraphDatabase();
            try (Transaction tx = graphDb.beginTx()) {
                Random random = new Random();
                Set<Long> targetables = new HashSet<>();
                for (int i = 0; i < 2000; i++) {
                    Long nodeId = (long) random.nextInt(20000);
                    targetables.add(nodeId);
                }

                targetableBuilder.setTargetables(targetables);
            }

            targetableBuilder.run();
        });

        syntheticQuery();
    }


    private static void syntheticImport() {
        HypergraphDatabase.execute("syn-import", "db/syn", true, () -> {
            Importer importer = new SimpleImporter("input/hypergraph.txt");
            importer.run();

            MinimalSourceSetBuilder builder = new FastDecompositionBuilder();
            builder.run();
        });
    }

    private static void syntheticQuery() {
        HypergraphDatabase.executeTx("syn-query", "db/syn", false, () -> {
            Measure measureNaive = new Measure("Naive Query MSS");
            Measure measureMixed = new Measure("Mixed Query MSS");
            Measure measureIndexed = new Measure("Indexed Query MSS");

            graphDb = HypergraphDatabase.getGraphDatabase();
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);
            int count = 0;
            int max = 25;

            boolean naive = false;
            boolean mixed = false;
            boolean indexed = true;

            while (nodes.hasNext()) {
                Node node = nodes.next();
                String name = (String) node.getProperty(Const.PROP_UNIQUE);

                if (Math.random() < 0.2) {//5) {
                    if (indexed) {
                        measureIndexed.printStatistic();
                        Log.debug("Indexed query for node " + node.getId() + " " + name);
                        measureIndexed.start();
                        BackwardDiscovery indexedDiscovery = new IndexedBackwardDiscovery();
                        MinimalSourceSet mssIndexed = indexedDiscovery.findMinimal(node);
                        measureIndexed.end();
                    }

                    if (mixed) {
                        measureMixed.printStatistic();
                        Log.debug("Mixed query for node " + node.getId() + " " + name);
                        measureMixed.start();
                        BackwardDiscovery mixedDiscovery = new MixedBackwardDiscovery();
                        MinimalSourceSet mssMixed = mixedDiscovery.findMinimal(node);
                        measureMixed.end();
                    }

                    if (naive) {
                        measureNaive.printStatistic();
                        Log.debug("Naive query for node " + node.getId() + " " + name);
                        measureNaive.start();
                        BackwardDiscovery naiveDiscovery = new NaiveBackwardDiscovery();
                        MinimalSourceSet mssNaive = naiveDiscovery.findMinimal(node);
                        measureNaive.end();
                    }

                    count++;
                    if (count > max)
                        break;
                }
            }
            measureIndexed.printStatistic();
            measureMixed.printStatistic();
            measureNaive.printStatistic();
        });
    }

    private static void codaImport() {
        HypergraphDatabase.execute("coda-import", "db/coda", true, () -> {
            Importer importer = new CodaImporter();
            importer.run();

            MinimalSourceSetBuilder builder = new DecompositionBuilder(512);
            builder.run();
        });
    }

    private static void codaQuery() {
        HypergraphDatabase.executeTx("coda-query", "db/coda", false, () -> {
            Measure measure = new Measure("Query MSS");
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);

            while (nodes.hasNext()) {
                Node node = nodes.next();

                if (node.hasLabel(Const.LABEL_STARTABLE)) {
                    String name = (String) node.getProperty("name");
                    Log.debug("\nquery for node " + node.getId() + " " + name);

                    measure.start();
                    MinimalSourceSetFinder finder = new DecompositionFinder();
                    MinimalSourceSet mss = finder.find(node);
                    ForwardDiscovery discovery = new ForwardDiscovery();
                    Set<Node> result = discovery.find(node, (v) -> {
                        return v.hasLabel(DynamicLabel.label("Disease"));
                    });
                    measure.end();
                    Log.debug("result " + result.size());
                }
            }
            measure.printStatistic();
        });
    }

    private static void keggImport() {
        HypergraphDatabase.execute("kegg-import", "db/kegg", true, () -> {
            Importer importer = new KeggImporter();
            importer.run();

            MinimalSourceSetBuilder builder = new NaiveBuilder();
            builder.run();
        });
    }

    private static void keggQuery() {
        HypergraphDatabase.executeTx("kegg-query", "db/kegg", false, () -> {
            Measure measure = new Measure("Query MSS");
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);

            while (nodes.hasNext()) {
                Node node = nodes.next();

                String name = (String) node.getProperty(Const.PROP_UNIQUE);
                Log.debug(name);

                if (name.startsWith("hsa:")) {
                    Log.debug("query for node " + node.getId() + " " + name);

                    measure.start();
                    MinimalSourceSetFinder finder = new DecompositionFinder();
                    MinimalSourceSet mss = finder.find(node);
                    measure.end();
                }
            }
            measure.printStatistic();
        });
    }



    // ---------- unnecessary part -------------
/*

    private static void syntheticImport() {
        execute("syn-import", "db/syn", true, () -> {
            Importer importer = new SimpleImporter("input/hypergraph.txt");
            importer.run();

            MinimalSourceSetBuilder builder = new NodeDecompositionBuilder(16);
            builder.run();
        });
    }

    private static void syntheticQueryForward(int sourceSize) {
        int numQuery = 25;
        int numNodes = 1000;

        Random random = new Random();

        // create query set
        Set<Set<Long>> querySet = new HashSet<>();

        for (int i = 0; i < numQuery; i++) {
            Set<Long> q = new HashSet<>();
            while (q.size() < sourceSize) {
                q.add((long) random.nextInt(numNodes));
            }
            querySet.add(q);
        }

        executeTx("syn-query", "db/syn", false, () -> {
            Measure measure = new Measure("Forward Query MSS " + sourceSize);

            for (Set<Long> q : querySet) {
                Set<Node> source = new HashSet<Node>();
                System.out.print("query for ");
                for (Long id : q) {
                    source.add(graphDb.getNodeById(id));
                    System.out.print(id + ",");
                }
                System.out.println(":");

                measure.start();
                ForwardDiscovery discovery = new ForwardDiscovery();
                Set<Node> result = discovery.find(source, (v) -> (true));
                measure.end();
                printNames(result);
            }
            measure.printStatistic();
        });
    }

    private static void syntheticQueryBackward(int sourceSize) {
        int numQuery = 25;
        int numNodes = 1000;

        Random random = new Random();

        // create query set
        Set<Set<Long>> querySet = new HashSet<>();

        for (int i = 0; i < numQuery; i++) {
            Set<Long> q = new HashSet<>();
            while (q.size() < sourceSize) {
                q.add((long) random.nextInt(numNodes));
            }
            querySet.add(q);
        }

        executeTx("syn-query", "db/syn", false, () -> {
            Measure measure = new Measure("Backward Query MSS " + sourceSize);

            for (Set<Long> q : querySet) {
                Set<Node> source = new HashSet<Node>();
//                System.out.print("query for ");
                for (Long id : q) {
                    source.add(graphDb.getNodeById(id));
//                    System.out.print(id + ",");
                }
//                System.out.println(":");

                measure.start();
                BackwardDiscovery indexedDiscovery = new IndexedBackwardDiscovery();
                MinimalSourceSet mssIndexed = indexedDiscovery.findMinimal(source);
                measure.end();
//                printNames(mssIndexed);
            }
            measure.printStatistic();
        });
    }

    private static void syntheticQueryForward() {
        executeTx("syn-query", "db/syn", false, () -> {
            Measure measure = new Measure("Forward Query MSS");
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);
            int count = 0;
            int max = 10;

            while (nodes.hasNext()) {
                Node node = nodes.next();
                String name = (String) node.getProperty(Const.PROP_UNIQUE);

                if (Math.random() < 0.2) {

                    measure.start();
                    ForwardDiscovery discovery = new ForwardDiscovery();
                    discovery.find(node, (v) -> (true));
                    measure.end();

                    count++;
                    if (count > max)
                        break;
                }
            }
            measure.printStatistic();
        });
    }

    private static void syntheticQuery() {
        executeTx("syn-query", "db/syn", false, () -> {
            Measure measureIndexed = new Measure("Indexed Query MSS");
            Measure measureNaive = new Measure("Naive Query MSS");
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);
            int count = 0;
            int max = 25;
            int countErr = 0;

            while (nodes.hasNext()) {
                Node node = nodes.next();
                String name = (String) node.getProperty(Const.PROP_UNIQUE);

                if (Math.random() < 0.2) {
//                    Log.info("Query for node " + node.getId() + " " + name);
                    Log.debug("Indexed query for node " + node.getId() + " " + name);
                    measureIndexed.start();
                    BackwardDiscovery indexedDiscovery = new IndexedBackwardDiscovery();
                    MinimalSourceSet mssIndexed = indexedDiscovery.findMinimal(node);
//                    DecompositionFinder finder = new DecompositionFinder();
//                    MinimalSourceSet mssIndexed = finder.findWithSampling(node);
                    measureIndexed.end();
//                    printNames(mssIndexed);


                    // temp - print for every time
                    Log.debug("query done" + measureIndexed.getRecentMeasureTime());

                    boolean naive = false;
                    if (naive) {
                        measureIndexed.printStatistic();
                        Log.debug("Naive query for node " + node.getId() + " " + name);
                        measureNaive.start();
                        BackwardDiscovery naiveDiscovery = new NaiveBackwardDiscovery();
                        Set<Node> targets = new HashSet<>();
                        targets.add(node);
                        MinimalSourceSet mssNaive = naiveDiscovery.findMinimal(targets);
                        measureNaive.end();
                        //                    printNames(mssNaive);
                        measureNaive.printStatistic();

                        if (!mssIndexed.equals(mssNaive)) {
                            Log.error("ERROR: MSS diff at " + node.getId() + " " + name);
                            Log.error(mssIndexed.toString());
                            Log.error("naive");
                            Log.error(mssNaive.toString());
                            countErr++;
                        }
                    }
                    count++;
                    if (count > max)
                        break;
                }
            }
            measureIndexed.printStatistic();
            measureNaive.printStatistic();
            Log.info("error " + countErr);
        });
    }

    private static void syntheticQueryFast() {
        executeTx("syn-query", "db/syn", false, () -> {
            Measure measureIndexed = new Measure("Indexed Query MSS");
            ResourceIterator<Node> nodes = graphDb.findNodes(Const.LABEL_NODE);
            int count = 0;
            int max = 25;
            int countErr = 0;

            while (nodes.hasNext()) {
                Node node = nodes.next();
                String name = (String) node.getProperty(Const.PROP_UNIQUE);

                if (Math.random() < 0.2) {
                    Log.debug("Indexed query for node " + node.getId() + " " + name);
                    measureIndexed.start();

                    FastDecompositionFinder finder = new FastDecompositionFinder();
                    Set<Long> min = finder.findMinimum(node);
                    measureIndexed.end();
                    printSet(min);

                    // temp - print for every time
                    Log.debug("query done" + measureIndexed.getRecentMeasureTime());
                    measureIndexed.printStatistic();

                    count++;
                    if (count > max)
                        break;
                }
            }
            measureIndexed.printStatistic();
            Log.info("error " + countErr);
        });
    }

    private static void checkMss(MinimalSourceSet mss, Node t) {
        Set<Node> target = new HashSet<>();
        target.add(t);

        for (Set<Long> source : mss.getSourceSets()) {
            Set<Node> sourceSet = new HashSet<>();
            for (Long s : source) {
                Node v = graphDb.getNodeById(s);
                sourceSet.add(v);
            }

            ForwardDiscovery discovery = new ForwardDiscovery();
            //discovery.isReachable(sourceSet, target);
        }
    }

    private static void printNames(MinimalSourceSet mss) {
        for (Set<Long> source : mss.getSourceSets()) {
            System.out.print("{");
            for (Long sid : source) {
                Node s = graphDb.getNodeById(sid);
                String name = (String) s.getProperty(Const.PROP_UNIQUE);
                System.out.print(name + ", ");
                if (!s.hasLabel(Const.LABEL_STARTABLE)) {
                    Log.error("ERROR: NOT A STARTABLE!!");
                }
            }
            System.out.println("}");
        }
    }

    private static void printNames(Set<Node> nodes) {
        for (Node v : nodes) {
            if (v.hasProperty(Const.PROP_UNIQUE)) {
                String name = (String) v.getProperty(Const.PROP_UNIQUE);
                Log.debug(name);
            }
            else if (v.hasProperty("name")) {
                String name = (String) v.getProperty("name");
                Log.debug(name);
            }
            else {
                Log.debug("node " + v.getId());
            }
        }
    }

    private static void printSet(Set<Long> s) {
        if (s == null) {
            Log.debug("null");
            return;
        }

        String str = "{";
        for (Long l : s) {
            str += l + ",";
        }
        str += "}";
        Log.debug(str);
    }

    private static void execute(String log, String db, boolean init, Runnable runnable) {
        // Initialize log
        Log.init(log);

        // Init or open hypergraph database
        if (init) graphDb = HypergraphDatabase.init(db);
        else graphDb = HypergraphDatabase.open(db);

        runnable.run();

        // close
        HypergraphDatabase.close();
        Log.close();
    }

    private static void executeTx(String log, String db, boolean init, Runnable runnable) {
        execute(log, db, init, () -> {
            try (Transaction tx = graphDb.beginTx()) {
                runnable.run();
            }
        });
    }
    */
}
