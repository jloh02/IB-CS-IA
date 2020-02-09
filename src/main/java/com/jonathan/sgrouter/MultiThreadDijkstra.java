package com.jonathan.sgrouter;

import java.util.*;

class MultiThreadDijkstra implements Runnable {
    private double firstWalk, lastWalk;
    private String source, dest;
    private Map<String, Set<String>> numPathToNode;

    MultiThreadDijkstra(double fw, double lw, String src, String de,
                        Map<String, Set<String>> nptn) {
        this.firstWalk = fw;
        this.lastWalk = lw;
        this.source = src;
        this.dest = de;
        this.numPathToNode = nptn;
    }

    // Algorithm for SSSP (Single source shortest path)
    private static void Dijkstra(double firstWalk, double lastWalk,
                                 String source, String dest,
                                 Map<String, Set<String>> numPathToNode) {
        final int kShortest = 3;

        // Priority queue to store vertex,weight pairs
        // (Uses custom comparator for comparing distance)
        PriorityQueue<Route> pq =
                new PriorityQueue<>(11, new routeWeightComparator());
        int tempPathToDest = 0;

        // Pushing the source with distance as 0 and service as "walk"
        ArrayList<String> startList = new ArrayList<>();
        startList.add(source);
        ArrayList<String> startServ = new ArrayList<>();
        startServ.add("walk");
        ArrayList<Double> startTime = new ArrayList<>();
        startTime.add(firstWalk);
        pq.add(new Route(startList, startTime, startServ));

        // While priority queue has vertexes and number of routes found < 3
        while (pq.size() > 0 && tempPathToDest < kShortest) {
            Route curr = pq.poll(); // Current edge/vertex

            ArrayList<String> currPath = new ArrayList<>(curr.path);
            ArrayList<String> currServ = new ArrayList<>(curr.service);
            ArrayList<Double> currTime = new ArrayList<>(curr.weightPath);
            String cv = currPath.get(curr.path.size() - 1);
            String cs = currServ.get(curr.service.size() - 1);
            double cw = currTime.get(curr.weightPath.size() - 1);

            //Synchronized handles concurrency between Threads when modifying
            // the global list of routes (SGRouter.outputPaths)
            synchronized (SGRouter.outputPaths) {
                //Check if the current path is already slower than 3rd
                // shortest path
                if (SGRouter.outputPaths.size() >= kShortest) {
                    Route slowest = SGRouter.outputPaths.get(kShortest - 1);
                    if (cw > slowest.weightPath
                            .get(slowest.weightPath.size() - 1)) {
                        continue;
                    }
                }
            }

            //Current vertex reaches destination
            if (cv.equals(dest)) {
                currTime.add(currTime.get(currTime.size() - 1) + lastWalk);
                currPath.add("destination");
                currServ.add("walk");

                //Add path to global list of routes
                Route addedRoute = new Route(currPath, currTime, currServ);
                synchronized (SGRouter.outputPaths) {
                    SGRouter.outputPaths.add(addedRoute);
                    Collections.sort(SGRouter.outputPaths,
                            new routeWeightComparator());
                }
                tempPathToDest++;

            } else if (!numPathToNode.get(cv).contains(cs)) {
                numPathToNode.get(cv).add(cs);
                //Iterate through all vertexes in adjacency list from current
                // node
                for (int i = 0; i < SGRouter.a.get(cv).size(); i++) {
                    ArrayList<String> tempPath = new ArrayList<>(currPath);
                    ArrayList<String> tempServ = new ArrayList<>(currServ);
                    ArrayList<Double> tempTime = new ArrayList<>(currTime);

                    String addedService = (SGRouter.a.get(cv).get(i)).service;

                    //Ensure no double passing of a node and do not walk from
                    // node to node
                    if (!tempPath.contains((SGRouter.a.get(cv).get(i)).dest) &&
                            !(cs.equals("walk") &&
                                    addedService.equals("walk"))) {
                        tempPath.add((SGRouter.a.get(cv).get(i)).dest);
                        tempServ.add(addedService);

                        //Cater for waiting and stop times
                        double waitTime = 0;
                        if (Route.isMRT(addedService)) {
                            try {
                                waitTime += SGRouter.mrtStopTime.get(cv);
                                if (!addedService.equals(cs)) {
                                    waitTime += SGRouter.getWaitTime(
                                            (SGRouter.a.get(cv).get(i)).dest);
                                }
                            } catch (NullPointerException e) {
                                waitTime += 5;
                            }
                        } else if (!addedService.equals(cs)) {
                            try {
                                waitTime += SGRouter.getWaitTime(addedService);
                            } catch (NullPointerException e) {
                                waitTime += 15;
                            }
                        }

                        double addedTime =
                                (SGRouter.a.get(cv).get(i)).weight + cw +
                                        waitTime;
                        tempTime.add(addedTime);

                        //Add path to priority queue
                        Route insertedPath =
                                new Route(tempPath, tempTime, tempServ);
                        pq.add(insertedPath);
                    }
                }
            }
        }
    }

    public void run() {
        Dijkstra(this.firstWalk, this.lastWalk, this.source, this.dest,
                this.numPathToNode);
    }
}