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

// mvn spring-boot:run -Drun.jvmArguments="-XX:MaxRAM=512m -XX:ActiveProcessorCount=2 -Xmx512m -XX:+UseSerialGC"

// startLon=103.749300&startLat=1.379323&endLon=103.892575&endLat=1.317978
// startLat=1.351653&startLon=103.864673&endLat=1.301839&endLon=103.781992 //Home to School
// startLon=103.864673&startLat=1.351653&endLon=103.988500&endLat=1.357178 //Home to Changi
// startLat=1.351653&startLon=103.864673&endLat=1.349766&endLon=103.873742 //Home to Serangoon

@SpringBootApplication
@RestController
public class SpringbootApplication {
    private static SGRouter router;
    private static long beforeUsedMem = 0;

    public static void main(String[] args) {
        beforeUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        router = new SGRouter();
        SpringApplication.run(SpringbootApplication.class, args);
    }

    @GetMapping("/")
    public String home() {
        return "Root";
    }

    @GetMapping("/route")
    public String route(@RequestParam("startLat") double startLat, @RequestParam("startLon") double startLon, @RequestParam("endLat") double endLat, @RequestParam("endLon") double endLon) {
        System.out.println(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).toString());
        List<Route> allRoutes = router.route(startLat, startLon, endLat, endLon, 0.3);
        ArrayList<Route> output = new ArrayList<>();
        for (Route i : allRoutes) {
            boolean repeated = false;
            i.compressPath();
            for (Route j : output) {
                if (i.service.equals(j.service)) repeated = true;
            }
            if (!repeated) output.add(i);
            if (output.size() == 3) break;
        }
        long afterUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Mem used: " + (afterUsedMem - beforeUsedMem) + " B  (" + (afterUsedMem - beforeUsedMem) / (1024.0 * 1024.0) + " MB)");
        System.gc();
        return output.toString();
    }

    @GetMapping("/nearestBusStop")
    public String nearestBusStop(@RequestParam("startLat") double startLat, @RequestParam("startLon") double startLon) {
        ArrayList<BusStopDetails> bs = router.getNearestBS(startLat,startLon);
        String codeOut="[",nameOut="[",roadOut="[";
        for(int i=0;i<bs.size();i++){
            codeOut+= "\"" + bs.get(i).code + "\"";
            nameOut+= "\"" + bs.get(i).name + "\"";
            roadOut+= "\"" + bs.get(i).road + "\"";
            if(i != bs.size()-1) {
                codeOut += ",";
                nameOut += ",";
                roadOut += ",";
            }
        }
        codeOut += "]";
        nameOut += "]";
        roadOut += "]";

        return String.format("{\"code\":%s,\"name\":%s,\"road\":%s}",codeOut,nameOut,roadOut);
    }
}