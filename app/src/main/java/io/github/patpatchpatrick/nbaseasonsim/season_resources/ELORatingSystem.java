package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import java.util.ArrayList;

import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract;

public class ELORatingSystem {

    // ELO constant used to determine magnitude that ratings change when a team wins
    private static double constantK = 24;
    private static final double HOME_FIELD_ADVANTAGE_PROB = 0.079;

    private static final String LOG_TAG = ELORatingSystem.class.getSimpleName();

    public static boolean simulateMatch(Match match, Team teamOne, Team teamTwo, Boolean useHomeFieldAdvantage){

        double eloTeamOne = teamOne.getElo();
        double eloTeamTwo = teamTwo.getElo();

        //Simulate a match between two teams with two elo ratings
        //If the first team wins, return true, otherwise return false

        //Calculate probability of the first team winning (double between 0-1)
        double probTeamOneWin = probabilityOfTeamOneWinning(eloTeamOne, eloTeamTwo, useHomeFieldAdvantage);

        //Calculate a random double value between 0-1
        double matchOutcome = (double) Math.random();

        //If the random double is less than the probability of the first team winning, they won and set boolean to true
        //If not, they lost and set boolean to false
        int teamOneWon;
        if (matchOutcome <= probTeamOneWin) {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        }

        //Get the losers score
        int losingScore = ScoringSystem.getLosingScore();

        //Get the winners score
        int winningScore = ScoringSystem.getWinningScore(losingScore, matchOutcome, probTeamOneWin);

        //Set the scores in the match for the winners and losers
        if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES) {
            match.setTeam1Score(winningScore);
            match.setmTeam2Score(losingScore);
        } else {
            match.setTeam1Score(losingScore);
            match.setmTeam2Score(winningScore);
        }

        //Update the teams ratings based on the outcome, and return the outcome boolean
        updateRatings(teamOne, teamTwo, probTeamOneWin, teamOneWon);

        if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES){
            return true;
        } else {
            return false;
        }
    }

    public static boolean simulateTestMatch(Team teamOne, Team teamTwo, Boolean useHomeFieldAdvantage){

        //Simulate a test match when calculating playoff probabilities

        double eloTeamOne = teamOne.getElo();
        double eloTeamTwo = teamTwo.getElo();

        //Simulate a match between two teams with two elo ratings
        //If the first team wins, return true, otherwise return false

        //Calculate probability of the first team winning (double between 0-1)
        double probTeamOneWin = probabilityOfTeamOneWinning(eloTeamOne, eloTeamTwo, useHomeFieldAdvantage);

        //Calculate a random double value between 0-1
        double matchOutcome = (double) Math.random();

        //If the random double is less than the probability of the first team winning, they won and set boolean to true
        //If not, they lost and set boolean to false
        int teamOneWon;
        if (matchOutcome <= probTeamOneWin) {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        }

        if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES){
            return true;
        } else {
            return false;
        }

    }

    public static int completeCurrentSeasonMatch(Match match, int teamOneScore, int teamTwoScore){

        Team teamOne = match.getTeam1();
        Team teamTwo = match.getTeam2();

        double eloTeamOne = teamOne.getElo();
        double eloTeamTwo = teamTwo.getElo();
        double probTeamOneWin = probabilityOfTeamOneWinning(eloTeamOne, eloTeamTwo, true);

        match.setTeam1Score(teamOneScore);
        match.setmTeam2Score(teamTwoScore);

        int teamOneWon;
        if (teamOneScore > teamTwoScore){
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES;
        } else if (teamOneScore < teamTwoScore) {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO;
        } else {
            teamOneWon = SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_DRAW;
        }

        updateRatings(teamOne, teamTwo, probTeamOneWin, teamOneWon);

        return teamOneWon;
    }

    public static double probabilityOfTeamOneWinning(double eloTeamOne, double eloTeamTwo, boolean useHomeFieldAdvantage) {
        //Returns probably of team one winning based on their ELO
        //Team one is always the away team, so if home field advantage is being used in the calculation,
        // subtract the Home field advantage probability from their elo probability of winning

        if (useHomeFieldAdvantage){
            //Return the elo probability of team one winning minus the home field advantage probability
            //since team one is the away team
            //If the probability is negative, return 0.001 (1 percent chance of winning)
            double probOfTeamOneWinning = (1.0 /(1 + Math.pow(10,
                    (eloTeamTwo - eloTeamOne) / 400.0))) - HOME_FIELD_ADVANTAGE_PROB;
            if (probOfTeamOneWinning > 0){
                return probOfTeamOneWinning;
            } else {
                return 0.001;
            }

        } else {
            return 1.0 /(1 + Math.pow(10,
                    (eloTeamTwo - eloTeamOne) / 400.0));
        }

    }

    private static void updateRatings(Team teamOne, Team teamTwo, double probTeamOneWin, int teamOneWon){
        //Updates the ratings of teamOne and teamTwo, based on the outcome of match

        //Get the elos of the teams
        double eloTeamOne = teamOne.getElo();
        double eloTeamTwo = teamTwo.getElo();

        //Get the probability of team two winning from the probability of team one winning
        double probTeamTwoWin = (1.0 - probTeamOneWin);

        //Calculate the new elos for both teams depending on outcome of match
        if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_YES) {
            eloTeamOne = eloTeamOne + constantK * (1.0 - probTeamOneWin);
            eloTeamTwo = eloTeamTwo + constantK * (0.0 - probTeamTwoWin);
        } else if (teamOneWon == SeasonSimContract.MatchEntry.MATCH_TEAM_ONE_WON_NO){
            eloTeamOne = eloTeamOne + constantK * (0.0 - probTeamOneWin);
            eloTeamTwo = eloTeamTwo + constantK * (1.0 - probTeamTwoWin);
        }

        //Set the new ELO ratings for the teams
        teamOne.setElo(eloTeamOne);
        teamTwo.setElo(eloTeamTwo);

    }

    public static ArrayList<Integer> simulateMatchNoDbUpdates(Double teamOneElo, Double teamTwoElo){

        //Simulate a match without updating the database
        //This is used for the match predictor activity
        //Return an arraylist with the scores of both teams post-simulation

        double eloTeamOne = teamOneElo;
        double eloTeamTwo = teamTwoElo;

        //Simulate a match between two teams with two elo ratings
        //If the first team wins, return true, otherwise return false

        //Calculate probability of the first team winning (double between 0-1)
        double probTeamOneWin = probabilityOfTeamOneWinning(eloTeamOne, eloTeamTwo, true);

        //Calculate a random double value between 0-1
        double matchOutcome = (double) Math.random();

        //If the random double is less than the probability of the first team winning, they won and set boolean to true
        //If not, they lost and set boolean to false
        boolean teamOneWon;
        if (matchOutcome <= probTeamOneWin) {
            teamOneWon = true;
        } else {
            teamOneWon = false;
        }

        //Get the losers score
        int losingScore = ScoringSystem.getLosingScore();

        //Get the winners score
        int winningScore = ScoringSystem.getWinningScore(losingScore, matchOutcome, probTeamOneWin);

        //Set the scores in the arraylist for both teams
        //The first score in the arraylist is the team one score
        ArrayList<Integer> simulatedMatchScores = new ArrayList<>();
        if (teamOneWon) {
            simulatedMatchScores.add(winningScore);
            simulatedMatchScores.add(losingScore);
        } else {
            simulatedMatchScores.add(losingScore);
            simulatedMatchScores.add(winningScore);
        }

        return simulatedMatchScores;
    }

}
