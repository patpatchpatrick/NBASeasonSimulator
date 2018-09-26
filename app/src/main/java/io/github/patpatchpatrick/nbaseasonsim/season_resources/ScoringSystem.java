package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class ScoringSystem {

    //Scoring system to determine winners and losers scores


    protected static int getLosingScore() {

        //Calculate a random double between 0 and 1 for the losing score percentage
        //Based on historical scores, depending on what the random value is, generate a corresponding historical score
        //The double will give you the range that the score falls within, and then a random number between 1-9 will be added to it
        double loserScorePercentage = (double) Math.random();
        int losingScoreValue;

        if (loserScorePercentage <= 0.01) {
            losingScoreValue = 70;
        } else if (loserScorePercentage <= 0.079) {
            losingScoreValue = 80;
        } else if (loserScorePercentage <= 0.292) {
            losingScoreValue = 90;
        } else if (loserScorePercentage <= 0.606) {
            losingScoreValue = 100;
        } else if (loserScorePercentage <= 0.860) {
            losingScoreValue = 110;
        } else if (loserScorePercentage <= 0.975) {
            losingScoreValue = 120;
        } else if (loserScorePercentage <= 0.994) {
            losingScoreValue = 130;
        } else {
            losingScoreValue = 140;
        }

        return losingScoreValue + (int) (Math.random() * 10);


    }

    protected static int getWinningScore(int losingScore, double matchOutcome, double probTeamOneWin) {
        
        //Assign winning score percentage to the same percentage value as the losing score
        //Then, add a random number between the winning score percentage to determine winning margin
        //If determine what the winning score will be
        //If the winning score is within the same range of 10 value as the losing score, then add a random number (within this 10 value) to ensure it is higher than the losing score

        //Calculate the margin of victory
        //The margin of victory is randomly calculated by dividing the match outcome (random double between 0 and 1)
        // by the probability of team one winning.  This will give you a random percentage between 0 and 100
        // This random percentage will be used to determine the margin of victory
        double marginOfVictory;
        if (matchOutcome <= probTeamOneWin) {
            marginOfVictory = matchOutcome / probTeamOneWin;
        } else {
            marginOfVictory = (matchOutcome - probTeamOneWin) / (1.0 - probTeamOneWin);
        }

        double winningScorePercentage;
        int winningScoreValue;

        if (losingScore < 80){
            winningScorePercentage = 0.01;
        } else if (losingScore < 90){
            winningScorePercentage = 0.03;
        } else if (losingScore < 100) {
            winningScorePercentage = 0.079;
        } else if (losingScore < 110) {
            winningScorePercentage = 0.292;
        } else if (losingScore < 120) {
            winningScorePercentage = 0.606;
        } else if (losingScore < 130) {
            winningScorePercentage = 0.860;
        } else if (losingScore < 140) {
            winningScorePercentage = 0.975;
        } else {
            winningScorePercentage = 0.994;
        }

        winningScorePercentage = winningScorePercentage + marginOfVictory * (1.0 - winningScorePercentage);

        if (winningScorePercentage <= 0.01) {
            winningScoreValue = 70;
        } else if (winningScorePercentage <= 0.079) {
            winningScoreValue = 80;
        } else if (winningScorePercentage <= 0.292) {
            winningScoreValue = 90;
        } else if (winningScorePercentage <= 0.606) {
            winningScoreValue = 100;
        } else if (winningScorePercentage <= 0.860) {
            winningScoreValue = 110;
        } else if (winningScorePercentage <= 0.975) {
            winningScoreValue = 120;
        } else if (winningScorePercentage <= 0.994) {
            winningScoreValue = 130;
        } else {
            winningScoreValue = 140;
        }

        //If the random winning score is less than or equal to the losing score,  add a random number to the losing score between the next 10 value
        //Make this the value of the winning score
        //If the winning score is still  less  than or equal to the losing score after adding this, then the winning score should just be the losing score plus one
        if (winningScoreValue <= losingScore){
            int margin = (int)((10 - (losingScore - (Math.floor((double)losingScore / 10.0) * 10))) * Math.random());
            winningScoreValue = losingScore + margin;
            if (winningScoreValue <= losingScore){
                winningScoreValue = losingScore + 1;
            }
        } else {
            winningScoreValue = winningScoreValue + (int)(Math.random() * 10);
        }
        return winningScoreValue;
    }


}
