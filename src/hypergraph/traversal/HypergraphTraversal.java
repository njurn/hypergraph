package hypergraph.traversal;

import hypergraph.Application;
import hypergraph.common.Const;
import hypergraph.common.HypergraphDatabase;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Created by Hyunjun on 2015-04-17.
 */
public class HypergraphTraversal {
    private Set<Long> visited; // visited nodes, in-memory
    private HypergraphTraversalCallback onVisitNode;
    private HypergraphTraversalCallback onVisitHyperedge;

    public HypergraphTraversal() {
        this((v)->{}, (v)->{});
    }

    public HypergraphTraversal(HypergraphTraversalCallback onVisitNode) {
        this(onVisitNode, (v)->{});
    }

    public HypergraphTraversal(HypergraphTraversalCallback onVisitNode, HypergraphTraversalCallback onVisitHyperedge) {
        this.visited = new HashSet<Long>();
        this.onVisitNode = onVisitNode;
        this.onVisitHyperedge = onVisitHyperedge;
    }

    public void traverse(Node s) {
        Set<Node> start = new HashSet<>();
        start.add(s);
        traverse(start);
    }

    public void traverse(Set<Node> start) {
        Queue<Node> queue = new LinkedList<Node>();

        for (Node s : start) {
            setVisited(s);
            queue.add(s);
            onVisitNode.onVisit(s);
        }

        while (!queue.isEmpty()) {
            // dequeue a normal node (one of source nodes)
            Node v = queue.poll();

            // get connected hyperedges
            Iterable<Relationship> fromSources = v.getRelationships(Direction.OUTGOING, Const.REL_FROM_SOURCE);
            for (Relationship fromSource : fromSources) {
                // get pseudo hypernode
                Node h = fromSource.getEndNode();
                if (isVisited(h))
                    continue;
                else if (!isEnabled(h))
                    continue;

                setVisited(h);
                onVisitHyperedge.onVisit(h);

                // get single target node
                // Node t = h.getSingleRelationship(Const.REL_TO_TARGET, Direction.OUTGOING).getEndNode();
                // modified to support multiple target nodes
                Iterable<Relationship> toTargets = h.getRelationships(Direction.OUTGOING, Const.REL_TO_TARGET);
                for (Relationship toTarget : toTargets) {
                    Node t = toTarget.getEndNode();
                    if (!isVisited(t)) {
                        setVisited(t);
                        queue.add(t);
                        onVisitNode.onVisit(t);
                    }
                }
            }
        }
    }

    private void setVisited(Node node) {
        visited.add(node.getId());
    }

    private boolean isVisited(Node node) {
        return visited.contains(node.getId());
    }

    private boolean isEnabled(Node hypernode) {
        Iterable<Relationship> rels = hypernode.getRelationships(Direction.INCOMING, Const.REL_FROM_SOURCE);
        for (Relationship rel : rels) {
            if (!isVisited(rel.getStartNode())) {
                return false;
            }
        }
        return true;
    }
}
