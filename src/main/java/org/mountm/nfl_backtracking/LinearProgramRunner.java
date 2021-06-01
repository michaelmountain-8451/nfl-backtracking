package org.mountm.nfl_backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class LinearProgramRunner {
	
	private static List<Game> games = new ArrayList<>(270);
	private static Map<Stadium, Map<Game, Set<Game>>> gamesLeavingGamePerStadium = new HashMap<>();
	private static Map<Stadium, Map<Game, Set<Game>>> gamesArrivingGamePerStadium = new HashMap<>();
	private static Set<Game> firstWeekGames = new HashSet<>();
	private static Set<Game> lastWeekGames = new HashSet<>();
	private static Map<Stadium, Map<Stadium, Map<Game, Set<Game>>>> stadiumsLeavingGamePerStadium = new HashMap<>();
	private static Map<Game, Set<Game>> gamesLeavingGamePerGame = new HashMap<>();
	private static Map<Game, Set<Game>> gamesArrivingGamePerGame = new HashMap<>();
	private static final String GAME_FILE = "Games.csv";
	private static List<String> decisionVars = new ArrayList<>();
	private static List<String> magicDecisionVars = new ArrayList<>();

	public static void main(String[] args) {

		readGameInputFile();
		
		populateMap();
		
		try {
			String objective, constraint, declaration;
			
			File file = new File("NFLTSP.lp");
			
			if (!file.exists()) {
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			objective = "MINIMIZE ";
			for (Entry<Stadium, Map<Stadium, Map<Game, Set<Game>>>> e1 : stadiumsLeavingGamePerStadium.entrySet()) {
				for (Entry<Stadium, Map<Game, Set<Game>>> e2 : e1.getValue().entrySet()) {
					for (Entry<Game, Set<Game>> e3 : e2.getValue().entrySet()) {
						Game g1 = e3.getKey();
						for (Game g2 : e3.getValue()) {
							objective += g1.getMinutesTo(g2) + " " + g1.getShortString() + "to" + g2.getShortString();
							if (objective.length() >= 500) {
								bw.write(objective);
								bw.newLine();
								objective = "";
							}
							objective += " + ";
						}
					}
				}
			}
			objective = objective.substring(0, objective.length() - 3);
			bw.write(objective);
			bw.newLine();
			constraint = "SUBJECT TO";
			bw.write(constraint);
			bw.newLine();
			
			// must be exactly one arc leaving each stadium
			// unless that is the last stadium visited
			for (Entry<Stadium, Map<Game, Set<Game>>> e1 : gamesLeavingGamePerStadium.entrySet()) {
				constraint = "";
				for (Entry<Game, Set<Game>> e2: e1.getValue().entrySet()) {
					Game g1 = e2.getKey();
					for (Game g2 : e2.getValue()) {
						constraint += g1.getShortString() + "to" + g2.getShortString();
						if (constraint.length() >= 500) {
							bw.write(constraint);
							bw.newLine();
							constraint = "";
						}
						constraint += " + ";
					}
				}
				for (Game g1 : lastWeekGames) {
					if (g1.getStadium().equals(e1.getKey())) {
						for (Game g2: firstWeekGames) {
							if (!g1.getStadium().equals(g2.getStadium())) {
								constraint += g1.getShortString() + "to" + g2.getShortString();
								if (constraint.length() >= 500) {
									bw.write(constraint);
									bw.newLine();
									constraint = "";
								}
								constraint += " + ";
							}
						}
					}
				}
				// constraint += "l" + e1.getKey() + " = 1";
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// must be exactly one arc arriving at each stadium
			// unless that is the first stadium visited
			for (Entry<Stadium, Map<Game, Set<Game>>> e1 : gamesArrivingGamePerStadium.entrySet()) {
				constraint = "";
				for (Entry<Game, Set<Game>> e2 : e1.getValue().entrySet()) {
					Game g1 = e2.getKey();
					for (Game g2 : e2.getValue()) {
						constraint += g2.getShortString() + "to" + g1.getShortString();
						if (constraint.length() >= 500) {
							bw.write(constraint);
							bw.newLine();
							constraint = "";
						}
						constraint += " + ";
					}
				}
				for (Game g1 : firstWeekGames) {
					if (g1.getStadium().equals(e1.getKey())) {
						for (Game g2: lastWeekGames) {
							if (!g2.getStadium().equals(g1.getStadium())) {
								constraint += g2.getShortString() + "to" + g1.getShortString();
								if (constraint.length() >= 500) {
									bw.write(constraint);
									bw.newLine();
									constraint = "";
								}
								constraint += " + ";
							}
						}
					}
				}
				// constraint += "f" + e1.getKey() + " = 1";
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// for each game, sum of arcs arriving at game must be less than or equal to one
			for (Game g1: games) {
				constraint = "";
				for (Game g2: gamesArrivingGamePerGame.getOrDefault(g1, new HashSet<>())) {
					constraint += g2.getShortString() + "to" + g1.getShortString();
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (firstWeekGames.contains(g1)) {
					for (Game g2: lastWeekGames) {
						if (!g2.getStadium().equals(g1.getStadium())) {
							constraint += g2.getShortString() + "to" + g1.getShortString();
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + ";
						}
					}
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " <= 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// for each game, sum of arcs leaving game must be less than or equal to one
			for (Game g1: games) {
				constraint = "";
				for (Game g2: gamesLeavingGamePerGame.getOrDefault(g1, new HashSet<>())) {
					constraint += g1.getShortString() + "to" + g2.getShortString();
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (lastWeekGames.contains(g1)) {
					for (Game g2: firstWeekGames) {
						if (!g2.getStadium().equals(g1.getStadium())) {
							constraint += g1.getShortString() + "to" + g2.getShortString();
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + ";
						}
					}
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " <= 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// for each game, sum of arcs arriving at game must equal sum of arcs leaving game
			for (Game g1: games) {
				constraint = "";
				for (Game g2: gamesLeavingGamePerGame.getOrDefault(g1, new HashSet<>())) {
					constraint += g1.getShortString() + "to" + g2.getShortString();
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (lastWeekGames.contains(g1)) {
					for (Game g2: firstWeekGames) {
						if (!g2.getStadium().equals(g1.getStadium())) {
							constraint += g1.getShortString() + "to" + g2.getShortString();
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + ";
						}
					}
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " - ";
				for (Game g2: gamesArrivingGamePerGame.getOrDefault(g1, new HashSet<>())) {
					constraint += g2.getShortString() + "to" + g1.getShortString();
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " - ";
				}
				if (firstWeekGames.contains(g1)) {
					for (Game g2: lastWeekGames) {
						if (!g2.getStadium().equals(g1.getStadium())) {
							constraint += g2.getShortString() + "to" + g1.getShortString();
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " - ";
						}
					}
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 0";
				bw.write(constraint);
				bw.newLine();
			}
			
			// sum of "magic" arcs must be one
			constraint = "";
			for (String decisionVar : magicDecisionVars) {
				constraint += decisionVar;
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
			
//			// One stadium must be first
//			constraint = "";
//			for (Stadium s : Stadium.values()) { 
//				constraint += "f" + s;
//				if (s.getIndex() < 31) {
//					constraint += " + ";
//				} else {
//					constraint += " = 1";
//				}
//			}
//			bw.write(constraint);
//			bw.newLine();
//			
//			// One stadium must be last
//			constraint = "";
//			for (Stadium s : Stadium.values()) { 
//				constraint += "l" + s;
//				if (s.getIndex() < 31) {
//					constraint += " + ";
//				} else {
//					constraint += " = 1";
//				}
//			}
//			bw.write(constraint);
//			bw.newLine();
//			
//			// first stadium visited must be the earliest game in the solution
//			// For each pair of stadiums S1, S2:
//			// sum of decision variables for arcs leaving S1 * start time for S1 games
//			// + M * isFirst(S1) - sum of decision variables for arcs leaving S2 * start time for S2 games
//			// <= M
//			for (Stadium s1 : Stadium.values()) {
//				for (Stadium s2 : Stadium.values()) {
//					if (!s1.equals(s2)) {
//						constraint = "";
//						Map<Game, Set<Game>> gamesLeavingStadium = gamesLeavingGamePerStadium.get(s1);
//						for (Entry<Game, Set<Game>> entry : gamesLeavingStadium.entrySet()) {
//							Game g1 = entry.getKey();
//							for (Game g2 : entry.getValue()) {
//								constraint += g1.getStartTime() + " " + g1.getShortString() + "to" + g2.getShortString();
//								if (constraint.length() >= 500) {
//									bw.write(constraint);
//									bw.newLine();
//									constraint = "";
//								}
//								constraint += " + ";
//							}
//						}
//						constraint += "500000 f" + s1 + " - ";
//						gamesLeavingStadium = gamesLeavingGamePerStadium.get(s2);
//						for (Entry<Game, Set<Game>> entry : gamesLeavingStadium.entrySet()) {
//							Game g1 = entry.getKey();
//							for (Game g2 : entry.getValue()) {
//								constraint += g1.getStartTime() + " " + g1.getShortString() + "to" + g2.getShortString();
//								if (constraint.length() >= 500) {
//									bw.write(constraint);
//									bw.newLine();
//									constraint = "";
//								}
//								constraint += " - ";
//							}
//						}
//						constraint = constraint.substring(0, constraint.length() - 2) + "<= 500000";
//						bw.write(constraint);
//						bw.newLine();
//					}
//				}
//			}
//			
//			// last stadium visited must be the latest game in the solution
//			// For each pair of stadiums S1, S2:
//			// M * isLast(S1) - sum of decision variables for arcs arriving S1 * start time for S1 games
//			// + sum of decision variables for arcs arriving at S2 * start time for S2 games
//			// <= M
//			for (Stadium s1: Stadium.values()) {
//				for (Stadium s2: Stadium.values()) {
//					if (!s1.equals(s2)) {
//						constraint = "500000 l" + s1 + " - ";
//						Map<Game, Set<Game>> gamesArrivingStadium = gamesArrivingGamePerStadium.get(s1);
//						for (Entry<Game, Set<Game>> entry : gamesArrivingStadium.entrySet()) {
//							Game g1 = entry.getKey();
//							for (Game g2 : entry.getValue()) {
//								constraint += g1.getStartTime() + " " + g2.getShortString() + "to" + g1.getShortString();
//								if (constraint.length() >= 500) {
//									bw.write(constraint);
//									bw.newLine();
//									constraint = "";
//								}
//								constraint += " - ";
//							}
//						}
//						constraint = constraint.substring(0, constraint.length() - 2) + "+ ";
//						gamesArrivingStadium = gamesArrivingGamePerStadium.get(s2);
//						for (Entry<Game, Set<Game>> entry : gamesArrivingStadium.entrySet()) {
//							Game g1 = entry.getKey();
//							for (Game g2 : entry.getValue()) {
//								constraint += g1.getStartTime() + " " + g2.getShortString() + "to" + g1.getShortString();
//								if (constraint.length() >= 500) {
//									bw.write(constraint);
//									bw.newLine();
//									constraint = "";
//								}
//								constraint += " + ";
//							}
//						}
//						constraint = constraint.substring(0, constraint.length() - 2) + "<= 500000";
//						bw.write(constraint);
//						bw.newLine();
//					}
//				}
//			}
			
			declaration = "BINARY";
			bw.write(declaration);
			bw.newLine();
//			for (Stadium s : Stadium.values()) {
//				declaration = "f" + s;
//				bw.write(declaration);
//				bw.newLine();
//				declaration = "l" + s;
//				bw.write(declaration);
//				bw.newLine();
//			}
			for (String decisionVar : decisionVars) {
				bw.write(decisionVar);
				bw.newLine();
			}
			for (String decisionVar : magicDecisionVars) {
				bw.write(decisionVar);
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
	
	
	private static void populateMap() {
		int firstWeek = games.get(0).getWeek();
		int lastWeek = games.get(games.size()-1).getWeek();
		for (int i = 0; i < games.size(); i++) {
			Game g1 = games.get(i);
			if (g1.getWeek() == firstWeek) {
				firstWeekGames.add(g1);
			} else if (g1.getWeek() == lastWeek) {
				lastWeekGames.add(g1);
			}
			for (int j = i+1; j < games.size(); j++) {
				Game g2 = games.get(j);
				if (!g1.getStadium().equals(g2.getStadium()) && g1.canReachStrict(g2)) {
					Map<Game, Set<Game>> gamesLeavingGame = gamesLeavingGamePerStadium.getOrDefault(g1.getStadium(), new HashMap<>());
					Set<Game> innerSet = gamesLeavingGame.getOrDefault(g1, new HashSet<>());
					innerSet.add(g2);
					gamesLeavingGame.put(g1, innerSet);
					gamesLeavingGamePerStadium.put(g1.getStadium(), gamesLeavingGame);
					
					Map<Game, Set<Game>> gamesArrivingGame = gamesArrivingGamePerStadium.getOrDefault(g2.getStadium(), new HashMap<>());
					innerSet = gamesArrivingGame.getOrDefault(g2, new HashSet<>());
					innerSet.add(g1);
					gamesArrivingGame.put(g2, innerSet);
					gamesArrivingGamePerStadium.put(g2.getStadium(), gamesArrivingGame);
					
					Map<Stadium, Map<Game, Set<Game>>> stadiumsLeavingGame = stadiumsLeavingGamePerStadium.getOrDefault(g1.getStadium(), new HashMap<>());
					Map<Game, Set<Game>> innerMap = stadiumsLeavingGame.getOrDefault(g2.getStadium(), new HashMap<>());
					innerSet = innerMap.getOrDefault(g1, new HashSet<>());
					innerSet.add(g2);
					innerMap.put(g1, innerSet);
					stadiumsLeavingGame.put(g2.getStadium(), innerMap);
					stadiumsLeavingGamePerStadium.put(g1.getStadium(), stadiumsLeavingGame);
					
					innerSet = gamesLeavingGamePerGame.getOrDefault(g1, new HashSet<>());
					innerSet.add(g2);
					gamesLeavingGamePerGame.put(g1, innerSet);
					
					innerSet = gamesArrivingGamePerGame.getOrDefault(g2, new HashSet<>());
					innerSet.add(g1);
					gamesArrivingGamePerGame.put(g2, innerSet);
					
					decisionVars.add(g1.getShortString() + "to" + g2.getShortString());
				}
			}
		}
		for (Game g1 : lastWeekGames) {
			for (Game g2: firstWeekGames) {
				if (!g1.getStadium().equals(g2.getStadium())) {
					magicDecisionVars.add(g1.getShortString() + "to" + g2.getShortString());
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
	}

}
