package com.jonathan.sgrouter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.*;

class SGRouter {
    final static ArrayList<Route> outputPaths = new ArrayList<>();
    static Map<String, ArrayList<Edge>> a = new HashMap<>();
    static Map<String, Double> mrtStopTime = new HashMap<>();
    private static Map<String, ArrayList<Double>> busFreq = new HashMap<>();
    private static Map<String, ArrayList<Double>> mrtFreq = new HashMap<>();
    private static Set<String> nodes = new HashSet<>();
    private static JSONObject jObj;

    SGRouter() {
        try {
            long startParse = System.nanoTime();
            try {
                jObj = new JSONObject(new String(Thread.currentThread().getContextClassLoader().getResourceAsStream("SGPublicTransportData.json").readAllBytes(), StandardCharsets.UTF_8));
                long fileParseTime = System.nanoTime();
                System.out.println("Read File: " + (fileParseTime - startParse) / 1000000 + "ms");
            } catch (IOException e) {
                System.out.println("IO Exception");
                System.out.println(e.toString());
            }
        } catch (JSONException e) {
            System.out.println("JSON Exception");
            System.out.println(e.toString());
        }
    }

    static double getWaitTime(String service) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        if (service.equals("walk"))
            return 0;
        else if (service.charAt(0) >= 'A' && service.charAt(0) <= 'z' && !service.contains("CT") && !service.contains("BPS")) {
            service = service.substring(0, 2);
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                if (h >= 12 && h <= 14) return mrtFreq.get(service).get(1);
                if (h >= 18 && h <= 22) return mrtFreq.get(service).get(1);
                return mrtFreq.get(service).get(0);
            }
            if (h == 7 && m >= 30) return mrtFreq.get(service).get(1);
            if (h == 8) return mrtFreq.get(service).get(1);
            if (h == 9 && m <= 30) return mrtFreq.get(service).get(1);
            if (h == 17 && m >= 30) return mrtFreq.get(service).get(1);
            if (h == 18) return mrtFreq.get(service).get(1);
            if (h == 19 && m <= 30) return mrtFreq.get(service).get(1);
            return mrtFreq.get(service).get(0);
        } else {
            if (h <= 8 && m <= 30)
                return busFreq.get(service).get(0);
            if (h < 17)
                return busFreq.get(service).get(1);
            if (h < 19)
                return busFreq.get(service).get(2);
            return busFreq.get(service).get(3);
        }
    }

    private static double calc_dist(double lon1, double lat1, double lon2, double lat2) {
        lon1 = toRadians(lon1);
        lat1 = toRadians(lat1);
        lon2 = toRadians(lon2);
        lat2 = toRadians(lat2);

        double distLon = lon2 - lon1;
        double distLat = lat2 - lat1;

        double di = sin(distLat / 2) * sin(distLat / 2) + cos(lat1) * cos(lat2) * sin(distLon / 2) * sin(distLon / 2);
        return 2 * asin(sqrt(di)) * 6378.1; //approx radius of earth (km)
    }


    private static ArrayList<String> routeToText() {
        ArrayList<String> routeString = new ArrayList<>();
        for (int i = 0; i < outputPaths.size(); i++) {
            ArrayList<String> p = outputPaths.get(i).path;
            ArrayList<String> s = outputPaths.get(i).service;
            ArrayList<Double> t = outputPaths.get(i).weightPath;
            routeString.add("");
            String prevServ = "walk";
            for (int j = 0; j < p.size(); j++) {
                if (!s.get(j).equals(prevServ)) {
                    String cmd = "Take " + prevServ + " ";
                    if (prevServ.equals("walk"))
                        cmd = "Walk ";
                    routeString.set(i, routeString.get(i) + cmd + "to " + p.get(j - 1) + "\n");
                    prevServ = s.get(j);
                }
            }
            if (prevServ.equals("walk"))
                routeString.set(i, routeString.get(i) + "Walk to " + p.get(p.size() - 1) + "\nTotal time: "
                        + Math.round(t.get(t.size() - 1)));
            else
                routeString.set(i, routeString.get(i) + "Take " + prevServ + " to " + p.get(p.size() - 1) + "\nTotal time: "
                        + Math.round(t.get(t.size() - 1)));
        }
        return routeString;
    }

    private static boolean checkInService(String first, String last, Calendar now) {
        if (first.equals("-") || last.equals("-")) return false;
        Calendar start = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        Calendar end = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));

        start.set(Calendar.HOUR_OF_DAY, Integer.parseInt(first.split(" ")[0]));
        start.set(Calendar.MINUTE, Integer.parseInt(first.split(" ")[1]));
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        end.set(Calendar.HOUR_OF_DAY, Integer.parseInt(last.split(" ")[0]));
        end.set(Calendar.MINUTE, Integer.parseInt(last.split(" ")[1]));
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        if (end.before(start)) {
            if (now.get(Calendar.HOUR_OF_DAY) <= 3) end.add(Calendar.DATE, -1);
            else end.add(Calendar.DATE, 1);
        }

        return !(now.before(start) || now.after(end));
    }

    private static void calculate(double startLat, double startLon, double endLat, double endLon, double maxWalkKm) {
        long start = System.nanoTime();

        ArrayList<String> xStore = new ArrayList<>();
        ArrayList<String> yStore = new ArrayList<>();
        ArrayList<Double> wStore = new ArrayList<>();
        ArrayList<String> sStore = new ArrayList<>();

        ArrayList<NodeDist> nearbyStart = new ArrayList<>();
        ArrayList<NodeDist> nearbyEnd = new ArrayList<>();

        for (Map.Entry<String, ArrayList<Edge>> entry : a.entrySet())
            entry.getValue().clear();

        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));

        JSONObject busAdjList = jObj.getJSONObject("busAdjList");
        Iterator<String> it = busAdjList.keys();

        while (it.hasNext()) {
            String x = it.next();
            nodes.add(x);

            JSONArray edgeList = busAdjList.getJSONObject(x).getJSONArray("edges");
            for (int i = 0; i < edgeList.length(); i++) {
                JSONObject edge = edgeList.getJSONObject(i);
                nodes.add(edge.getString("BusStopCode"));
                boolean inService;

                if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                    String first = edge.getString("SAT_FirstBus");
                    String last = edge.getString("SAT_LastBus");
                    inService = checkInService(first, last, c);
                } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    String first = edge.getString("SUN_FirstBus");
                    String last = edge.getString("SUN_LastBus");
                    inService = checkInService(first, last, c);
                } else {
                    String first = edge.getString("WD_FirstBus");
                    String last = edge.getString("WD_LastBus");
                    inService = checkInService(first, last, c);
                }

                if (inService) {
                    xStore.add(x);
                    yStore.add(edge.getString("BusStopCode"));
                    wStore.add(edge.getDouble("Time"));
                    sStore.add(edge.getString("Service"));
                }
            }
            double lonNode = busAdjList.getJSONObject(x).getDouble("lon");
            double latNode = busAdjList.getJSONObject(x).getDouble("lat");
            double distFromStart = calc_dist(lonNode, latNode, startLon, startLat);
            if (abs(distFromStart) <= maxWalkKm) nearbyStart.add(new NodeDist(x, distFromStart));
            double distFromEnd = calc_dist(lonNode, latNode, endLon, endLat);
            if (abs(distFromEnd) <= maxWalkKm) nearbyEnd.add(new NodeDist(x, distFromEnd));
        }

        JSONObject mrtAdjList = jObj.getJSONObject("mrtAdjList");
        Iterator<String> mrtIt = mrtAdjList.keys();
        while (mrtIt.hasNext()) {
            String x = mrtIt.next();
            nodes.add(x);

            JSONArray edgeList = mrtAdjList.getJSONObject(x).getJSONArray("edges");
            for (int i = 0; i < edgeList.length(); i++) {
                JSONObject edge = edgeList.getJSONObject(i);
                nodes.add(edge.getString("Station"));
                boolean inService;

                if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    String first = edge.getString("SUN_FirstTrain");
                    String last = edge.getString("LastTrain");
                    inService = checkInService(first, last, c);
                } else {
                    String first = edge.getString("FirstTrain");
                    String last = edge.getString("LastTrain");
                    inService = checkInService(first, last, c);
                }

                if (inService) {
                    xStore.add(x);
                    yStore.add(edge.getString("Station"));
                    wStore.add(edge.getDouble("Time"));
                    sStore.add(edge.getString("Service"));
                    mrtStopTime.put(x, edge.getDouble("StopTime"));
                }
            }
            double lonNode = mrtAdjList.getJSONObject(x).getDouble("lon");
            double latNode = mrtAdjList.getJSONObject(x).getDouble("lat");
            double distFromStart = calc_dist(lonNode, latNode, startLon, startLat);
            if (abs(distFromStart) <= 0.3) nearbyStart.add(new NodeDist(x, distFromStart));
            double distFromEnd = calc_dist(lonNode, latNode, endLon, endLat);
            if (abs(distFromEnd) <= 0.3) nearbyEnd.add(new NodeDist(x, distFromEnd));
        }
        JSONArray interchanges = jObj.getJSONArray("interchange");
        for (int i = 0; i < interchanges.length(); i++) {
            JSONArray ed = interchanges.getJSONArray(i);
            xStore.add(ed.getString(0));
            yStore.add(ed.getString(1));
            wStore.add(ed.getDouble(2));
            sStore.add("walk");

            xStore.add(ed.getString(1));
            yStore.add(ed.getString(0));
            wStore.add(ed.getDouble(2));
            sStore.add("walk");
        }

        JSONObject busFreqMap = jObj.getJSONObject("busFreq");
        Iterator<String> busNums = busFreqMap.keys();
        while (busNums.hasNext()) {
            String bus = busNums.next();
            ArrayList<Double> delays = new ArrayList<>();
            JSONArray jsArr = busFreqMap.getJSONArray(bus);
            for (int i = 0; i < jsArr.length(); i++) {
                delays.add(jsArr.getDouble(i));
            }
            busFreq.put(bus, delays);
        }

        JSONObject mrtFreqMap = jObj.getJSONObject("mrtFreq");
        Iterator<String> mrtLines = mrtFreqMap.keys();
        while (mrtLines.hasNext()) {
            String mrt = mrtLines.next();
            ArrayList<Double> delays = new ArrayList<>();
            JSONArray jsArr = mrtFreqMap.getJSONArray(mrt);
            for (int i = 0; i < jsArr.length(); i++) {
                delays.add(jsArr.getDouble(i));
            }
            mrtFreq.put(mrt, delays);
        }

        long dataParseTime = System.nanoTime();
        System.out.println("Process File: " + (dataParseTime - start) / 1000000 + "ms");

        for (String temp : nodes) {
            a.put(temp, new ArrayList<>());
        }

        for (int i = 0; i < xStore.size(); i++) { // Build graph
            a.get(xStore.get(i)).add(new Edge(yStore.get(i), wStore.get(i), sStore.get(i)));
        }

        Collections.sort(nearbyStart, new nodeDistComparator());
        Collections.sort(nearbyEnd, new nodeDistComparator());

        outputPaths.clear();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        int startUpperLimit = nearbyStart.size() > 5 ? 5 : nearbyStart.size();
        int endUpperLimit = nearbyEnd.size() > 5 ? 5 : nearbyEnd.size();
        for (int s = 0; s < startUpperLimit; s++) {
            for (int e = 0; e < endUpperLimit; e++) {
                //System.out.println(nearbyStart.get(s).node + " " + nearbyEnd.get(e).node);
                Map<String, Set<String>> tempNumPathToNode = new HashMap<>();
                for (String temp : nodes) {
                    tempNumPathToNode.put(temp, new HashSet<>());
                }
                double startWalkTime = nearbyStart.get(s).dist / 5;
                double lastWalkTime = nearbyEnd.get(e).dist / 5;
                executor.execute(new Thread(new MultiThreadDijkstra(startWalkTime, lastWalkTime, nearbyStart.get(s).node, nearbyEnd.get(e).node, tempNumPathToNode)));
            }
        }

        executor.shutdown();
        synchronized (executor) {
            try {
                executor.awaitTermination(60000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        executor.shutdownNow();

        System.out.println(routeToText().toString());
        System.out.println("Run Time: " + (System.nanoTime() - start) / 1000000000.0 + "s");
    }

    static List<Route> route(double startLat, double startLon, double endLat, double endLon, double maxWalkKm) {
        calculate(startLat, startLon, endLat, endLon, maxWalkKm);
        if (outputPaths.size() > 3) return outputPaths.subList(0, 3);
        return outputPaths;
    }

    static ArrayList<BusStopDetails> getNearestBS(double startLat, double startLon) {
        JSONObject busAdjList = jObj.getJSONObject("busAdjList");
        Iterator<String> it = busAdjList.keys();
        ArrayList<BusStopDetails> dists = new ArrayList<>();

        while (it.hasNext()) {
            String x = it.next();
            double lat = busAdjList.getJSONObject(x).getDouble("lat");
            double lon = busAdjList.getJSONObject(x).getDouble("lon");
            double d = calc_dist(startLon, startLat, lon, lat);
            if (d < 2.0) dists.add(new BusStopDetails(x,busAdjList.getJSONObject(x).getString("name"),busAdjList.getJSONObject(x).getString("road"),d));

        }

        Collections.sort(dists, new busStopDistComparator());

        ArrayList<BusStopDetails> busSt = new ArrayList<>();
        for (int i = 0; i < 10 && i < dists.size(); i++) {
            busSt.add(dists.get(i));
        }
        return busSt;
    }
}

class busDistComparator implements Comparator<String> {
    Map<String, Double> dists;

    public busDistComparator(Map<String, Double> a) {
        this.dists = a;
    }

    @Override
    public int compare(String s1, String s2) {
        return Double.compare(this.dists.get(s1), this.dists.get(s2));
    }
}