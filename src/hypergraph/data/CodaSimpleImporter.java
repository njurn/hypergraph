package hypergraph.data;

import hypergraph.common.Const;
import hypergraph.common.Hyperedge;
import hypergraph.common.HypergraphDatabase;
import hypergraph.util.Log;
import org.neo4j.graphdb.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Import CODA data
 * Tab separated
 * ex) CE00000001 D000050 Acanthocytes Acanthocyte
 * ex) left(GE02054564 <<bd:BD00000567><ce:CE00000535>>, GE02064094 <<bd:BD00000567><ce:CE00000535>>
 *     RE00000162
 *     right(GE02054441 <<ts:TS00000021><ce:CE00000326>>) -r [EndoNet] -t [1]
 *
 * Created by Hyunjun on 2015-05-18.
 */
public class CodaSimpleImporter implements Importer {

    private class CodaEntity {
        String id;
        String originId;
        String name;
        String synonym;

        public CodaEntity(String s) {
            String[] token = s.split("\t");
            id = token[0];
            originId = token[1];
            name = token[2];
            synonym = (token.length > 3) ? token[3] : "";
        }

        public String toString() {
            return id + "(" + name + ") " + synonym;
        }
    }

    private class CodaSide {
        String id;
        String[] tags;

        public CodaSide(String s) {
            String data[] = s.split(" ");
            id = data[0];
            //tags = data[1];//.split("<>"); //TODO:complex
        }
    }

    private class CodaRule {

    }

    private GraphDatabaseService graphDb;
    private int countEntity;
    private int countRule;
    private Map<String, String> ruleTypes;

    public CodaSimpleImporter() {
        graphDb = HypergraphDatabase.getGraphDatabase();
        countEntity = 0;
        countRule = 0;
    }

    @Override
    public void run() {
        constructRuleType(new File("input/coda/BISL_Ontology/relation(RE).txt"));
        importEntityFile(new File("input/coda/BISL_Ontology/gene(GE)_HomoSapiens.txt"), "Gene");
        importEntityFile(new File("input/coda/BISL_Ontology/disease(DS).txt"), "Disease");
        importEntityFile(new File("input/coda/BISL_Ontology/metabolite(MB).txt"), "Metabolite");
        importRuleFile(new File("input/coda/FinalNetwork/CODA2_Gene_Disease_Network.txt"));
        importRuleFile(new File("input/coda/FinalNetwork/CODA2_Inter_Cell_Network.txt"));
//        importRuleFile(new File("input/coda/FinalNetwork/CODA2_Intra_Cell_Network.txt"));
        importRuleFile(new File("input/coda/FinalNetwork/kegg.txt"));
        importDrugAndInteraction(new File("input/coda/drug_target_interaction_alldrugs.txt"));

        Log.info("CodaImporter DONE");
        Log.info("countEntity: " + countEntity);
        Log.info("countRule: " + countRule);
    }

    private void constructRuleType(File file) {
        ruleTypes = new HashMap<>();
        try (FileReader fr = new FileReader(file)) {
            BufferedReader br = new BufferedReader(fr);
            String s = br.readLine(); // skip first line
            while ((s = br.readLine()) != null) {
                if (s.startsWith("<DB END>")) break;
                CodaEntity entity = new CodaEntity(s);
                ruleTypes.put(entity.id, entity.originId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void importEntityFile(File file, String label) {
        try (Transaction tx = graphDb.beginTx()) {
            try (FileReader fr = new FileReader(file)) {
                BufferedReader br = new BufferedReader(fr);
                String s = br.readLine(); // skip first line
                while ((s = br.readLine()) != null) {
                    importEntity(s, label);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            tx.success();
        }
    }

    private void importEntity(String row, String label) {
        CodaEntity entity = new CodaEntity(row);

        if (graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, entity.id) != null)
            return;

        Node node = graphDb.createNode(Const.LABEL_NODE);
        node.setProperty(Const.PROP_UNIQUE, entity.id);
        node.setProperty("name", entity.name);
        node.setProperty("synonym", entity.synonym);
        node.addLabel(DynamicLabel.label(label));
        countEntity++;
    }

    private void importRuleFile(File file) {
        try (Transaction tx = graphDb.beginTx()) {
            try (FileReader fr = new FileReader(file)) {
                BufferedReader br = new BufferedReader(fr);
                String s;
                while ((s = br.readLine()) != null) {
                    importRule(s);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            tx.success();
        }
    }

    private void importRule(String row) {
        String[] data = row.split("\t");
        String left = data[0];
        String rel = data[1];
        String right = data[2];
        String mode = (data.length > 3) ? data[3] : "";

        if (rel.length() < 10)
            rel = rel.replaceFirst("RE", "RE0");
        if (rel.startsWith("RE00000111")) // XXX: exclude inhibit
            return;
        if (mode.startsWith("-i")) //XXX: exclude inhibit
            return;

        // remove left() and right()
        left = left.substring(5, left.length() - 1);
        right = right.substring(6, right.length() - 1);

        Set<CodaSide> source = parseSides(left);
        Set<CodaSide> target = parseSides(right);

        if (source == null || target == null)
            return;

        // handle -a
        if (mode.startsWith("-a")) {
            mode = mode.substring(4, mode.length() - 1);
            source.add(new CodaSide(mode));
        }

        Hyperedge h = new Hyperedge();

        for (CodaSide s : source) {
            Node node = graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, s.id);
            if (node == null) {
                Log.error("no source entry : " + s.id);
                return;
            }
            h.addSource(node);
        }

        for (CodaSide t : target) {
            Node node = graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, t.id);
            if (node == null) {
                Log.error("no target entry : " + t.id);
                return;
            }
            h.addTarget(node);
        }

        if (!isDuplicated(h)) {
            h.save(graphDb);
            Node hypernode = h.getHypernode();
            if (hypernode != null) {
                hypernode.setProperty("type", rel);
                hypernode.setProperty("name", ruleTypes.get(rel));
                countRule++;
            }
        }
    }

    private Set<CodaSide> parseSides(String s) {
        //TODO: handle complex
        if (s.startsWith("complex"))
            return null;

        Set<CodaSide> result = new HashSet<>();
        String[] entries = s.split(",");
        for (String entry : entries) {
            if (entry.startsWith(" "))
                entry = entry.substring(1, entry.length());
            if (entry.startsWith("<"))
                continue;
            CodaSide codaRelSide = new CodaSide(entry);
            result.add(codaRelSide);
        }
        return result;
    }

    private boolean isDuplicated(Hyperedge hyperedge) {
        Set<Hyperedge> hyperedges = Hyperedge.getHyperedgesFrom(hyperedge.getSource());
        for (Hyperedge e : hyperedges) {
            if (e.getTarget().containsAll(hyperedge.getTarget())) {
                return true;
            }
        }
        return false;
    }

    private void importDrugAndInteraction(File file) {
        try (Transaction tx = graphDb.beginTx()) {
            try (FileReader fr = new FileReader(file)) {
                BufferedReader br = new BufferedReader(fr);
                String s = br.readLine(); // skip line
                while ((s = br.readLine()) != null) {
                    importDrug(s);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            tx.success();
        }
    }

    private void importDrug(String s) {
        String[] data = s.split("\t");
        String name = data[0];
        String up = data[1];
        String down = data[2];

        Node node = graphDb.createNode(Const.LABEL_NODE);
        node.addLabel(Const.LABEL_STARTABLE);
        node.addLabel(DynamicLabel.label("Drug"));
        node.setProperty(Const.PROP_UNIQUE, name);
        node.setProperty("name", name);

        if (!up.startsWith("null"))
            importDrugInteraction(node, up);
        if (!down.startsWith("null"))
            importDrugInteraction(node, down);
    }

    private void importDrugInteraction(Node node, String targetSeq) {
        Set<String> targetIds = new HashSet<>();
//        Log.debug(targetSeq);
        String[] targets = targetSeq.split("\\|");
        for (String target : targets) {
//            Log.debug(target);
            String id = target.split(" ")[0];
//            Log.debug(id);
            targetIds.add(id);
        }

        Hyperedge h = new Hyperedge();
        h.addSource(node);

        for (String target : targetIds) {
            Node t = graphDb.findNode(Const.LABEL_NODE, Const.PROP_UNIQUE, target);
            h.addTarget(t);
        }

        if (!isDuplicated(h)) {
            h.save(graphDb);
        }
    }
}
