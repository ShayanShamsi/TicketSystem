package org.openmetromaps.maps;

import org.openmetromaps.maps.model.Line;
import org.openmetromaps.maps.model.ModelData;
import org.openmetromaps.maps.model.Station;

import java.util.List;

public class SemanticModelEquality {
    public static boolean modelsSemanticallyEqual(ModelData dataA, ModelData dataB) {
        return
            dataA.stations.stream().allMatch(stationA -> dataB.stations.stream().anyMatch(stationB -> stationsSemanticallyEqual(stationA, stationB))) &&
            dataA.lines.stream().allMatch(lineA -> dataB.lines.stream().anyMatch(lineB -> linesSemanticallyEqual(lineA, lineB)));
    }

    private static boolean stationsSemanticallyEqual(Station stationA, Station stationB) {
        List<String> stationALines = stationA.getStops().stream().map(s -> s.getLine().getName()).sorted().toList();
        List<String> stationBLines = stationB.getStops().stream().map(s -> s.getLine().getName()).sorted().toList();

        return
            stationA.getName().equals(stationB.getName()) &&
            doubleEqual(stationA.getLocation().getLatitude(), stationB.getLocation().getLatitude()) &&
            doubleEqual(stationA.getLocation().getLongitude(), stationB.getLocation().getLongitude()) &&
            stationALines.equals(stationBLines);
    }

    private static boolean linesSemanticallyEqual(Line lineA, Line lineB) {
        List<String> lineAStops = lineA.getStops().stream().map(s -> s.getStation().getName()).toList();
        List<String> lineBStops = lineB.getStops().stream().map(s -> s.getStation().getName()).toList();
        List<String> lineBStopsReversed = List.copyOf(lineBStops).reversed();

        return
            lineA.getName().equals(lineB.getName()) &&
            lineA.getColor().equals(lineB.getColor()) &&
            lineAStops.equals(lineBStops) || lineAStops.equals(lineBStopsReversed);
    }

    private static boolean doubleEqual(double d1, double d2) {
        return Math.abs(d1 - d2) < 0.0001;
    }
}
