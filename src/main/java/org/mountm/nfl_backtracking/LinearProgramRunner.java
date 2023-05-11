package org.mountm.nfl_backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class LinearProgramRunner {

	private static final String GAME_FILE = "2022 Games.csv";

	private static final List<Game> games = new ArrayList<>(270);
	private static final Map<Game, Set<Game>> gamesLeavingGame = new HashMap<>();
	private static final Set<Game> firstGamePerStadium = new HashSet<>();
	private static final Set<Game> lastGamePerStadium = new HashSet<>();
	private static final Map<Stadium, List<String>> arcsLeavingStadium = new HashMap<>();
	private static final Map<Stadium, List<String>> arcsArrivingStadium = new HashMap<>();
	private static final Map<Game, List<String>> arcsLeavingGame = new HashMap<>();
	private static final Map<Game, List<String>> arcsArrivingGame = new HashMap<>();

	private static final List<String> regularArcs = new ArrayList<>();
	private static final List<String> magicArcs = new ArrayList<>();

	public static void main(String[] args) {

		readGameInputFile();

		populateMaps();

		try {
			String objective, constraint, declaration;

			File file = new File("NFLTSP.lp");

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			objective = "MINIMIZE ";
			for (Entry<Game, Set<Game>> entry : gamesLeavingGame.entrySet()) {
				Game g1 = entry.getKey();
				for (Game g2 : entry.getValue()) {
					objective += g1.getMinutesTo(g2) + " " + g1.getShortString() + "to" + g2.getShortString();
					if (objective.length() >= 500) {
						bw.write(objective);
						bw.newLine();
						objective = "";
					}
					objective += " + ";
				}
			}
			objective = objective.substring(0, objective.length() - 3);
			bw.write(objective);
			bw.newLine();
			constraint = "SUBJECT TO";
			bw.write(constraint);
			bw.newLine();

			// must be exactly one arc leaving each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsLeavingStadium.values()) {
				constraint = "";
				for (String arc : arcs) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}

			// must be exactly one arc arriving at each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsArrivingStadium.values()) {
				constraint = "";
				for (String arc : arcs) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}

			// for each game,
			for (Game g : games) {
				// sum of arriving arcs must equal sum of departing arcs
				constraint = "";
				for (String arc : arcsArrivingGame.get(g)) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " - ";
				for (String arc : arcsLeavingGame.get(g)) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " - ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 0";
				bw.write(constraint);
				bw.newLine();
			}

			// must have exactly one magic arc "closing the loop"
			constraint = "";
			for (String arc : magicArcs) {
				constraint += arc;
				if (constraint.length() >= 500) {
					bw.write(constraint);
					bw.newLine();
					constraint = "";
				}
				constraint += " + ";
			}
			constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
			bw.write(constraint);
			bw.newLine();

			declaration = "BINARY";
			bw.write(declaration);
			bw.newLine();

			for (String arc : regularArcs) {
				bw.write(arc);
				bw.newLine();
			}
			for (String arc : magicArcs) {
				bw.write(arc);
				bw.newLine();
			}

			declaration = "END";
			bw.write(declaration);

			bw.close();

			System.out.println("Done");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void populateMaps() {
		Game[] lastGameHere = new Game[Stadium.values().length];
		for (int i = 0; i < games.size(); i++) {
			Game g1 = games.get(i);
			if (firstGamePerStadium.stream().map(Game::getStadium).noneMatch(s -> s.equals(g1.getStadium()))) {
				firstGamePerStadium.add(g1);
			}
			lastGameHere[g1.getStadium().getIndex()] = g1;
			for (int j = i+1; j < games.size(); j++) {
				Game g2 = games.get(j);
				if (!g1.getStadium().equals(g2.getStadium()) && g1.canReach(g2)) {
					String arc = g1.getShortString() + "to" + g2.getShortString();
					regularArcs.add(arc);
					
					Set<Game> innerSet = gamesLeavingGame.getOrDefault(g1, new HashSet<>());
					innerSet.add(g2);
					gamesLeavingGame.put(g1, innerSet);
					
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

		lastGamePerStadium.addAll(Arrays.asList(lastGameHere).subList(0, Stadium.values().length));
		
		for (Game g1: lastGamePerStadium) {
			for (Game g2: firstGamePerStadium) {
				if (!g1.getStadium().equals(g2.getStadium())) {
					String arc = g1.getShortString() + "to" + g2.getShortString();
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
	}

}
