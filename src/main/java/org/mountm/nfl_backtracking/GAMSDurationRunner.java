package org.mountm.nfl_backtracking;

import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

public class GAMSDurationRunner {

    private static final String GAME_FILE = "Games.csv";
    private static final String DISTANCE_FILE = "distancematrix.txt";
    private static final int NUM_TEAMS = 149;

    private static final List<Game> gameList = new ArrayList<>();

    public static void main(String[] args) {
        readGameInputFile();
        Set<Integer> startTimes = gameList.stream().map(Game::getStartTime).collect(Collectors.toSet());
        Map<Integer, Set<Stadium>> stadiaByStartTime = new HashMap<>();
        for (Integer startTime : startTimes) {
            stadiaByStartTime.put(startTime, gameList.stream().filter(g -> g.getStartTime() == startTime).map(Game::getStadium).collect(Collectors.toSet()));
        }
        readDistanceData();
        try {

            File file = new File("MLB.gams");

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("Sets\n    s \"stadiums\" / ");
            bw.write(EnumSet.allOf(Stadium.class).stream().map(Enum::toString).collect(Collectors.joining(", ")));
            bw.write(" /\n");
            bw.write("    t \"times\" / " +startTimes.stream().map(Object::toString).collect(Collectors.joining(", ")) + " /\n");
            bw.write("    g(t,s) \"games\"\n    illegal(t,s,t,s) / ");
            for (Game g1: gameList) {
                for (Game g2: gameList.subList(gameList.indexOf(g1) + 1, gameList.size())) {
                    if (Minutes.minutesBetween(g1.getDate().plusMinutes(210), g2.getDate()).getMinutes() <= g1.getMinutesTo(g2)) {
                        bw.write(g1.getStartTime() + "." + g1.getStadium() + "." + g2.getStartTime() + "." + g2.getStadium());
                        bw.newLine();
                    }
                }
            }
            bw.write(" / ;\n");
            bw.write("Alias (g,gg);\nAlias (s,ss);\nAlias (t,tt);\nScalar M / 500000 /;\n");
            bw.write("Table g(t,s)\n             ");
            for (Stadium s : Stadium.values()) {
                bw.write(s + " ");
            }
            for(Map.Entry<Integer, Set<Stadium>> entry : stadiaByStartTime.entrySet()) {
                bw.newLine();
                String rowLabel = "    " + entry.getKey();
                while (rowLabel.length() < 13) {
                    rowLabel += " ";
                }
                bw.write(rowLabel);
                for (Stadium s : Stadium.values()) {
                    if (entry.getValue().contains(s)) {
                        bw.write("yes ");
                    } else {
                        bw.write("    ");
                    }
                }
            }
            bw.write(";\n");
            bw.write("Binary Variables\n    x(t,s)\nPositive Variables\n    end, start\nFree Variables\n    obj;\n");
            bw.write("Equations\n" +
                    "    defobj\n" +
                    "    first(t)                \"start before first game\"\n" +
                    "    last(t)                 \"end after last game\"\n" +
                    "    remove(t,s,t,s)         \"disallow illegal edges\"\n" +
                    "    visit(s)                \"depart each stadium once\"\n" +
                    "    subtour                 \"subtour elimination\";\n");
            bw.write("defobj..                    obj =e= end - start;\n" +
                    "efficiency..                mileage =e= sum(legal(t,s,tt,ss),travelTime(s,ss)*x(legal));\n" +
                    "first(t)..                  start =l= (M + t.val - sum(s $ g(t,s),x(t,s) * M));\n" +
                    "last(t)..                   (sum(s $ g(t,s),x(t,s) * M) - end) =l= (M - t.val);\n" +
                    "visit(s)..                  sum(t $ g(t,s),x(t,s)) =e= 1;\n" +
                    "remove(illegal(g,gg))..     x(g) + x(gg) =l= 1;\n");

            bw.write("Model mlb /all/  ;\nmlb.optfile = 1 ;\noption reslim = 28500;\nSolve mlb using mip minimizing obj;\nDisplay x.l  ;");
            bw.close();

            System.out.println("Done");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readGameInputFile() {
        BufferedReader br = null;

        try {
            String currentLine;
            String[] gameData;
            br = new BufferedReader(new FileReader(GAME_FILE));
            DateTimeFormatter dateFormat = DateTimeFormat.forPattern("MM/dd/yy hh:mm aa");
            int i = 1;
            while ((currentLine = br.readLine()) != null) {
                gameData = currentLine.split(",");
                DateTime startDate = dateFormat.parseDateTime(gameData[1]);
                Stadium stadium = Stadium.valueOf(gameData[0]);
                gameList.add(new Game(stadium, startDate, i++));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void readDistanceData() {
        File matrixFile = new File(DISTANCE_FILE);
        int[][] matrixData = new int[NUM_TEAMS][NUM_TEAMS];
        try {
            Scanner scanner = new Scanner(matrixFile);
            int i = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] entries = line.split(",");
                if (entries.length > 0) {
                    for (int j = 0; j < entries.length; j++) {
                        matrixData[i][j] = parseInt(entries[j], 10);
                    }
                }
                i++;
            }
            Stadium.setMinutesBetweenData(matrixData);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
