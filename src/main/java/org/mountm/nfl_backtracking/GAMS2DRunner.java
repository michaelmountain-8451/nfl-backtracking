package org.mountm.nfl_backtracking;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GAMS2DRunner {

    private static final String GAME_FILE = "Games.csv";
    private static final List<Game> gameList = new ArrayList<>();

    public static void main(String[] args) {
        readGameInputFile();
        List<Integer> gameTimes = gameList.stream().map(Game::getStartTime).sorted().distinct().collect(Collectors.toList());
        Map<Integer, List<Game>> gamesByStartTime = new HashMap<>();
        for (Integer startTime: gameTimes) {
            gamesByStartTime.put(startTime, gameList.stream().filter(g -> g.getStartTime() == startTime).collect(Collectors.toList()));
        }
        Map<Stadium, List<Game>> gamesByStadium = new HashMap<>();
        for (Stadium s : Stadium.values()) {
            gamesByStadium.put(s, gameList.stream().filter(g -> g.getStadium() == s).sorted().collect(Collectors.toList()));
        }
        int maxGameCount = gamesByStadium.values().stream().mapToInt(List::size).max().orElse(0);

        try {
            File file = new File("NFL2D.gams");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("Sets\n    s \"stadiums\" / ");
            bw.write(EnumSet.allOf(Stadium.class).stream().map(Enum::toString).collect(Collectors.joining(", ")));
            bw.write(" /\n");
            bw.write("    i \"game indices\" / 1 * " + maxGameCount + " /\n");
            bw.write("    g(i,s) \"games\" / #i.#s /\n    legal(i,s,i,s)\n    magic(i,s,i,s)\n    illegal(i,s,i,s) / ");
            String legalGames = "(";
            legalGames += gamesByStadium.entrySet().stream().map(e -> "1*" + e.getValue().size() + "." + e.getKey()).collect(Collectors.joining(",")) + ")";
            String illegalGames = "(";
            Set<Stadium> stadiaBelowMaxGameCount = gamesByStadium.entrySet().stream().filter(entry -> entry.getValue().size() < maxGameCount).map(Map.Entry::getKey).collect(Collectors.toSet());
            for (Stadium s: stadiaBelowMaxGameCount) {
                int numGames = gamesByStadium.get(s).size();
                if (numGames + 1 < maxGameCount) {
                    illegalGames += ((numGames + 1) + " * " + maxGameCount + "." + s);
                } else {
                    illegalGames += (maxGameCount + "." + s);
                }
                illegalGames += ",";
            }
            illegalGames = illegalGames.substring(0, illegalGames.length() - 1) + ")";
            bw.write(illegalGames + "." + legalGames + ",\n#i.#s." + illegalGames);
            bw.newLine();
            bw.write(" / ;\n");
            bw.write("Alias (g,gg);\nAlias (s,ss);\nAlias (i,ii);\nScalar gameLength / 240 / ;\nScalar overnight / 700 / ;\n");
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
            bw.write(";\nParameter startTime(i,s)\n/ ");
            for (Integer startTime : gameTimes) {
                List<Game> games = gamesByStartTime.get(startTime);
                if (games.size() == 1) {
                    Game g = games.get(0);
                    bw.write((gamesByStadium.get(g.getStadium()).indexOf(g) + 1) + "." + g.getStadium());
                } else {
                    String entries = "(";
                    entries += games.stream().map(g -> (gamesByStadium.get(g.getStadium()).indexOf(g) + 1) + "." + g.getStadium()).collect(Collectors.joining(",")) + ")";
                    bw.write(entries);
                }
                bw.write("  =  " + startTime);
                bw.newLine();
            }
            // magic arcs are any going backwards in time
            bw.write("/ ;\nmagic(i,s,ii,ss)$(startTime(ii,ss) < startTime(i,s)) = yes;\n" +
                    // but not if they connect the same stadium
                    "magic(i,s,ii,s) = no;\n" +
                    // and not if they are illegal (i.e. game indices that don't exist)
                    "magic(illegal) = no;\n"+
                    // legal arcs connect forwards in time as long as the travel time is shorter than the difference between start times
                    // or if the two games are on different days, then a flight is OK (but you still pay the travel cost in the objective function)
                    "legal(i,s,ii,ss)$(startTime(ii,ss) > startTime(i,s) AND ((travelTime(s,ss) < (startTime(ii,ss) - startTime(i,s) - gameLength)) OR overnight < (startTime(ii,ss) - startTime(i,s)))) = yes;\n" +
                    // but not if they connect the same stadium
                    "legal(i,s,ii,s) = no;\n" +
                    // and not if they are illegal (i.e. game indices that don't exist)
                    "legal(illegal) = no;\n" +
                    // finally, mark any other arcs as illegal
                    "illegal(i,s,ii,ss) = not(magic(i,s,ii,ss) + legal(i,s,ii,ss));\n");
            bw.write("Binary Variables\n" +
                    "    x(i,s,i,s)\n" +
                    "Free Variables\n" +
                    "    mileage;\n" +
                    "Equations\n" +
                    "    efficiency\n" +
                    "    remove(i,s,i,s)         \"disallow illegal edges\"\n" +
                    "    leave(s)                \"depart each stadium once\"\n" +
                    "    arrive(s)               \"arrive each stadium once\"\n" +
                    "    balance(i,s)            \"arrive and depart each game equal number of times\"\n" +
                    "    subtour                 \"subtour elimination\";\n" +
                    "efficiency..                mileage =e= sum(legal(i,s,ii,ss),travelTime(s,ss)*x(legal));\n" +
                    "leave(s)..                  sum((i,gg),x(i,s,gg)) =e= 1;\n" +
                    "arrive(s)..                 sum((i,gg),x(gg,i,s)) =e= 1;\n" +
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
