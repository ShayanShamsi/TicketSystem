package org.openmetromaps.maps;

import org.openmetromaps.maps.model.*;
import java.util.*;

class ReplacementServices {
    private List<Line> lines;
    private List<Station> stations;
    private int replacementLineCounter = 0;

    public ReplacementServices() {
        this.lines = new ArrayList<>();
        this.stations = new ArrayList<>();
    }

    // A1: Closing Stations
    public boolean closeStation(Station stationToClose, List<Line> selectedLines) {
        if (stationToClose == null || selectedLines == null || selectedLines.isEmpty()) {
            return false;
        }

        // Validate that the station exists in all selected lines
        for (Line line : selectedLines) {
            boolean stationFound = false;
            for (Stop stop : line.getStops()) {
                if (stop.getStation().equals(stationToClose)) {
                    stationFound = true;
                    break; // Exit the inner loop once the station is found
                }
            }
            if (!stationFound) {
                return false;
            }
        }

        for (Line line : selectedLines) {
            List<Station> lineStations = line.getStops().stream()
                .map(Stop::getStation)
                .collect(Collectors.toList());
            int stationIndex = lineStations.indexOf(stationToClose);

            // Skip if station not found in this line
            if (stationIndex == -1) continue;

            // Check if removing would leave less than 2 stations
            if (lineStations.size() <= 2) {
                return false;
            }

            // Handle terminal station case
            if (stationIndex == 0 || stationIndex == lineStations.size() - 1) {
                line.getStops().removeIf(stop -> stop.getStation().equals(stationToClose));
                continue;
            }

            // Regular case: connect previous and next stations
            Station prevStation = lineStations.get(stationIndex - 1);
            Station nextStation = lineStations.get(stationIndex + 1);
            line.getStops().removeIf(stop -> stop.getStation().equals(stationToClose));
        }

        // Check if station should be removed completely
        boolean stationStillInUse = lines.stream()
            .anyMatch(line -> line.getStops().stream()
                .anyMatch(stop -> stop.getStation().equals(stationToClose)));

        if (!stationStillInUse) {
            stations.remove(stationToClose);
        }

        return true;
    }

    // A2: Organizing Replacement Services
    public boolean organizeReplacementService(List<Station> selectedStations, List<Line> selectedLines) {
        if (selectedStations.size() < 2 || selectedLines.isEmpty()) {
            return false;
        }

        Station primaryBoundary = selectedStations.get(0);
        Station secondaryBoundary = selectedStations.get(selectedStations.size() - 1);

        // Validate consecutive stations in all selected lines
        for (Line line : selectedLines) {
            boolean hasConsecutiveStations = false;

            List<Station> lineStations = line.getStops().stream()
                .map(Stop::getStation)
                .collect(Collectors.toList());

            for (int i = 0; i < selectedStations.size() - 1; i++) {
                Station currentStation = selectedStations.get(i);
                Station nextStation = selectedStations.get(i + 1);

                if (lineStations.contains(currentStation) && lineStations.contains(nextStation)) {
                    int currentIndex = lineStations.indexOf(currentStation);
                    int nextIndex = lineStations.indexOf(nextStation);

                    if (Math.abs(currentIndex - nextIndex) == 1) {
                        hasConsecutiveStations = true;
                        break;
                    }
                }
            }

            if (!hasConsecutiveStations) {
                return false;
            }
        }

        // Create replacement line
        String replacementLineName;
        if (selectedLines.size() == 1) {
            replacementLineName = "P" + selectedLines.get(0).getName();
        } else {
            replacementLineCounter++;
            replacementLineName = "P-" + replacementLineCounter;
        }

        Line replacementLine = new Line(
            replacementLineName,
            "#009EE3",
            selectedStations
        );

        // Modify existing lines
        for (Line line : selectedLines) {
            List<Station> lineStations = line.getStops().stream()
                .map(Stop::getStation)
                .collect(Collectors.toList());

            boolean primaryIsTerminal = lineStations.get(0).equals(primaryBoundary) ||
                                        lineStations.get(lineStations.size() - 1).equals(primaryBoundary);
            boolean secondaryIsTerminal = lineStations.get(0).equals(secondaryBoundary) ||
                                        lineStations.get(lineStations.size() - 1).equals(secondaryBoundary);


            // Check if entire line is selected
            if (primaryIsTerminal && secondaryIsTerminal) {
                return false;
            }

            if (primaryIsTerminal || secondaryIsTerminal) {
                // Modify existing line
                Station nonTerminalBoundary = primaryIsTerminal ? secondaryBoundary : primaryBoundary;
                List<Station> newStations = new ArrayList<>();
                
                for (Stop stop : line.getStops()) {
                    Station station = stop.getStation();
                    if (!selectedStations.contains(station) || station.equals(nonTerminalBoundary)) {
                        newStations.add(station);
                    }
                }
                
                if (newStations.size() < 2) {
                    return false;
                }
                
                line = new Line(line.getName(), line.getColor(), newStations);
            } else {
                // Split line into two
                List<Station> stations1 = new ArrayList<>();
                List<Station> stations2 = new ArrayList<>();
                boolean reachedPrimary = false;
                
                for (Stop stop : line.getStops()) {
                    Station station = stop.getStation();
                    if (station.equals(primaryBoundary)) {
                        reachedPrimary = true;
                        stations1.add(station);
                        continue;
                    }
                    
                    if (!reachedPrimary) {
                        stations1.add(station);
                    } else if (!selectedStations.contains(station) || station.equals(secondaryBoundary)) {
                        stations2.add(station);
                    }
                }
                
                if (stations1.size() < 2 || stations2.size() < 2) {
                    return false;
                }
                
                Line line1 = new Line(line.getName() + "-1", line.getColor(), stations1);
                Line line2 = new Line(line.getName() + "-2", line.getColor(), stations2);
                
                lines.remove(line);
                lines.add(line1);
                lines.add(line2);
            }
        }

        lines.add(replacementLine);
        return true;
    }

    // A3: Introducing Alternative Replacement Services
    public boolean createAlternativeReplacementService(Station station1, Station station2) {
        if (station1 == null || station2 == null || station1.equals(station2)) {
            return false;
        }

        replacementLineCounter++;
        String replacementLineName = "P-" + replacementLineCounter;
        
        Line replacementLine = new Line(
            replacementLineName,
            "#009EE3",
            Arrays.asList(station1, station2)
        );
        
        lines.add(replacementLine);
        return true;
    }
    
    // Utility methods
    public void addLine(Line line) {
        lines.add(line);
    }
    
    public void addStation(Station station) {
        stations.add(station);
    }
    
    public List<Line> getLines() {
        return new ArrayList<>(lines);
    }
    
    public List<Station> getStations() {
        return new ArrayList<>(stations);
    }
}