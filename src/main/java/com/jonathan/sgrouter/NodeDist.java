package com.jonathan.sgrouter;

import java.util.Comparator;

class NodeDist {
    String node;
    double dist;

    NodeDist(String n, double d) {
        this.node = n;
        this.dist = d;
    }
}

class nodeDistComparator implements Comparator<NodeDist> {
    @Override
    public int compare(NodeDist d1, NodeDist d2) {
        return Double.compare(d1.dist, d2.dist);
    }
}