package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import java.util.ArrayList;

public class Series {

    private ArrayList<Match> mMatches;
    private Team mAwayTeam;
    private Team mHomeTeam;

    public Series(Team awayTeam, Team homeTeam){
        mAwayTeam = awayTeam;
        mHomeTeam = homeTeam;
    }

}
