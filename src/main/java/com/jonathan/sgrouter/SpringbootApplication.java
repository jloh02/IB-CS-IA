package com.jonathan.sgrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

// mvn spring-boot:run -Drun.jvmArguments="-XX:MaxRAM=512m
// -XX:ActiveProcessorCount=2 -Xmx512m -XX:+UseSerialGC"

// startLon=103.749300&startLat=1.379323&endLon=103.892575&endLat=1.317978
// startLat=1.351653&startLon=103.864673&endLat=1.301839&endLon=103.781992
// Home to School
// startLon=103.864673&startLat=1.351653&endLon=103.988500&endLat=1.357178
// Home to Changi
// startLat=1.351653&startLon=103.864673&endLat=1.349766&endLon=103.873742
// Home to Serangoon


@SpringBootApplication // Uses springboot REST framework
@RestController
public class SpringbootApplication {
    private static SGRouter router; //User created class for routing
    private static long beforeUsedMem = 0;

    public static void main(String[] args) {
        //Variable to monitor memory usage
        beforeUsedMem = Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory();
        //Initializing SGRouter will run non-route specific code (Reading and
        // parsing of adjacency list file)
        router = new SGRouter();
        //Run initialization for Springboot Application
        SpringApplication.run(SpringbootApplication.class, args);
    }

    @GetMapping("/")
    //Empty function for application to ping to intiialize instance before
    // user queries
    public boolean home() {
        return true;
    }

    // Function to be run when /route is called
    @GetMapping("/route")
    //GET Parameters: startLat, startLon, endLat, endLon, maxWalkKm
    public String route(@RequestParam("startLat") double startLat,
                        @RequestParam("startLon") double startLon,
                        @RequestParam("endLat") double endLat,
                        @RequestParam("endLon") double endLon, @RequestParam(
            "maxWalkKm") double maxWalkKm) {
        //Debug prints to log
        System.out.println(
                Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).toString());
        //Call route member function which returns List of all routes
        List<Route> allRoutes =
                router.route(startLat, startLon, endLat, endLon, maxWalkKm);
        List<Route> output = new ArrayList<>(); //Final output list of 3 routes
        for (Route i : allRoutes) { //Iterate through all route produced
            // sorted by distance
            //Boolean to check for repeated paths indicated by the same
            // services taken in the same order
            boolean repeated = false;
            //Route member function to shorten paths and have a new array
            // indicating number of stops between paths
            i.compressPath();
            //Check against all existing output routes if services array is
            // repeated
            for (Route j : output) {
                if (i.service.equals(j.service)) {
                    repeated = true;
                }
            }
            if (!repeated) {
                output.add(i); //Add non-repeated routes
            }
            if (output.size() == 3) {
                break;  //Break once 3 routes have been added
            }
        }
        //Debug statements to monitor memory usage
        long afterUsedMem = Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory();
        System.out.println(
                "Mem used: " + (afterUsedMem - beforeUsedMem) + " B  (" +
                        (afterUsedMem - beforeUsedMem) / (1024.0 * 1024.0) +
                        " MB)");
        System.gc(); //Call garbage collection to conserve memory for future
        // operations
        return output
                .toString(); //Return routes in the form of string. Uses
        // Override toString function
    }

    @GetMapping("/nearestBusStop")
    public String nearestBusStop(@RequestParam("startLat") double startLat,
                                 @RequestParam("startLon") double startLon) {
        ArrayList<BusStopDetails> bs = router.getNearestBS(startLat, startLon);
        String codeOut = "[", nameOut = "[", roadOut = "[";
        for (int i = 0; i < bs.size(); i++) {
            codeOut += "\"" + bs.get(i).code + "\"";
            nameOut += "\"" + bs.get(i).name + "\"";
            roadOut += "\"" + bs.get(i).road + "\"";
            if (i != bs.size() - 1) {
                codeOut += ",";
                nameOut += ",";
                roadOut += ",";
            }
        }
        codeOut += "]";
        nameOut += "]";
        roadOut += "]";

        return String.format("{\"code\":%s,\"name\":%s,\"road\":%s}", codeOut,
                nameOut, roadOut);
    }
}