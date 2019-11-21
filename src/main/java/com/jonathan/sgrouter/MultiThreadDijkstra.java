package com.jonathan.sgrouter;

import java.util.*;

class MultiThreadDijkstra implements Runnable {
    private double firstWalk, lastWalk;
    private String source, dest;
    private Map<String, Set<String>> numPathToNode;

    MultiThreadDijkstra(double fw, double lw, String src, String de, Map<String, Set<String>> nptn) {
        this.firstWalk = fw;
        this.lastWalk = lw;
        this.source = src;
        this.dest = de;
        this.numPathToNode = nptn;
    }

    private static void Dijkstra(double firstWalk, double lastWalk, String source, String dest, Map<String, Set<String>> numPathToNode) // Algorithm for SSSP (Source, Num vertices)
    {
        final int kShortest = 3;

        // Priority queue to store vertex,weight pairs
        PriorityQueue<Route> pq = new PriorityQueue<>(11, new routeWeightComparator());
        int tempPathToDest = 0;

        // Pushing the source with distance from itself as 0
        ArrayList<String> startList = new ArrayList<>();
        startList.add(source);
        ArrayList<String> startServ = new ArrayList<>();
        startServ.add("walk");
        ArrayList<Double> startTime = new ArrayList<>();
        startTime.add(firstWalk);

        pq.add(new Route(startList, startTime, startServ));

        while (pq.size() > 0 && tempPathToDest < kShortest) /// AND NUMBER OF PATHS < K (3 for routing, 1 for calc)
        {
            Route curr = pq.poll(); // Current vertex

            ArrayList<String> currPath = new ArrayList<>(curr.path);
            ArrayList<String> currServ = new ArrayList<>(curr.service);
            ArrayList<Double> currTime = new ArrayList<>(curr.weightPath);
            String cv = currPath.get(curr.path.size() - 1);
            String cs = currServ.get(curr.service.size() - 1);
            double cw = currTime.get(curr.weightPath.size() - 1);

            synchronized (SGRouter.outputPaths) {
                if (SGRouter.outputPaths.size() >= kShortest){
                    Route slowest = SGRouter.outputPaths.get(kShortest - 1);
                    if (cw > slowest.weightPath.get(slowest.weightPath.size() - 1))
                        continue;
                }
            }
            if (cv.equals(dest)) {
                currTime.add(currTime.get(currTime.size() - 1) + lastWalk);
                currPath.add("destination");
                currServ.add("walk");

                Route addedRoute = new Route(currPath, currTime, currServ);
                synchronized (SGRouter.outputPaths) {
                    SGRouter.outputPaths.add(addedRoute);
                    Collections.sort(SGRouter.outputPaths, new routeWeightComparator());
                }

                tempPathToDest++;
            } else if (!numPathToNode.get(cv).contains(cs)) {
                numPathToNode.get(cv).add(cs);
                for (int i = 0; i < SGRouter.a.get(cv).size(); i++) {
                    ArrayList<String> tempPath = new ArrayList<>(currPath);
                    ArrayList<String> tempServ = new ArrayList<>(currServ);
                    ArrayList<Double> tempTime = new ArrayList<>(currTime);

                    String addedService = (SGRouter.a.get(cv).get(i)).service;

                    //Ensure no double passing of a node and do not walk from node to node
                    if (!tempPath.contains((SGRouter.a.get(cv).get(i)).dest) && !(cs.equals("walk") && addedService.equals("walk"))) {
                        tempPath.add((SGRouter.a.get(cv).get(i)).dest);
                        tempServ.add(addedService);

                        double waitTime = 0;
                        if (addedService.charAt(0) >= 'A' && addedService.charAt(0) <= 'z' && !addedService.equals("walk") && !addedService.contains("CT") && !addedService.contains("BPS")) {
                            try {
                                waitTime += SGRouter.mrtStopTime.get(cv);
                                if (!addedService.equals(cs))
                                    waitTime += SGRouter.getWaitTime((SGRouter.a.get(cv).get(i)).dest);
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

                        double addedTime = (SGRouter.a.get(cv).get(i)).weight + cw + waitTime;
                        tempTime.add(addedTime);

                        Route insertedPath = new Route(tempPath, tempTime, tempServ);
                        pq.add(insertedPath);
                    }
                }
            }
        }
    }

    public void run() {
        Dijkstra(this.firstWalk, this.lastWalk, this.source, this.dest, this.numPathToNode);
    }
}