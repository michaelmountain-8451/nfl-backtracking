package org.mountm.nfl_backtracking;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static java.lang.Integer.parseInt;

public class DistanceMinimizationRunner {

	private static final String GAME_FILE = "Games2.csv";

	private static final List<Game> games = new ArrayList<>(270);
	private static final Map<Stadium, List<String>> arcsLeavingStadium = new HashMap<>();
	private static final Map<Stadium, List<String>> arcsArrivingStadium = new HashMap<>();
	private static final Map<Game, List<String>> arcsLeavingGame = new HashMap<>();
	private static final Map<Game, List<String>> arcsArrivingGame = new HashMap<>();

	private static final List<String> regularArcs = new ArrayList<>();
	private static final List<String> magicArcs = new ArrayList<>();
	private static final List<String> arcsWithWeights = new ArrayList<>();

	public static void main(String[] args) {

		readGameInputFile();
		readDistanceData();
		calculateRestDays();

		populateMaps();

		try {
			StringBuilder objective, constraint;

			File file = new File("MLBTSP.lp");

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			objective = new StringBuilder("MINIMIZE ");
			for (String arcsWithWeight : arcsWithWeights) {
				objective.append(arcsWithWeight);
				if (objective.length() >= 500) {
					bw.write(objective.toString());
					bw.newLine();
					objective = new StringBuilder();
				}
				objective.append(" + ");
			}
			bw.write(objective.substring(0, objective.length() - 3));
			bw.newLine();
			constraint = new StringBuilder("SUBJECT TO");
			bw.write(constraint.toString());
			bw.newLine();

			// must be exactly one arc leaving each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsLeavingStadium.values()) {
				constraint = new StringBuilder();
				for (String arc : arcs) {
					constraint.append(arc);
					if (constraint.length() >= 500) {
						bw.write(constraint.toString());
						bw.newLine();
						constraint = new StringBuilder();
					}
					constraint.append(" + ");
				}
				bw.write(constraint.substring(0, constraint.length() - 3) + " = 1");
				bw.newLine();
			}

			// must be exactly one arc arriving at each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsArrivingStadium.values()) {
				constraint = new StringBuilder();
				for (String arc : arcs) {
					constraint.append(arc);
					if (constraint.length() >= 500) {
						bw.write(constraint.toString());
						bw.newLine();
						constraint = new StringBuilder();
					}
					constraint.append(" + ");
				}
				bw.write(constraint.substring(0, constraint.length() - 3) + " = 1");
				bw.newLine();
			}

			// for each game,
			for (Game g : games) {
				if (arcsArrivingGame.containsKey(g) && arcsLeavingGame.containsKey(g)) {
					// sum of arriving arcs must equal sum of departing arcs
					constraint = new StringBuilder();
					for (String arc : arcsArrivingGame.get(g)) {
						constraint.append(arc);
						if (constraint.length() >= 500) {
							bw.write(constraint.toString());
							bw.newLine();
							constraint = new StringBuilder();
						}
						constraint.append(" + ");
					}
					constraint = new StringBuilder(constraint.substring(0, constraint.length() - 3) + " - ");
					for (String arc : arcsLeavingGame.get(g)) {
						constraint.append(arc);
						if (constraint.length() >= 500) {
							bw.write(constraint.toString());
							bw.newLine();
							constraint = new StringBuilder();
						}
						constraint.append(" - ");
					}
					bw.write(constraint.substring(0, constraint.length() - 3) + " = 0");
					bw.newLine();
				} else if (arcsLeavingGame.containsKey(g)) {
					System.out.println("This shouldn't happen! leaving");
					constraint = new StringBuilder();
					for (String arc : arcsLeavingGame.get(g)) {
						constraint.append(arc);
						if (constraint.length() >= 500) {
							bw.write(constraint.toString());
							bw.newLine();
							constraint = new StringBuilder();
						}
						constraint.append(" + ");
					}
					bw.write(constraint.substring(0, constraint.length() - 3) + " = 0");
					bw.newLine();
				} else if (arcsArrivingGame.containsKey(g)) {
					System.out.println("This shouldn't happen! arriving");
					constraint = new StringBuilder();
					for (String arc : arcsArrivingGame.get(g)) {
						constraint.append(arc);
						if (constraint.length() >= 500) {
							bw.write(constraint.toString());
							bw.newLine();
							constraint = new StringBuilder();
						}
						constraint.append(" + ");
					}
					bw.write(constraint.substring(0, constraint.length() - 3) + " = 0");
					bw.newLine();
				}
			}

			// must have exactly one magic arc "closing the loop"
			constraint = new StringBuilder();
			for (String arc : magicArcs) {
				constraint.append(arc);
				if (constraint.length() >= 500) {
					bw.write(constraint.toString());
					bw.newLine();
					constraint = new StringBuilder();
				}
				constraint.append(" + ");
			}
			bw.write(constraint.substring(0, constraint.length() - 3) + " = 1");
			bw.newLine();

			bw.write("BINARY");
			bw.newLine();

			for (String arc : regularArcs) {
				bw.write(arc);
				bw.newLine();
			}

			for (String arc : magicArcs) {
				bw.write(arc);
				bw.newLine();
			}

			bw.write("END");

			bw.close();

			System.out.println("Done");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void populateMaps() {
		int firstDay = games.get(0).dayOfYear();
		int lastDay = games.get(games.size() - 1).dayOfYear();
		for (int i = 0; i < games.size(); i++) {
			Game g1 = games.get(i);
			for (int j = i+1; j < games.size(); j++) {
				Game g2 = games.get(j);
				if (!g1.getStadium().equals(g2.getStadium()) && g1.canReach(g2)) {
					String arc = g1.getShortString() + g2.getShortString();
					regularArcs.add(arc);
					arcsWithWeights.add(g1.getMinutesTo(g2) + " " + arc);
					
					List<String> innerList = arcsLeavingStadium.getOrDefault(g1.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsLeavingStadium.put(g1.getStadium(), innerList);
					
					innerList = arcsArrivingStadium.getOrDefault(g2.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsArrivingStadium.put(g2.getStadium(), innerList);
					
					innerList = arcsLeavingGame.getOrDefault(g1, new ArrayList<>());
					innerList.add(arc);
					arcsLeavingGame.put(g1, innerList);
					
					innerList = arcsArrivingGame.getOrDefault(g2, new ArrayList<>());
					innerList.add(arc);
					arcsArrivingGame.put(g2, innerList);
				}
			}
		}

		Set<Game> firstDayGames = games.stream().filter(g -> g.dayOfYear() == firstDay).collect(Collectors.toSet());
		Set<Game> lastDayGames = games.stream().filter(g -> g.dayOfYear() == lastDay).collect(Collectors.toSet());
		
		for (Game g1: lastDayGames) {
			for (Game g2: firstDayGames) {
				if (!g1.getStadium().equals(g2.getStadium())) {
					String arc = g1.getShortString() + g2.getShortString();
					magicArcs.add(arc);
					
					List<String> innerList = arcsLeavingStadium.getOrDefault(g1.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsLeavingStadium.put(g1.getStadium(), innerList);
					
					innerList = arcsArrivingStadium.getOrDefault(g2.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsArrivingStadium.put(g2.getStadium(), innerList);
					
					innerList = arcsLeavingGame.getOrDefault(g1, new ArrayList<>());
					innerList.add(arc);
					arcsLeavingGame.put(g1, innerList);
					
					innerList = arcsArrivingGame.getOrDefault(g2, new ArrayList<>());
					innerList.add(arc);
					arcsArrivingGame.put(g2, innerList);
				}
			}
		}

		if (removeUnusedArcs()) {
			System.out.println("Removed games, recalculating...");
			regularArcs.clear();
			magicArcs.clear();
			arcsWithWeights.clear();
			arcsLeavingGame.clear();
			arcsLeavingStadium.clear();
			arcsArrivingGame.clear();
			arcsArrivingStadium.clear();
			calculateRestDays();
			populateMaps();
		};
	}

	// If a game only has arcs arriving and not leaving, or vice versa, it cannot be part of the solution
	private static boolean removeUnusedArcs() {
		return games.removeIf(g -> !arcsLeavingGame.containsKey(g) || !arcsArrivingGame.containsKey(g));
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
				games.add(new Game(stadium, startDate, i++));
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


	private static void calculateRestDays() {
		int firstGameDay = games.get(0).dayOfYear();
		int lastGameDay = games.get(games.size() - 1).dayOfYear();
		int totalDays = lastGameDay - firstGameDay + 1;
		List<Integer> daysWithoutGames = IntStream.range(firstGameDay + 1, lastGameDay).filter(i -> games.stream().noneMatch(g -> g.dayOfYear() == i)).boxed().sorted().collect(Collectors.toList());
		List<Integer> daysWithFewGames = new ArrayList<>();
		// if there is a possibility for additional rest days, only allow that when skipping a day with a small number of games
		int maxAdditionalRestDaysAllowed = totalDays - Stadium.values().length - daysWithoutGames.size();
		if (maxAdditionalRestDaysAllowed < 0) {
			System.out.println("Negative rest days allowed!");
		} else if (maxAdditionalRestDaysAllowed == 0) {
			System.out.println("0 rest days allowed!");
		}
		if (maxAdditionalRestDaysAllowed > 0) {
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
