package org.mountm.nfl_backtracking;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GAMSDistanceRunner {

    private static final String GAME_FILE = "Games.csv";
    private static final List<Game> gameList = new ArrayList<>();

    public static void main(String[] args) {
        readGameInputFile();
        List<Integer> gameTimes = gameList.stream().map(Game::getHourOfSeason).sorted().distinct().collect(Collectors.toList());
        Map<Integer, Set<Stadium>> stadiaByStartTime = new HashMap<>();
        for (Integer startTime : gameTimes) {
            stadiaByStartTime.put(startTime, gameList.stream().filter(g -> g.getHourOfSeason() == startTime).map(Game::getStadium).collect(Collectors.toSet()));
        }

        try {
            File file = new File("NFL.gams");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("Sets\n    s \"stadiums\" / ");
            bw.write(EnumSet.allOf(Stadium.class).stream().map(Enum::toString).collect(Collectors.joining(", ")));
            bw.write(" /\n");
            bw.write("    t \"times\" / " + gameTimes.stream().map(Object::toString).collect(Collectors.joining(", ")) + " /\n");
            bw.write("    g(t,s) \"games\"\n    legal(t,s,t,s)\n    magic(t,s,t,s)\n    illegal(t,s,t,s) / ");
            for (Game g1: gameList) {
                for (Game g2: gameList.subList(gameList.indexOf(g1) + 1, gameList.size())) {
                    if (g2.getHourOfSeason() > g1.getHourOfSeason() && !g1.canReachStrict(g2)) {
                        bw.write(g1.getHourOfSeason() + "." + g1.getStadium() + "." + g2.getHourOfSeason() + "." + g2.getStadium());
                        bw.newLine();
                    }
                }
            }
            bw.write(" / ;\n");
            bw.write("Alias (g,gg);\nAlias (s,ss);\nAlias (t,tt);\n");
            bw.write("Table g(t,s)\n             ");
            for (Stadium s : Stadium.values()) {
                String label = s + " ";
                if (label.length() == 3) {
                    label = " " + label;
                }
                bw.write(label);
            }
            for(Integer gameTime : gameTimes) {
                bw.newLine();
                String rowLabel = "    " + gameTime;
                while (rowLabel.length() < 13) {
                    rowLabel += " ";
                }
                bw.write(rowLabel);
                for (Stadium s : Stadium.values()) {
                    if (stadiaByStartTime.get(gameTime).contains(s)) {
                        bw.write("yes ");
                    } else {
                        bw.write("    ");
                    }
                }
            }
            bw.write(";\nTable travelTime(s,ss)\n         ");
            for (Stadium s : Stadium.values()) {
                String label = s + "  ";
                if (label.length() == 4) {
                    label = " " + label;
                }
                bw.write(label);
            }
            for (Stadium s1 : Stadium.values()) {
                bw.newLine();
                String label = "    " + s1 + " ";
                if (label.length() == 7) {
                    label += " ";
                }
                bw.write(label);
                for (Stadium s2 : Stadium.values()) {
                    int distance = s1.getMinutesTo(s2);
                    if (distance < 10) {
                        bw.write("   " + distance + " ");
                    } else if (distance < 100) {
                        bw.write("  " + distance + " ");
                    } else if (distance < 1000) {
                        bw.write(" " + distance + " ");
                    } else {
                        bw.write(distance + " ");
                    }
                }
            }
            bw.write(";\nmagic(g(t,s),gg(tt,ss))$(tt.val < t.val) = yes;\n" +
                    "magic(g(t,s),gg(tt,s)) = no;\n" +
                    "legal(g(t,s),gg(tt,ss))$(tt.val > t.val) = yes;\n" +
                    "legal(g(t,s),gg(tt,s)) = no;\n" +
                    "legal(illegal) = no;\n" +
                    "illegal(t,s,tt,ss) = not(magic(t,s,tt,ss) + legal(t,s,tt,ss));\n");
            bw.write("Binary Variables\n" +
                    "    x(t,s,t,s)\n" +
                    "Free Variables\n" +
                    "    mileage;\n" +
                    "Equations\n" +
                    "    efficiency\n" +
                    "    remove(t,s,t,s)         \"disallow illegal edges\"\n" +
                    "    leave(s)                \"depart each stadium once\"\n" +
                    "    arrive(s)               \"arrive each stadium once\"\n" +
                    "    balance(t,s)            \"arrive and depart each game equal number of times\"\n" +
                    "    subtour                 \"subtour elimination\";\n" +
                    "efficiency..                mileage =e= sum(legal(t,s,tt,ss),travelTime(s,ss)*x(legal));\n" +
                    "leave(s)..                  sum((t,gg) $ g(t,s),x(t,s,gg)) =e= 1;\n" +
                    "arrive(s)..                 sum((t,gg) $ g(t,s),x(gg,t,s)) =e= 1;\n" +
                    "balance(g)..                sum(gg,x(g,gg)) =e= sum(gg,x(gg,g));\n" +
                    "remove(illegal)..           x(illegal) =e= 0;\n" +
                    "subtour..                   sum(magic,x(magic)) =e= 1;\n" +
                    "Model nfl /all/  ;\n" +
//                    "nfl.optfile = 1 ;\n" +
                    "option reslim = 28500;\n" +
                    "option solprint = off ;\n" +
                    "Solve nfl using mip minimizing mileage;\n" +
                    "Display x.l  ;");

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
            DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormat = DateTimeFormat.forPattern("HH:mm");
            while ((currentLine = br.readLine()) != null) {
                gameData = currentLine.split(",");
                DateTime startTime = timeFormat.parseDateTime(gameData[2]);
                DateTime startDateTime = dateFormat.parseDateTime(gameData[1])
                        .withTime(LocalTime.fromMillisOfDay(startTime.getMillisOfDay()));
                Stadium stadium = Stadium.valueOf(gameData[0]);
                gameList.add(new Game(stadium, startDateTime));
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
}
