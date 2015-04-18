import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.io.fs.FileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Main Application
 *
 * Initialize Neo4j as follows
 * http://neo4j.com/docs/stable/tutorials-java-embedded-hello-world.html
 *
 * Created by Hyunjun on 2015-04-15.
 */
public class Application {
    private static GraphDatabaseService graphDb = null;

    public static GraphDatabaseService getGraphDatabase() {
        return graphDb;
    }

    public static void main2(String[] args) {
        SimpleGenerator generator = new SimpleGenerator();
        generator.run();
    }

    //XXX: write test cases
    public static void main3(String[] args) {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(Const.DB_PATH);
        registerShutdownHook(graphDb);

        try (Transaction tx = graphDb.beginTx()) {
            Set<Node> start = new HashSet<Node>();
            start.add(graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, 0));
            start.add(graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, 1));
            start.add(graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, 2));
            start.add(graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, 3));

            HypergraphTraversal traversal = new HypergraphTraversal(node -> {System.out.println(node.getId());});
            traversal.traverse(start);
        }

        graphDb.shutdown();
    }

    public static void main(String[] args) {
        deleteDatabase();
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(Const.DB_PATH);
        registerShutdownHook(graphDb);
        createIndex();

        SimpleImporter importer = new SimpleImporter("output.txt");
        importer.run();

        MinimalSourceSetBuilder builder = new MinimalSourceSetBuilder();
        builder.run();

        graphDb.shutdown();
    }

    private static void registerShutdownHook(final GraphDatabaseService graphDb)
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }

    private static void deleteDatabase() {
        try {
            FileUtils.deleteRecursively(new File(Const.DB_PATH));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // This only needs to be done once
    private static void createIndex() {
        IndexDefinition indexDefinition;
        try (Transaction tx = graphDb.beginTx()) {
            Schema schema = graphDb.schema();
            indexDefinition = schema.indexFor(Const.LABEL_NODE)
                    .on(Const.PROP_UNIQUE)
                    .create();
            tx.success();
        }

        try (Transaction tx = graphDb.beginTx()) {
            Schema schema = graphDb.schema();
            schema.awaitIndexOnline(indexDefinition, 10, TimeUnit.SECONDS);
        }
    }
}
