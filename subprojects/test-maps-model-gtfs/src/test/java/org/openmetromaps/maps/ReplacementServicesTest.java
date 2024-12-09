package org.openmetromaps.maps;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openmetromaps.gtfs.DraftModel;
import org.openmetromaps.gtfs.GtfsImporter;
import org.openmetromaps.maps.model.Line;
import org.openmetromaps.maps.model.ModelData;
import org.openmetromaps.maps.model.Station;
import org.openmetromaps.maps.model.Stop;
import org.openmetromaps.misc.NameChanger;
import org.openmetromaps.model.gtfs.DraftModelConverter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class ReplacementServicesTest {

    @Parameterized.Parameter(0)
    public String path;

    private static Collection<String> userTests() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = Objects.requireNonNull(loader.getResource("./replacement-services"));

        return Arrays.stream(Objects.requireNonNull(new File(url.getPath()).listFiles()))
                .filter(
                        file -> file.exists() &&
                                file.isDirectory() &&
                                Arrays.stream(Objects.requireNonNull(file.listFiles())).anyMatch(f -> f.getName().equals("arrange.zip"))
                )
                .map(File::getName)
                .toList();
    }

    @Parameterized.Parameters(name = "ID: {0}")
    public static Collection<Object[]> data() {
        Collection<String> userTests = userTests();

        return userTests.stream()
                .flatMap(test -> Stream.of(Arrays.asList(test)))
                .map(List::toArray).toList();
    }

    @Test(timeout = 2000)
    public void testModelsEqual() throws IOException {
        Path basePath = Path.of("src/test/resources/replacement-services/" + path);
        Path inputPath = basePath.resolve("arrange.zip");
        if(!Files.exists(inputPath)) {
            throw new IllegalArgumentException(path + " - arrange.zip not found in folder: " + inputPath);
        }
        Path expectedPath = basePath.resolve("assert.zip");
        if(!Files.exists(expectedPath)) {
            throw new IllegalArgumentException(path + " - assert.txt not found in folder: " + expectedPath);
        }
        Path operationPath = basePath.resolve("act.txt");
        if(!Files.exists(operationPath)) {
            throw new IllegalArgumentException(path + " - act.txt not found in folder: " + operationPath);
        }

        Path commentPath = basePath.resolve("comment.txt");
        if(!Files.exists(commentPath)) {
            throw new IllegalArgumentException(path + " - comment.txt not found in folder: " + commentPath);
        }

        if (Files.readString(commentPath).isBlank()) {
            throw new IllegalArgumentException(path + " - comment.txt is empty");
        }

        ModelData inputModel;
        try {
            inputModel = importModelFromGtfs(inputPath);
        } catch (Exception e){
            throw new IllegalArgumentException(path + " - Failed to import input model from GTFS: " + e.getMessage());
        }

        try {
            importModelFromGtfs(expectedPath);
        } catch (Exception e){
            throw new IllegalArgumentException(path + " - Failed to import expected model from GTFS: " + e.getMessage());
        }

        String[] linesInFile = Files.readString(operationPath).split("\n");

        // Validate the lines in file, and report the row and column of the error.
        for (int row = 0; row < linesInFile.length; row++) {
            String line = linesInFile[row];

            // Position of the first non-matching character (with respect to "[\p{L}\p{N};-_ \r\n]") or -1
            int column = IntStream.range(0, line.length())
                    .filter(i -> {
                        char c = line.charAt(i);
                        return !Character.isLetterOrDigit(c) && c != ';' && c != '_' && c != '-' && c != ' ' && c != '\r' && c != '\n';
                    })
                    .findFirst()
                    .orElse(-1);
            if (column != -1) {
                System.out.printf("Invalid character '%c' in line %d at position %d (only alphanumeric characters, semicolons, underscores, dashes, spaces, and newlines are allowed).%n", line.charAt(column), row + 1, column + 1);

                String inflatedSeparator = "-".repeat(line.length());
                System.out.println(inflatedSeparator);
                System.out.printf("%s%n", line);
                System.out.printf("%s^%n", " ".repeat(column));
                System.out.println(inflatedSeparator);

                throw new IllegalArgumentException(path + " - Invalid character in operation file");
            }
        }

        List<List<String>> argumentLines = Arrays.stream(linesInFile)
                .map(line -> Arrays.asList(line.split(";")))
                .collect(Collectors.toList());
        if (argumentLines.isEmpty() || argumentLines.get(0).isEmpty()) {
            // Handle error - no operations found
            throw new IllegalArgumentException(path + " - No operations found");
        }

        String operation = argumentLines.get(0).get(0).trim();
        argumentLines.set(0, argumentLines.get(0).subList(1, argumentLines.get(0).size()));

        switch (operation) {
            case "CLOSE": {
                List<String> arguments = argumentLines.get(0);
                String stationName = arguments.get(0).trim();

                Station station = inputModel.stations.stream()
                        .filter(s -> s.getName().equals(stationName))
                        .findFirst()
                        .orElse(null);
                if (station != null) {
                    // Get the lines from the operation
                    Set<Line> linesAtStation = new HashSet<>();

                    // Iterate through the stops of the station
                    for (Stop stop : station.getStops()) {
                        Line line = stop.getLine();
                        linesAtStation.add(line);
                    }
                    for (int i = 1; i < arguments.size(); i++) {
                        String lineName = arguments.get(i).trim();
                        if (lineName.isEmpty()) {
                            // Handle error - line name is empty
                            throw new IllegalArgumentException(path + " - Line name is empty");
                        }

                        Line line = linesAtStation.stream()
                                .filter(l -> l.getName().equals(lineName))
                                .findFirst()
                                .orElse(null);
                        if (line == null) {
                            // Handle error - line not found
                            throw new IllegalArgumentException(path + " - Line not found: " + arguments.get(i));
                        }
                    }
                } else {
                    // Handle error - station not found
                    throw new IllegalArgumentException(path + " - Station not found: " + stationName);
                }
                break;
            }
            case "REPLACEMENT": {
                List<String> stationArguments = argumentLines.get(1);
                List<String> lineArguments = argumentLines.get(2);

                for (String stationArgument : stationArguments) {
                    String stationName = stationArgument.trim();
                    if (stationName.isEmpty()) {
                        // Handle error - station name is empty
                        throw new IllegalArgumentException(path + " - Station name is empty");
                    }

                    Station station = inputModel.stations.stream()
                            .filter(l -> l.getName().equals(stationName))
                            .findFirst()
                            .orElse(null);
                    if (station == null) {
                        // Handle error - station not found
                        throw new IllegalArgumentException(path + " - Station not found: " + stationName);
                    }
                }

                for (String lineArgument : lineArguments) {
                    String lineName = lineArgument.trim();
                    if (lineName.isEmpty()) {
                        // Handle error - line name is empty
                        throw new IllegalArgumentException(path + " - Line name is empty");
                    }

                    Line line = inputModel.lines.stream()
                            .filter(l -> l.getName().equals(lineName))
                            .findFirst()
                            .orElse(null);
                    if (line == null) {
                        // Handle error - line not found
                        throw new IllegalArgumentException(path + " - Line not found: " + lineName);
                    }
                }
                break;
            }
            case "ALTERNATIVE": {
                List<String> arguments = argumentLines.get(0);

                String stationNameA = arguments.get(0).trim();
                String stationNameB = arguments.get(1).trim();

                Station stationA = inputModel.stations.stream()
                        .filter(s -> s.getName().equals(stationNameA))
                        .findFirst()
                        .orElse(null);
                Station stationB = inputModel.stations.stream()
                        .filter(s -> s.getName().equals(stationNameB))
                        .findFirst()
                        .orElse(null);

                if (stationA == null || stationB == null) {
                    // Handle error - one or both stations not found
                    if (stationA == null && stationB == null) {
                        throw new IllegalArgumentException(path + " - Stations not found: " + stationNameA + ", " + stationNameB);
                    }
                    else if (stationA == null) {
                        throw new IllegalArgumentException(path + " - Station not found: " + stationNameA);
                    }
                    else {
                        throw new IllegalArgumentException(path + " - Station not found: " + stationNameB);
                    }
                }
                break;
            }
            default:
                // Handle unsupported operation
                throw new IllegalArgumentException(path + " - Unsupported operation: " + operation);
        }
    }

    private ModelData importModelFromGtfs(Path pathInput) throws IOException {
        GtfsImporter importer = new GtfsImporter(
                pathInput,
                new NameChanger(new ArrayList<>(), new ArrayList<>()),
                false
        );
        importer.execute();

        DraftModel draft = importer.getModel();
        ModelData data = new DraftModelConverter().convert(draft);
        return data;
    }
}
