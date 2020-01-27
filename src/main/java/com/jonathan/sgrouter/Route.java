package com.jonathan.sgrouter;

import org.json.JSONObject;

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

    boolean isMRT(String service) {
        return (service.charAt(0) >= 'A' && service.charAt(0) <= 'z' && !service.contains("CT") && !service.contains("BPS"));
    }

    public String toString() {
        ArrayList<String> outPath = new ArrayList<>(), outService = new ArrayList<>();
        ArrayList<Double> outWeight = new ArrayList<>();
        ArrayList<Integer> outNumStop = new ArrayList<>();

        int n = this.path.size();
        int countStops = 0;
        String prevServ = "walk";
        double prevWeight = 0;
        for (int i = 0; i < n; i++) {
            countStops++;
            if (!this.service.get(i).equals(prevServ)) {
                if (prevServ == "walk")
                    outService.add("walk");
                else if (isMRT(prevServ))
                    outService.add(this.path.get(i - 1).substring(0, 2) + this.service.get(i -1).substring(this.service.get(i -1).indexOf("("),this.service.get(i -1).indexOf(")")+1));
                else
                    outService.add(this.service.get(i - 1));

                outPath.add(this.path.get(i - 1));
                outWeight.add(this.weightPath.get(i - 1) - prevWeight);
                outNumStop.add(countStops);

                prevWeight = this.weightPath.get(i - 1);
                countStops = 0;
            }
            prevServ = this.service.get(i);
        }

        String pathStr, serviceStr;
        pathStr = serviceStr = "[";
        for (int i = 0; i < outPath.size(); i++) {
            pathStr += "\"" + outPath.get(i) + "\"";
            serviceStr += "\"" + outService.get(i) + "\"";
            if (i != outPath.size() - 1) {
                pathStr += ",";
                serviceStr += ",";
            }
        }
        pathStr += "]";
        serviceStr += "]";
        return String.format("{\"path\":%s,\"weight\":%s,\"service\":%s,\"stops\":%s}", pathStr, outWeight.toString(), serviceStr, outNumStop.toString());
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