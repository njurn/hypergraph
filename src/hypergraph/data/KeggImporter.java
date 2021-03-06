package hypergraph.data;

import hypergraph.common.Const;
import hypergraph.common.Hyperedge;
import hypergraph.common.HypergraphDatabase;
import hypergraph.util.Log;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * Examples are from hsa00010.xml
 * Created by hyunjun on 2015. 5. 7..
 */
public class KeggImporter implements Importer {
    /*
     * Example of kegg entry
     * <entry id="44" name="ko:K13970 ko:K13971 ko:K13972" type="ortholog"
     *   link="http://www.kegg.jp/dbget-bin/www_bget?K13970+K13971+K13972">
     *   <graphics name="K13970..." fgcolor="#000000" bgcolor="#FFFFFF"
     *       type="rectangle" x="804" y="1181" width="46" height="17"/>
     * </entry>
     * <entry id="90" name="undefined" type="group">
     *   <graphics fgcolor="#000000" bgcolor="#FFFFFF"
     *     type="rectangle" x="902" y="521" width="46" height="34"/>
     *   <component id="18"/>
     *   <component id="31"/>
     * </entry>
     */
    private class KeggEntry {
        public String id;
        public String name;
        public String type;

        public Set<String> nameSet = null;

        public KeggEntry(Element entry, Map<String, KeggEntry> entryMap) {
            id = entry.getAttribute("id");
            name = entry.getAttribute("name");
            type = entry.getAttribute("type");

            // construct nameSet
            nameSet = new HashSet<>();

            if (type.equals("group")) {
                NodeList components = entry.getElementsByTagName("component");
                for (int i = 0; i < components.getLength(); i++) {
                    Element component = (Element) components.item(i);
                    String id = component.getAttribute("id");
                    KeggEntry componentEntry = entryMap.get(id);
                    nameSet.addAll(componentEntry.nameSet);
                }
            }
            else {
                String[] names = name.split(" ");
                for (String n : names) {
                    nameSet.add(n);
                }
            }
        }

        public void save(GraphDatabaseService graphDb) {
            for (String name : nameSet) {
                Node node = graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, name);
                if (node == null) {
                    node = graphDb.createNode(Const.LABEL_NODE);
                    node.setProperty(Const.PROP_UNIQUE, name);
                    node.setProperty("type", type);
                    countEntry++;
                    Log.debug(name);
                }
            }
        }
    }

    /*
     * Example of kegg relation
     * <relation entry1="55" entry2="60" type="ECrel">
     *   <subtype name="compound" value="57"/>
     *   <subtype name="activation" value="--&gt;"/>
     *   <subtype name="indirect effect" value="..&gt;"/>
     * </relation>
     */
    private class KeggRelation {
        KeggEntry entry1;
        KeggEntry entry2;
        boolean inhibit;

        public KeggRelation(Element relation, Map<String, KeggEntry> entryMap) {
            String entryId1 = relation.getAttribute("entry1");
            String entryId2 = relation.getAttribute("entry2");
            entry1 = entryMap.get(entryId1);
            entry2 = entryMap.get(entryId2);
            inhibit = false;

            NodeList subtypes = relation.getElementsByTagName("subtype");
            for (int i = 0; i < subtypes.getLength(); i++) {
                Element subtype = (Element) subtypes.item(i);
                inhibit = inhibit | subtype.getAttribute("name").equals("inhibition");
            }
        }

        public void save(GraphDatabaseService graphDb) {
            Set<Node> sources = namesToNodes(graphDb, entry1.nameSet);
            Set<Node> targets = namesToNodes(graphDb, entry2.nameSet);

            if (entry1.type.equals("group") && entry2.type.equals("group")) {
                saveHyperedge(new Hyperedge(sources, targets));
            }

            // change semantics as simple edges
            else if (entry1.type.equals("group")) {
                for (Node t : targets) {
                    saveHyperedge(new Hyperedge(sources, t));
                }
            }

            else if (entry2.type.equals("group")) {
                for (Node s : sources) {
                    saveHyperedge(new Hyperedge(s, targets));
                }
            }

            else {
                for (Node s : sources) {
                    for (Node t : targets) {
                        saveHyperedge(new Hyperedge(s, t));
                    }
                }
            }
        }

        private void saveHyperedge(Hyperedge hyperedge) {
            if (!isDuplicated(hyperedge)) {
                hyperedge.save(graphDb);
                countRelations++;
            }
        }
    }

    /*
     * Example of kegg reaction
     * All entries are compound <- tested!
     * <reaction id="100" name="rn:R03991" type="reversible">
     *   <substrate id="191" name="cpd:C00024"/>
     *   <substrate id="158" name="cpd:C02593"/>
     *   <product id="163" name="cpd:C00010"/>
     *   <product id="164" name="cpd:C05259"/>
     * </reaction>
     */
    private class KeggReaction {
        Set<String> sourceNames;
        Set<String> targetNames;

        public KeggReaction(Element reaction, Map<String, KeggEntry> entryMap) {
            NodeList substrates = reaction.getElementsByTagName("substrate");
            NodeList products = reaction.getElementsByTagName("product");
            sourceNames = constructNameSet(substrates, entryMap);
            targetNames = constructNameSet(products, entryMap);
        }

        private Set<String> constructNameSet(NodeList list, Map<String, KeggEntry> entryMap) {
            Set<String> result = new HashSet<String>();
            for (int i = 0; i < list.getLength(); i++) {
                Element elem = (Element) list.item(i);
                String id = elem.getAttribute("id");
                String name = elem.getAttribute("name");

                KeggEntry entry = entryMap.get(id);
                result.addAll(entry.nameSet);
            }
            return result;
        }

        public void save(GraphDatabaseService graphDb) {
            Set<Node> sources = namesToNodes(graphDb, sourceNames);
            Set<Node> targets = namesToNodes(graphDb, targetNames);

            Hyperedge hyperedge = new Hyperedge(sources, targets);
            if (!isDuplicated(hyperedge)) {
                hyperedge.save(graphDb);
                countReactions++;
            }
        }
    }

    /**
     * Kegg importer configuration
     */
    public class KeggImporterConf {
        public boolean importAttributes = false;
        public boolean importRelations = true;
        public boolean importFileLimit = false;
        public int fileLimit = 0;
        public double startableRatio = 1.0;
    }

    private GraphDatabaseService graphDb;
    private KeggImporterConf conf;
    private int countFile;
    private int countEntry;
    private int countRelations;
    private int countReactions;

    public KeggImporter() {
        graphDb = HypergraphDatabase.getGraphDatabase();
        conf = new KeggImporterConf();
        countFile = 0;
        countEntry = 0;
        countRelations = 0;
        countReactions = 0;
    }

    public KeggImporter(KeggImporterConf conf) {
        this();
        this.conf = conf;
    }

    @Override
    public void run() {
        File[] files = new File("input/kegg").listFiles();
        for (File file : files) {
            if (file.getName().endsWith(".xml")) {
                handleFile(file);
                countFile++;
            }
            if (conf.importFileLimit && countFile == conf.fileLimit) {
                break;
            }
        }

//        markStartables();

        Log.info("countFile : " + countFile);
        Log.info("countEntry : " + countEntry);
        Log.info("countRelations : " + countRelations);
        Log.info("countReactions : " + countReactions);
    }

    private void handleFile(File file) {
        Log.debug("file " + file.getName());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            handleDocument(doc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDocument(Document doc) {
        try (Transaction tx = graphDb.beginTx()) {
            Element root = doc.getDocumentElement();
            NodeList entries = root.getElementsByTagName("entry");
            NodeList relations = root.getElementsByTagName("relation");
            NodeList reactions = root.getElementsByTagName("reaction");

            Map<String, KeggEntry> entryMap = new HashMap<>();

            // parse entry
            for (int i = 0; i < entries.getLength(); i++) {
                KeggEntry entry = new KeggEntry((Element) entries.item(i), entryMap);
                entryMap.put(entry.id, entry);
                entry.save(graphDb);
            }

            // parse relation
            if (conf.importRelations) {
                for (int i = 0; i < relations.getLength(); i++) {
                    KeggRelation relation = new KeggRelation((Element) relations.item(i), entryMap);
                    if (!relation.inhibit) { //TODO: handle inhibition
                        relation.save(graphDb);
                    }
                }
            }

            // parse reactions
            for (int i = 0; i < reactions.getLength(); i++) {
                KeggReaction reaction = new KeggReaction((Element) reactions.item(i), entryMap);
                reaction.save(graphDb);
            }

            tx.success();
        }
    }

    private Set<Node> namesToNodes(GraphDatabaseService graphDb, Set<String> names) {
        Set<Node> nodeSet = new HashSet<>();
        for (String name : names) {
            Node node = graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, name);
            if (node == null) {
                Log.debug("null node");
            } else {
                nodeSet.add(node);
            }
        }
        return nodeSet;
    }

    private void markStartables() {
        try (Transaction tx = graphDb.beginTx()) {
            int numStartable = 0;

            ResourceIterator<Node> iter = graphDb.findNodes(Const.LABEL_NODE);

            while (iter.hasNext()) {
                Node n = iter.next();

//                if (Math.random() > conf.startableRatio)
//                    continue;

                String name = (String) n.getProperty(Const.PROP_UNIQUE);
                if (n.getProperty("type").equals("compound")) {
//                if (name.startsWith("dr:")) {
                    Log.debug(name);
                    n.addLabel(Const.LABEL_STARTABLE);
                    numStartable++;
                }
            }

            Log.info("numStartable : " + numStartable);
            tx.success();
        }
    }

    //TODO: add as a target in previsoue hyperedge
    private boolean isDuplicated(Hyperedge hyperedge) {
        Set<Hyperedge> hyperedges = Hyperedge.getHyperedgesFrom(hyperedge.getSource());
        for (Hyperedge e : hyperedges) {
            if (e.getTarget().containsAll(hyperedge.getTarget())) {
                return true;
            }
        }
        return false;
    }
}
