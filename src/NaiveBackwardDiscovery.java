import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Hyunjun on 2015-05-06.
 */
public class NaiveBackwardDiscovery {
    private GraphDatabaseService graphDb;

    public NaiveBackwardDiscovery() {
        graphDb = Application.getGraphDatabase();
    }

    public Set<Node> find(Node target) {
        return findSourceSet(target);
    }

    public Set<Node> find(Set<Node> targets) {
        //TODO:
        return null;
    }

    //XXX: avoid recursive impl
    private Set<Node> findSourceSet(Node t) {
        Set<Node> sourceSet = new HashSet<>();

        // base case
        if (t.hasLabel(Const.LABEL_STARTABLE)) {
            sourceSet.add(t);
            return sourceSet;
        }

        // get backward star
        Set<Node> sources = new HashSet<>();
        Iterable<Relationship> rels = t.getRelationships(Direction.INCOMING, Const.REL_TO_TARGET);
        for (Relationship rel : rels) {
            Node h = rel.getEndNode(); // pseudo-hypernode

            Iterable<Relationship> sourceRels = h.getRelationships(Direction.INCOMING, Const.REL_FROM_SOURCE);
            for (Relationship sourceRel : sourceRels) {
                Node s = rel.getEndNode();
                sources.add(s);
            }
        }

        for (Node s : sources) {
            sourceSet.addAll(findSourceSet(s));
        }

        return sourceSet;
    }
}
