package cp2023.solution;

import java.util.HashSet;
import java.util.Map;

public abstract class DirectedGraph<V, E> {
    private Map<V, HashSet<Edge<E>>> adj;

    public void addEdge(V u, V v, E weight) {

    }

    public void addVertex(V v) {
        adj.putIfAbsent(v, new HashSet<>());
    }

}
