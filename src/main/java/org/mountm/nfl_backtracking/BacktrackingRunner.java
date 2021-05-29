package org.mountm.nfl_backtracking;

import static java.lang.Integer.parseInt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import gnu.trove.procedure.TIntIntProcedure;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.procedure.TShortObjectProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public class BacktrackingRunner {
	
	private static final String GAME_FILE = "Games.csv";
	private static final String NO_EXTENSIONS_FILE_NAME = "noExtensions.dat";
	private static final String BEST_SOLUTION_FILE_NAME = "bestSolution.dat";
	private static final String SHORTEST_PATH_FILE_NAME = "shortestPath.dat";
	private static final int NUMBER_OF_TEAMS = Stadium.values().length;
	
	private static int bestTripLength = Integer.MAX_VALUE;
	private static List<Game> games = new ArrayList<>(270);
	private static TShortObjectMap<TIntSet> noExtensions = new TShortObjectHashMap<>(270);
	private static Set<Game> lastGamePerStadium = new HashSet<>(NUMBER_OF_TEAMS);
	private static TShortObjectMap<TIntIntMap> shortestPath = new TShortObjectHashMap<>(270);
	private static int maxSize = 0;
	private static int lastWeekOfSeason;
	private static List<Game> bestSolution = new ArrayList<>(NUMBER_OF_TEAMS);
	private static boolean foundSolution = false;
	private static TShortObjectMap<Set<Game>> gamesPerWeek = new TShortObjectHashMap<>();

	public static void main(String[] args) {
		
		readGameInputFile();
		
		bestTripLength = Integer.MAX_VALUE;
		List<Game> partial = new ArrayList<>(NUMBER_OF_TEAMS);
		for (int i = 0; i < args.length; i++) {
			Game g = games.get(parseInt(args[i]));
			if (haveVisitedStadium(partial, g.getStadium())) {
				System.out.println("Trying to visit " + g.getStadium() + " twice!");
				return;
			}
			partial.add(g);
		}
		
		if (verifyInitialData(partial)) {
			System.out.println(partial);
			
			calculateFailureCriteria();
			
			readPruningData();
			readPathData();
			if (badSolution(partial) || isExcessiveTripLength(partial)) {
				System.out.println("Infeasible starting point.");
				return;
			}
			readBestSolution();
			prunePathData();

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

	private static void prunePathData() {
		shortestPath.retainEntries(new TShortObjectProcedure<TIntIntMap>(){

			public boolean execute(short key, TIntIntMap innerMap) {
				innerMap.retainEntries(new TIntIntProcedure() {

					@Override
					public boolean execute(int k, int v) {
						return v <= bestTripLength;
					}
					
				});
				return !innerMap.isEmpty();
			}
		});
	}

	private static void calculateFailureCriteria() {
		Game[] lastGameHere = new Game[NUMBER_OF_TEAMS];
		for (Game g: games) {
			lastGameHere[g.stadiumIndex()] = g;
		}
		for (int i = 0; i < NUMBER_OF_TEAMS; i++) {
			lastGamePerStadium.add(lastGameHere[i]);
		}
		
		for (int i = 0; i < games.size() - 1; i++) {
			Game g1 = games.get(i);
			int week = g1.getWeek();
			if (gamesPerWeek.containsKey((short) week)) {
				continue;
			}
			Set<Game> thisWeek = new HashSet<>();
			thisWeek.add(g1);
			for (int j = i+1; j < games.size(); j++) {
				Game g2 = games.get(j);
				if (g2.getWeek() != week) {
					break;
				}
				thisWeek.add(g2);
			}
			gamesPerWeek.put((short) week, thisWeek);
		}
	}

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

	private static List<Game> nextExtension(List<Game> partial) {
		int index = games.indexOf(partial.remove(partial.size() - 1)) + 1;
		return extendSolution(partial, index);
	}

	private static List<Game> firstExtension(List<Game> partial) {
		int index = 0;
		if (partial.size() > 0) {
			index = games.indexOf(partial.get(partial.size() - 1)) + 1;
		}
		return extendSolution(partial, index);
	}

	private static List<Game> extendSolution(List<Game> partial, int index) {
		if (partial.size() == 0) {
			partial.add(games.get(index));
			return partial;
		}
		Game last = partial.get(partial.size() - 1);
		for (int i = index; i < games.size(); i++) {
			Game candidate = games.get(i);
			if (candidate.getWeek() > last.getWeek() + 1) {
				break;
			}
			if (!haveVisitedStadium(partial, candidate.getStadium()) && last.canReach(candidate)) {
				partial.add(candidate);
				return partial;
			}
		}
		
		if (!foundSolution) {
			addToParity(partial);
		}
		insertPathDataFromSubList(partial);
		return null;
	}

	private static void addToParity(List<Game> partial) {
		short key = (short) games.indexOf(partial.get(partial.size() - 1));
		if (noExtensions.containsKey(key)) {
			noExtensions.get(key).add(calculateValue(partial));
		} else {
			TIntSet entry = new TIntHashSet();
			entry.add(calculateValue(partial));
			noExtensions.put(key, entry);
		}
	}

	private static void printPartial(List<Game> partial) {
		StringBuilder sb = new StringBuilder(tripLength(partial).toString());
		while (sb.length() < 6) {
			sb.append(" ");
		}
		sb.append(partial.stream().map(Game::getShortString).collect(Collectors.joining(", ")));

		System.out.println(sb);	
	}

	private static void processSolution(List<Game> partial) {
		printSolution(partial);
		if (!foundSolution) {
			foundSolution = true;
			writePruningData();
		}
		int newTripLength = tripLength(partial);
		if (newTripLength < bestTripLength) {
			bestSolution.clear();
			bestSolution.addAll(partial);
			System.out.println("Best solution is " + newTripLength + ", prev was " + bestTripLength);
			bestTripLength = newTripLength;
			prunePathData();
			writeBestSolution();
		}
		updatePathData(partial);
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

	private static void updatePathData(List<Game> partial) {
		boolean didUpdate = false;
		for (int i = 2; i < partial.size(); i++) {
			didUpdate = didUpdate || insertPathDataFromSubList(partial.subList(0, i + 1));
		}
		if (didUpdate) {
			writePathData();
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

	private static boolean insertPathDataFromSubList(List<Game> subList) {
		boolean didUpdate = false;
		short key = (short) games.indexOf(subList.get(subList.size() - 1));
		int mask = calculateValue(subList);
		int tripLength = tripLength(subList);
		if (tripLength < bestTripLength) {
			TIntIntMap innerMap;
			
			if (shortestPath.containsKey(key)) {
				innerMap = shortestPath.get(key);
				if (!innerMap.containsKey(mask) || innerMap.get(mask) > tripLength) {
					innerMap.put(mask, tripLength);
					didUpdate = true;
				}
			} else {
				innerMap = new TIntIntHashMap();
				innerMap.put(mask, tripLength);
				shortestPath.put(key, innerMap);
				didUpdate = true;
			}
		}
		
		return didUpdate;
		
	}

	private static void printSolution(List<Game> partial) {
		for (Game g : partial) {
			System.out.println(g);
		}
	}

	@SuppressWarnings("unchecked")
	private static void readBestSolution() {
		try {
			FileInputStream fis = new FileInputStream(BEST_SOLUTION_FILE_NAME);
			ObjectInputStream ois = new ObjectInputStream(fis);
			bestSolution = (List<Game>) ois.readObject();
			ois.close();
			bestTripLength = tripLength(bestSolution);
		} catch (FileNotFoundException e) {
			System.out.println("No best solution file found");
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static boolean badSolution(List<Game> partial) {
		if (foundSolution && isExcessiveTripLength(partial)) {
			return true;
		}

		if (validSolution(partial) || partial.isEmpty()) {
			return false;
		}
		
		if ((partial.size() + getPossibleRemainingGames(partial)) < NUMBER_OF_TEAMS) {
			return true;
		}
		
		Game last = partial.get(partial.size() - 1);
		
		// Check if any stadiums are impossible to visit
		if (lastGamePerStadium.stream().anyMatch(g -> !haveVisitedStadium(partial, g.getStadium()) && !last.canReach(g))) {
			return true;
		}

		// Check to see if an equivalent path was already discarded
		short key = (short) games.indexOf(last);
		return didEvaluateEquivalentPath(key, partial);
	}

	private static int getPossibleRemainingGames(List<Game> partial) {
		if (partial.isEmpty()) {
			return NUMBER_OF_TEAMS;
		}
		int result = 0;
		Game lastGame = partial.get(partial.size() - 1);
		EnumSet<Stadium> visited = EnumSet.noneOf(Stadium.class);
		visited.addAll(partial.stream().map(g -> g.getStadium()).collect(Collectors.toSet()));
		int lastWeekOfPartial = lastGame.getWeek();
		for (int i = lastWeekOfPartial; i <= lastWeekOfSeason; i++) {
			result += gamesPerWeek.get((short) i).stream()
					.filter(g -> lastGame.canReach(g) && !visited.contains(g.getStadium()))
					.map(g -> g.getDate().getDayOfWeek())
					.distinct().count();
		}
		return result;
	}

	private static boolean didEvaluateEquivalentPath(short key, List<Game> partial) {
		if (noExtensions.containsKey(key)) {
			TIntSet previouslyConsidered = noExtensions.get(key);
			int val = calculateValue(partial);
			// forEach returns false if the iteration terminated early
			return !previouslyConsidered.forEach(new TIntProcedure() {
				// execute returns true if additional operations are allowed;
				// i.e. if the number we're testing was not a match for the new partial solution
				public boolean execute (int test) {
					// A & B == B iff all 1 bits in B are also 1 bits in A
					// meaning the stadium masks in B are all also in A (extra stadiums in A are allowed)
					return (test & val) != val;
				}
			});
		}
		return false;
	}

	private static boolean validSolution(List<Game> partial) {
		return partial.size() == NUMBER_OF_TEAMS;
	}

	private static boolean isExcessiveTripLength(List<Game> partial) {
		if (partial.isEmpty()) {
			return false;
		}
		int tripLength = tripLength(partial);
		if (tripLength > bestTripLength) {
			return true;
		}
		
		short key = (short) games.indexOf(partial.get(partial.size() - 1));
		if (shortestPath.containsKey(key)) {
			TIntIntMap previouslyConsidered = shortestPath.get(key);
			int val = calculateValue(partial);
			// forEach returns false if the iteration terminated early
			return !previouslyConsidered.forEachEntry(new TIntIntProcedure() {
				// execute returns true if additional operations are allowed;
				// i.e. if the number we're testing was not a match for the new partial solution
				public boolean execute (int k, int v) {
					return ((k & val) != val || v >= tripLength);
				}
			});
		}
		
		return false;
	}

	private static int calculateValue(List<Game> partial) {
		return partial.stream().map(g -> g.getStadium().getMask()).reduce(0, (a, b) -> a ^ b);
	}

	private static Integer tripLength(List<Game> partial) {
		if (partial.size() < 2) {
			return 0;
		}
		EnumSet<Stadium> notVisited = EnumSet.allOf(Stadium.class);
		Integer tripLength = 0;
		for (int i = 0; i < partial.size(); i++) {
			notVisited.remove(partial.get(i).getStadium());
			if (i > 0) {
				tripLength += partial.get(i - 1).getMinutesTo(partial.get(i));
			}
		}
		Stadium last = partial.get(partial.size() - 1).getStadium();
		int padding = notVisited.stream().mapToInt(s -> last.getMinutesTo(s)).max().orElse(0);
		return tripLength + padding;
	}

	@SuppressWarnings("unchecked")
	private static void readPathData() {
		try {
			FileInputStream fis = new FileInputStream(SHORTEST_PATH_FILE_NAME);
			ObjectInputStream ois = new ObjectInputStream(fis);
			shortestPath = (TShortObjectMap<TIntIntMap>) ois.readObject();
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
			noExtensions = (TShortObjectMap<TIntSet>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			System.out.println("No pruning file found");
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static boolean verifyInitialData(List<Game> partial) {
		if (partial.size() < 2) {
			return true;
		}
		for (int i = 0; i < partial.size() - 1; i++) {
			if (!partial.get(i).canReach(partial.get(i + 1))) {
				System.out.println("Can't get from " + partial.get(i) + " to " + partial.get(i + 1));
				return false;
			}
		}
		return true;
	}

	private static boolean haveVisitedStadium(List<Game> partial, Stadium stadium) {
		return partial.stream().anyMatch(g -> g.getStadium().equals(stadium));
	}

	private static void readGameInputFile() {
		BufferedReader br = null;

		try {
			String currentLine;
			String[] gameData;
			br = new BufferedReader(new FileReader(GAME_FILE));
			DateTimeFormatter dateFormat = DateTimeFormat.forPattern("dd-MMM-yyyy");
			DateTimeFormatter timeFormat = DateTimeFormat.forPattern("HH:mm");
			while ((currentLine = br.readLine()) != null) {
				gameData = currentLine.split(",");
				DateTime startTime = timeFormat.parseDateTime(gameData[3]);
				DateTime startDateTime = dateFormat.parseDateTime(gameData[2]).withTime(LocalTime.fromMillisOfDay(startTime.getMillisOfDay()));
				Stadium stadium = Stadium.valueOf(gameData[1]);
				games.add(new Game(stadium, startDateTime));
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
		lastWeekOfSeason = games.get(games.size() - 1).getWeek();
	}

}
