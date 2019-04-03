package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.HomeScreen;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;

public class Match {

    private static final String LOG_TAG = Match.class.getSimpleName();

    @Inject
    ContentResolver contentResolver;

    private Data mData;
    private Uri matchUri;
    //Team one is always the away team and team two is always the home team
    private Team mTeam1;
    private Team mTeam2;
    private int mWeek;
    private Team winner;
    private Team loser;
    private int mTeam1Score;
    private int mTeam2Score;
    private Double mTeamTwoOdds;
    private Integer mTeamOneWon;
    private Integer mPlayoffGame;
    private Boolean matchComplete;
    private Boolean divisionalMatchup;
    private Boolean playoffMatchup;
    //Boolean to check if match was successfully updated in database
    private Boolean mMatchUpdated = false;
    //Int to check if match is being used for current season or simulator season
    private int mCurrentSeason;


    public Match(Team team1, Team team2, int week, Data data, int currentSeason) {

        //Inject match with dagger to get contentResolver
        HomeScreen.getActivityComponent().inject(this);

        mData = data;
        mTeam1 = team1;
        mTeam2 = team2;
        mWeek = week;
        matchComplete = false;
        mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        mTeamTwoOdds = SeasonSimContract.MatchEntry.MATCH_NO_ODDS_SET;
        mCurrentSeason = currentSeason;

        //Determine if match is a divisional matchup
        if (mTeam1.getDivision() == mTeam2.getDivision()) {
            divisionalMatchup = true;
        } else {
            divisionalMatchup = false;
        }

        if (mWeek > 24) {
            playoffMatchup = true;
        } else {
            playoffMatchup = false;
        }

    }

    public Match(Team team1, Team team2, int week, Data data, int currentSeason, int playoffGame) {

        //Inject match with dagger to get contentResolver
        HomeScreen.getActivityComponent().inject(this);

        mData = data;
        mTeam1 = team1;
        mTeam2 = team2;
        mWeek = week;
        matchComplete = false;
        mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        mTeamTwoOdds = SeasonSimContract.MatchEntry.MATCH_NO_ODDS_SET;
        mCurrentSeason = currentSeason;
        mPlayoffGame = playoffGame;

        //Determine if match is a divisional matchup
        if (mTeam1.getDivision() == mTeam2.getDivision()) {
            divisionalMatchup = true;
        } else {
            divisionalMatchup = false;
        }

        if (mWeek > 24) {
            playoffMatchup = true;
        } else {
            playoffMatchup = false;
        }



    }

    public Match(Team team1, Team team2, int week, Data data, Double teamTwoOdds, int currentSeason) {

        //Inject match with dagger to get contentResolver
        HomeScreen.getActivityComponent().inject(this);

        mData = data;
        mTeam1 = team1;
        mTeam2 = team2;
        mWeek = week;
        matchComplete = false;
        mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        mTeamTwoOdds = teamTwoOdds;
        mCurrentSeason = currentSeason;
        mPlayoffGame = 0;

        //Determine if match is a divisional matchup
        if (mTeam1.getDivision() == mTeam2.getDivision()) {
            divisionalMatchup = true;
        } else {
            divisionalMatchup = false;
        }

        if (mWeek > 24) {
            playoffMatchup = true;
        } else {
            playoffMatchup = false;
        }

    }

    public Match(Team team1, Team team2, int teamOneWon, int week, Data data, Uri uri, Double teamTwoOdds, int currentSeason, int matchCompleteInt) {

        //Inject match with dagger to get contentResolver
        HomeScreen.getActivityComponent().inject(this);

        mData = data;
        mTeam1 = team1;
        mTeam2 = team2;
        mWeek = week;
        if (matchCompleteInt == SeasonSimContract.MatchEntry.MATCH_COMPLETE_NO) {
            matchComplete = false;
        } else {
            matchComplete = true;
        }
        matchUri = uri;
        mTeamTwoOdds = teamTwoOdds;
        mCurrentSeason = currentSeason;
        mPlayoffGame = 0;

        //Determine if match is a divisional matchup
        if (mTeam1.getDivision() == mTeam2.getDivision()) {
            divisionalMatchup = true;
        } else {
            divisionalMatchup = false;
        }
        if (mWeek > 24) {
            playoffMatchup = true;
        } else {
            playoffMatchup = false;
        }

        //Set the teamOneWon boolean based on DB values
        if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES) {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        }

    }

    public void complete(int teamOneScore, int teamTwoScore) {
        mTeamOneWon = ELORatingSystem.completeCurrentSeasonMatch(this, teamOneScore, teamTwoScore);

        //Update team records based on outcome and mark match as complete
        setMatchWins();
        Log.d("MATCHCOMP", "FROMCOMPLETE");
        matchComplete = true;

        //Callback to presenter to update match in database with match result
        mData.updateMatchCallback(this, matchUri);



    }

    protected void simulate(boolean useHomeFieldAdvantage) {

        //Simulate match to determine if team one won
        Boolean teamOneWonBool;
        teamOneWonBool = ELORatingSystem.simulateMatch(this, mTeam1, mTeam2, useHomeFieldAdvantage);

        if (teamOneWonBool) {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        }

        //Update team records based on outcome and mark match as complete
        setMatchWins();
        Log.d("MATCHCOMP", "FROMSIMULATE");
        matchComplete = true;

        //Callback to presenter to update match in database with match result
        mData.updateMatchCallback(this, matchUri);

    }

    protected boolean simulatePlayoffSeries() {
        //Simulate the playoff series and set the team's playoff game value to be equal to the next playoff game
        //This is used when sorting the database and determining playoff teams and setting playoff matchups
        int awayTeamGamesWon = 0;
        int homeTeamGamesWon = 0;

        int gameNumber = 0;
        boolean awayTeamWon;

        while (awayTeamGamesWon < 4 && homeTeamGamesWon < 4){
             gameNumber++;
             if (gameNumber == 1 || gameNumber == 2 || gameNumber == 5 || gameNumber == 7){
                 awayTeamWon = ELORatingSystem.simulateTestMatch(mTeam1, mTeam2, true);
                 if (awayTeamWon){
                     awayTeamGamesWon++;
                 } else {
                     homeTeamGamesWon++;
                 }
             } else {
                 awayTeamWon = !ELORatingSystem.simulateTestMatch(mTeam2, mTeam1,  true);
                 if (awayTeamWon){
                     awayTeamGamesWon++;
                 } else {
                     homeTeamGamesWon++;
                 }
             }
        }

        mTeam1Score = awayTeamGamesWon;
        mTeam2Score = homeTeamGamesWon;

        if (mTeam1Score > mTeam2Score){
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
            mTeam2.setPlayoffGame(mPlayoffGame);
            mTeam1.setPlayoffGame(0);
            mTeam2.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
        } else  {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
            mTeam1.setPlayoffGame(mPlayoffGame);
            mTeam2.setPlayoffGame(0);
            mTeam1.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
        }

        Log.d("MATCHCOMP",  "FROMSIMPLAYOFF");
        matchComplete = true;

        //Callback to presenter to update match in database with match result
        mData.updateMatchCallback(this, matchUri);

        if (mTeam1Score > mTeam2Score){
            return true;
        } else {
            return false;
        }

    }

    protected void simulateTestMatch(boolean useHomeFieldAdvantage){
        //Simulate match to determine if team one won
        Boolean teamOneWonBool;
        teamOneWonBool = ELORatingSystem.simulateTestMatch( mTeam1, mTeam2, useHomeFieldAdvantage);

        if (teamOneWonBool) {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else {
            mTeamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        }

        //Update team records based on outcome and mark match as complete
        setMatchWins();
        Log.d("MATCHCOMP", "FROMSIMTESTMATCH");
        matchComplete = true;

    }

    private void setMatchWins() {
        if (mTeamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES) {
            if (divisionalMatchup) {
                mTeam1.divisionalWin();
                mTeam2.divisionalLoss();
            }
            if (playoffMatchup) {
                mTeam2.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
            }
            mTeam1.win();
            mTeam2.lose();
        } else if (mTeamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO) {
            if (divisionalMatchup) {
                mTeam1.divisionalLoss();
                mTeam2.divisionalWin();
            }
            if (playoffMatchup) {
                mTeam1.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
            }
            mTeam1.lose();
            mTeam2.win();
        } else {
            mTeam1.draw();
            mTeam2.draw();
        }
    }

    public Integer getTeamOneWon() {
        return mTeamOneWon;
    }

    protected void setTeam1Score(int score) {
        mTeam1Score = score;
        mTeam1.addPointsFor(score);
        mTeam2.addPointsAllowed(score);
    }

    protected void setmTeam2Score(int score) {
        mTeam2Score = score;
        mTeam2.addPointsFor(score);
        mTeam1.addPointsAllowed(score);
    }

    public void setUri(Uri uri) {

        matchUri = uri;
    }

    public Team getTeam1() {
        return mTeam1;
    }

    public Team getTeam2() {
        return mTeam2;
    }

    public int getTeam1Score() {
        return mTeam1Score;
    }

    public int getTeam2Score() {
        return mTeam2Score;
    }

    public Uri getUri() {
        return matchUri;
    }

    public int getWeek() {
        return mWeek;
    }

    public Double getTeamTwoOdds() {
        return mTeamTwoOdds;
    }

    public void setMatchUpdated(Boolean matchUpdated) {
        mMatchUpdated = matchUpdated;
    }

    public Boolean getMatchUpdated() {
        return mMatchUpdated;
    }

    public int getCurrentSeason() {
        return mCurrentSeason;
    }

    public boolean getComplete() {
        return matchComplete;
    }

    public void setOdds(Double teamTwoOdds) {
        mTeamTwoOdds = teamTwoOdds;
        mData.updateMatchOddsCallback(this, matchUri);
    }

    public double getOdds(){
        return mTeamTwoOdds;
    }


}
