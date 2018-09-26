package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.util.Log;

import java.util.ArrayList;

public class Week {

    private ArrayList<Match> mMatches;
    private ArrayList<Series> mSeries;
    private int mWeekNumber;
    private int mNumberMatchesUpdated = 0;
    private boolean mComplete;

    public Week(int weekNumber) {
        mMatches = new ArrayList<Match>();
        mWeekNumber = weekNumber;
    }

    public void addMatch(Match match) {
        mMatches.add(match);
    }

    public void addSeries(Series series) {mSeries.add(series);}

    public ArrayList<Match> getMatches() {
        return mMatches;
    }

    public void simulate(boolean useHomeFieldAdvantage) {
        for (Match match : mMatches) {
            if (!match.getComplete()) {
                match.simulate(useHomeFieldAdvantage);
            }
        }
    }

    public void simulatePlayoffSeries(boolean useHomeFieldAdvantage) {
        for (Match match : mMatches) {
            if (!match.getComplete()) {
                match.simulatePlayoffSeries();
            }
        }
    }

    public void simulateTestMatches(boolean useHomeFieldAdvantage) {
        for (Match match : mMatches) {
            match.simulateTestMatch(useHomeFieldAdvantage);
        }
    }

    public void matchUpdated() {
        mNumberMatchesUpdated++;
    }

    public int getNumberMatchesUpdated() {
        return mNumberMatchesUpdated;
    }
}
