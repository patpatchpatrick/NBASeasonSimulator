package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class ScoringSystem {

    //Scoring system to determine winners and losers scores

    //Values for losers scores
    //The values represent the total number of historical games  played with that score
    //For example, 1400 games have been played with a losing score of zero
    //30 games have been played with a losing score of two (you can get this by subtracting LOSING_SCORE_ZERO from LOSING_SCORE_TWO
    //75 games have been played with a losing score of three LOSING_SCORE_THREE minus LOSING_SCORE_TWO
    //This pattern continues for all ints
    private static int LOSING_SCORE_ZERO = 1400;
    private static int LOSING_SCORE_TWO = 1430;
    private static int LOSING_SCORE_THREE = 2205;
    private static int LOSING_SCORE_FIVE = 2215;
    private static int LOSING_SCORE_SIX = 2885;
    private static int LOSING_SCORE_SEVEN = 4435;
    private static int LOSING_SCORE_EIGHT = 4470;
    private static int LOSING_SCORE_NINE = 4770;
    private static int LOSING_SCORE_TEN = 6170;
    private static int LOSING_SCORE_ELEVEN = 6200;
    private static int LOSING_SCORE_TWELVE = 6375;
    private static int LOSING_SCORE_THIRTEEN = 7375;
    private static int LOSING_SCORE_FOURTEEN = 8775;
    private static int LOSING_SCORE_FIFTEEN = 8875;
    private static int LOSING_SCORE_SIXTEEN = 9325;
    private static int LOSING_SCORE_SEVENTEEN = 10625;
    private static int LOSING_SCORE_EIGHTEEN = 10675;
    private static int LOSING_SCORE_NINETEEN = 10875;
    private static int LOSING_SCORE_TWENTY = 11675;
    private static int LOSING_SCORE_TWENTYONE = 12475;
    private static int LOSING_SCORE_TWENTYTWO = 12575;
    private static int LOSING_SCORE_TWENTYTHREE = 12875;
    private static int LOSING_SCORE_TWENTYFOUR = 13525;
    private static int LOSING_SCORE_TWENTYFIVE = 13575;
    private static int LOSING_SCORE_TWENTYSIX = 13675;
    private static int LOSING_SCORE_TWENTYSEVEN = 13975;
    private static int LOSING_SCORE_TWENTYEIGHT = 14275;
    private static int LOSING_SCORE_TWENTYNINE = 14300;
    private static int LOSING_SCORE_THIRTY = 14375;
    private static int LOSING_SCORE_THIRTYONE = 14575;
    private static int LOSING_SCORE_THIRTYTWO = 14595;
    private static int LOSING_SCORE_THIRTYTHREE = 14630;
    private static int LOSING_SCORE_THIRTYFOUR = 14700;
    private static int LOSING_SCORE_THIRTYFIVE = 14760;
    private static int LOSING_SCORE_THIRTYEIGHT = 14790;
    private static int LOSING_SCORE_FOURTYONE = 14810;

    //HashMap that maps the winning  score with the number of historical games that have had that score
    //For example, 3 teams have won games with a score of 2
    //72 teams  have won games with a score of 3 (75 - 3)
    //215 teams have won games with a score of 7 (415 - 200)
    //This pattern continues throughout.. the value in the hashmap is always added on to the value before it
    private static LinkedHashMap<Integer, Integer> WINNING_SCORE_MAP = new LinkedHashMap<Integer, Integer>() {{
        put(1, 0);
        put(2, 3);
        put(3, 75);
        put(4, 77);
        put(5, 79);
        put(6, 200);
        put(7, 415);
        put(8, 417);
        put(9, 530);
        put(10, 830);
        put(11,833);
        put(12, 930);
        put(13, 1380);
        put(14, 1830);
        put(15, 1930);
        put(16, 2350);
        put(17, 3200);
        put(18, 3250);
        put(19, 3550);
        put(20, 4500);
        put(21, 5150);
        put(22, 5300);
        put(23, 6000);
        put(24, 7100);
        put(25, 7200);
        put(26, 7580);
        put(27, 8600);
        put(28, 9300);
        put(29, 9450);
        put(30, 10000);
        put(31, 10900);
        put(32, 11000);
        put(33, 11250);
        put(34, 11900);
        put(35, 12350);
        put(36, 12450);
        put(37, 12775);
        put(38, 13275);
        put(39, 13325);
        put(40, 13425);
        put(41, 13725);
        put(42, 13950);
        put(43, 14000);
        put(44, 14100);
        put(45, 14325);
        put(46, 13330);
        put(47, 14375);
        put(48, 14475);
        put(49, 14550);
        put(50, 14555);
        put(51, 14600);
        put(52, 14650);
        put(53,  14655);
        put(54, 14660);
        put(55, 14675);
        put(56, 14700);
        put(57, 14703);
        put(58, 14706);
        put(59, 14715);
    }};


    protected static int getLosingScore() {

        //Calculate a random double between 0 and 1
        //Calculate the losing score by multiplying that random double by the total number of games played in history
        //This will give you a random game number
        //Using this random game number, you can randomly get an accurate historical losing score value using the losing score
        //integers as seen below
        double loserScorePercentage = (double) Math.random();
        double losingScoreValue = loserScorePercentage * (double) LOSING_SCORE_FOURTYONE;


        if (losingScoreValue > LOSING_SCORE_THIRTYEIGHT) {
            return 41;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTYFIVE) {
            return 38;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTYFOUR) {
            return 35;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTYTHREE) {
            return 34;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTYTWO) {
            return 33;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTYONE) {
            return 32;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTY) {
            return 31;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYNINE) {
            return 30;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYEIGHT) {
            return 29;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYSEVEN) {
            return 28;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYSIX) {
            return 27;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYFIVE) {
            return 26;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYFOUR) {
            return 25;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYTHREE) {
            return 24;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYTWO) {
            return 23;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTYONE) {
            return 22;
        }
        if (losingScoreValue > LOSING_SCORE_TWENTY) {
            return 21;
        }
        if (losingScoreValue > LOSING_SCORE_NINETEEN) {
            return 20;
        }
        if (losingScoreValue > LOSING_SCORE_EIGHTEEN) {
            return 19;
        }
        if (losingScoreValue > LOSING_SCORE_SEVENTEEN) {
            return 18;
        }
        if (losingScoreValue > LOSING_SCORE_SIXTEEN) {
            return 17;
        }
        if (losingScoreValue > LOSING_SCORE_FIFTEEN) {
            return 16;
        }
        if (losingScoreValue > LOSING_SCORE_FOURTEEN) {
            return 15;
        }
        if (losingScoreValue > LOSING_SCORE_THIRTEEN) {
            return 14;
        }
        if (losingScoreValue > LOSING_SCORE_TWELVE) {
            return 13;
        }
        if (losingScoreValue > LOSING_SCORE_ELEVEN) {
            return 12;
        }
        if (losingScoreValue > LOSING_SCORE_TEN) {
            return 11;
        }
        if (losingScoreValue > LOSING_SCORE_NINE) {
            return 10;
        }
        if (losingScoreValue > LOSING_SCORE_EIGHT) {
            return 9;
        }
        if (losingScoreValue > LOSING_SCORE_SEVEN) {
            return 8;
        }
        if (losingScoreValue > LOSING_SCORE_SIX) {
            return 7;
        }
        if (losingScoreValue > LOSING_SCORE_FIVE) {
            return 6;
        }
        if (losingScoreValue > LOSING_SCORE_THREE) {
            return 5;
        }
        if (losingScoreValue > LOSING_SCORE_TWO) {
            return 3;
        }
        if (losingScoreValue > LOSING_SCORE_ZERO) {
            return 2;
        }
        return 0;

    }

    protected static int getWinningScore(int losingScore, double matchOutcome, double probTeamOneWin) {

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

        //Determine what the minimum winning score needs to be based on the losing score
        int minimumWinningScore = losingScore + 1;

        //Get the game value of the minimum winning score (similar to how we calculate the losing score above)
        int winningScoreValue = WINNING_SCORE_MAP.get(minimumWinningScore);

        //Multiply the margin of victory by the difference between the minimum winning score value and the
        //maximum winning score value.  This will give you a random winning score proportional to the margin of victory
        int winningScoreValueWithMOV = winningScoreValue + (int) (marginOfVictory * (14715 - winningScoreValue));

        //Iterate through all winning score values, starting from the highest score,  to determine what
        //winning score corresponds to your winning score value
        int winningScore = 59;
        while (true) {
            if (winningScoreValueWithMOV > WINNING_SCORE_MAP.get(winningScore)){
                break;
            }
            winningScore--;
        }

        //The actual winning score that corresponds to the value that you get in your iteration above
        //is one more than the winning score that the while loop provides.   Therefore, add one to your winning score
        winningScore++;

        return winningScore;
    }


}
