package org.mountm.nfl_backtracking;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class BacktrackingRunner {

    private static final int NUMBER_OF_TEAMS = Stadium.values().length;
    private static final String GAME_FILE = "Games.csv";
    private static final String BEST_SOLUTION_FILE_NAME = "bestSolution.dat";
    private static final String NO_EXTENSIONS_FILE_NAME = "noExtensions.dat";
    private static final String SHORTEST_PATH_FILE_NAME = "shortestPath.dat";
    private static final int ALL_STAR_BREAK_START_DAY = 198;
    private static final int ALL_STAR_BREAK_END_DAY = 202;
    private static final Map<Integer, EnumSet<Stadium>> stadiumsPerGameDay = new HashMap<>();

    private static int maxNumDays = NUMBER_OF_TEAMS;
    private static int bestTripLength = Integer.MAX_VALUE;
    private static final TShortObjectMap<Set<Game>> missedStadiums = new TShortObjectHashMap<>(NUMBER_OF_TEAMS);
    private static TShortObjectMap<Set<EnumSet<Stadium>>> noExtensions = new TShortObjectHashMap<>();
    private static TShortObjectMap<TObjectIntMap<EnumSet<Stadium>>> shortestPath = new TShortObjectHashMap<>();
    private static final List<Game> games = new ArrayList<>();
    private static int maxSize = 0;
    private static List<Game> bestSolution = new ArrayList<>(NUMBER_OF_TEAMS);
    private static boolean foundSolution = false;


    public static void main(String[] args) {

        readGameInputFile();
        readDistanceData();

        bestTripLength = parseInt(args[0]);

        maxNumDays = parseInt(args[1]);


        List<Game> partial = new ArrayList<>(NUMBER_OF_TEAMS);
        for (int i = 2; i < args.length; i++) {
            Game g = games.get(parseInt(args[i]));
            if (haveVisitedStadium(partial, g.getStadium())) {
                System.out.println("Trying to visit " + g.getStadium() + " twice! " + args[i]);
                return;
            }
            partial.add(g);
        }

        // System.out.println(partial);
        printNextDayCandidates(partial);

        if (verifyInitialData(partial)) {

            // initialize the missedStadiums collection
            if (partial.isEmpty()) {
                recalculateFailureCriteria(0);
                calculateRestDays(games.get(0));
            } else {
                recalculateFailureCriteria(games.indexOf(partial.get(0)));
                calculateRestDays(partial.get(0));
            }

            readPruningData();
            readPathData();
            if (badSolution(partial)) {
                System.out.println("Infeasible starting point.");
                return;
            }
            readBestSolution();

            // decrement maxSize once per minute to increase output
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                public void run() {
                    if (maxSize > 0) {
                        maxSize--;
                    }
                }
            }, 60000, 60000);

            backtrack(partial);
            writePathData();
            if (!foundSolution) {
                writePruningData();
            }
            System.out.println(partial);
            if (!bestSolution.isEmpty()) {
                printSolution(bestSolution);
                System.out.println(tripLength(bestSolution));
            }
        }

    }

    private static void printNextDayCandidates(List<Game> partial) {
        if (partial.isEmpty()) return;
        EnumSet<Stadium> visitedStadiums = visitedStadia(partial);

        Game lastGame = partial.get(partial.size() - 1);

        int lastDay = lastGame.dayOfYear();
        int nextDay = lastDay + 1;
        List<Integer> distanceToNextDaysGames = games.stream().filter(g -> g.dayOfYear() == nextDay && !visitedStadiums.contains(g.getStadium()) && lastGame.canReachStrict(g)).mapToInt(lastGame::getMinutesTo).distinct().sorted().boxed().collect(Collectors.toList());
        Map<Integer, List<Game>> nextDaysGamesByDistance = games.stream().filter(g -> g.dayOfYear() == nextDay && !visitedStadiums.contains(g.getStadium()) && lastGame.canReachStrict(g)).collect(Collectors.groupingBy(lastGame::getMinutesTo));
        for (int i : distanceToNextDaysGames) {
            System.out.println(i + ": " + nextDaysGamesByDistance.get(i));
        }
    }

    private static void readGameInputFile() {
        BufferedReader br = null;

        try {
            String currentLine;
            String[] gameData;
            br = new BufferedReader(new FileReader(GAME_FILE));
            DateTimeFormatter dateFormat = DateTimeFormat.forPattern("MM/dd/yy hh:mm aa");
            while ((currentLine = br.readLine()) != null) {
                gameData = currentLine.split(",");
                DateTime startDate = dateFormat.parseDateTime(gameData[1]);
                Stadium stadium = Stadium.valueOf(gameData[0]);
                games.add(new Game(stadium, startDate));
                if (startDate.getDayOfYear() != ALL_STAR_BREAK_END_DAY) {
                    stadiumsPerGameDay.merge(startDate.getDayOfYear(), EnumSet.of(stadium), (prev, next) -> {
                        prev.addAll(next);
                        return prev;
                    });
                }
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

    private static boolean verifyInitialData(List<Game> partial) {
        if (partial.size() < 2) {
            return true;
        }
        for (int i = 0; i < partial.size() - 1; i++) {
            if (!partial.get(i).canReachStrict(partial.get(i + 1))) {
                System.out.println("Can't get from " + partial.get(i) + " to " + partial.get(i + 1));
                return false;
            }
        }
        return true;
    }


    @SuppressWarnings("unchecked")
    private static void readBestSolution() {
        try {
            FileInputStream fis = new FileInputStream(BEST_SOLUTION_FILE_NAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            bestSolution = (List<Game>) ois.readObject();
            ois.close();
            if (travelDays(bestSolution) > maxNumDays || tripLength(bestSolution) > bestTripLength) {
                bestSolution.clear();
            } else {
                bestTripLength = tripLength(bestSolution);
            }
        } catch (FileNotFoundException e) {
            System.out.println("No best solution file found");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // missedStadiums stores information about the latest point in time at which
    // each stadium can be visited. The keys are days of the year. The values
    // are a set of games. There is one game for each stadium that must have
    // been visited by the key date.
    //
    // noExtensions also depends on the starting date of all partial solutions
    // being the same. It must be cleared when the starting date changes.
    private static void recalculateFailureCriteria(int index) {
        noExtensions.clear();
        missedStadiums.clear();
        shortestPath.clear();
        Game[] lastGameHere = new Game[NUMBER_OF_TEAMS];
        Game g = games.get(index++);
        int lastDay = g.dayOfYear() + maxNumDays;
        int firstDay = g.dayOfYear();
        while (g.dayOfYear() < lastDay) {
            lastGameHere[g.stadiumIndex()] = g;
            if (index < games.size()) {
                g = games.get(index++);
            } else break;

        }
        for (int i = 0; i < maxNumDays; i++) {
            Set<Game> mapEntry = new HashSet<>(NUMBER_OF_TEAMS);
            for (int j = 0; j < NUMBER_OF_TEAMS; j++) {
                if (lastGameHere[j].dayOfYear() - firstDay <= i) {
                    mapEntry.add(lastGameHere[j]);
                }
            }
            missedStadiums.put((short) (firstDay + i), mapEntry);
        }
    }

    // standard backtracking algorithm - just added the printPartial logic after
    // returning from a bad solution.
    private static void backtrack(List<Game> partial) {
        if (badSolution(partial)) {
            return;
        }
        if (validSolution(partial)) {
            processSolution(partial);

        } else if (maxSize < partial.size()) {
            maxSize = partial.size();
            printPartial(partial);
        }
        partial = firstExtension(partial);
        while (partial != null) {
            backtrack(partial);
            partial = nextExtension(partial);
        }
    }

    private static boolean badSolution(List<Game> partial) {
        if (travelDays(partial) > maxNumDays
                || (foundSolution && isExcessiveTripLength(partial))) {
            return true;
        }

        if (validSolution(partial) || partial.isEmpty()) {
            return false;
        }

        if (maxNumDays < NUMBER_OF_TEAMS + getRestDays(partial)) {
            return true;
        }

        Game last = partial.get(partial.size() - 1);

        // Check if any stadiums are missing that must be present based on
        // the time limits (i.e. teams leaving for a long road trip).
        Set<Game> lastGamePerStadium = missedStadiums.get((short) last.dayOfYear());
        if (lastGamePerStadium.stream().anyMatch(g -> !haveVisitedStadium(partial, g.getStadium()) && !last.canReachStrict(g))) {
            return true;
        }

        // Finally, check to see if an equivalent path was already discarded
        short key = (short) games.indexOf(last);
        return didEvaluateEquivalentPath(key, partial);
    }

    private static boolean didEvaluateEquivalentPath(short key, List<Game> partial) {
        if (noExtensions.containsKey(key)) {
            Set<EnumSet<Stadium>> previouslyConsidered = noExtensions.get(key);
            EnumSet<Stadium> visitedStadia = visitedStadia(partial);
            return previouslyConsidered.stream().anyMatch(s -> s.containsAll(visitedStadia));
        }
        return false;
    }

    private static boolean isExcessiveTripLength(List<Game> partial) {
        int tripLength = tripLength(partial);
        if  (tripLength > bestTripLength) {
            return true;
        }

        short key = (short) games.indexOf(partial.get(partial.size() - 1));
        if (shortestPath.containsKey(key)) {
            EnumSet<Stadium> visitedStadia = visitedStadia(partial);
            // forEach returns false if the iteration terminated early
            return !shortestPath.get(key).forEachEntry((stadia, length) -> {
                // execute returns true if additional operations are allowed;
                // i.e. if the entry we're testing was not a match for the new partial solution
                // the candidate is bad if it has a longer trip length than this entry value
                // while visiting the same stadiums or fewer
                return !(tripLength > length && stadia.containsAll(visitedStadia));
            });
        }
        return false;
    }

    private static void updatePathData(List<Game> partial) {
        for (int i = 2; i < partial.size(); i++) {
            insertPathDataFromSubList(partial.subList(0, i + 1));
        }
        writePathData();
    }

    // shortestPath contains data about trip length for previously considered partial solutions.
    // If a candidate ends at the same game and visits the same stadiums (or a subset of stadiums)
    // of a previously considered candidate, and does so with a longer trip length, then it is not a better solution.
    // shortestPath structure: Map(Short, Map(EnumSet, Integer))
    // Short value is the index of the last game in the candidate - must match
    // EnumSet key of the inner map is the set of stadia visited - must be a superset of the candidate stadium set
    // Value is the shortest known trip length visiting exactly those stadia and ending at the given game.
    private static void insertPathDataFromSubList(List<Game> subList) {
        short key = (short) games.indexOf(subList.get(subList.size() - 1));
        EnumSet<Stadium> visitedStadia = visitedStadia(subList);
        int tripLength = tripLength(subList);

        TObjectIntMap<EnumSet<Stadium>> innerMap;

        if (shortestPath.containsKey(key)) {
            innerMap = shortestPath.get(key);
            if (!innerMap.containsKey(visitedStadia) || innerMap.get(visitedStadia) > tripLength) {
                innerMap.put(visitedStadia, tripLength);
            }
        } else {
            innerMap = new TObjectIntHashMap<>();
            innerMap.put(visitedStadia, tripLength);
            shortestPath.put(key, innerMap);
        }
    }

    private static EnumSet<Stadium> visitedStadia(List<Game> partial) {
        EnumSet<Stadium> retVal = EnumSet.noneOf(Stadium.class);
        retVal.addAll(partial.stream().map(Game::getStadium).collect(Collectors.toUnmodifiableSet()));
        return retVal;
    }


    // number of days without seeing a game
    private static int getRestDays(List<Game> partial) {
        if (partial.isEmpty()) {
            return 0;
        }
        int daysOff = 0;
        for (int i = 0; i < partial.size() - 1; i++) {
            if (partial.get(i).dayOfYear() + 1 < partial.get(i + 1).dayOfYear()) {
                daysOff += (partial.get(i + 1).dayOfYear() - partial.get(i).dayOfYear()) - 1;
            }
        }
        Set<Stadium> visited = visitedStadia(partial);
        Game lastGame = partial.get(partial.size() - 1);
        int firstGameDay = partial.get(0).dayOfYear();
        int lastGameDay = lastGame.dayOfYear();
        // all-star break involves a minimum of one rest day (LAD, HOF and NEG are options on the four days)
        if (lastGameDay <= ALL_STAR_BREAK_START_DAY) {
            daysOff += 1;
        }
        // for each remaining day in the schedule,
        // if all the stadiums hosting games on that day have already been visited, then call it a rest day
        // note that days during the all-star break have already been excluded
        for (int i = lastGameDay + 1; i < firstGameDay + maxNumDays; i++) {
            if (stadiumsPerGameDay.containsKey(i) && visited.containsAll(stadiumsPerGameDay.get(i))) {
                daysOff++;
            }
        }
        return daysOff;
    }

    private static boolean validSolution(List<Game> partial) {
        // all stadium-related error checking is done prior to this point - we
        // only need to check the size of the solution.
        return partial.size() == NUMBER_OF_TEAMS;
    }

    // keep track of the current best solution
    private static void processSolution(List<Game> partial) {
        printSolution(partial);
        int newTripLength = tripLength(partial);
        System.out.println(newTripLength);
        updatePathData(partial);
        if (!foundSolution) {
            foundSolution = true;
            writePruningData();
        }
        if (travelDays(partial) < maxNumDays) {
            System.out.println("New solution is fewer days! " + travelDays(partial));
            maxNumDays = travelDays(partial);
            recalculateFailureCriteria(games.indexOf(partial.get(0)));
        } else if (tripLength(partial) >= bestTripLength) {
            return;
        }

        bestSolution.clear();
        bestSolution.addAll(partial);
        System.out.println("Best solution is " + newTripLength + ", prev was " + bestTripLength);
        bestTripLength = newTripLength;
        writeBestSolution();
    }

    // The first extension of a given partial solution. Looks for the very next
    // game that can be added.
    private static List<Game> firstExtension(List<Game> partial) {
        int index = 0;
        if (partial.size() > 0) {
            index = games.indexOf(partial.get(partial.size() - 1)) + 1;
        }
        return extendSolution(partial, index, 0, false);
    }

    // Replace the current endpoint of this partial solution with the "next"
    // one. Returns null if no other options are available.
    // intentionally passing the index of previous game to extendSolution
    // because the next game in the master list might be on the following day
    // and we want to check earlier games in the list if they are farther away from the previous stadium
    private static List<Game> nextExtension(List<Game> partial) {
        int distance = 0;
        Game gameToRemove = partial.remove(partial.size() - 1);
        int index = games.indexOf(gameToRemove);
        if (partial.isEmpty()) {
            maxSize = 0;
            if (games.get(index).dayOfYear() != games.get(index + 1).dayOfYear()) {
                System.out.println("Checking " + games.get(index + 1).getDate().toString("M/dd"));
                recalculateFailureCriteria(index + 1);
            }
            index++;
        } else {
            distance = partial.get(partial.size() - 1).getMinutesTo(gameToRemove);
        }
        return extendSolution(partial, index, distance, true);
    }

    // Extends the partial solution by looking in the master game list starting
    // at the specified index. The first valid extension is returned. If no
    // valid extension exists, the partial solution is added to noExtensions and
    // null is returned.
    private static List<Game> extendSolution(List<Game> partial, int index, int distance, boolean excludeNextIndex) {
        if (partial.size() == 0) {
            partial.add(games.get(index));
            return partial;
        }
        if (index < games.size()) {
            Game last = partial.get(partial.size() - 1);
            Game next = games.get(index);
            List<Game> candidates = getCandidatesForDayAndDistance(next.dayOfYear(), distance, last);
            for (Game candidate: candidates) {
                if ((!excludeNextIndex || (last.getMinutesTo(candidate) > last.getMinutesTo(next) || next.compareTo(candidate) < 0)) && !haveVisitedStadium(partial, candidate.getStadium()) && last.canReach(candidate)) {
                    partial.add(candidate);
                    return partial;
                }
            }

            int latestGameDayAllowed = last.dayOfYear() + 1;
            // extra days allowed if it's the all star break
            if (last.dayOfYear() == ALL_STAR_BREAK_START_DAY) {
                latestGameDayAllowed += 4;
            }

            for (int day = next.dayOfYear() + 1; day <= latestGameDayAllowed; day++) {
                candidates = getCandidatesForDayAndDistance(day, 0, last);
                for (Game candidate: candidates) {
                    if (!haveVisitedStadium(partial, candidate.getStadium()) && last.canReach(candidate)) {
                        partial.add(candidate);
                        return partial;
                    }
                }
            }
        }

        if (!foundSolution) {
            addToParity(partial);
        }
        insertPathDataFromSubList(partial);
        return null;
    }

    // noExtensions stores known invalid partial solutions. The key is the
    // master index of the last game in the solution. The value in the map is a
    // set of EnumSets - each EnumSet represents a set of stadiums visited in a
    // partial solution that ends at the game specified by the key. If two
    // partial solutions end with the same game and visit the same set of
    // stadiums, they are equivalent (as long as both start on the same day!)
    private static void addToParity(List<Game> partial) {
        short key = (short) games.indexOf(partial.get(partial.size() - 1));
        if (noExtensions.containsKey(key)) {
            noExtensions.get(key).add(visitedStadia(partial));
        } else {
            Set<EnumSet<Stadium>> entry = new HashSet<>();
            entry.add(visitedStadia(partial));
            noExtensions.put(key, entry);
        }
    }

    private static List<Game> getCandidatesForDayAndDistance(int day, int distance, Game lastGame) {
        if (day == lastGame.dayOfYear()) {
            return Collections.emptyList();
        }
        return games.stream()
                .filter(g -> g.dayOfYear() == day && lastGame.getMinutesTo(g) >= distance)
                .sorted()
                .sorted(Comparator.comparingInt(lastGame::getMinutesTo))
                .collect(Collectors.toList());
    }

    private static void printPartial(List<Game> partial) {
        StringBuilder sb = new StringBuilder(tripLength(partial).toString());
        while (sb.length() < 6) {
            sb.append(" ");
        }
        int startDay = partial.get(0).dayOfYear();
        int endDay = partial.get(partial.size() - 1).dayOfYear();
        int currentIndex = 0;
        for (int i = startDay; i <= endDay; i++) {
            if (currentIndex + 1 < partial.size() && partial.get(currentIndex + 1).dayOfYear() == i) {
                sb.append("(").append(partial.get(currentIndex++).getStadium().toString()).append(" ")
                        .append(partial.get(currentIndex++).getStadium().toString()).append(") ");
            } else if (partial.get(currentIndex).dayOfYear() == i) {
                sb.append(partial.get(currentIndex++).getStadium().toString()).append(" ");
            } else {
                sb.append("drive ");
            }
        }
        sb.append(partial.size()).append(" in ").append(travelDays(partial));

        System.out.println(sb);
    }

    // The total trip length is padded with distance from Baltimore to the
    // starting stadium, and from the ending stadium to Baltimore. However, if
    // the partial solution is not complete, the "post-trip" padding can be
    // increased. For any stadium not in the trip, the padding must be at least
    // the distance from the current endpoint to the unvisited stadium, plus
    // the distance from the unvisited stadium to Baltimore.
    private static Integer tripLength(List<Game> partial) {
        if (partial.size() < 2) {
            return 0;
        }
        EnumSet<Stadium> notVisited = EnumSet.complementOf(visitedStadia(partial));
        int tripLength = 0;
        for (int i = 1; i < partial.size(); i++) {
            tripLength += partial.get(i - 1).getMinutesTo(partial.get(i));
        }
        Stadium last = partial.get(partial.size() - 1).getStadium();
        int padding = notVisited.stream().map(last::getMinutesTo).max(Integer::compare).orElse(0);
        return tripLength + padding;
    }

    private static boolean haveVisitedStadium(List<Game> partial, Stadium stadium) {
        return partial.stream().anyMatch(g -> g.getStadium().equals(stadium));
    }

    private static void printSolution(List<Game> partial) {
        for (Game g : partial) {
            System.out.println(g);
        }
    }

    private static int travelDays(List<Game> partial) {
        if (partial.isEmpty()) {
            return 0;
        }
        return partial.get(partial.size() - 1).dayOfYear() - partial.get(0).dayOfYear() + 1;
    }

    private static void writeBestSolution() {
        try {
            File file = new File(BEST_SOLUTION_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(BEST_SOLUTION_FILE_NAME);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(bestSolution);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void writePathData() {
        try {
            File file = new File(SHORTEST_PATH_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(SHORTEST_PATH_FILE_NAME);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(shortestPath);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writePruningData() {
        try {
            File file = new File(NO_EXTENSIONS_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream fos = new FileOutputStream(NO_EXTENSIONS_FILE_NAME);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(noExtensions);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void readPathData() {
        try {
            FileInputStream fis = new FileInputStream(SHORTEST_PATH_FILE_NAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            shortestPath = (TShortObjectMap<TObjectIntMap<EnumSet<Stadium>>>) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) {
            System.out.println("No shortest path file found");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void readPruningData() {
        try {
            FileInputStream fis = new FileInputStream(NO_EXTENSIONS_FILE_NAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            noExtensions = (TShortObjectMap<Set<EnumSet<Stadium>>>) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) {
            System.out.println("No pruning file found");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
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

    private static void calculateRestDays(Game firstGame) {
        int firstGameDay = firstGame.dayOfYear();
        int lastGameDay = firstGameDay + maxNumDays - 1;
        List<Integer> daysWithoutGames = IntStream.range(firstGameDay + 1, lastGameDay).filter(i -> games.stream().noneMatch(g -> g.dayOfYear() == i)).boxed().sorted().collect(Collectors.toList());
        List<Integer> daysWithFewGames = new ArrayList<>();
        // if there is a possibility for additional rest days, only allow that when skipping a day with a small number of games
        int maxAdditionalRestDaysAllowed = maxNumDays - Stadium.values().length - daysWithoutGames.size();
        if (maxAdditionalRestDaysAllowed < 0) {
            System.out.println("Negative rest days allowed!");
        }
        if (maxAdditionalRestDaysAllowed > 0) {
            System.out.println("A few rest days allowed");
            daysWithFewGames.addAll(IntStream.rangeClosed(firstGameDay, lastGameDay).filter(i -> games.stream().filter(g -> g.dayOfYear() == i).count() < 20).boxed().sorted().collect(Collectors.toList()));
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
    }
}
