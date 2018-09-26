package io.github.patpatchpatrick.nbaseasonsim.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class SeasonSimContract {

    //Contract for DB used to store data for this app

    //URI Information
    public static final String CONTENT_AUTHORITY = "io.github.patpatchpatrick.nbaseasonsim";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    public static final String PATH_TEAM = "team";
    public static final String PATH_MATCH = "match";

    private SeasonSimContract() {
    }

    public static final class TeamEntry implements BaseColumns {

        //The MIME type of the {@link #CONTENT_URI} for a list of teams.
        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_TEAM;


        //The MIME type of the {@link #CONTENT_URI} for a single team.
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_TEAM;

        //URI for Team Ratings table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_TEAM);


        //Define table and columns for team data
        public static final String TABLE_NAME = "team";

        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TEAM_NAME = "teamName";
        public static final String COLUMN_TEAM_SHORT_NAME = "teamShortName";
        public static final String COLUMN_TEAM_ELO = "teamELO";
        public static final String COLUMN_TEAM_DEFAULT_ELO = "teamDefaultELO";
        public static final String COLUMN_TEAM_USER_ELO = "teamUserELO";
        public static final String COLUMN_TEAM_RANKING = "teamRanking";
        public static final String COLUMN_TEAM_OFF_RATING = "teamOffensiveRating";
        public static final String COLUMN_TEAM_DEF_RATING = "teamDefensiveRating";
        public static final String COLUMN_TEAM_CURRENT_WINS = "teamWins";
        public static final String COLUMN_TEAM_CURRENT_LOSSES = "teamLosses";
        public static final String COLUMN_TEAM_CURRENT_DRAWS = "teamDraws";
        public static final String COLUMN_TEAM_WIN_LOSS_PCT = "teamWinLossPct";
        public static final String COLUMN_TEAM_DIV_WINS = "divisionalWins";
        public static final String COLUMN_TEAM_DIV_LOSSES = "divisionalLosses";
        public static final String COLUMN_TEAM_DIV_WIN_LOSS_PCT = "divisionalWinLossPct";
        public static final String COLUMN_TEAM_DIVISION = "teamDivision";
        public static final String COLUMN_TEAM_CONFERENCE = "teamConference";
        public static final String COLUMN_TEAM_PLAYOFF_ELIGIBILE = "playoffEligible";
        public static final String COLUMN_TEAM_PLAYOFF_GAME = "playoffGame";
        public static final String COLUMN_TEAM_CURRENT_SEASON = "currentSeason";

        //Define input variables for team divisions
        public static final int DIVISION_WESTERN_CONFERENCE = 1;
        public static final int DIVISION_EASTERN_CONFERENCE = 2;

        public static final String getDivisionString(int divisionInt){
            if (divisionInt == DIVISION_WESTERN_CONFERENCE){
                return "Western Conference";
            }
            if (divisionInt == DIVISION_EASTERN_CONFERENCE){
                return "Eastern Conference";
            }
                return null;
        }

        //Define input variables for team conference
        public static final int CONFERENCE_WESTERN = 1;
        public static final int CONFERENCE_EASTERN = 2;

        //Define input variables for playoff eligibility
        public static final int PLAYOFF_NOT_ELIGIBLE = 0;
        public static final int PLAYOFF_DIVISION_WINNER = 1;
        public static final int PLAYOFF_WILD_CARD = 2;

        //Define input variables for if team is from current season or not
        public static final int CURRENT_SEASON_NO = 1;
        public static final int CURRENT_SEASON_YES = 2;


    }

    public static final class MatchEntry implements BaseColumns{

        //The MIME type of the {@link #CONTENT_URI} for a list of matches.
        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MATCH;


        //The MIME type of the {@link #CONTENT_URI} for a single match.
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_MATCH;

        //URI for Matches table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI,  PATH_MATCH);


        //Define table and columns for streaks data
        public static final String TABLE_NAME = "Matches";

        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_MATCH_TEAM_ONE = "matchTeamOne";
        public static final String COLUMN_MATCH_TEAM_TWO = "matchTeamTwo";
        public static final String COLUMN_MATCH_TEAM_ONE_SCORE = "matchTeamOneScore";
        public static final String COLUMN_MATCH_TEAM_TWO_SCORE = "matchTeamTwoScore";
        public static final String COLUMN_MATCH_TEAM_ONE_WON = "matchTeamOneWon";
        public static final String COLUMN_MATCH_TEAM_TWO_ODDS = "matchTeamTwoOdds";
        public static final String COLUMN_MATCH_WEEK = "matchWeek";
        public static final String COLUMN_MATCH_CURRENT_SEASON = "matchCurrentSeason";
        public static final String COLUMN_MATCH_COMPLETE = "matchComplete";


        //Define input variables for match table
        public static final int MATCH_COMPLETE_NO = 0;
        public static final int MATCH_COMPLETE_YES = 1;

        //Define input variables for match table
        public static final int MATCH_TEAM_ONE_WON_NO = 0;
        public static final int MATCH_TEAM_ONE_WON_YES = 1;
        public static final int MATCH_TEAM_ONE_WON_DRAW = 2;

        //Define input variables for match playoff weeks
        public static final int MATCH_WEEK_FIRST_ROUND = 25;
        public static final int MATCH_WEEK_CONFERENCE_SEMIFINALS = 26;
        public static final int MATCH_WEEK_CONFERENCE_FINALS = 27;
        public static final int MATCH_WEEK_NBA_FINALS = 28;

        //Define input variables for no odds set
        public static final double MATCH_NO_ODDS_SET = 50.0;

        //Define input variables for if match is from current season
        public static final int MATCH_TEAM_CURRENT_SEASON_NO = 1;
        public static final int MATCH_TEAM_CURRENT_SEASON_YES = 2;




    }
}
