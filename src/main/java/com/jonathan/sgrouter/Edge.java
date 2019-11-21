package com.jonathan.sgrouter;

class Edge {
    String dest, service;
    double weight;

    Edge(String d, double w, String s) {
        this.dest = d;
        this.service = s;
        this.weight = w;
    }
}