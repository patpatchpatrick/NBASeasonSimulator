package io.github.patpatchpatrick.nbaseasonsim.presenter;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.SimulatorActivity;
import io.github.patpatchpatrick.nbaseasonsim.R;
import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.BaseView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.ScoreView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.SimulatorMvpContract;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.MatchEntry;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Data;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.ELORatingSystem;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Match;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.NBAConstants;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Schedule;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Week;

public class SimulatorPresenter extends BasePresenter<SimulatorMvpContract.SimulatorView>
        implements SimulatorMvpContract.SimulatorPresenter, Data {

    //Simulator presenter class is used to communicate between the SimulatorActivity (view) and the Model (MVP Architecture)

    @Inject
    SimulatorModel mModel;

    @Inject
    SharedPreferences mSharedPreferences;

    @Inject
    Context mContext;

    @Inject
    BaseView mHomeScreenBaseView;

    @Inject
    ArrayList<BaseView> mBaseViews;

    @Inject
    ArrayList<ScoreView> mScoreViews;

    private static int mCurrentSimulatorWeek;
    private static int mCurrentSeasonWeek;
    private static Boolean mSeasonInitialized = false;
    private static Boolean mSimulatorPlayoffsStarted = false;
    private static Boolean mCurrentSeasonPlayoffsStarted = false;
    public static Boolean mCurrentSeasonMatchesLoaded = false;
    public static Boolean mTestSimulation = false;
    public static int mTotalTestSimulations;
    public static int mCurrentTestSimulations;
    private static final int SEASON_TYPE_CURRENT = 1;
    private static final int SEASON_TYPE_SIMULATOR = 2;

    public SimulatorPresenter(SimulatorMvpContract.SimulatorView view) {
        super(view);
    }

    public SimulatorPresenter() {
        super();
    }

    public void setView(SimulatorMvpContract.SimulatorView view) {
        super.setView(view);
    }

    @Override
    public void simulateWeek() {

        //Simulate a single week
        Week currentWeek = mModel.getSimulatorSchedule().getWeek(mCurrentSimulatorWeek);
        currentWeek.simulate(true);

        ArrayList<Match> currentWeekMatches = currentWeek.getMatches();

        //After the week is complete, generate the playoff teams and query the standings (and display them)
        generateAndSetPlayoffSeeds(SimulatorPresenter.SEASON_TYPE_SIMULATOR);
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_PLAYOFF);

        Log.d("Post Stand Query Num", "" + currentWeek.getNumberMatchesUpdated());
        //Query the week scores and display them
        mModel.querySimulatorMatches(mCurrentSimulatorWeek, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        //Week is complete so increment the current week value
        mCurrentSimulatorWeek++;
        this.view.setCurrentWeekPreference(mCurrentSimulatorWeek);
    }

    @Override
    public void simulatePlayoffWeek() {
        //Simulate a single playoff week

        mModel.getSimulatorSchedule().getWeek(mCurrentSimulatorWeek).simulatePlayoffSeries(true);

        //After the week is complete, query the standings (and display them)
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_POSTSEASON);
        //Week is complete so increment the current week value
        mCurrentSimulatorWeek++;
        this.view.setCurrentWeekPreference(mCurrentSimulatorWeek);
    }

    private void generateAndSetPlayoffSeeds(int seasonType) {

        //Generate the playoff teams and set the playoff seeds

        ArrayList<ArrayList<Team>> allPlayoffTeams;
        if (seasonType == SimulatorPresenter.SEASON_TYPE_CURRENT) {
            allPlayoffTeams = generateSimulatorPlayoffTeams(SimulatorPresenter.SEASON_TYPE_CURRENT);
        } else {
            allPlayoffTeams = generateSimulatorPlayoffTeams(SimulatorPresenter.SEASON_TYPE_SIMULATOR);
        }
        ArrayList<Team> westernConferencePlayoffTeams = allPlayoffTeams.get(0);
        ArrayList<Team> easternConferencePlayoffTeams = allPlayoffTeams.get(1);
        ArrayList<Team> seasonTeams;

        if (seasonType == SimulatorPresenter.SEASON_TYPE_CURRENT) {
            seasonTeams = mModel.getSeasonTeamArrayList();
        } else {
            seasonTeams = mModel.getSimulatorTeamArrayList();
        }

        for (Team team : seasonTeams) {
            team.setPlayoffEligible(0);
        }
        int i = 0;
        while (i < 8) {
            westernConferencePlayoffTeams.get(i).setPlayoffEligible(i + 1);
            i++;
        }
        i = 0;
        while (i < 8) {
            easternConferencePlayoffTeams.get(i).setPlayoffEligible(i + 1);
            i++;
        }

    }

    @Override
    public void queryCurrentSeasonStandings() {

        //Generate the playoff teams and set the playoff seeds
        generateAndSetPlayoffSeeds(SimulatorPresenter.SEASON_TYPE_CURRENT);

        //Query playoff teams
        mModel.queryCurrentSeasonStandings(SimulatorModel.QUERY_STANDINGS_PLAYOFF);

    }

    @Override
    public void initializeSeason() {
        //Set current week to 1 for both the simulator and current season and create teams for both
        mCurrentSimulatorWeek = 1;
        setCurrentSimulatorWeekPreference(mCurrentSimulatorWeek);
        createSimulatorTeams();

        mCurrentSeasonWeek = 1;
        setCurrentSeasonWeekPreference(mCurrentSeasonWeek);
        createSeasonTeams();

        //Insert teams into database.  After teams are inserted, the simulatorTeamsInserted() callback is
        //received from the model
        mModel.insertSimulatorTeams();
    }

    @Override
    public void initiatePlayoffs() {
        mCurrentSimulatorWeek = 25;
        this.view.setCurrentWeekPreference(mCurrentSimulatorWeek);
        //Initiate the playoffs
        //Query the standings from the playoffs standings and the rest of the playoffs is initiated via the
        // standingsQueried method
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_POSTSEASON);

    }

    @Override
    public void loadSeasonFromDatabase() {
        //Load season from database for both simulator activity and current season (create teams and schedule)
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_LOAD_SEASON);
        mModel.queryCurrentSeasonStandings(SimulatorModel.QUERY_STANDINGS_LOAD_SEASON);

        //Season has been loaded, so set preference to true
        setSeasonLoadedPreference(true);

        mHomeScreenBaseView.onSeasonLoadedFromDb();

        //Notify all baseViews that the season was loaded
        for (BaseView baseView : mBaseViews) {
            baseView.onSeasonLoadedFromDb();
        }


    }

    @Override
    public void loadAlreadySimulatedData() {
        //Generate the playoff seeds and load the standings, as well as the matches that have already been simulated (last week's matches)
        generateAndSetPlayoffSeeds(SimulatorPresenter.SEASON_TYPE_SIMULATOR);
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_PLAYOFF);
        //Query all weeks that have already occurred;
        mModel.querySimulatorMatches(mCurrentSimulatorWeek - 1, false, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
    }

    @Override
    public void loadAlreadySimulatedPlayoffData() {
        //Query playoff data
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_LOAD_POSTSEASON);
    }

    @Override
    public void simulatorTeamsInserted() {
        //After the teams are inserted into the DB, create the season schedule and insert the
        //schedule matches into the DB
        //After the matches are inserted into the DB, the simulatorMatchesInserted() callback is received
        //from the model
        createSimulatorSchedule();

        //Set the teams elo types based on user selected preference
        setSimulatorTeamEloType();

        mModel.insertSimulatorMatches(SimulatorModel.INSERT_MATCHES_SCHEDULE);

    }

    @Override
    public void seasonTeamsInserted() {

        //Set current season teams to use current season elos
        resetSimulatorTeamCurrentSeasonElos();
        createSeasonSchedule();
        mModel.insertSeasonMatches(SimulatorModel.INSERT_MATCHES_SCHEDULE);

    }

    @Override
    public void addBaseView(BaseView baseView) {
        //Add a new baseView to the list of baseViews to notify when items are changed
        mBaseViews.add(baseView);
    }

    @Override
    public void loadCurrentSeasonMatches() {
        //Load all the current  season matches
        //If they are complete, they have already been completed/loaded so no need to complete them again


    }

    @Override
    public void loadCurrentSeasonPlayoffOdds() {
        HashMap<String, Team> currentSeasonTeams = mModel.getSeasonTeamList();
        currentSeasonTeams.get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING).setPlayoffOddsString("7.73-2.12-0.05-0.02");
        currentSeasonTeams.get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING).setPlayoffOddsString("11.01-2.97-0.19-0.07");
        currentSeasonTeams.get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING).setPlayoffOddsString("83.64-52.32-14.74-7.79");
        currentSeasonTeams.get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING).setPlayoffOddsString("0.36-0.03-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING).setPlayoffOddsString("98.39-97.37-30.9-16.9");
        currentSeasonTeams.get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING).setPlayoffOddsString("94.32-88.38-29.01-16.68");
        currentSeasonTeams.get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING).setPlayoffOddsString("0.02-0-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING).setPlayoffOddsString("3.29-0.77-0.03-0");
        currentSeasonTeams.get(NBAConstants.TEAM_BOSTON_CELTICS_STRING).setPlayoffOddsString("35.69-17.37-2.1-0.94");
        currentSeasonTeams.get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING).setPlayoffOddsString("0.64-0.22-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_MIAMI_HEAT_STRING).setPlayoffOddsString("97.32-87.03-31.26-15.46");
        currentSeasonTeams.get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING).setPlayoffOddsString("28.27-13.63-1.87-0.83");
        currentSeasonTeams.get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING).setPlayoffOddsString("95.58-92.48-27.35-13.53");
        currentSeasonTeams.get(NBAConstants.TEAM_BROOKLYN_NETS_STRING).setPlayoffOddsString("65.55-47.03-6.71-2.56");
        currentSeasonTeams.get(NBAConstants.TEAM_DETROIT_PISTONS_STRING).setPlayoffOddsString("2.14-0.73-0.02-0.01");
        currentSeasonTeams.get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING).setPlayoffOddsString("37.25-21.3-1.61-0.45");
        currentSeasonTeams.get(NBAConstants.TEAM_INDIANA_PACERS_STRING).setPlayoffOddsString("76.01-39.44-9.66-4.62");
        currentSeasonTeams.get(NBAConstants.TEAM_PHOENIX_SUNS_STRING).setPlayoffOddsString("7.3-2.02-0.05-0.03");
        currentSeasonTeams.get(NBAConstants.TEAM_UTAH_JAZZ_STRING).setPlayoffOddsString("2.27-0.52-0.01-0");
        currentSeasonTeams.get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING).setPlayoffOddsString("47.98-30.82-4.63-1.89");
        currentSeasonTeams.get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING).setPlayoffOddsString("44.58-8.69-2.2-0.66");
        currentSeasonTeams.get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING).setPlayoffOddsString("39.07-4.26-1.51-0.45");
        currentSeasonTeams.get(NBAConstants.TEAM_CHICAGO_BULLS_STRING).setPlayoffOddsString("70.34-48.59-8.5-3.91");
        currentSeasonTeams.get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING).setPlayoffOddsString("89.48-78.49-23.12-11.88");
        currentSeasonTeams.get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING).setPlayoffOddsString("1.22-0.44-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING).setPlayoffOddsString("48.4-19.28-1.21-0.35");
        currentSeasonTeams.get(NBAConstants.TEAM_DENVER_NUGGETS_STRING).setPlayoffOddsString("2.02-0.86-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING).setPlayoffOddsString("27.61-8.17-0.66-0.15");
        currentSeasonTeams.get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING).setPlayoffOddsString("0.37-0.08-0-0");
        currentSeasonTeams.get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING).setPlayoffOddsString("41.36-20.42-1.51-0.5");
    }

    public void addScoreView(ScoreView scoreView) {
        mScoreViews.add(scoreView);
    }

    @Override
    public void simulatorMatchesInserted(int insertType) {

        //Callback received after matches are inserted into the database in the model
        //An action is performed below depending on the insertType

        if (insertType == SimulatorModel.INSERT_MATCHES_SCHEDULE) {
            //After simulator season is initialized, teams are inserted, and matches inserted, 
            //insert season teams to finish initializing the current season

            updateSimulatorCompletedGameScores();

            mModel.insertSeasonTeams();

        }

        if (insertType == SimulatorModel.INSERT_MATCHES_PLAYOFFS_FIRSTROUND) {
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_FIRST_ROUND, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }

        if (insertType == SimulatorModel.INSERT_MATCHES_PLAYOFFS_CONF_SEMIS) {
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
        if (insertType == SimulatorModel.INSERT_MATCHES_PLAYOFFS_CONF_FINALS) {
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
        if (insertType == SimulatorModel.INSERT_MATCHES_PLAYOFFS_NBA_FINALS) {
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_NBA_FINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
    }

    private void updateSimulatorCompletedGameScores() {
        //Update the scores for games that have already occurred
        mCurrentSimulatorWeek = 0;
        mCurrentSimulatorWeek++;
        //Set current week preference when week is updated
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(mContext.getString(R.string.settings_simulator_week_num_key), mCurrentSimulatorWeek).apply();
        prefs.commit();


    }

    @Override
    public void seasonMatchesInserted(int insertType) {

        //Callback received after current season matches are inserted into the database in the model
        //An action is performed below depending on the insertType

        if (insertType == SimulatorModel.INSERT_MATCHES_SCHEDULE) {

            updateCurrentSeasonTeamOdds();

            //After current season is initialized, set season initialized pref to true
            //Set season loading preference to true as well, since initializing a season also loads it
            //Notify all base views that the season was initialized
            setSeasonInitializedPreference(true);
            setSeasonLoadedPreference(true);
            for (BaseView baseView : mBaseViews) {
                baseView.onSeasonInitialized();
            }

        }

    }

    @Override
    public void simulatorMatchesQueried(int queryType, Cursor matchesCursor, int queriedFrom) {

        //If you are not querying all matches, put the match score data in a string and display it in the main activity
        //If all matches are being queried, the ELSE statement below will be hit and the entire schedule will loaded and created
        //from the data queried in the database

        if (queryType != SimulatorModel.QUERY_MATCHES_ALL) {

            //Load match data into a string to be displayed in the main activity
            //Set the string header depending on the week number that was queried

            int weekNumber = queryType;

            String scoreWeekNumberHeader;

            scoreWeekNumberHeader = "Week " + weekNumber;
            if (queryType == MatchEntry.MATCH_WEEK_FIRST_ROUND) {
                scoreWeekNumberHeader = "Playoffs First Round";
            }
            if (queryType == MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS) {
                scoreWeekNumberHeader = "Playoffs Conference Semi-Finals";
            }
            if (queryType == MatchEntry.MATCH_WEEK_CONFERENCE_FINALS) {
                scoreWeekNumberHeader = "Playoffs Conference Finals";
            }
            if (queryType == MatchEntry.MATCH_WEEK_NBA_FINALS) {
                scoreWeekNumberHeader = "Playoffs NBA Finals";
            }

            matchesCursor.moveToPosition(0);

            for (ScoreView scoreView : mScoreViews) {
                scoreView.onDisplayScores(queryType, matchesCursor, scoreWeekNumberHeader, queriedFrom);
            }


        } else {

            //This else statement is hit if ALL matches are queried
            //Initialize all schedule, weeks, and matches.  Add weeks to schedule.

            Schedule seasonSchedule = new Schedule();
            Week weekOne = new Week(1);
            Week weekTwo = new Week(2);
            Week weekThree = new Week(3);
            Week weekFour = new Week(4);
            Week weekFive = new Week(5);
            Week weekSix = new Week(6);
            Week weekSeven = new Week(7);
            Week weekEight = new Week(8);
            Week weekNine = new Week(9);
            Week weekTen = new Week(10);
            Week weekEleven = new Week(11);
            Week weekTwelve = new Week(12);
            Week weekThirteen = new Week(13);
            Week weekFourteen = new Week(14);
            Week weekFifteen = new Week(15);
            Week weekSixteen = new Week(16);
            Week weekSeventeen = new Week(17);
            Week weekEighteen = new Week(18);
            Week weekNineteen = new Week(19);
            Week weekTwenty = new Week(20);
            Week weekTwentyOne = new Week(21);
            Week weekTwentyTwo = new Week(22);
            Week weekTwentyThree = new Week(23);
            Week weekTwentyFour = new Week(24);
            Week firstRound = new Week(MatchEntry.MATCH_WEEK_FIRST_ROUND);
            Week confSemifinals = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS);
            Week confFinals = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS);
            Week nbaFinals = new Week(MatchEntry.MATCH_WEEK_NBA_FINALS);


            //Get matches from database cursor and add them to the schedule
            matchesCursor.moveToPosition(-1);
            while (matchesCursor.moveToNext()) {

                String teamOne = matchesCursor.getString(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE));
                String teamTwo = matchesCursor.getString(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO));
                int teamOneWon = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE_WON));
                int matchWeek = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_WEEK));
                int matchComplete = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_COMPLETE));
                double teamTwoOdds = matchesCursor.getDouble(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS));
                int ID = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry._ID));
                Uri matchUri = ContentUris.withAppendedId(MatchEntry.CONTENT_URI, ID);

                HashMap<String, Team> teamList = mModel.getSimulatorTeamList();

                if (teamList != null) {

                    switch (matchWeek) {

                        case 1:
                            weekOne.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 1, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 2:
                            weekTwo.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 2, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 3:
                            weekThree.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 3, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 4:
                            weekFour.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 4, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 5:
                            weekFive.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 5, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 6:
                            weekSix.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 6, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 7:
                            weekSeven.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 7, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 8:
                            weekEight.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 8, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 9:
                            weekNine.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 9, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 10:
                            weekTen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 10, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 11:
                            weekEleven.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 11, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 12:
                            weekTwelve.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 12, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 13:
                            weekThirteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 13, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 14:
                            weekFourteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 14, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 15:
                            weekFifteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 15, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 16:
                            weekSixteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 16, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 17:
                            weekSeventeen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 17, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 18:
                            weekEighteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 18, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 19:
                            weekNineteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 19, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 20:
                            weekTwenty.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 20, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 21:
                            weekTwentyOne.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 21, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 22:
                            weekTwentyTwo.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 22, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 23:
                            weekTwentyThree.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 23, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 24:
                            weekTwentyFour.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 24, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 25:
                            firstRound.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 25, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 26:
                            confSemifinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 26, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 27:
                            confFinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 27, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;
                        case 28:
                            nbaFinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 28, this, matchUri, teamTwoOdds, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, matchComplete));
                            break;

                    }
                }

            }

            matchesCursor.close();

            seasonSchedule.addWeek(weekOne);
            seasonSchedule.addWeek(weekTwo);
            seasonSchedule.addWeek(weekThree);
            seasonSchedule.addWeek(weekFour);
            seasonSchedule.addWeek(weekFive);
            seasonSchedule.addWeek(weekSix);
            seasonSchedule.addWeek(weekSeven);
            seasonSchedule.addWeek(weekEight);
            seasonSchedule.addWeek(weekNine);
            seasonSchedule.addWeek(weekTen);
            seasonSchedule.addWeek(weekEleven);
            seasonSchedule.addWeek(weekTwelve);
            seasonSchedule.addWeek(weekThirteen);
            seasonSchedule.addWeek(weekFourteen);
            seasonSchedule.addWeek(weekFifteen);
            seasonSchedule.addWeek(weekSixteen);
            seasonSchedule.addWeek(weekSeventeen);
            seasonSchedule.addWeek(weekEighteen);
            seasonSchedule.addWeek(weekNineteen);
            seasonSchedule.addWeek(weekTwenty);
            seasonSchedule.addWeek(weekTwentyOne);
            seasonSchedule.addWeek(weekTwentyTwo);
            seasonSchedule.addWeek(weekTwentyThree);
            seasonSchedule.addWeek(weekTwentyFour);

            if (mCurrentSimulatorWeek >= 25 && mSimulatorPlayoffsStarted) {
                seasonSchedule.addWeek(firstRound);
            }
            if (mCurrentSimulatorWeek >= 26 && mSimulatorPlayoffsStarted) {
                seasonSchedule.addWeek(confSemifinals);
            }
            if (mCurrentSimulatorWeek >= 27 && mSimulatorPlayoffsStarted) {
                seasonSchedule.addWeek(confFinals);
            }
            if (mCurrentSimulatorWeek >= 28 && mSimulatorPlayoffsStarted) {
                seasonSchedule.addWeek(nbaFinals);
            }


            mModel.setSimulatorSchedule(seasonSchedule);

            if (mSimulatorPlayoffsStarted) {
                //If the playoffs have already started, re-query the playoff standings  after all matches are created
                mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_LOAD_POSTSEASON);
            }
        }

    }

    @Override
    public void currentSeasonMatchesQueried(int queryType, Cursor matchesCursor, int queriedFrom) {

        //If you are not querying all matches, put the match score data in a string and display it in the main activity
        //If all matches are being queried, the ELSE statement below will be hit and the entire schedule will loaded and created
        //from the data queried in the database

        if (queryType != SimulatorModel.QUERY_MATCHES_ALL) {

            //Load match data into a string to be displayed in the main activity
            //Set the string header depending on the week number that was queried

            int weekNumber = queryType;

            String scoreWeekNumberHeader;

            scoreWeekNumberHeader = "Week " + weekNumber;
            if (queryType == MatchEntry.MATCH_WEEK_FIRST_ROUND) {
                scoreWeekNumberHeader = "Playoffs First Round";
            }
            if (queryType == MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS) {
                scoreWeekNumberHeader = "Playoffs Conference Semi-Finals";
            }
            if (queryType == MatchEntry.MATCH_WEEK_CONFERENCE_FINALS) {
                scoreWeekNumberHeader = "Playoffs Conference Finals";
            }
            if (queryType == MatchEntry.MATCH_WEEK_NBA_FINALS) {
                scoreWeekNumberHeader = "Playoffs NBA Finals";
            }

            matchesCursor.moveToPosition(0);

            for (ScoreView scoreView : mScoreViews) {
                scoreView.onDisplayScores(queryType, matchesCursor, scoreWeekNumberHeader, queriedFrom);
            }


        } else {

            //This else statement is hit if ALL matches are queried
            //Initialize all schedule, weeks, and matches.  Add weeks to schedule.

            Schedule seasonSchedule = new Schedule();
            Week weekOne = new Week(1);
            Week weekTwo = new Week(2);
            Week weekThree = new Week(3);
            Week weekFour = new Week(4);
            Week weekFive = new Week(5);
            Week weekSix = new Week(6);
            Week weekSeven = new Week(7);
            Week weekEight = new Week(8);
            Week weekNine = new Week(9);
            Week weekTen = new Week(10);
            Week weekEleven = new Week(11);
            Week weekTwelve = new Week(12);
            Week weekThirteen = new Week(13);
            Week weekFourteen = new Week(14);
            Week weekFifteen = new Week(15);
            Week weekSixteen = new Week(16);
            Week weekSeventeen = new Week(17);
            Week weekEighteen = new Week(18);
            Week weekNineteen = new Week(19);
            Week weekTwenty = new Week(20);
            Week weekTwentyOne = new Week(21);
            Week weekTwentyTwo = new Week(22);
            Week weekTwentyThree = new Week(23);
            Week weekTwentyFour = new Week(24);
            Week firstRound = new Week(MatchEntry.MATCH_WEEK_FIRST_ROUND);
            Week confSemifinals = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS);
            Week confFinals = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS);
            Week nbaFinals = new Week(MatchEntry.MATCH_WEEK_NBA_FINALS);


            //Get matches from database cursor and add them to the schedule
            matchesCursor.moveToPosition(-1);
            while (matchesCursor.moveToNext()) {

                String teamOne = matchesCursor.getString(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE));
                String teamTwo = matchesCursor.getString(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO));
                int teamOneWon = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE_WON));
                int matchWeek = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_WEEK));
                int matchComplete = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_COMPLETE));
                double teamTwoOdds = matchesCursor.getDouble(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS));
                int currentSeason = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_CURRENT_SEASON));
                int ID = matchesCursor.getInt(matchesCursor.getColumnIndexOrThrow(MatchEntry._ID));
                Uri matchUri = ContentUris.withAppendedId(MatchEntry.CONTENT_URI, ID);

                HashMap<String, Team> teamList = mModel.getSeasonTeamList();

                if (teamList != null) {

                    switch (matchWeek) {

                        case 1:
                            weekOne.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 1, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 2:
                            weekTwo.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 2, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 3:
                            weekThree.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 3, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 4:
                            weekFour.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 4, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 5:
                            weekFive.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 5, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 6:
                            weekSix.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 6, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 7:
                            weekSeven.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 7, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 8:
                            weekEight.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 8, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 9:
                            weekNine.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 9, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 10:
                            weekTen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 10, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 11:
                            weekEleven.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 11, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 12:
                            weekTwelve.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 12, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 13:
                            weekThirteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 13, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 14:
                            weekFourteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 14, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 15:
                            weekFifteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 15, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 16:
                            weekSixteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 16, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 17:
                            weekSeventeen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 17, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 18:
                            weekEighteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 18, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 19:
                            weekNineteen.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 19, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 20:
                            weekTwenty.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 20, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 21:
                            weekTwentyOne.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 21, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 22:
                            weekTwentyTwo.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 22, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 23:
                            weekTwentyThree.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 23, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 24:
                            weekTwentyFour.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 24, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 25:
                            firstRound.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 25, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 26:
                            confSemifinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 26, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 27:
                            confFinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 27, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;
                        case 28:
                            nbaFinals.addMatch(new Match(teamList.get(teamOne), teamList.get(teamTwo), teamOneWon, 28, this, matchUri, teamTwoOdds, currentSeason, matchComplete));
                            break;

                    }
                }

            }

            matchesCursor.close();

            seasonSchedule.addWeek(weekOne);
            seasonSchedule.addWeek(weekTwo);
            seasonSchedule.addWeek(weekThree);
            seasonSchedule.addWeek(weekFour);
            seasonSchedule.addWeek(weekFive);
            seasonSchedule.addWeek(weekSix);
            seasonSchedule.addWeek(weekSeven);
            seasonSchedule.addWeek(weekEight);
            seasonSchedule.addWeek(weekNine);
            seasonSchedule.addWeek(weekTen);
            seasonSchedule.addWeek(weekEleven);
            seasonSchedule.addWeek(weekTwelve);
            seasonSchedule.addWeek(weekThirteen);
            seasonSchedule.addWeek(weekFourteen);
            seasonSchedule.addWeek(weekFifteen);
            seasonSchedule.addWeek(weekSixteen);
            seasonSchedule.addWeek(weekSeventeen);
            seasonSchedule.addWeek(weekEighteen);
            seasonSchedule.addWeek(weekNineteen);
            seasonSchedule.addWeek(weekTwenty);
            seasonSchedule.addWeek(weekTwentyOne);
            seasonSchedule.addWeek(weekTwentyTwo);
            seasonSchedule.addWeek(weekTwentyThree);
            seasonSchedule.addWeek(weekTwentyFour);

            if (mCurrentSimulatorWeek >= 25 && mCurrentSeasonPlayoffsStarted) {
                seasonSchedule.addWeek(firstRound);
            }
            if (mCurrentSimulatorWeek >= 26 && mCurrentSeasonPlayoffsStarted) {
                seasonSchedule.addWeek(confSemifinals);
            }
            if (mCurrentSimulatorWeek >= 27 && mCurrentSeasonPlayoffsStarted) {
                seasonSchedule.addWeek(confFinals);
            }
            if (mCurrentSimulatorWeek >= 28 && mCurrentSeasonPlayoffsStarted) {
                seasonSchedule.addWeek(nbaFinals);
            }


            mModel.setSeasonSchedule(seasonSchedule);
            updateCurrentSeasonTeamOdds();

            if (mCurrentSeasonPlayoffsStarted) {
                //If the playoffs have already started, re-query the playoff standings  after all matches are created
                mModel.queryCurrentSeasonStandings(SimulatorModel.QUERY_STANDINGS_LOAD_POSTSEASON);
            }
        }


    }

    @Override
    public void simulatorStandingsQueried(int queryType, Cursor standingsCursor) {

        //This callback will be received from the model whenever teams/standings are queried for the simulated season
        //Depending on the queryType, a specific action is performed

        if (queryType == SimulatorModel.QUERY_STANDINGS_PLAYOFF) {
            //A playoff standings was queried
            //Display playoff standings in UI
            Log.d("Standings", "displayStandingsCalled");
            displaySimulatorStandings(standingsCursor);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_LOAD_SEASON) {
            //The entire season was loaded from the db
            //Create teams from the db data and then query all matches from the db
            createSimulatorTeamsFromDb(standingsCursor);
            mModel.querySimulatorMatches(SimulatorModel.QUERY_MATCHES_ALL, false, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_POSTSEASON) {
            //The postseason standings were queried
            //Create playoff matchups based on the playoff standings/round and then display the
            //standings in the Main Activity UI
            createPlayoffMatchups(standingsCursor);
            displaySimulatorPlayoffStandings(standingsCursor);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_LOAD_POSTSEASON) {
            //The app was restarted and the postseason standings need to be re-loaded from the database
            //Display the standings in the SimulatorActivity UI and query the playoff matches
            displaySimulatorPlayoffStandings(standingsCursor);
            mModel.querySimulatorMatches(mCurrentSimulatorWeek, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }

    }

    @Override
    public void currentSeasonStandingsQueried(int queryType, Cursor standingsCursor) {
        //This callback will be received from the model whenever teams/standings are queried for the current season
        //Depending on the queryType, a specific action is performed

        if (queryType == SimulatorModel.QUERY_STANDINGS_PLAYOFF) {
            //A playoff standings was queried
            //Display playoff standings in UI
            displayCurrentSeasonStandings(standingsCursor);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_LOAD_SEASON) {
            //The entire season was loaded from the db
            //Create teams from the db data and then query all matches from the db
            createCurrentSeasonTeamsFromDb(standingsCursor);
            mModel.queryCurrentSeasonMatches(SimulatorModel.QUERY_MATCHES_ALL, false, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_POSTSEASON) {
            //TODO Fix this so that it works for loading postseason if necessary for current season instead of simulator
            //The postseason standings were queried
            //Create playoff matchups based on the playoff standings/round and then display the
            //standings in the Main Activity UI
            createPlayoffMatchups(standingsCursor);
            displaySimulatorPlayoffStandings(standingsCursor);
        }
        if (queryType == SimulatorModel.QUERY_STANDINGS_LOAD_POSTSEASON) {
            //TODO Fix this so that it works for loading postseason if necessary for current season instead of simulator
            //The app was restarted and the postseason standings need to be re-loaded from the database
            //Display the standings in the SimulatorActivity UI and query the playoff matches
            displaySimulatorPlayoffStandings(standingsCursor);
            mModel.querySimulatorMatches(mCurrentSimulatorWeek, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }

    }

    private void updateCurrentSeasonTeamOdds() {
        Schedule currentSchedule = mModel.getSeasonSchedule();
        Week weekOne = currentSchedule.getWeek(1);
    }

    private void createCurrentSeasonTeamsFromDb(Cursor standingsCursor) {

        //Create team objects from team database data

        HashMap<String, Team> teamList = new HashMap();
        HashMap<String, Double> teamUserElos = new HashMap();

        standingsCursor.moveToPosition(-1);


        while (standingsCursor.moveToNext()) {

            int ID = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry._ID));
            String teamName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            String teamShortName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_SHORT_NAME));
            int teamWins = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_WINS));
            int teamLosses = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES));
            int teamDraws = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS));
            double winLossPct = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT));
            int divisionWins = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_WINS));
            int divisionLosses = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_LOSSES));
            double divWinLossPct = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT));
            Double teamElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_ELO));
            Double teamDefaultElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DEFAULT_ELO));
            Double teamUserElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_USER_ELO));
            Double teamRanking = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_RANKING));
            Double offRating = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_OFF_RATING));
            Double defRating = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DEF_RATING));
            int division = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIVISION));
            int playoffEligible = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE));
            int playoffGame = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_GAME));
            Uri teamUri = ContentUris.withAppendedId(TeamEntry.CONTENT_URI, ID);


            teamList.put(teamName,
                    new Team(teamName, teamShortName, teamElo, teamDefaultElo, teamUserElo, teamRanking,
                            offRating, defRating, division, this, teamWins, teamLosses, teamDraws, divisionWins, divisionLosses, winLossPct, divWinLossPct, playoffEligible, playoffGame, teamUri, TeamEntry.CURRENT_SEASON_YES));

            teamUserElos.put(teamName, teamUserElo);

        }

        mModel.setSeasonTeamList(teamList);

        standingsCursor.close();
    }

    @Override
    public void queryCurrentSeasonMatches(int week, boolean singleMatch, int queryFrom) {
        mModel.queryCurrentSeasonMatches(week, singleMatch, queryFrom);
    }

    @Override
    public void resetSeason() {
        //When season is reset, delete all data in the database
        mModel.deleteAllData();
    }

    @Override
    public void resetSimulatorTeamLastSeasonElos() {
        //Reset teams Elos to last seasons Elo Values
        ArrayList<Team> teamList = mModel.getSimulatorTeamArrayList();
        for (Team team : teamList) {
            team.resetElo();
        }
    }

    @Override
    public void resetSimulatorTeamCurrentSeasonElos() {
        //Reset teams Elos to current season Elo Values
        ArrayList<Team> teamList = mModel.getSimulatorTeamArrayList();
        for (Team team : teamList) {
            team.setCurrentSeasonElos();
        }

    }

    @Override
    public void resetCurrentSeasonTeamCurrentSeasonElos() {
        //Reset teams Elos to current season Elo Values
        ArrayList<Team> teamList = mModel.getSeasonTeamArrayList();
        for (Team team : teamList) {
            team.setCurrentSeasonElos();
        }

    }

    @Override
    public void resetSimulatorTeamUserElos() {
        //Reset team Elo values to be user defined values
        HashMap<String, Team> teamMap = mModel.getSimulatorTeamList();
        HashMap<String, Double> teamElos = mModel.getTeamEloMap();

        for (String teamName : teamMap.keySet()) {
            teamMap.get(teamName).setElo(teamElos.get(teamName));
            teamMap.get(teamName).setUserElo();
        }


    }

    @Override
    public void resetSimulatorTeamWinsLosses() {

        for (Team team : mModel.getSimulatorTeamArrayList()) {
            team.resetWinsLosses();
        }

    }

    @Override
    public void setTeamUserElos() {
        //Set team User Elos to be whatever elo values were manually set by the user
        //Provide the data to the model in the form of a hashmap (this will be used to reset the values
        // back to default when the season is reset)

        HashMap<String, Double> teamUserElos = new HashMap<>();
        ArrayList<Team> teamList = mModel.getSimulatorTeamArrayList();

        for (Team team : teamList) {
            team.setUserElo();
            teamUserElos.put(team.getName(), team.getUserElo());
        }

        mModel.setTeamEloMap(teamUserElos);

    }

    @Override
    public void dataDeleted() {
        //Callback after data has been deleted
        //If this.view is not null, main activity is loaded so we will call on data deleted method of main activity
        if (this.view != null) {
            this.view.onDataDeleted();
        }
    }

    private void createSimulatorTeamsFromDb(Cursor standingsCursor) {

        //Create team objects from team database data

        HashMap<String, Team> teamList = new HashMap();
        HashMap<String, Double> teamUserElos = new HashMap();

        standingsCursor.moveToPosition(-1);


        while (standingsCursor.moveToNext()) {

            int ID = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry._ID));
            String teamName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            String teamShortName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_SHORT_NAME));
            int teamWins = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_WINS));
            int teamLosses = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES));
            int teamDraws = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS));
            double winLossPct = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT));
            int divisionWins = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_WINS));
            int divisionLosses = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_LOSSES));
            double divWinLossPct = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT));
            Double teamElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_ELO));
            Double teamDefaultElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DEFAULT_ELO));
            Double teamUserElo = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_USER_ELO));
            Double teamRanking = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_RANKING));
            Double offRating = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_OFF_RATING));
            Double defRating = standingsCursor.getDouble(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DEF_RATING));
            int division = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIVISION));
            int playoffEligible = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE));
            int playoffGame = standingsCursor.getInt(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_GAME));
            Uri teamUri = ContentUris.withAppendedId(TeamEntry.CONTENT_URI, ID);


            teamList.put(teamName,
                    new Team(teamName, teamShortName, teamElo, teamDefaultElo, teamUserElo, teamRanking,
                            offRating, defRating, division, this, teamWins, teamLosses, teamDraws, divisionWins, divisionLosses, winLossPct, divWinLossPct, playoffEligible, playoffGame, teamUri, TeamEntry.CURRENT_SEASON_NO));

            teamUserElos.put(teamName, teamUserElo);

        }

        mModel.setSimulatorTeamList(teamList);
        mModel.setTeamEloMap(teamUserElos);
        mModel.createTeamLogoMap();


        standingsCursor.close();

    }

    @Override
    public void destroyPresenter() {
        mModel.destroyModel();
    }


    private void createSimulatorTeams() {

        //Create teams for the first time

        HashMap<String, Team> simulatorTeamList = new HashMap();
        simulatorTeamList.put(NBAConstants.TEAM_ATLANTA_HAWKS_STRING,
                new Team(NBAConstants.TEAM_ATLANTA_HAWKS_STRING, NBAConstants.TEAM_ATLANTA_HAWKS_SHORT_STRING, NBAConstants.TEAM_ATLANTA_HAWKS_ELO, NBAConstants.TEAM_ATLANTA_HAWKS_FUTURE_RANKING,
                        NBAConstants.TEAM_ATLANTA_HAWKS_OFFRAT, NBAConstants.TEAM_ATLANTA_HAWKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_BOSTON_CELTICS_STRING,
                new Team(NBAConstants.TEAM_BOSTON_CELTICS_STRING, NBAConstants.TEAM_BOSTON_CELTICS_SHORT_STRING, NBAConstants.TEAM_BOSTON_CELTICS_ELO, NBAConstants.TEAM_BOSTON_CELTICS_FUTURE_RANKING,
                        NBAConstants.TEAM_BOSTON_CELTICS_OFFRAT, NBAConstants.TEAM_BOSTON_CELTICS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_BROOKLYN_NETS_STRING,
                new Team(NBAConstants.TEAM_BROOKLYN_NETS_STRING, NBAConstants.TEAM_BROOKLYN_NETS_SHORT_STRING, NBAConstants.TEAM_BROOKLYN_NETS_ELO, NBAConstants.TEAM_BROOKLYN_NETS_FUTURE_RANKING,
                        NBAConstants.TEAM_BROOKLYN_NETS_OFFRAT, NBAConstants.TEAM_BROOKLYN_NETS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING,
                new Team(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING, NBAConstants.TEAM_CHARLOTTE_HORNETS_SHORT_STRING, NBAConstants.TEAM_CHARLOTTE_HORNETS_ELO, NBAConstants.TEAM_CHARLOTTE_HORNETS_FUTURE_RANKING,
                        NBAConstants.TEAM_CHARLOTTE_HORNETS_OFFRAT, NBAConstants.TEAM_CHARLOTTE_HORNETS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_CHICAGO_BULLS_STRING,
                new Team(NBAConstants.TEAM_CHICAGO_BULLS_STRING, NBAConstants.TEAM_CHICAGO_BULLS_SHORT_STRING, NBAConstants.TEAM_CHICAGO_BULLS_ELO, NBAConstants.TEAM_CHICAGO_BULLS_FUTURE_RANKING,
                        NBAConstants.TEAM_CHICAGO_BULLS_OFFRAT, NBAConstants.TEAM_CHICAGO_BULLS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING,
                new Team(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING, NBAConstants.TEAM_CLEVELAND_CAVALIERS_SHORT_STRING, NBAConstants.TEAM_CLEVELAND_CAVALIERS_ELO, NBAConstants.TEAM_CLEVELAND_CAVALIERS_FUTURE_RANKING,
                        NBAConstants.TEAM_CLEVELAND_CAVALIERS_OFFRAT, NBAConstants.TEAM_CLEVELAND_CAVALIERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING,
                new Team(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING, NBAConstants.TEAM_DALLAS_MAVERICKS_SHORT_STRING, NBAConstants.TEAM_DALLAS_MAVERICKS_ELO, NBAConstants.TEAM_DALLAS_MAVERICKS_FUTURE_RANKING,
                        NBAConstants.TEAM_DALLAS_MAVERICKS_OFFRAT, NBAConstants.TEAM_DALLAS_MAVERICKS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_DENVER_NUGGETS_STRING,
                new Team(NBAConstants.TEAM_DENVER_NUGGETS_STRING, NBAConstants.TEAM_DENVER_NUGGETS_SHORT_STRING, NBAConstants.TEAM_DENVER_NUGGETS_ELO, NBAConstants.TEAM_DENVER_NUGGETS_FUTURE_RANKING,
                        NBAConstants.TEAM_DENVER_NUGGETS_OFFRAT, NBAConstants.TEAM_DENVER_NUGGETS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_DETROIT_PISTONS_STRING,
                new Team(NBAConstants.TEAM_DETROIT_PISTONS_STRING, NBAConstants.TEAM_DETROIT_PISTONS_SHORT_STRING, NBAConstants.TEAM_DETROIT_PISTONS_ELO, NBAConstants.TEAM_DETROIT_PISTONS_FUTURE_RANKING,
                        NBAConstants.TEAM_DETROIT_PISTONS_OFFRAT, NBAConstants.TEAM_DETROIT_PISTONS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING,
                new Team(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_SHORT_STRING, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_ELO, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_FUTURE_RANKING,
                        NBAConstants.TEAM_GOLDENSTATE_WARRIORS_OFFRAT, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING,
                new Team(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING, NBAConstants.TEAM_HOUSTON_ROCKETS_SHORT_STRING, NBAConstants.TEAM_HOUSTON_ROCKETS_ELO, NBAConstants.TEAM_HOUSTON_ROCKETS_FUTURE_RANKING,
                        NBAConstants.TEAM_HOUSTON_ROCKETS_OFFRAT, NBAConstants.TEAM_HOUSTON_ROCKETS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_INDIANA_PACERS_STRING,
                new Team(NBAConstants.TEAM_INDIANA_PACERS_STRING, NBAConstants.TEAM_INDIANA_PACERS_SHORT_STRING, NBAConstants.TEAM_INDIANA_PACERS_ELO, NBAConstants.TEAM_INDIANA_PACERS_FUTURE_RANKING,
                        NBAConstants.TEAM_INDIANA_PACERS_OFFRAT, NBAConstants.TEAM_INDIANA_PACERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING,
                new Team(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING, NBAConstants.TEAM_LOSANGELES_CLIPPERS_SHORT_STRING, NBAConstants.TEAM_LOSANGELES_CLIPPERS_ELO, NBAConstants.TEAM_LOSANGELES_CLIPPERS_FUTURE_RANKING,
                        NBAConstants.TEAM_LOSANGELES_CLIPPERS_OFFRAT, NBAConstants.TEAM_LOSANGELES_CLIPPERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING,
                new Team(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING, NBAConstants.TEAM_LOSANGELES_LAKERS_SHORT_STRING, NBAConstants.TEAM_LOSANGELES_LAKERS_ELO, NBAConstants.TEAM_LOSANGELES_LAKERS_FUTURE_RANKING,
                        NBAConstants.TEAM_LOSANGELES_LAKERS_OFFRAT, NBAConstants.TEAM_LOSANGELES_LAKERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING,
                new Team(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_SHORT_STRING, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_ELO, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_FUTURE_RANKING,
                        NBAConstants.TEAM_MEMPHIS_GRIZZLIES_OFFRAT, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_MIAMI_HEAT_STRING,
                new Team(NBAConstants.TEAM_MIAMI_HEAT_STRING, NBAConstants.TEAM_MIAMI_HEAT_SHORT_STRING, NBAConstants.TEAM_MIAMI_HEAT_ELO, NBAConstants.TEAM_MIAMI_HEAT_FUTURE_RANKING,
                        NBAConstants.TEAM_MIAMI_HEAT_OFFRAT, NBAConstants.TEAM_MIAMI_HEAT_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING,
                new Team(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING, NBAConstants.TEAM_MILWAUKEE_BUCKS_SHORT_STRING, NBAConstants.TEAM_MILWAUKEE_BUCKS_ELO, NBAConstants.TEAM_MILWAUKEE_BUCKS_FUTURE_RANKING,
                        NBAConstants.TEAM_MILWAUKEE_BUCKS_OFFRAT, NBAConstants.TEAM_MILWAUKEE_BUCKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING,
                new Team(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_SHORT_STRING, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_ELO, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_FUTURE_RANKING,
                        NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_OFFRAT, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING,
                new Team(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING, NBAConstants.TEAM_NEWORLEANS_PELICANS_SHORT_STRING, NBAConstants.TEAM_NEWORLEANS_PELICANS_ELO, NBAConstants.TEAM_NEWORLEANS_PELICANS_FUTURE_RANKING,
                        NBAConstants.TEAM_NEWORLEANS_PELICANS_OFFRAT, NBAConstants.TEAM_NEWORLEANS_PELICANS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_NEWYORK_KNICKS_STRING,
                new Team(NBAConstants.TEAM_NEWYORK_KNICKS_STRING, NBAConstants.TEAM_NEWYORK_KNICKS_SHORT_STRING, NBAConstants.TEAM_NEWYORK_KNICKS_ELO, NBAConstants.TEAM_NEWYORK_KNICKS_FUTURE_RANKING,
                        NBAConstants.TEAM_NEWYORK_KNICKS_OFFRAT, NBAConstants.TEAM_NEWYORK_KNICKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING,
                new Team(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_SHORT_STRING, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_ELO, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_FUTURE_RANKING,
                        NBAConstants.TEAM_OKLAHOMACITY_THUNDER_OFFRAT, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_ORLANDO_MAGIC_STRING,
                new Team(NBAConstants.TEAM_ORLANDO_MAGIC_STRING, NBAConstants.TEAM_ORLANDO_MAGIC_SHORT_STRING, NBAConstants.TEAM_ORLANDO_MAGIC_ELO, NBAConstants.TEAM_ORLANDO_MAGIC_FUTURE_RANKING,
                        NBAConstants.TEAM_ORLANDO_MAGIC_OFFRAT, NBAConstants.TEAM_ORLANDO_MAGIC_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING,
                new Team(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING, NBAConstants.TEAM_PHILADELPHIA_76ERS_SHORT_STRING, NBAConstants.TEAM_PHILADELPHIA_76ERS_ELO, NBAConstants.TEAM_PHILADELPHIA_76ERS_FUTURE_RANKING,
                        NBAConstants.TEAM_PHILADELPHIA_76ERS_OFFRAT, NBAConstants.TEAM_PHILADELPHIA_76ERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_PHOENIX_SUNS_STRING,
                new Team(NBAConstants.TEAM_PHOENIX_SUNS_STRING, NBAConstants.TEAM_PHOENIX_SUNS_SHORT_STRING, NBAConstants.TEAM_PHOENIX_SUNS_ELO, NBAConstants.TEAM_PHOENIX_SUNS_FUTURE_RANKING,
                        NBAConstants.TEAM_PHOENIX_SUNS_OFFRAT, NBAConstants.TEAM_PHOENIX_SUNS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING,
                new Team(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_SHORT_STRING, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_ELO, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_FUTURE_RANKING,
                        NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_OFFRAT, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING,
                new Team(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING, NBAConstants.TEAM_SACRAMENTO_KINGS_SHORT_STRING, NBAConstants.TEAM_SACRAMENTO_KINGS_ELO, NBAConstants.TEAM_SACRAMENTO_KINGS_FUTURE_RANKING,
                        NBAConstants.TEAM_SACRAMENTO_KINGS_OFFRAT, NBAConstants.TEAM_SACRAMENTO_KINGS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_SANANTONIO_SPURS_STRING,
                new Team(NBAConstants.TEAM_SANANTONIO_SPURS_STRING, NBAConstants.TEAM_SANANTONIO_SPURS_SHORT_STRING, NBAConstants.TEAM_SANANTONIO_SPURS_ELO, NBAConstants.TEAM_SANANTONIO_SPURS_FUTURE_RANKING,
                        NBAConstants.TEAM_SANANTONIO_SPURS_OFFRAT, NBAConstants.TEAM_SANANTONIO_SPURS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_TORONTO_RAPTORS_STRING,
                new Team(NBAConstants.TEAM_TORONTO_RAPTORS_STRING, NBAConstants.TEAM_TORONTO_RAPTORS_SHORT_STRING, NBAConstants.TEAM_TORONTO_RAPTORS_ELO, NBAConstants.TEAM_TORONTO_RAPTORS_FUTURE_RANKING,
                        NBAConstants.TEAM_TORONTO_RAPTORS_OFFRAT, NBAConstants.TEAM_TORONTO_RAPTORS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_UTAH_JAZZ_STRING,
                new Team(NBAConstants.TEAM_UTAH_JAZZ_STRING, NBAConstants.TEAM_UTAH_JAZZ_SHORT_STRING, NBAConstants.TEAM_UTAH_JAZZ_ELO, NBAConstants.TEAM_UTAH_JAZZ_FUTURE_RANKING,
                        NBAConstants.TEAM_UTAH_JAZZ_OFFRAT, NBAConstants.TEAM_UTAH_JAZZ_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this));
        simulatorTeamList.put(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING,
                new Team(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING, NBAConstants.TEAM_WASHINGTON_WIZARDS_SHORT_STRING, NBAConstants.TEAM_WASHINGTON_WIZARDS_ELO, NBAConstants.TEAM_WASHINGTON_WIZARDS_FUTURE_RANKING,
                        NBAConstants.TEAM_WASHINGTON_WIZARDS_OFFRAT, NBAConstants.TEAM_WASHINGTON_WIZARDS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this));
        mModel.setSimulatorTeamList(simulatorTeamList);

        mModel.createTeamLogoMap();
    }

    private void createSeasonTeams() {

        //Create teams for the first time

        HashMap<String, Team> seasonTeamList = new HashMap();
        seasonTeamList.put(NBAConstants.TEAM_ATLANTA_HAWKS_STRING,
                new Team(NBAConstants.TEAM_ATLANTA_HAWKS_STRING, NBAConstants.TEAM_ATLANTA_HAWKS_SHORT_STRING, NBAConstants.TEAM_ATLANTA_HAWKS_ELO, NBAConstants.TEAM_ATLANTA_HAWKS_FUTURE_RANKING,
                        NBAConstants.TEAM_ATLANTA_HAWKS_OFFRAT, NBAConstants.TEAM_ATLANTA_HAWKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_BOSTON_CELTICS_STRING,
                new Team(NBAConstants.TEAM_BOSTON_CELTICS_STRING, NBAConstants.TEAM_BOSTON_CELTICS_SHORT_STRING, NBAConstants.TEAM_BOSTON_CELTICS_ELO, NBAConstants.TEAM_BOSTON_CELTICS_FUTURE_RANKING,
                        NBAConstants.TEAM_BOSTON_CELTICS_OFFRAT, NBAConstants.TEAM_BOSTON_CELTICS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_BROOKLYN_NETS_STRING,
                new Team(NBAConstants.TEAM_BROOKLYN_NETS_STRING, NBAConstants.TEAM_BROOKLYN_NETS_SHORT_STRING, NBAConstants.TEAM_BROOKLYN_NETS_ELO, NBAConstants.TEAM_BROOKLYN_NETS_FUTURE_RANKING,
                        NBAConstants.TEAM_BROOKLYN_NETS_OFFRAT, NBAConstants.TEAM_BROOKLYN_NETS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING,
                new Team(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING, NBAConstants.TEAM_CHARLOTTE_HORNETS_SHORT_STRING, NBAConstants.TEAM_CHARLOTTE_HORNETS_ELO, NBAConstants.TEAM_CHARLOTTE_HORNETS_FUTURE_RANKING,
                        NBAConstants.TEAM_CHARLOTTE_HORNETS_OFFRAT, NBAConstants.TEAM_CHARLOTTE_HORNETS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_CHICAGO_BULLS_STRING,
                new Team(NBAConstants.TEAM_CHICAGO_BULLS_STRING, NBAConstants.TEAM_CHICAGO_BULLS_SHORT_STRING, NBAConstants.TEAM_CHICAGO_BULLS_ELO, NBAConstants.TEAM_CHICAGO_BULLS_FUTURE_RANKING,
                        NBAConstants.TEAM_CHICAGO_BULLS_OFFRAT, NBAConstants.TEAM_CHICAGO_BULLS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING,
                new Team(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING, NBAConstants.TEAM_CLEVELAND_CAVALIERS_SHORT_STRING, NBAConstants.TEAM_CLEVELAND_CAVALIERS_ELO, NBAConstants.TEAM_CLEVELAND_CAVALIERS_FUTURE_RANKING,
                        NBAConstants.TEAM_CLEVELAND_CAVALIERS_OFFRAT, NBAConstants.TEAM_CLEVELAND_CAVALIERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING,
                new Team(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING, NBAConstants.TEAM_DALLAS_MAVERICKS_SHORT_STRING, NBAConstants.TEAM_DALLAS_MAVERICKS_ELO, NBAConstants.TEAM_DALLAS_MAVERICKS_FUTURE_RANKING,
                        NBAConstants.TEAM_DALLAS_MAVERICKS_OFFRAT, NBAConstants.TEAM_DALLAS_MAVERICKS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_DENVER_NUGGETS_STRING,
                new Team(NBAConstants.TEAM_DENVER_NUGGETS_STRING, NBAConstants.TEAM_DENVER_NUGGETS_SHORT_STRING, NBAConstants.TEAM_DENVER_NUGGETS_ELO, NBAConstants.TEAM_DENVER_NUGGETS_FUTURE_RANKING,
                        NBAConstants.TEAM_DENVER_NUGGETS_OFFRAT, NBAConstants.TEAM_DENVER_NUGGETS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_DETROIT_PISTONS_STRING,
                new Team(NBAConstants.TEAM_DETROIT_PISTONS_STRING, NBAConstants.TEAM_DETROIT_PISTONS_SHORT_STRING, NBAConstants.TEAM_DETROIT_PISTONS_ELO, NBAConstants.TEAM_DETROIT_PISTONS_FUTURE_RANKING,
                        NBAConstants.TEAM_DETROIT_PISTONS_OFFRAT, NBAConstants.TEAM_DETROIT_PISTONS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING,
                new Team(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_SHORT_STRING, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_ELO, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_FUTURE_RANKING,
                        NBAConstants.TEAM_GOLDENSTATE_WARRIORS_OFFRAT, NBAConstants.TEAM_GOLDENSTATE_WARRIORS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING,
                new Team(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING, NBAConstants.TEAM_HOUSTON_ROCKETS_SHORT_STRING, NBAConstants.TEAM_HOUSTON_ROCKETS_ELO, NBAConstants.TEAM_HOUSTON_ROCKETS_FUTURE_RANKING,
                        NBAConstants.TEAM_HOUSTON_ROCKETS_OFFRAT, NBAConstants.TEAM_HOUSTON_ROCKETS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_INDIANA_PACERS_STRING,
                new Team(NBAConstants.TEAM_INDIANA_PACERS_STRING, NBAConstants.TEAM_INDIANA_PACERS_SHORT_STRING, NBAConstants.TEAM_INDIANA_PACERS_ELO, NBAConstants.TEAM_INDIANA_PACERS_FUTURE_RANKING,
                        NBAConstants.TEAM_INDIANA_PACERS_OFFRAT, NBAConstants.TEAM_INDIANA_PACERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING,
                new Team(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING, NBAConstants.TEAM_LOSANGELES_CLIPPERS_SHORT_STRING, NBAConstants.TEAM_LOSANGELES_CLIPPERS_ELO, NBAConstants.TEAM_LOSANGELES_CLIPPERS_FUTURE_RANKING,
                        NBAConstants.TEAM_LOSANGELES_CLIPPERS_OFFRAT, NBAConstants.TEAM_LOSANGELES_CLIPPERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING,
                new Team(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING, NBAConstants.TEAM_LOSANGELES_LAKERS_SHORT_STRING, NBAConstants.TEAM_LOSANGELES_LAKERS_ELO, NBAConstants.TEAM_LOSANGELES_LAKERS_FUTURE_RANKING,
                        NBAConstants.TEAM_LOSANGELES_LAKERS_OFFRAT, NBAConstants.TEAM_LOSANGELES_LAKERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING,
                new Team(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_SHORT_STRING, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_ELO, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_FUTURE_RANKING,
                        NBAConstants.TEAM_MEMPHIS_GRIZZLIES_OFFRAT, NBAConstants.TEAM_MEMPHIS_GRIZZLIES_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_MIAMI_HEAT_STRING,
                new Team(NBAConstants.TEAM_MIAMI_HEAT_STRING, NBAConstants.TEAM_MIAMI_HEAT_SHORT_STRING, NBAConstants.TEAM_MIAMI_HEAT_ELO, NBAConstants.TEAM_MIAMI_HEAT_FUTURE_RANKING,
                        NBAConstants.TEAM_MIAMI_HEAT_OFFRAT, NBAConstants.TEAM_MIAMI_HEAT_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING,
                new Team(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING, NBAConstants.TEAM_MILWAUKEE_BUCKS_SHORT_STRING, NBAConstants.TEAM_MILWAUKEE_BUCKS_ELO, NBAConstants.TEAM_MILWAUKEE_BUCKS_FUTURE_RANKING,
                        NBAConstants.TEAM_MILWAUKEE_BUCKS_OFFRAT, NBAConstants.TEAM_MILWAUKEE_BUCKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING,
                new Team(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_SHORT_STRING, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_ELO, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_FUTURE_RANKING,
                        NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_OFFRAT, NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING,
                new Team(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING, NBAConstants.TEAM_NEWORLEANS_PELICANS_SHORT_STRING, NBAConstants.TEAM_NEWORLEANS_PELICANS_ELO, NBAConstants.TEAM_NEWORLEANS_PELICANS_FUTURE_RANKING,
                        NBAConstants.TEAM_NEWORLEANS_PELICANS_OFFRAT, NBAConstants.TEAM_NEWORLEANS_PELICANS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_NEWYORK_KNICKS_STRING,
                new Team(NBAConstants.TEAM_NEWYORK_KNICKS_STRING, NBAConstants.TEAM_NEWYORK_KNICKS_SHORT_STRING, NBAConstants.TEAM_NEWYORK_KNICKS_ELO, NBAConstants.TEAM_NEWYORK_KNICKS_FUTURE_RANKING,
                        NBAConstants.TEAM_NEWYORK_KNICKS_OFFRAT, NBAConstants.TEAM_NEWYORK_KNICKS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING,
                new Team(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_SHORT_STRING, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_ELO, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_FUTURE_RANKING,
                        NBAConstants.TEAM_OKLAHOMACITY_THUNDER_OFFRAT, NBAConstants.TEAM_OKLAHOMACITY_THUNDER_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_ORLANDO_MAGIC_STRING,
                new Team(NBAConstants.TEAM_ORLANDO_MAGIC_STRING, NBAConstants.TEAM_ORLANDO_MAGIC_SHORT_STRING, NBAConstants.TEAM_ORLANDO_MAGIC_ELO, NBAConstants.TEAM_ORLANDO_MAGIC_FUTURE_RANKING,
                        NBAConstants.TEAM_ORLANDO_MAGIC_OFFRAT, NBAConstants.TEAM_ORLANDO_MAGIC_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING,
                new Team(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING, NBAConstants.TEAM_PHILADELPHIA_76ERS_SHORT_STRING, NBAConstants.TEAM_PHILADELPHIA_76ERS_ELO, NBAConstants.TEAM_PHILADELPHIA_76ERS_FUTURE_RANKING,
                        NBAConstants.TEAM_PHILADELPHIA_76ERS_OFFRAT, NBAConstants.TEAM_PHILADELPHIA_76ERS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_PHOENIX_SUNS_STRING,
                new Team(NBAConstants.TEAM_PHOENIX_SUNS_STRING, NBAConstants.TEAM_PHOENIX_SUNS_SHORT_STRING, NBAConstants.TEAM_PHOENIX_SUNS_ELO, NBAConstants.TEAM_PHOENIX_SUNS_FUTURE_RANKING,
                        NBAConstants.TEAM_PHOENIX_SUNS_OFFRAT, NBAConstants.TEAM_PHOENIX_SUNS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING,
                new Team(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_SHORT_STRING, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_ELO, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_FUTURE_RANKING,
                        NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_OFFRAT, NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING,
                new Team(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING, NBAConstants.TEAM_SACRAMENTO_KINGS_SHORT_STRING, NBAConstants.TEAM_SACRAMENTO_KINGS_ELO, NBAConstants.TEAM_SACRAMENTO_KINGS_FUTURE_RANKING,
                        NBAConstants.TEAM_SACRAMENTO_KINGS_OFFRAT, NBAConstants.TEAM_SACRAMENTO_KINGS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_SANANTONIO_SPURS_STRING,
                new Team(NBAConstants.TEAM_SANANTONIO_SPURS_STRING, NBAConstants.TEAM_SANANTONIO_SPURS_SHORT_STRING, NBAConstants.TEAM_SANANTONIO_SPURS_ELO, NBAConstants.TEAM_SANANTONIO_SPURS_FUTURE_RANKING,
                        NBAConstants.TEAM_SANANTONIO_SPURS_OFFRAT, NBAConstants.TEAM_SANANTONIO_SPURS_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_TORONTO_RAPTORS_STRING,
                new Team(NBAConstants.TEAM_TORONTO_RAPTORS_STRING, NBAConstants.TEAM_TORONTO_RAPTORS_SHORT_STRING, NBAConstants.TEAM_TORONTO_RAPTORS_ELO, NBAConstants.TEAM_TORONTO_RAPTORS_FUTURE_RANKING,
                        NBAConstants.TEAM_TORONTO_RAPTORS_OFFRAT, NBAConstants.TEAM_TORONTO_RAPTORS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_UTAH_JAZZ_STRING,
                new Team(NBAConstants.TEAM_UTAH_JAZZ_STRING, NBAConstants.TEAM_UTAH_JAZZ_SHORT_STRING, NBAConstants.TEAM_UTAH_JAZZ_ELO, NBAConstants.TEAM_UTAH_JAZZ_FUTURE_RANKING,
                        NBAConstants.TEAM_UTAH_JAZZ_OFFRAT, NBAConstants.TEAM_UTAH_JAZZ_DEFRAT, TeamEntry.DIVISION_WESTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        seasonTeamList.put(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING,
                new Team(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING, NBAConstants.TEAM_WASHINGTON_WIZARDS_SHORT_STRING, NBAConstants.TEAM_WASHINGTON_WIZARDS_ELO, NBAConstants.TEAM_WASHINGTON_WIZARDS_FUTURE_RANKING,
                        NBAConstants.TEAM_WASHINGTON_WIZARDS_OFFRAT, NBAConstants.TEAM_WASHINGTON_WIZARDS_DEFRAT, TeamEntry.DIVISION_EASTERN_CONFERENCE, this, TeamEntry.CURRENT_SEASON_YES));
        mModel.setSeasonTeamList(seasonTeamList);

    }

    private void createSimulatorSchedule() {

        //Initialize all schedule, weeks, and matches.  Add weeks to schedule.
        Schedule simulatorSeasonSchedule = new Schedule();
        Week weekOne = new Week(1);
        Week weekTwo = new Week(2);
        Week weekThree = new Week(3);
        Week weekFour = new Week(4);
        Week weekFive = new Week(5);
        Week weekSix = new Week(6);
        Week weekSeven = new Week(7);
        Week weekEight = new Week(8);
        Week weekNine = new Week(9);
        Week weekTen = new Week(10);
        Week weekEleven = new Week(11);
        Week weekTwelve = new Week(12);
        Week weekThirteen = new Week(13);
        Week weekFourteen = new Week(14);
        Week weekFifteen = new Week(15);
        Week weekSixteen = new Week(16);
        Week weekSeventeen = new Week(17);
        Week weekEighteen = new Week(18);
        Week weekNineteen = new Week(19);
        Week weekTwenty = new Week(20);
        Week weekTwentyOne = new Week(21);
        Week weekTwentyTwo = new Week(22);
        Week weekTwentyThree = new Week(23);
        Week weekTwentyFour = new Week(24);
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO));
        simulatorSeasonSchedule.addWeek(weekOne);
        simulatorSeasonSchedule.addWeek(weekTwo);
        simulatorSeasonSchedule.addWeek(weekThree);
        simulatorSeasonSchedule.addWeek(weekFour);
        simulatorSeasonSchedule.addWeek(weekFive);
        simulatorSeasonSchedule.addWeek(weekSix);
        simulatorSeasonSchedule.addWeek(weekSeven);
        simulatorSeasonSchedule.addWeek(weekEight);
        simulatorSeasonSchedule.addWeek(weekNine);
        simulatorSeasonSchedule.addWeek(weekTen);
        simulatorSeasonSchedule.addWeek(weekEleven);
        simulatorSeasonSchedule.addWeek(weekTwelve);
        simulatorSeasonSchedule.addWeek(weekThirteen);
        simulatorSeasonSchedule.addWeek(weekFourteen);
        simulatorSeasonSchedule.addWeek(weekFifteen);
        simulatorSeasonSchedule.addWeek(weekSixteen);
        simulatorSeasonSchedule.addWeek(weekSeventeen);
        simulatorSeasonSchedule.addWeek(weekEighteen);
        simulatorSeasonSchedule.addWeek(weekNineteen);
        simulatorSeasonSchedule.addWeek(weekTwenty);
        simulatorSeasonSchedule.addWeek(weekTwentyOne);
        simulatorSeasonSchedule.addWeek(weekTwentyTwo);
        simulatorSeasonSchedule.addWeek(weekTwentyThree);
        simulatorSeasonSchedule.addWeek(weekTwentyFour);


        mModel.setSimulatorSchedule(simulatorSeasonSchedule);


    }

    private void createSeasonSchedule() {
        //Initialize all schedule, weeks, and matches.  Add weeks to schedule.
        Schedule currentSeasonSchedule = new Schedule();
        Week weekOne = new Week(1);
        Week weekTwo = new Week(2);
        Week weekThree = new Week(3);
        Week weekFour = new Week(4);
        Week weekFive = new Week(5);
        Week weekSix = new Week(6);
        Week weekSeven = new Week(7);
        Week weekEight = new Week(8);
        Week weekNine = new Week(9);
        Week weekTen = new Week(10);
        Week weekEleven = new Week(11);
        Week weekTwelve = new Week(12);
        Week weekThirteen = new Week(13);
        Week weekFourteen = new Week(14);
        Week weekFifteen = new Week(15);
        Week weekSixteen = new Week(16);
        Week weekSeventeen = new Week(17);
        Week weekEighteen = new Week(18);
        Week weekNineteen = new Week(19);
        Week weekTwenty = new Week(20);
        Week weekTwentyOne = new Week(21);
        Week weekTwentyTwo = new Week(22);
        Week weekTwentyThree = new Week(23);
        Week weekTwentyFour = new Week(24);
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 1, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 2, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 3, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 4, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFive.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 5, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSix.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 6, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 7, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEight.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 8, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNine.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 9, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 10, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEleven.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 11, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwelve.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 12, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekThirteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 13, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFourteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 14, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekFifteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 15, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSixteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 16, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekSeventeen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 17, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekEighteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 18, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekNineteen.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 19, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwenty.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 20, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyOne.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 21, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyTwo.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 22, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyThree.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 23, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BOSTON_CELTICS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_TORONTO_RAPTORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHOENIX_SUNS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_INDIANA_PACERS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ATLANTA_HAWKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MIAMI_HEAT_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_BROOKLYN_NETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_ORLANDO_MAGIC_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DETROIT_PISTONS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_NEWYORK_KNICKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_CHICAGO_BULLS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SANANTONIO_SPURS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_DENVER_NUGGETS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_UTAH_JAZZ_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        weekTwentyFour.addMatch(new Match(mModel.getSimulatorTeamList().get(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING), mModel.getSimulatorTeamList().get(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING), 24, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES));
        currentSeasonSchedule.addWeek(weekOne);
        currentSeasonSchedule.addWeek(weekTwo);
        currentSeasonSchedule.addWeek(weekThree);
        currentSeasonSchedule.addWeek(weekFour);
        currentSeasonSchedule.addWeek(weekFive);
        currentSeasonSchedule.addWeek(weekSix);
        currentSeasonSchedule.addWeek(weekSeven);
        currentSeasonSchedule.addWeek(weekEight);
        currentSeasonSchedule.addWeek(weekNine);
        currentSeasonSchedule.addWeek(weekTen);
        currentSeasonSchedule.addWeek(weekEleven);
        currentSeasonSchedule.addWeek(weekTwelve);
        currentSeasonSchedule.addWeek(weekThirteen);
        currentSeasonSchedule.addWeek(weekFourteen);
        currentSeasonSchedule.addWeek(weekFifteen);
        currentSeasonSchedule.addWeek(weekSixteen);
        currentSeasonSchedule.addWeek(weekSeventeen);
        currentSeasonSchedule.addWeek(weekEighteen);
        currentSeasonSchedule.addWeek(weekNineteen);
        currentSeasonSchedule.addWeek(weekTwenty);
        currentSeasonSchedule.addWeek(weekTwentyOne);
        currentSeasonSchedule.addWeek(weekTwentyTwo);
        currentSeasonSchedule.addWeek(weekTwentyThree);
        currentSeasonSchedule.addWeek(weekTwentyFour);


        mModel.setSeasonSchedule(currentSeasonSchedule);

    }

    @Override
    public void simulateSeason() {
        //From week 1 to week 17 (full season), simulate the season
        while (mCurrentSimulatorWeek <= 24) {
            mModel.getSimulatorSchedule().getWeek(mCurrentSimulatorWeek).simulate(true);
            mCurrentSimulatorWeek++;
        }

        //After the season  is complete, query the standings (and display them)
        generateAndSetPlayoffSeeds(SimulatorPresenter.SEASON_TYPE_SIMULATOR);
        mModel.querySimulatorStandings(SimulatorModel.QUERY_STANDINGS_PLAYOFF);
        //Query all weeks that have already occurred;
        mModel.querySimulatorMatches(mCurrentSimulatorWeek - 1, false, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
    }

    @Override
    public void simulateTestSeason() {

        if (mCurrentTestSimulations > 0) {
            //If this is not the 1st simulation, update the completed game scores before completing the rest of the simulation
            updateSimulatorCompletedGameScores();
        }

        //From week 1 to week 17 (full season), simulate the season
        while (mCurrentSimulatorWeek <= 17) {
            mModel.getSimulatorSchedule().getWeek(mCurrentSimulatorWeek).simulateTestMatches(true);
            mCurrentSimulatorWeek++;
        }

        mCurrentSimulatorWeek = 18;
        this.view.setCurrentWeekPreference(mCurrentSimulatorWeek);

        ArrayList<ArrayList<Team>> allPlayoffTeams = generateSimulatorPlayoffTeams(SimulatorPresenter.SEASON_TYPE_SIMULATOR);
        simulateTestPlayoffs(allPlayoffTeams);


    }

    private ArrayList<ArrayList<Team>> generateSimulatorPlayoffTeams(int seasonType) {

        //Generate playoff teams

        ArrayList<Team> westernConferencePotentialPlayoffTeams = new ArrayList<>();
        ArrayList<Team> easternConferencePotentialPlayoffTeams = new ArrayList<>();


        ArrayList<Team> allTeams;
        if (seasonType == SimulatorPresenter.SEASON_TYPE_SIMULATOR) {
            allTeams = mModel.getSimulatorTeamArrayList();
        } else {
            allTeams = mModel.getSeasonTeamArrayList();
        }

        for (Team team : allTeams) {
            if (team.getDivision() == TeamEntry.DIVISION_WESTERN_CONFERENCE) {
                westernConferencePotentialPlayoffTeams.add(team);
            } else {
                easternConferencePotentialPlayoffTeams.add(team);
            }
        }

        Log.d("WC", "size" + westernConferencePotentialPlayoffTeams.size());
        Log.d("EC", "size" + easternConferencePotentialPlayoffTeams.size());

        ArrayList<Team> westernConferencePlayoffTeams = generateNBAPlayoffTeamsFromPotentialTeams(westernConferencePotentialPlayoffTeams);
        ArrayList<Team> easternConferencePlayoffTeams = generateNBAPlayoffTeamsFromPotentialTeams(easternConferencePotentialPlayoffTeams);

        ArrayList<ArrayList<Team>> allPlayoffTeams = new ArrayList<>();
        allPlayoffTeams.add(westernConferencePlayoffTeams);
        allPlayoffTeams.add(easternConferencePlayoffTeams);

        return allPlayoffTeams;
    }

    private ArrayList<Team> generateNBAPlayoffTeamsFromPotentialTeams(ArrayList<Team> potentialConferenceTeams) {

        //Sort the teams by win loss percentage in descending order
        Collections.sort(potentialConferenceTeams, new Comparator<Team>() {
            @Override
            public int compare(Team teamOne, Team teamTwo) {
                return teamOne.getWinLossPct() > teamTwo.getWinLossPct() ? -1 : (teamOne.getWinLossPct() < teamTwo.getWinLossPct()) ? 1 : 0;
            }
        });

        //Set each playoff team's seed and return a list of playoff teams
        ArrayList<Team> playoffTeams = new ArrayList<>();
        int i = 0;
        while (i <= 7) {
            playoffTeams.add(potentialConferenceTeams.get(i));
            i++;
        }

        return playoffTeams;

    }

    private void simulateTestPlayoffs(ArrayList<ArrayList<Team>> allPlayoffTeams) {

        ArrayList<Team> afcPlayoffTeams = allPlayoffTeams.get(0);
        ArrayList<Team> nfcPlayoffTeams = allPlayoffTeams.get(1);

        for (Team team : afcPlayoffTeams) {
            team.madePlayoffs();
        }
        for (Team team : nfcPlayoffTeams) {
            team.madePlayoffs();
        }

        Team afcSixSeed = afcPlayoffTeams.get(5);
        Team afcFiveSeed = afcPlayoffTeams.get(4);
        Team afcFourSeed = afcPlayoffTeams.get(3);
        Team afcThreeSeed = afcPlayoffTeams.get(2);
        Team nfcSixSeed = nfcPlayoffTeams.get(5);
        Team nfcFiveSeed = nfcPlayoffTeams.get(4);
        Team nfcFourSeed = nfcPlayoffTeams.get(3);
        Team nfcThreeSeed = nfcPlayoffTeams.get(2);

        Boolean afcWildCardSixSeedWon = ELORatingSystem.simulateTestMatch(afcSixSeed, afcThreeSeed, true);
        Boolean afcWildCardFiveSeedWon = ELORatingSystem.simulateTestMatch(afcFiveSeed, afcFourSeed, true);
        Boolean nfcWildCardSixSeedWon = ELORatingSystem.simulateTestMatch(nfcSixSeed, nfcThreeSeed, true);
        Boolean nfcWildCardFiveSeedWon = ELORatingSystem.simulateTestMatch(nfcFiveSeed, nfcFourSeed, true);

        if (afcWildCardSixSeedWon) {
            afcPlayoffTeams.remove(afcThreeSeed);
        } else {
            afcPlayoffTeams.remove(afcSixSeed);
        }
        if (afcWildCardFiveSeedWon) {
            afcPlayoffTeams.remove(afcFourSeed);
        } else {
            afcPlayoffTeams.remove(afcFiveSeed);
        }
        if (nfcWildCardSixSeedWon) {
            nfcPlayoffTeams.remove(nfcThreeSeed);
        } else {
            nfcPlayoffTeams.remove(nfcSixSeed);
        }
        if (nfcWildCardFiveSeedWon) {
            nfcPlayoffTeams.remove(nfcFourSeed);
        } else {
            nfcPlayoffTeams.remove(nfcFiveSeed);
        }

        Team afcDivFourSeed = afcPlayoffTeams.get(3);
        Team afcDivThreeSeed = afcPlayoffTeams.get(2);
        Team afcDivTwoSeed = afcPlayoffTeams.get(1);
        Team afcDivOneSeed = afcPlayoffTeams.get(0);
        Team nfcDivFourSeed = nfcPlayoffTeams.get(3);
        Team nfcDivThreeSeed = nfcPlayoffTeams.get(2);
        Team nfcDivTwoSeed = nfcPlayoffTeams.get(1);
        Team nfcDivOneSeed = nfcPlayoffTeams.get(0);

        Boolean afcDivisionFourSeedWon = ELORatingSystem.simulateTestMatch(afcDivFourSeed, afcDivOneSeed, true);
        Boolean afcDivisionThreeSeedWon = ELORatingSystem.simulateTestMatch(afcDivThreeSeed, afcDivTwoSeed, true);
        Boolean nfcDivisionFourSeedWon = ELORatingSystem.simulateTestMatch(nfcDivFourSeed, nfcDivOneSeed, true);
        Boolean nfcDivisionThreeSeedWon = ELORatingSystem.simulateTestMatch(nfcDivThreeSeed, nfcDivTwoSeed, true);

        if (afcDivisionFourSeedWon) {
            afcPlayoffTeams.remove(afcDivOneSeed);
        } else {
            afcPlayoffTeams.remove(afcDivFourSeed);
        }
        if (afcDivisionThreeSeedWon) {
            afcPlayoffTeams.remove(afcDivTwoSeed);
        } else {
            afcPlayoffTeams.remove(afcDivThreeSeed);
        }
        if (nfcDivisionFourSeedWon) {
            nfcPlayoffTeams.remove(nfcDivOneSeed);
        } else {
            nfcPlayoffTeams.remove(nfcDivFourSeed);
        }
        if (nfcDivisionThreeSeedWon) {
            nfcPlayoffTeams.remove(nfcDivTwoSeed);
        } else {
            nfcPlayoffTeams.remove(nfcDivThreeSeed);
        }

        Boolean afcConfLowSeedWon = ELORatingSystem.simulateTestMatch(afcPlayoffTeams.get(1), afcPlayoffTeams.get(0), true);
        Boolean nfcConfLowSeedWon = ELORatingSystem.simulateTestMatch(nfcPlayoffTeams.get(1), nfcPlayoffTeams.get(0), true);

        if (afcConfLowSeedWon) {
            afcPlayoffTeams.remove(0);
        } else {
            afcPlayoffTeams.remove(1);
        }
        if (nfcConfLowSeedWon) {
            nfcPlayoffTeams.remove(0);
        } else {
            nfcPlayoffTeams.remove(1);
        }

        afcPlayoffTeams.get(0).wonConference();
        nfcPlayoffTeams.get(0).wonConference();
        Boolean afcWonSuperbowl = ELORatingSystem.simulateTestMatch(afcPlayoffTeams.get(0), nfcPlayoffTeams.get(0), false);
        if (afcWonSuperbowl) {
            afcPlayoffTeams.get(0).wonSuperBowl();
        } else {
            nfcPlayoffTeams.get(0).wonSuperBowl();
        }

        Log.d("AFCSB", "" + afcPlayoffTeams.get(0).getName());
        Log.d("NFCSB", "" + nfcPlayoffTeams.get(0).getName());
        Log.d("AFCWon", "" + afcWonSuperbowl);

        this.view.simulateAnotherTestWeek();

    }

    private void displaySimulatorStandings(Cursor standingsCursor) {

        //Call display standings call for simulator regular season
        for (ScoreView scoreView : mScoreViews) {
            scoreView.onDisplayStandings(SimulatorActivity.STANDINGS_TYPE_REGULAR_SEASON, standingsCursor, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }
    }

    private void displayCurrentSeasonStandings(Cursor standingsCursor) {

        //Call display standings call for current season regular season
        for (ScoreView scoreView : mScoreViews) {
            scoreView.onDisplayStandings(SimulatorActivity.STANDINGS_TYPE_REGULAR_SEASON, standingsCursor, SimulatorModel.QUERY_FROM_SEASON_STANDINGS_ACTIVITY);
        }

    }

    private void displaySimulatorPlayoffStandings(Cursor standingsCursor) {

        //Call display standings call for simulator playoffs
        for (ScoreView scoreView : mScoreViews) {
            scoreView.onDisplayStandings(SimulatorActivity.STANDINGS_TYPE_PLAYOFFS, standingsCursor, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);
        }

    }


    @Override
    public void updateMatchCallback(Match match, Uri uri) {

        //Callback is received when a match is completed
        //The model is then notified to update the match in the database
        if (!mTestSimulation) {
            mModel.updateMatch(match, uri);
        }
    }

    @Override
    public void updateMatchOddsCallback(Match match, Uri uri) {
        //Callback is received when a match is completed
        //The model is then notified to update the match odds in the database
        mModel.updateMatchOdds(match, uri);
    }

    @Override
    public void updateTeamCallback(Team team, Uri uri) {

        //Callback is received when a match is completed
        //The model is then notified to update the team wins, losses and winLossPct.
        //Don't update the database if it is a test simulation
        if (!mTestSimulation) {
            mModel.updateTeam(team, uri);
        }

    }

    public static boolean seasonIsInitialized() {
        return mSeasonInitialized;
    }

    public static void setSeasonInitialized(Boolean seasonIsInitialized) {
        mSeasonInitialized = seasonIsInitialized;
    }

    public static void setCurrentWeek(int currentWeek) {
        mCurrentSimulatorWeek = currentWeek;
    }

    public static int getCurrentWeek() {
        return mCurrentSimulatorWeek;
    }

    public void createPlayoffMatchups(Cursor standingsCursor) {

        //If standings cursor count is 16, the playoffs are just starting because there are still 16 teams
        //Therefore, initialize the playoffs schedule and set the first round matchups

        //If the standings cursor count is 8, initialize conference semifinal playoffs.
        //If the standings cursor count is 4, initialize conference final playoffs.
        //If the standings cursor count is 2, initialize nba finals.

        int remainingPlayoffTeams = standingsCursor.getCount();

        ArrayList<Team> westernConferenceTeams = new ArrayList<>();
        ArrayList<Team> easternConferenceTeams = new ArrayList<>();

        //Go through the cursor and add the teams to their respective conference in order of their seed (cursor is sorted by seed)
        standingsCursor.moveToPosition(-1);
        while (standingsCursor.moveToNext()) {
            String teamName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            Team team = mModel.getSimulatorTeamList().get(teamName);
            if (team.getConference() == TeamEntry.CONFERENCE_WESTERN) {
                westernConferenceTeams.add(team);
            } else {
                easternConferenceTeams.add(team);
            }
        }

        if (remainingPlayoffTeams == 16) {

            //Initialize all playoffs schedule
            Week firstRound = new Week(MatchEntry.MATCH_WEEK_FIRST_ROUND);

            //Initialize first round matchups from cursor
            //The better seed is always the home team, so they are added as team two (home team)
            firstRound.addMatch(new Match(westernConferenceTeams.get(7), westernConferenceTeams.get(0), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            firstRound.addMatch(new Match(westernConferenceTeams.get(4), westernConferenceTeams.get(3), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            firstRound.addMatch(new Match(westernConferenceTeams.get(5), westernConferenceTeams.get(2), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 2));
            firstRound.addMatch(new Match(westernConferenceTeams.get(6), westernConferenceTeams.get(1), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 2));
            firstRound.addMatch(new Match(easternConferenceTeams.get(7), easternConferenceTeams.get(0), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            firstRound.addMatch(new Match(easternConferenceTeams.get(4), easternConferenceTeams.get(3), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            firstRound.addMatch(new Match(easternConferenceTeams.get(5), easternConferenceTeams.get(2), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 2));
            firstRound.addMatch(new Match(easternConferenceTeams.get(6), easternConferenceTeams.get(1), MatchEntry.MATCH_WEEK_FIRST_ROUND, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 2));


            //Add the week to the schedule and insert the matches in the database
            mModel.getSimulatorSchedule().addWeek(firstRound);
            mModel.insertSimulatorMatches(SimulatorModel.INSERT_MATCHES_PLAYOFFS_FIRSTROUND, firstRound);
        }

        if (remainingPlayoffTeams == 8) {

            //Query the first round match scores
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_FIRST_ROUND, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);

            Week conferenceSemifinals = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS);

            //Initialize conferenceSemifinals matchups from cursor
            conferenceSemifinals.addMatch(new Match(westernConferenceTeams.get(1), westernConferenceTeams.get(0), MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            conferenceSemifinals.addMatch(new Match(westernConferenceTeams.get(3), westernConferenceTeams.get(2), MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            conferenceSemifinals.addMatch(new Match(easternConferenceTeams.get(1), easternConferenceTeams.get(0), MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            conferenceSemifinals.addMatch(new Match(easternConferenceTeams.get(3), easternConferenceTeams.get(2), MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));

            //Add the week to the schedule and  insert the matches in the database
            mModel.getSimulatorSchedule().addWeek(conferenceSemifinals);
            mModel.insertSimulatorMatches(SimulatorModel.INSERT_MATCHES_PLAYOFFS_CONF_SEMIS, conferenceSemifinals);


        }
        if (remainingPlayoffTeams == 4) {

            //Query the divisional match scores
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);

            Week conferenceChampionship = new Week(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS);

            //Initialize conference conferenceChampionship matchups from cursor
            //The better seed is always the home team, so they are added as team two (home team)
            // Highest remaining seed plays lowest
            conferenceChampionship.addMatch(new Match(westernConferenceTeams.get(1), westernConferenceTeams.get(0), MatchEntry.MATCH_WEEK_CONFERENCE_FINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO,  1));
            conferenceChampionship.addMatch(new Match(easternConferenceTeams.get(1), easternConferenceTeams.get(0), MatchEntry.MATCH_WEEK_CONFERENCE_FINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));

            //Add the week to the schedule and insert the matches in the database
            mModel.getSimulatorSchedule().addWeek(conferenceChampionship);
            mModel.insertSimulatorMatches(SimulatorModel.INSERT_MATCHES_PLAYOFFS_CONF_FINALS, conferenceChampionship);

        }
        if (remainingPlayoffTeams == 2) {

            //Query the conference championship match scores
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);

            Week nbaFinals = new Week(MatchEntry.MATCH_WEEK_NBA_FINALS);

            //Initialize nbaFinals
            //Team with better record is the home team
            if (westernConferenceTeams.get(0).getWinLossPct() > easternConferenceTeams.get(0).getWinLossPct()) {
                nbaFinals.addMatch(new Match(easternConferenceTeams.get(0), westernConferenceTeams.get(0), MatchEntry.MATCH_WEEK_NBA_FINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO, 1));
            } else {
                nbaFinals.addMatch(new Match(westernConferenceTeams.get(0), easternConferenceTeams.get(0), MatchEntry.MATCH_WEEK_NBA_FINALS, this, MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO,  1));
            }


            //Add the week to the schedule and insert the matches in the database
            mModel.getSimulatorSchedule().addWeek(nbaFinals);
            mModel.insertSimulatorMatches(SimulatorModel.INSERT_MATCHES_PLAYOFFS_NBA_FINALS, nbaFinals);

        }

        if (remainingPlayoffTeams == 1) {

            //Query the superbowl match scores
            mModel.querySimulatorMatches(MatchEntry.MATCH_WEEK_NBA_FINALS, true, SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY);

        }


    }

    @Override
    public boolean getPlayoffsStarted() {
        return mSimulatorPlayoffsStarted;
    }

    @Override
    public void setPlayoffsStarted(boolean playoffsStarted) {
        mSimulatorPlayoffsStarted = playoffsStarted;
    }

    public void setCurrentSimulatorWeekPreference(int currentWeek) {
        //Set current week preference when week is updated
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(mContext.getString(R.string.settings_simulator_week_num_key), currentWeek).apply();
        prefs.commit();
    }

    public void setCurrentSeasonWeekPreference(int currentWeek) {
        //Set current week preference when week is updated
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(mContext.getString(R.string.settings_season_week_num_key), currentWeek).apply();
        prefs.commit();
    }

    private void setSeasonLoadedPreference(Boolean seasonLoaded) {
        //Set the season loaded preference boolean
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putBoolean(mContext.getString(R.string.settings_season_loaded_key), seasonLoaded).apply();
        prefs.commit();
    }


    private void setSimulatorTeamEloType() {
        Integer eloType = mSharedPreferences.getInt(mContext.getString(R.string.settings_elo_type_key), mContext.getResources().getInteger(R.integer.settings_elo_type_current_season));
        if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_current_season)) {
            resetSimulatorTeamCurrentSeasonElos();
        }
        if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_user)) {
            resetSimulatorTeamUserElos();
        }
        if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_last_season)) {
            resetSimulatorTeamLastSeasonElos();
        }
    }

    private void setSeasonInitializedPreference(boolean seasonInitialized) {
        //Set season initialized boolean preference
        SimulatorPresenter.setSeasonInitialized(seasonInitialized);
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putBoolean(mContext.getString(R.string.settings_season_initialized_key), seasonInitialized);
        prefs.commit();
    }

    private Boolean getSeasonLoadedPref() {
        //Return the season loaded preference boolean
        return mSharedPreferences.getBoolean(mContext.getString(R.string.settings_season_loaded_key), mContext.getResources().getBoolean(R.bool.settings_season_loaded_default));
    }


}
