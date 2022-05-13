package org.mountm.nfl_backtracking;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class DurationMinimizationRunner {
    private static final String GAME_FILE = "Games.csv";

    private static final int DAYS_ALLOWED = 152;

    private static final List<Game> gameList = new ArrayList<>();
    
    public static void main(String[] args) {
        
        readGameInputFile();
        readDistanceData();

        int counter = 1;
        int firstGameDay = gameList.get(0).dayOfYear();
        int lastGameDay = gameList.get(gameList.size() - 1).dayOfYear();

        for (int startDay = firstGameDay; startDay <= lastGameDay - (DAYS_ALLOWED - 1); startDay++) {
            int index = 0;
            List<Game> gameRange = new ArrayList<>();
            while (gameList.get(index).dayOfYear() < startDay) {
                index++;
            }
            while (index < gameList.size() && startDay + DAYS_ALLOWED > gameList.get(index).dayOfYear()) {
                gameRange.add(gameList.get(index++));
            }
            // removeUnreachableGames(gameRange);
            try {
                String objective;
                StringBuilder constraint;
                String declaration;

                File file = new File("MLBTSP" + (counter++) + ".lp");

                if (!file.exists()) {
                    file.createNewFile();
                }

                FileWriter fw = new FileWriter(file.getAbsoluteFile());
                BufferedWriter bw = new BufferedWriter(fw);

                objective = "MINIMIZE lastDay - firstDay";
                bw.write(objective);
                bw.newLine();
                constraint = new StringBuilder("SUBJECT TO");
                bw.write(constraint.toString());
                bw.newLine();

                Map<Integer, List<Game>> gamesByStartTime = gameRange.stream().collect(Collectors.groupingBy(Game::getStartTime));

                // startTime must be before any game in the solution:
                // startTime + M*isVisited(g) <= g.getStartTime() + M
                // combining games with same start time for brevity
                // only works because you can't visit more than one of each such game, so the M's still cancel properly
                for (Map.Entry<Integer, List<Game>> entry : gamesByStartTime.entrySet()) {
                    constraint = new StringBuilder("firstDay");
                    for (Game g : entry.getValue()) {
                        constraint.append(" + 500 ").append(g.getTinyString());
                        if (constraint.length() > 500) {
                            bw.write(constraint.toString());
                            bw.newLine();
                            constraint = new StringBuilder();
                        }
                    }
                    constraint.append(" <= ").append(500 + entry.getKey());
                    bw.write(constraint.toString());
                    bw.newLine();
                }
                bw.newLine();

                // endTime must be after any game in the solution:
                // M*isVisited(g) - endTime <= M - (g.getStartTime() + gameLength)
                // combining games with same start time for brevity
                // only works because you can't visit more than one of each such game, so the M's still cancel properly
                for (Map.Entry<Integer, List<Game>> entry : gamesByStartTime.entrySet()) {
                    constraint = new StringBuilder();
                    for (Game g : entry.getValue()) {
                        constraint.append("500 ").append(g.getTinyString()).append(" + ");
                        if (constraint.length() > 500) {
                            bw.write(constraint.substring(0, constraint.length() - 3));
                            bw.newLine();
                            constraint = new StringBuilder(" + ");
                        }
                    }
                    bw.write(constraint.substring(0, constraint.length() - 3) + " - lastDay <= " + (500 - entry.getKey()));
                    bw.newLine();
                }
                bw.newLine();

                // each ballpark must be visited once
                // for each park, sum of all decision variables = 1
                for (Stadium s : Stadium.values()) {
                    constraint = new StringBuilder();
                    for (Game g : gameRange) {
                        if (g.getStadium() == s) {
                            constraint.append(g.getTinyString());
                            if (constraint.length() >= 500) {
                                bw.write(constraint.toString());
                                bw.newLine();
                                constraint = new StringBuilder();
                            }
                            constraint.append(" + ");
                        }
                    }
                    bw.write(constraint.substring(0, constraint.length() - 3) + " = 1");
                    bw.newLine();
                }
                bw.newLine();

                // if you can't get from one game to another, they can't
                // both be
                // in
                // the solution. This is a big section.
                // isVisited(g1) + isVisited(g2) <= 1
                for (Game g1 : gameRange) {
                    for (Game g2 : gameRange.subList(gameRange.indexOf(g1) + 1, gameRange.size())) {
                        if (!g1.canReachStrict(g2)) {
                            constraint = new StringBuilder(g1.getTinyString() + " + " + g2.getTinyString() + " <= 1");
                            bw.write(constraint.toString());
                            bw.newLine();
                        }
                    }
                }
                bw.newLine();

                declaration = "GENERAL";
                bw.write(declaration);
                bw.newLine();
                declaration = "firstDay";
                bw.write(declaration);
                bw.newLine();
                declaration = "lastDay";
                bw.write(declaration);
                bw.newLine();

                declaration = "BINARY";
                bw.write(declaration);
                bw.newLine();
                for (Game g : gameRange) {
                    bw.write(g.getTinyString());
                    bw.newLine();
                }
                bw.write("END");
                bw.newLine();

                bw.close();

                System.out.println("Done");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void removeUnreachableGames(List<Game> gameRange) {
        int firstGameDay = gameRange.get(0).dayOfYear();
        int lastGameDay = gameRange.get(gameRange.size() - 1).dayOfYear();
        int minRestDays = calculateRestDays(gameRange);
        // the minimum possible trip length is numTeams + numRestDays
        int minNumDays = Stadium.values().length + minRestDays;

        Set<Integer> earlyGameDays = IntStream.rangeClosed(firstGameDay, lastGameDay - minNumDays).boxed().collect(Collectors.toSet());
        Set<Integer> lateGameDays = IntStream.rangeClosed(firstGameDay + minNumDays, lastGameDay).boxed().collect(Collectors.toSet());

        if (gameRange.removeIf(g1 -> {
            if (earlyGameDays.contains(g1.dayOfYear())) {
                // early games are OK as long as you can go somewhere from them
                return gameRange.stream().noneMatch(g1::canReach);
            } else if (lateGameDays.contains(g1.dayOfYear())) {
                // late games are OK as long as you can get there from somewhere
                return gameRange.stream().noneMatch(g2 -> g2.canReach(g1));
            }
            // games in the middle need to be reachable and also reach somewhere else
            return gameRange.stream().noneMatch(g1::canReach) || gameRange.stream().noneMatch(g2 -> g2.canReach(g1));
        })) {
            removeUnreachableGames(gameRange);
        }

    }

    private static int calculateRestDays(List<Game> gameRange) {
        int firstGameDay = gameRange.get(0).dayOfYear();
        int lastGameDay = gameRange.get(gameRange.size() - 1).dayOfYear();
        int totalDays = lastGameDay - firstGameDay + 1;
        List<Integer> daysWithoutGames = IntStream.range(firstGameDay + 1, lastGameDay).filter(i -> gameRange.stream().noneMatch(g -> g.dayOfYear() == i)).boxed().sorted().collect(Collectors.toList());
        List<Integer> daysWithFewGames = new ArrayList<>();
        // if there is a possibility for additional rest days, only allow that when skipping a day with a small number of games
        int maxAdditionalRestDaysAllowed = totalDays - Stadium.values().length - daysWithoutGames.size();
        if (maxAdditionalRestDaysAllowed < 0) {
            System.out.println("Negative rest days allowed!");
        }
        if (maxAdditionalRestDaysAllowed > 0) {
            daysWithFewGames.addAll(IntStream.rangeClosed(firstGameDay, lastGameDay).filter(i -> gameRange.stream().filter(g -> g.dayOfYear() == i).count() < 20).boxed().sorted().collect(Collectors.toList()));
        }
        Map<Integer, Integer> restDaysAllowedPerGameDay = new HashMap<>();
        for (int i = firstGameDay; i < lastGameDay; i++) {
            int restDaysAllowed = 0;
            int dayToCheck = i + 1;
            while (daysWithoutGames.contains(dayToCheck)) {
                restDaysAllowed++;
                dayToCheck++;
            }
            while (restDaysAllowed < maxAdditionalRestDaysAllowed && daysWithFewGames.contains(dayToCheck)) {
                restDaysAllowed++;
                dayToCheck++;
            }
            restDaysAllowedPerGameDay.put(i, restDaysAllowed);
        }
        restDaysAllowedPerGameDay.put(lastGameDay, 0);
        Game.setMap(restDaysAllowedPerGameDay);
        return daysWithoutGames.size();
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
        File matrixFile = new File("distancematrix.txt");
        int[][] matrixData = new int[151][151];
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
