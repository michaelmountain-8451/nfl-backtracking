package org.mountm.nfl_backtracking;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

public class Game implements Comparable<Game>, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5433969714606317194L;
	private final Stadium stadium;
	private final DateTime date;
	private final int index;

	private static int DATETIME_OFFSET = 0;
	private static final int TIME_OF_GAME = 240;
	private static final int NINE_AM = 32400000;
	private static final int TEN_PM = 79200000;
	private static final int MAX_DRIVING = 720;
	private static Map<Integer, Integer> restDaysAllowedPerGameDay = new HashMap<>();

	public static void setOffset(int offset) {
		DATETIME_OFFSET = offset;
	}



	public Game(Stadium stadium, DateTime date, int index) {
		this.stadium = stadium;
		this.date = date;
		this.index = index;
		if (DATETIME_OFFSET == 0) {
			DATETIME_OFFSET = 1440 * date.getDayOfYear() + date.getMinuteOfDay();
		}
	}

	public Game(Stadium stadium, DateTime date) {
		this.stadium = stadium;
		this.date = date;
		index = 0;
	}
	
	public Stadium getStadium() {
		return stadium;
	}

	public int getMinutesTo(Game g) {
		return stadium.getMinutesTo(g.stadium);
	}

	public int getStartTime() {
		return 1440 * date.getDayOfYear() + date.getMinuteOfDay() - DATETIME_OFFSET;
	}
	
	public boolean canReach(Game g) {
		if (g.stadium.equals(stadium)) {
			return false;
		}
		int daysOff = g.date.getDayOfYear() - date.getDayOfYear() - 1;
		if (restDaysAllowedPerGameDay.get(date.getDayOfYear()) < daysOff) {
			return false;
		}
		// no days off!
		// unless it is the all-star break, then you have a days' gap
//		if (dayDiff > 4 || (dayDiff > 1 && date.getDayOfYear() != ALL_STAR_BREAK_START_DAY) || dayDiff < 0) {
//			return false;
//		}
		return canReachStrict(g);
	}

	public boolean canReachStrict(Game g) {
		// return Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME), g.date).getMinutes() > getMinutesTo(g);
		int dayDiff = g.date.getDayOfYear() - date.getDayOfYear();
		int drivingTime = getMinutesTo(g);
		if (dayDiff == 0) {
			return Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME), g.date).getMinutes() > getMinutesTo(g);
		}
		drivingTime -= Math.max(Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME), date.withMillisOfDay(TEN_PM).plusHours(stadium.getTimeZone())).getMinutes(), 0);

		// if there's a dedicated travel day, you can drive up to 12 hours
		while (dayDiff > 1 && drivingTime > 0) {
			drivingTime -= MAX_DRIVING;
			dayDiff--;
		}
		// if the travel day was enough to get all the drive time completed, you're good
		// otherwise, you need to be able to make it to the game after leaving no later than 9 AM
		return drivingTime <= 0 || Minutes.minutesBetween(g.date.withMillisOfDay(NINE_AM).plusHours(g.stadium.getTimeZone()), g.date).getMinutes() > drivingTime;
	}
	
	@Override
	public String toString() {
		return stadium + " " + date.toString("M/dd hh:mm aa");
	}
	
	public String getShortString() {
		return stadium + date.toString("MMdd");
	}

	public String getTinyString() {
		return "g" + Integer.toHexString(index);
	}
	

	public int compareTo(Game g) {
		int startTimeDiff = Long.compare(date.getMillis(), g.date.getMillis());
		return startTimeDiff == 0 ? stadium.compareTo(g.stadium) : startTimeDiff;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stadium == null) ? 0 : stadium.hashCode());
		result = prime * result + ((date == null) ? 0 : date.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Game other = (Game) obj;
		if (stadium != other.stadium)
			return false;
		if (date == null) {
			return other.date == null;
		} else return date.equals(other.date);
	}

	public int dayOfYear() {
		return date.getDayOfYear();
	}

	public int stadiumIndex() {
		return stadium.getIndex();
	}

	public DateTime getDate() {
		return date;
	}

	public static void setMap(Map<Integer, Integer> map) {
		restDaysAllowedPerGameDay = map;
	}
}
