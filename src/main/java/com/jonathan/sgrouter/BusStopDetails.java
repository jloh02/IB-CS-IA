package com.jonathan.sgrouter;

import java.util.Comparator;

public class BusStopDetails {
    String code,name,road;
    double dist;

    public BusStopDetails(String c, String n, String r, double d){
        this.code = c;
        this.name = n;
        this.road = r;
        this.dist = d;
    }
}

class busStopDistComparator implements Comparator<BusStopDetails> {
    @Override
    public int compare(BusStopDetails d1, BusStopDetails d2) {
        return Double.compare(d1.dist,d2.dist);
    }
}