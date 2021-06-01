package org.mountm.nfl_backtracking;

import java.io.Serializable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Minutes;

public class Game implements Comparable<Game>, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5433969714606317194L;
	private Stadium stadium;
	private DateTime date;
	private int week;
	
	private static final int TIME_OF_GAME = 240;
	private static final int NINE_AM = 32400000;
	private static final int TEN_PM = 79200000;
	private static final int MAX_DRIVING = 720;
	private static int seasonYear = 0;
	private static int firstWeek = 0;
	private static int daysInSeasonYear = 0;
	private static int firstDayOfSeason = 0;
	
	public Game(Stadium stadium, DateTime date) {
		this.stadium = stadium;
		this.date = date;
		week = date.getWeekOfWeekyear();
		if (date.getDayOfWeek() == DateTimeConstants.MONDAY) {
			week--;
		}
		if (seasonYear == 0) {
			seasonYear = date.getYear();
			firstWeek = week;
			LocalDate ld = new LocalDate(seasonYear, 1, 1);
			daysInSeasonYear = Days.daysBetween(ld, ld.plusYears(1)).getDays();
			firstDayOfSeason = date.getDayOfYear();
		}
		if (week < firstWeek) {
			week += 52;
		}
	}
	
	public Stadium getStadium() {
		return stadium;
	}
	
	public DateTime getDate() {
		return date;
	}
	
	public int getDayOfYear() {
		int day = date.getDayOfYear();
		if (date.getYear() != seasonYear) {
			day += daysInSeasonYear;
		}
		return day;
	}
	
	public int getDayOfSeason() {
		return getDayOfYear() - firstDayOfSeason;
	}
	
	public int getWeek() {
		return week;
	}
	
	public int stadiumIndex() {
		return stadium.getIndex();
	}
	
	public int getStartTime() {
		return 1440 * getDayOfSeason() + date.getMinuteOfDay();
	}
	
	public int getMinutesTo(Game g) {
		return stadium.getMinutesTo(g.stadium);
	}
	
	public int getMinutesTo(Stadium s) {
		return stadium.getMinutesTo(s);
	}
	
	public boolean canReachStrict(Game g) {
		return Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME), g.date).getMinutes() > stadium.getMinutesTo(g.stadium);
	}
	
	public boolean canReach(Game g) {
		if (date.isAfter(g.date.minusMinutes(TIME_OF_GAME))) {
			return false;
		}
		int dayDiff = g.getDayOfYear() - this.getDayOfYear();
		int drivingTime = stadium.getMinutesTo(g.stadium);
		if (dayDiff == 0) {
			return Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME), g.date).getMinutes() > drivingTime;
		}
		int drivingAfterGame = Minutes.minutesBetween(date.plusMinutes(TIME_OF_GAME),
				date.withMillisOfDay(TEN_PM).plusHours(stadium.getTimeZone())).getMinutes();
		if (drivingAfterGame > 0) {
			drivingTime -= drivingAfterGame;
		}
		while (dayDiff > 1 && drivingTime > 0) {
			drivingTime -= MAX_DRIVING;
			dayDiff--;
		}
		if (dayDiff == 1) {
			return Minutes.minutesBetween(g.date.withMillisOfDay(NINE_AM).plusHours(g.stadium.getTimeZone()),
					g.date).getMinutes() > drivingTime;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return stadium + " " + date.toString("M/dd hh:mm aa");
	}
	
	public String getShortString() {
		return stadium + date.toString("MMMdd");
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
			if (other.date != null)
				return false;
		} else if (!date.equals(other.date))
			return false;
		return true;
	}

}
