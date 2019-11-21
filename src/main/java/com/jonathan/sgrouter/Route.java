package com.jonathan.sgrouter;

import java.util.ArrayList;
import java.util.Comparator;

class Route {
    ArrayList<String> path, service;
    ArrayList<Double> weightPath;

    Route(ArrayList<String> a, ArrayList<Double> b, ArrayList<String> s) {
        this.path = new ArrayList<>(a);
        this.weightPath = new ArrayList<>(b);
        this.service = new ArrayList<>(s);
    }

    public String toString(){
        return String.format("{\"path\":%s,\"weight\":%s,\"service\":%s}",this.path.toString(),this.weightPath.toString(),this.service.toString());
    }
}

class routeWeightComparator implements Comparator<Route> {
    @Override
    public int compare(Route p1, Route p2) {
        double w1 = p1.weightPath.get(p1.weightPath.size() - 1);
        double w2 = p2.weightPath.get(p2.weightPath.size() - 1);

        return Double.compare(w1, w2);
    }
}