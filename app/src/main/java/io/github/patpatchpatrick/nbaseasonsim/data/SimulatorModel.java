package io.github.patpatchpatrick.nbaseasonsim.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.R;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.SimulatorMvpContract;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Match;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.NBAConstants;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Schedule;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.MatchEntry;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Week;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SimulatorModel implements SimulatorMvpContract.SimulatorModel {
    //Simulator model class used to manage communication with database

    @Inject
    ContentResolver contentResolver;

    @Inject
    SimulatorMvpContract.SimulatorPresenter mPresenter;

    private CompositeDisposable mCompositeDisposable;

    //FINAL integers used to determine insert types and query types for when data  is inserted or
    //queried from the database.
    //These insertTypes and queryTypes are used to determine what happens to the data

    public static final int QUERY_STANDINGS_PLAYOFF = 1;
    public static final int QUERY_STANDINGS_REGULAR = 2;
    public static final int QUERY_STANDINGS_LOAD_SEASON = 3;
    public static final int QUERY_STANDINGS_POSTSEASON = 4;
    public static final int QUERY_STANDINGS_LOAD_POSTSEASON = 5;
    public static final int QUERY_MATCHES_ALL = 0;

    public static final int QUERY_FROM_SIMULATOR_ACTIVITY = 0;
    public static final int QUERY_FROM_NEXT_WEEK_MATCHES_ACTIVITY = 1;
    public static final int QUERY_FROM_SEASON_STANDINGS_ACTIVITY = 2;

    public static final int INSERT_MATCHES_SCHEDULE = 0;
    public static final int INSERT_MATCHES_PLAYOFFS_FIRSTROUND = 1;
    public static final int INSERT_MATCHES_PLAYOFFS_CONF_SEMIS = 2;
    public static final int INSERT_MATCHES_PLAYOFFS_CONF_FINALS = 3;
    public static final int INSERT_MATCHES_PLAYOFFS_NBA_FINALS = 4;

    public static final int LOAD_SEASON_FROM_HOME_SEASON_SIM = 0;
    public static final int LOAD_SEASON_FROM_HOME_MATCH_PREDICT = 1;
    public static final int LOAD_SEASON_FROM_SETTINGS = 2;

    //Data for season resources
    public Schedule mSimulatorSchedule;
    public Schedule mSeasonSchedule;
    public HashMap<String, Team> mSimulatorTeamList;
    public HashMap<String, Team> mSeasonTeamList;
    public HashMap<String, Double> mUserEloList;
    public HashMap<String, Integer> mTeamLogoMap;

    private Scheduler mScheduler;

    public SimulatorModel() {

        //Create new composite disposable to manage disposables from RxJava subscriptions
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        mCompositeDisposable = compositeDisposable;

        //Scheduler with fixed thread pool to prevent Schedulers.io from creating too many threads/memory leak
        mScheduler = Schedulers.from(Executors.newFixedThreadPool(50));

    }


    @Override
    public void setSimulatorSchedule(Schedule schedule) {
        mSimulatorSchedule = schedule;
    }

    @Override
    public void setSeasonSchedule(Schedule schedule) {
        mSeasonSchedule = schedule;
    }

    @Override
    public void setSimulatorTeamList(HashMap<String, Team> teamList) {
        mSimulatorTeamList = teamList;
    }

    @Override
    public void setSeasonTeamList(HashMap<String, Team> teamList) {
        mSeasonTeamList = teamList;
    }

    @Override
    public void createTeamLogoMap() {

        //Create list that maps team logos to names
        HashMap<String, Integer> teamLogos = new HashMap();
        teamLogos.put(NBAConstants.TEAM_ATLANTA_HAWKS_STRING, R.drawable.atlantahawks);
        teamLogos.put(NBAConstants.TEAM_BOSTON_CELTICS_STRING, R.drawable.bostonceltics);
        teamLogos.put(NBAConstants.TEAM_BROOKLYN_NETS_STRING, R.drawable.brooklynnets);
        teamLogos.put(NBAConstants.TEAM_CHARLOTTE_HORNETS_STRING, R.drawable.charlottehornets);
        teamLogos.put(NBAConstants.TEAM_CHICAGO_BULLS_STRING, R.drawable.chicagobulls);
        teamLogos.put(NBAConstants.TEAM_CLEVELAND_CAVALIERS_STRING, R.drawable.clevelandcavaliers);
        teamLogos.put(NBAConstants.TEAM_DALLAS_MAVERICKS_STRING, R.drawable.dallasmavericks);
        teamLogos.put(NBAConstants.TEAM_DENVER_NUGGETS_STRING, R.drawable.denvernuggets);
        teamLogos.put(NBAConstants.TEAM_DETROIT_PISTONS_STRING, R.drawable.detroitpistons);
        teamLogos.put(NBAConstants.TEAM_GOLDENSTATE_WARRIORS_STRING, R.drawable.goldenstatewarriors);
        teamLogos.put(NBAConstants.TEAM_HOUSTON_ROCKETS_STRING, R.drawable.houstonrockets);
        teamLogos.put(NBAConstants.TEAM_INDIANA_PACERS_STRING, R.drawable.indianapacers);
        teamLogos.put(NBAConstants.TEAM_LOSANGELES_CLIPPERS_STRING, R.drawable.losangelesclippers);
        teamLogos.put(NBAConstants.TEAM_LOSANGELES_LAKERS_STRING, R.drawable.losangeleslakers);
        teamLogos.put(NBAConstants.TEAM_MEMPHIS_GRIZZLIES_STRING, R.drawable.memphisgrizzlies);
        teamLogos.put(NBAConstants.TEAM_MIAMI_HEAT_STRING, R.drawable.miamiheat);
        teamLogos.put(NBAConstants.TEAM_MILWAUKEE_BUCKS_STRING, R.drawable.milwaukeebucks);
        teamLogos.put(NBAConstants.TEAM_MINNESOTA_TIMBERWOLVES_STRING, R.drawable.minnesotatimberwolves);
        teamLogos.put(NBAConstants.TEAM_NEWORLEANS_PELICANS_STRING, R.drawable.neworleanspelicans);
        teamLogos.put(NBAConstants.TEAM_NEWYORK_KNICKS_STRING, R.drawable.newyorkknicks);
        teamLogos.put(NBAConstants.TEAM_OKLAHOMACITY_THUNDER_STRING, R.drawable.oklahomacitythunder);
        teamLogos.put(NBAConstants.TEAM_ORLANDO_MAGIC_STRING, R.drawable.orlandomagic);
        teamLogos.put(NBAConstants.TEAM_PHILADELPHIA_76ERS_STRING, R.drawable.philadelphia76ers);
        teamLogos.put(NBAConstants.TEAM_PHOENIX_SUNS_STRING, R.drawable.phoenixsuns);
        teamLogos.put(NBAConstants.TEAM_PORTLAND_TRAILBLAZERS_STRING, R.drawable.portlandtrailblazers);
        teamLogos.put(NBAConstants.TEAM_SACRAMENTO_KINGS_STRING, R.drawable.sacramentokings);
        teamLogos.put(NBAConstants.TEAM_SANANTONIO_SPURS_STRING, R.drawable.sanantoniospurs);
        teamLogos.put(NBAConstants.TEAM_TORONTO_RAPTORS_STRING, R.drawable.torontoraptors);
        teamLogos.put(NBAConstants.TEAM_UTAH_JAZZ_STRING, R.drawable.utahjazz);
        teamLogos.put(NBAConstants.TEAM_WASHINGTON_WIZARDS_STRING, R.drawable.washingtonwizards);

        mTeamLogoMap = teamLogos;
    }

    @Override
    public int getLogo(String teamName) {
        int teamDrawable = mTeamLogoMap.get(teamName);
        return teamDrawable;
    }

    @Override
    public void setTeamEloMap(HashMap<String, Double> teamEloMap) {
        mUserEloList = teamEloMap;
    }

    @Override
    public Team getSimulatorTeam(String teamName) {
        return mSimulatorTeamList.get(teamName);
    }

    @Override
    public Team getCurrentSeasonTeam(String teamName) {
        return mSeasonTeamList.get(teamName);
    }

    @Override
    public HashMap<String, Team> getSimulatorTeamList() {
        return mSimulatorTeamList;
    }

    @Override
    public HashMap<String, Team> getSeasonTeamList() {
        return mSeasonTeamList;
    }

    @Override
    public ArrayList<Team> getSimulatorTeamArrayList() {

        ArrayList<Team> teamArrayList = new ArrayList();

        for (String teamName : mSimulatorTeamList.keySet()) {
            teamArrayList.add(mSimulatorTeamList.get(teamName));
        }

        return teamArrayList;
    }

    @Override
    public ArrayList<Team> getSeasonTeamArrayList() {
        ArrayList<Team> teamArrayList = new ArrayList();

        for (String teamName : mSeasonTeamList.keySet()) {
            teamArrayList.add(mSeasonTeamList.get(teamName));
        }

        return teamArrayList;
    }

    @Override
    public ArrayList<String> getTeamNameArrayList() {
        ArrayList<String> teamNameArrayList = new ArrayList();

        for (String teamName : mSimulatorTeamList.keySet()) {
            teamNameArrayList.add(teamName);
        }

        return teamNameArrayList;
    }

    @Override
    public HashMap<String, Double> getTeamEloMap() {
        return mUserEloList;
    }

    @Override
    public Schedule getSimulatorSchedule() {
        return mSimulatorSchedule;
    }

    @Override
    public Schedule getSeasonSchedule() {
        return mSeasonSchedule;
    }

    @Override
    public void insertMatch(final Match match) {

        //Insert a match into the database

        Observable<Uri> insertMatchObservable = Observable.fromCallable(new Callable<Uri>() {
            @Override
            public Uri call() throws Exception {
                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE, match.getTeam1().getName());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO, match.getTeam2().getName());
                values.put(MatchEntry.COLUMN_MATCH_WEEK, match.getWeek());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());
                Uri uri = contentResolver.insert(MatchEntry.CONTENT_URI, values);
                return uri;
            }
        });

        insertMatchObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Uri>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Uri uri) {
                match.setUri(uri);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {


            }
        });


    }

    @Override
    public void insertSimulatorMatches(final int insertType) {

        //Insert a regular season schedule's matches into the db
        //First, iterate through the schedule and add all season matches to an ArrayList
        //Then, add an Obervable.fromIterable to iterate through the ArrayList and add each
        //match to the db.
        //After all matches are added to the db, notify the presenter via the simulatorMatchesInserted callback
        ArrayList<Match> simulatorSeasonMatches = new ArrayList<>();
        int weekNumber = 1;
        while (weekNumber <= 17) {
            ArrayList<Match> weekMatches = mSimulatorSchedule.getWeek(weekNumber).getMatches();
            for (Match match : weekMatches) {
                simulatorSeasonMatches.add(match);
            }
            weekNumber++;
        }


        Observable<Match> insertMatchesObservable = Observable.fromIterable(simulatorSeasonMatches);
        insertMatchesObservable.subscribeOn(AndroidSchedulers.mainThread()).observeOn(mScheduler).subscribe(new Observer<Match>() {
            @Override
            public void onSubscribe(Disposable d) {

                mCompositeDisposable.add(d);

            }

            @Override
            public void onNext(Match match) {

                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE, match.getTeam1().getName());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO, match.getTeam2().getName());
                values.put(MatchEntry.COLUMN_MATCH_WEEK, match.getWeek());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());
                Uri uri = contentResolver.insert(MatchEntry.CONTENT_URI, values);

                match.setUri(uri);

            }

            @Override
            public void onError(Throwable e) {

                Log.d("InsertMatchesError: ", "" + e);

            }

            @Override
            public void onComplete() {

                mPresenter.simulatorMatchesInserted(insertType);
            }
        });


    }

    @Override
    public void insertSimulatorMatches(final int insertType, Week week) {

        //Insert a week's matches into the database

        ArrayList<Match> weekMatches = week.getMatches();


        Observable<Match> insertMatchesObservable = Observable.fromIterable(weekMatches);
        insertMatchesObservable.subscribeOn(AndroidSchedulers.mainThread()).observeOn(mScheduler).subscribe(new Observer<Match>() {
            @Override
            public void onSubscribe(Disposable d) {

                mCompositeDisposable.add(d);

            }

            @Override
            public void onNext(Match match) {

                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE, match.getTeam1().getName());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO, match.getTeam2().getName());
                values.put(MatchEntry.COLUMN_MATCH_WEEK, match.getWeek());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());
                Uri uri = contentResolver.insert(MatchEntry.CONTENT_URI, values);

                match.setUri(uri);

            }

            @Override
            public void onError(Throwable e) {

                Log.d("InsertMatchesError: ", "" + e);

            }

            @Override
            public void onComplete() {

                mPresenter.simulatorMatchesInserted(insertType);
            }
        });


    }

    @Override
    public void insertSeasonMatches(final int insertType) {

        //Insert a regular season schedule's matches into the db
        //First, iterate through the schedule and add all season matches to an ArrayList
        //Then, add an Obervable.fromIterable to iterate through the ArrayList and add each
        //match to the db.
        //After all matches are added to the db, notify the presenter via the simulatorMatchesInserted callback
        ArrayList<Match> currentSeasonMatches = new ArrayList<>();
        int weekNumber = 1;
        while (weekNumber <= 17) {
            ArrayList<Match> weekMatches = mSeasonSchedule.getWeek(weekNumber).getMatches();
            for (Match match : weekMatches) {
                currentSeasonMatches.add(match);
            }
            weekNumber++;
        }


        Observable<Match> insertMatchesObservable = Observable.fromIterable(currentSeasonMatches);
        insertMatchesObservable.subscribeOn(AndroidSchedulers.mainThread()).observeOn(mScheduler).subscribe(new Observer<Match>() {
            @Override
            public void onSubscribe(Disposable d) {

                mCompositeDisposable.add(d);

            }

            @Override
            public void onNext(Match match) {

                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE, match.getTeam1().getName());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO, match.getTeam2().getName());
                values.put(MatchEntry.COLUMN_MATCH_WEEK, match.getWeek());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());
                values.put(MatchEntry.COLUMN_MATCH_CURRENT_SEASON, match.getCurrentSeason());
                Uri uri = contentResolver.insert(MatchEntry.CONTENT_URI, values);

                match.setUri(uri);

            }

            @Override
            public void onError(Throwable e) {

                Log.d("InsertMatchesError: ", "" + e);

            }

            @Override
            public void onComplete() {

                mPresenter.seasonMatchesInserted(insertType);
            }
        });

    }

    @Override
    public void insertTeam(final Team team) {

        //Insert a team into the database

        Observable<Uri> insertTeamObservable = Observable.fromCallable(new Callable<Uri>() {
            @Override
            public Uri call() throws Exception {
                String name = team.getName();
                String shortName = team.getShortName();
                double elo = team.getElo();
                double offRating = team.getOffRating();
                double defRating = team.getDefRating();
                int currentWins = team.getWins();
                int currentLosses = team.getLosses();
                int currentDraws = team.getDraws();
                int division = team.getDivision();
                int conference = team.getConference();

                ContentValues values = new ContentValues();
                values.put(TeamEntry.COLUMN_TEAM_NAME, name);
                values.put(TeamEntry.COLUMN_TEAM_SHORT_NAME, name);
                values.put(TeamEntry.COLUMN_TEAM_ELO, elo);
                values.put(TeamEntry.COLUMN_TEAM_OFF_RATING, offRating);
                values.put(TeamEntry.COLUMN_TEAM_DEF_RATING, defRating);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_WINS, currentWins);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES, currentLosses);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS, currentDraws);
                values.put(TeamEntry.COLUMN_TEAM_DIVISION, division);
                values.put(TeamEntry.COLUMN_TEAM_CONFERENCE, conference);

                //Insert values into database
                Uri uri = contentResolver.insert(TeamEntry.CONTENT_URI, values);
                return uri;
            }
        });

        insertTeamObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Uri>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Uri uri) {
                team.setUri(uri);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });


    }

    @Override
    public void insertSimulatorTeams() {

        //Insert a hashMap's matches into the db
        //First, iterate through the HashMap and add all matches to an ArrayList
        //Then, add an Obervable.fromIterable to iterate through the ArrayList and add each
        //team to the db.
        //After all teams are added to the db, notify the presenter via the simulatorTeamsInserted callback

        ArrayList<Team> simulatorTeamArrayList = new ArrayList<>();

        for (String teamName : mSimulatorTeamList.keySet()) {
            simulatorTeamArrayList.add(mSimulatorTeamList.get(teamName));
        }

        Observable<Team> insertTeamsObservable = Observable.fromIterable(simulatorTeamArrayList);
        insertTeamsObservable.subscribeOn(AndroidSchedulers.mainThread()).observeOn(mScheduler).subscribe(new Observer<Team>() {
            @Override
            public void onSubscribe(Disposable d) {

                mCompositeDisposable.add(d);

            }

            @Override
            public void onNext(Team team) {

                String name = team.getName();
                String shortName = team.getShortName();
                double elo = team.getElo();
                double defaultElo = team.getDefaultElo();
                double userElo = team.getUserElo();
                double teamRanking = team.getTeamRanking();
                double offRating = team.getOffRating();
                double defRating = team.getDefRating();
                int currentWins = team.getWins();
                int currentLosses = team.getLosses();
                int currentDraws = team.getDraws();
                int division = team.getDivision();
                int conference = team.getConference();

                ContentValues values = new ContentValues();
                values.put(TeamEntry.COLUMN_TEAM_NAME, name);
                values.put(TeamEntry.COLUMN_TEAM_SHORT_NAME, shortName);
                values.put(TeamEntry.COLUMN_TEAM_ELO, elo);
                values.put(TeamEntry.COLUMN_TEAM_DEFAULT_ELO, defaultElo);
                values.put(TeamEntry.COLUMN_TEAM_USER_ELO, userElo);
                values.put(TeamEntry.COLUMN_TEAM_RANKING, teamRanking);
                values.put(TeamEntry.COLUMN_TEAM_OFF_RATING, offRating);
                values.put(TeamEntry.COLUMN_TEAM_DEF_RATING, defRating);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_WINS, currentWins);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES, currentLosses);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS, currentDraws);
                values.put(TeamEntry.COLUMN_TEAM_DIVISION, division);
                values.put(TeamEntry.COLUMN_TEAM_CONFERENCE, conference);

                //Insert values into database
                Uri uri = contentResolver.insert(TeamEntry.CONTENT_URI, values);

                team.setUri(uri);

            }

            @Override
            public void onError(Throwable e) {

                Log.d("InsertTeamsError: ", "" + e);

            }

            @Override
            public void onComplete() {

                mPresenter.simulatorTeamsInserted();

            }
        });

    }

    @Override
    public void insertSeasonTeams() {

        //Insert the current seasons hashMap's teams into the db
        //First, iterate through the HashMap and add all matches to an ArrayList
        //Then, add an Obervable.fromIterable to iterate through the ArrayList and add each
        //team to the db.
        //After all teams are added to the db, notify the presenter via the seasonTeamsInserted callback

        ArrayList<Team> seasonTeamArrayList = new ArrayList<>();

        for (String teamName : mSeasonTeamList.keySet()) {
            seasonTeamArrayList.add(mSeasonTeamList.get(teamName));
        }

        Observable<Team> insertTeamsObservable = Observable.fromIterable(seasonTeamArrayList);
        insertTeamsObservable.subscribeOn(AndroidSchedulers.mainThread()).observeOn(mScheduler).subscribe(new Observer<Team>() {
            @Override
            public void onSubscribe(Disposable d) {

                mCompositeDisposable.add(d);

            }

            @Override
            public void onNext(Team team) {

                String name = team.getName();
                String shortName = team.getShortName();
                double elo = team.getElo();
                double defaultElo = team.getDefaultElo();
                double userElo = team.getUserElo();
                double teamRanking = team.getTeamRanking();
                double offRating = team.getOffRating();
                double defRating = team.getDefRating();
                int currentWins = team.getWins();
                int currentLosses = team.getLosses();
                int currentDraws = team.getDraws();
                int division = team.getDivision();
                int conference = team.getConference();
                int currentSeason = team.getCurrentSeason();

                ContentValues values = new ContentValues();
                values.put(TeamEntry.COLUMN_TEAM_NAME, name);
                values.put(TeamEntry.COLUMN_TEAM_SHORT_NAME, shortName);
                values.put(TeamEntry.COLUMN_TEAM_ELO, elo);
                values.put(TeamEntry.COLUMN_TEAM_DEFAULT_ELO, defaultElo);
                values.put(TeamEntry.COLUMN_TEAM_USER_ELO, userElo);
                values.put(TeamEntry.COLUMN_TEAM_RANKING, teamRanking);
                values.put(TeamEntry.COLUMN_TEAM_OFF_RATING, offRating);
                values.put(TeamEntry.COLUMN_TEAM_DEF_RATING, defRating);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_WINS, currentWins);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES, currentLosses);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS, currentDraws);
                values.put(TeamEntry.COLUMN_TEAM_DIVISION, division);
                values.put(TeamEntry.COLUMN_TEAM_CONFERENCE, conference);
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_SEASON, currentSeason);

                //Insert values into database
                Uri uri = contentResolver.insert(TeamEntry.CONTENT_URI, values);

                team.setUri(uri);

            }

            @Override
            public void onError(Throwable e) {

                Log.d("InsertTeamsError: ", "" + e);

            }

            @Override
            public void onComplete() {

                mPresenter.seasonTeamsInserted();

            }
        });

    }

    @Override
    public void updateMatch(final Match match, final Uri uri) {


        //Update a match in the database

        Observable<Integer> updateMatchObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {

                //Get integers for if team one won or not.  Convert the match boolean to an int.
                int teamOneWon = match.getTeamOneWon();


                //Update match database scores and match complete values
                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE, match.getTeam1Score());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE, match.getTeam2Score());
                values.put(MatchEntry.COLUMN_MATCH_TEAM_ONE_WON, teamOneWon);
                values.put(MatchEntry.COLUMN_MATCH_COMPLETE, MatchEntry.MATCH_COMPLETE_YES);
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());

                int rowsUpdated = contentResolver.update(uri, values, null, null);

                return rowsUpdated;
            }
        });

        updateMatchObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Integer rowsUpdated) {
                Log.d("ModelWeek", "" + match.getWeek());
                getSimulatorSchedule().getWeek(match.getWeek()).matchUpdated();
            }

            @Override
            public void onError(Throwable e) {
                Log.d("UpdateMatchError ", "" + e);
            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void updateMatchOdds(final Match match, final Uri uri) {

        //Update a match in the database

        Observable<Integer> updateMatchObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {


                //Update match database odds values
                ContentValues values = new ContentValues();
                values.put(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS, match.getTeamTwoOdds());

                int rowsUpdated = contentResolver.update(uri, values, null, null);

                return rowsUpdated;
            }
        });

        updateMatchObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Integer rowsUpdated) {
            }

            @Override
            public void onError(Throwable e) {
                Log.d("UpdateMatchError ", "" + e);
            }

            @Override
            public void onComplete() {
            }
        });

    }


    @Override
    public void updateTeam(final Team team, final Uri uri) {


        //Update a team in the database

        Observable<Integer> updateTeamObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                //Update team database wins, losses and win loss pct values
                ContentValues values = new ContentValues();
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_WINS, team.getWins());
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES, team.getLosses());
                values.put(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS, team.getDraws());
                values.put(TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT, team.getWinLossPct());
                values.put(TeamEntry.COLUMN_TEAM_DIV_WINS, team.getDivisionWins());
                values.put(TeamEntry.COLUMN_TEAM_DIV_LOSSES, team.getDivisionLosses());
                values.put(TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT, team.getDivisionWinLossPct());
                values.put(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE, team.getPlayoffEligible());
                values.put(TeamEntry.COLUMN_TEAM_PLAYOFF_GAME, team.getPlayoffGame());
                values.put(TeamEntry.COLUMN_TEAM_ELO, team.getElo());
                values.put(TeamEntry.COLUMN_TEAM_USER_ELO, team.getUserElo());

                int rowsUpdated = contentResolver.update(uri, values, null, null);


                return rowsUpdated;
            }
        });

        updateTeamObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Integer rowsUpdated) {
            }

            @Override
            public void onError(Throwable e) {
                Log.d("UpdateTeamError: ", "" + e);

            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void querySimulatorStandings(final int queryType) {

        //Query the standings from the database

        Observable<Cursor> queryStandingsObservable = Observable.fromCallable(new Callable<Cursor>() {
            @Override
            public Cursor call() throws Exception {
                //Query standings
                String[] standingsProjection = {
                        TeamEntry._ID,
                        TeamEntry.COLUMN_TEAM_NAME,
                        TeamEntry.COLUMN_TEAM_SHORT_NAME,
                        TeamEntry.COLUMN_TEAM_DIVISION,
                        TeamEntry.COLUMN_TEAM_CONFERENCE,
                        TeamEntry.COLUMN_TEAM_CURRENT_WINS,
                        TeamEntry.COLUMN_TEAM_CURRENT_LOSSES,
                        TeamEntry.COLUMN_TEAM_CURRENT_DRAWS,
                        TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT,
                        TeamEntry.COLUMN_TEAM_DIV_WINS,
                        TeamEntry.COLUMN_TEAM_DIV_LOSSES,
                        TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT,
                        TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE,
                        TeamEntry.COLUMN_TEAM_PLAYOFF_GAME,
                        TeamEntry.COLUMN_TEAM_ELO,
                        TeamEntry.COLUMN_TEAM_DEFAULT_ELO,
                        TeamEntry.COLUMN_TEAM_USER_ELO,
                        TeamEntry.COLUMN_TEAM_RANKING,
                        TeamEntry.COLUMN_TEAM_OFF_RATING,
                        TeamEntry.COLUMN_TEAM_DEF_RATING,
                        TeamEntry.COLUMN_TEAM_CURRENT_SEASON,
                };

                Cursor standingsCursor;

                //Query the team data depending on queryType requested

                if (queryType == QUERY_STANDINGS_POSTSEASON || queryType == QUERY_STANDINGS_LOAD_POSTSEASON) {

                    //For postseason query,  don't query teams that aren't playoff eligible
                    //Sort by playoff seed and conference

                    String selection = TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE + "!=? AND " + TeamEntry.COLUMN_TEAM_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(TeamEntry.PLAYOFF_NOT_ELIGIBLE),  String.valueOf(TeamEntry.CURRENT_SEASON_NO)};

                    standingsCursor = contentResolver.query(TeamEntry.CONTENT_URI, standingsProjection,
                            selection, selectionArgs,
                            TeamEntry.COLUMN_TEAM_CONFERENCE + ", " + TeamEntry.COLUMN_TEAM_PLAYOFF_GAME + ", " + TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE);

                } else {

                    String selection = TeamEntry.COLUMN_TEAM_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(TeamEntry.CURRENT_SEASON_NO)};

                    standingsCursor = contentResolver.query(TeamEntry.CONTENT_URI, standingsProjection,
                            selection, selectionArgs,
                            TeamEntry.COLUMN_TEAM_DIVISION + ", " + TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT + " DESC, " + TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT + " DESC");
                }

                return standingsCursor;
            }
        });

        queryStandingsObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Cursor>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Cursor standingsCursor) {
                Log.d("MODEL", "STANDQUERESPONSE");
                mPresenter.simulatorStandingsQueried(queryType, standingsCursor);
            }

            @Override
            public void onError(Throwable e) {
                Log.d("Query Error: ", "" + e);

            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void queryCurrentSeasonStandings(final int queryType) {

        //Query the standings from the database

        Observable<Cursor> queryStandingsObservable = Observable.fromCallable(new Callable<Cursor>() {
            @Override
            public Cursor call() throws Exception {
                //Query standings
                String[] standingsProjection = {
                        TeamEntry._ID,
                        TeamEntry.COLUMN_TEAM_NAME,
                        TeamEntry.COLUMN_TEAM_SHORT_NAME,
                        TeamEntry.COLUMN_TEAM_DIVISION,
                        TeamEntry.COLUMN_TEAM_CONFERENCE,
                        TeamEntry.COLUMN_TEAM_CURRENT_WINS,
                        TeamEntry.COLUMN_TEAM_CURRENT_LOSSES,
                        TeamEntry.COLUMN_TEAM_CURRENT_DRAWS,
                        TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT,
                        TeamEntry.COLUMN_TEAM_DIV_WINS,
                        TeamEntry.COLUMN_TEAM_DIV_LOSSES,
                        TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT,
                        TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE,
                        TeamEntry.COLUMN_TEAM_PLAYOFF_GAME,
                        TeamEntry.COLUMN_TEAM_ELO,
                        TeamEntry.COLUMN_TEAM_DEFAULT_ELO,
                        TeamEntry.COLUMN_TEAM_USER_ELO,
                        TeamEntry.COLUMN_TEAM_RANKING,
                        TeamEntry.COLUMN_TEAM_OFF_RATING,
                        TeamEntry.COLUMN_TEAM_DEF_RATING,
                        TeamEntry.COLUMN_TEAM_CURRENT_SEASON,
                };

                Cursor standingsCursor;

                //Query the team data depending on queryType requested

                if (queryType == QUERY_STANDINGS_POSTSEASON || queryType == QUERY_STANDINGS_LOAD_POSTSEASON) {

                    //For postseason query,  don't query teams that aren't playoff eligible
                    //Sort by playoff seed and conference

                    String selection = TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE + "!=? AND " + TeamEntry.COLUMN_TEAM_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(TeamEntry.PLAYOFF_NOT_ELIGIBLE),  String.valueOf(TeamEntry.CURRENT_SEASON_YES)};

                    standingsCursor = contentResolver.query(TeamEntry.CONTENT_URI, standingsProjection,
                            selection, selectionArgs,
                            TeamEntry.COLUMN_TEAM_CONFERENCE + ", " + TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE);

                } else {

                    String selection = TeamEntry.COLUMN_TEAM_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(TeamEntry.CURRENT_SEASON_YES)};

                    standingsCursor = contentResolver.query(TeamEntry.CONTENT_URI, standingsProjection,
                            selection, selectionArgs,
                            TeamEntry.COLUMN_TEAM_DIVISION + ", " + TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT + " DESC, " + TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT + " DESC");
                }

                return standingsCursor;
            }
        });

        queryStandingsObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Cursor>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Cursor standingsCursor) {
                Log.d("MODEL", "STANDQUERESPONSE");
                mPresenter.currentSeasonStandingsQueried(queryType, standingsCursor);
            }

            @Override
            public void onError(Throwable e) {
                Log.d("Query Error: ", "" + e);

            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void querySimulatorMatches(final int weekNumber, final boolean singleWeek, final int queryFrom) {

        //Query the matches/schedule from the database for simulator

        Observable<Cursor> queryMatchesObservable = Observable.fromCallable(new Callable<Cursor>() {
            @Override
            public Cursor call() throws Exception {
                //Query standings
                String[] matchesProjection = {
                        MatchEntry._ID,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS,
                        MatchEntry.COLUMN_MATCH_WEEK,
                        MatchEntry.COLUMN_MATCH_COMPLETE,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE_WON,
                        MatchEntry.COLUMN_MATCH_CURRENT_SEASON,
                };

                Cursor matchesCursor;

                //Either query all matches or query matches for a specific week, depending on the
                //input weekNumber integer that was given

                if (weekNumber == QUERY_MATCHES_ALL) {

                    //Query all matches

                    String selection = MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            null);
                } else if (weekNumber == MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS) {

                    //Query divisional playoff matches (include both division games and completed wildcard games)

                    String selection = "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )" ;
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");
                } else if (weekNumber == MatchEntry.MATCH_WEEK_CONFERENCE_FINALS) {

                    //Query conference playoff matches (include conferences matches and completed division and wilcard matches)

                    String selection = "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");
                } else if (weekNumber == MatchEntry.MATCH_WEEK_NBA_FINALS) {

                    //Query superbowl playoff matches (include superbowl matches and completed conference, division and wilcard matches)

                    String selection =  "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");

                } else if (singleWeek == true) {

                    //Query a single week's matches

                    String selection = MatchEntry.COLUMN_MATCH_WEEK + "=? AND " + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            null);

                } else {

                    //Query all matches up to and including a single week

                    String selection = MatchEntry.COLUMN_MATCH_WEEK + "<=? AND " + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_NO)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");

                }


                return matchesCursor;
            }
        });

        queryMatchesObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Cursor>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Cursor matchesCursor) {
                Log.d("MODEL", "MATCHESQUERESPONSE");
                mPresenter.simulatorMatchesQueried(weekNumber, matchesCursor, queryFrom);
            }

            @Override
            public void onError(Throwable e) {
                Log.d("Query Error: ", "" + e);

            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void queryCurrentSeasonMatches(final int weekNumber, final boolean singleMatch, final int queryFrom) {

        //Query the matches/schedule from the database for simulator

        Observable<Cursor> queryMatchesObservable = Observable.fromCallable(new Callable<Cursor>() {
            @Override
            public Cursor call() throws Exception {
                //Query standings
                String[] matchesProjection = {
                        MatchEntry._ID,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE,
                        MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS,
                        MatchEntry.COLUMN_MATCH_WEEK,
                        MatchEntry.COLUMN_MATCH_COMPLETE,
                        MatchEntry.COLUMN_MATCH_TEAM_ONE_WON,
                        MatchEntry.COLUMN_MATCH_CURRENT_SEASON,
                };

                Cursor matchesCursor;

                //Either query all matches or query matches for a specific week, depending on the
                //input weekNumber integer that was given

                if (weekNumber == QUERY_MATCHES_ALL) {

                    //Query all matches

                    String selection = MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            null);
                } else if (weekNumber == MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS) {

                    //Query divisional playoff matches (include both division games and completed wildcard games)

                    String selection = "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )" ;
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");
                } else if (weekNumber == MatchEntry.MATCH_WEEK_CONFERENCE_FINALS) {

                    //Query conference playoff matches (include conferences matches and completed division and wilcard matches)

                    String selection = "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");
                } else if (weekNumber == MatchEntry.MATCH_WEEK_NBA_FINALS) {

                    //Query superbowl playoff matches (include superbowl matches and completed conference, division and wilcard matches)

                    String selection =  "(" + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? OR " + MatchEntry.COLUMN_MATCH_WEEK + "=? ) AND (" + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=? )";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_FINALS), String.valueOf(MatchEntry.MATCH_WEEK_CONFERENCE_SEMIFINALS), String.valueOf(MatchEntry.MATCH_WEEK_FIRST_ROUND), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");

                } else if (singleMatch == true) {

                    //Query a single week's matches

                    String selection = MatchEntry.COLUMN_MATCH_WEEK + "=? AND " + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            null);

                } else {

                    //Query all matches up to and including a single week

                    String selection = MatchEntry.COLUMN_MATCH_WEEK + "<=? AND " + MatchEntry.COLUMN_MATCH_CURRENT_SEASON + "=?";
                    String[] selectionArgs = new String[]{String.valueOf(weekNumber), String.valueOf(MatchEntry.MATCH_TEAM_CURRENT_SEASON_YES)};

                    matchesCursor = contentResolver.query(MatchEntry.CONTENT_URI, matchesProjection,
                            selection, selectionArgs,
                            MatchEntry.COLUMN_MATCH_WEEK + " DESC");

                }


                return matchesCursor;
            }
        });

        queryMatchesObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Cursor>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Cursor matchesCursor) {
                mPresenter.currentSeasonMatchesQueried(weekNumber, matchesCursor, queryFrom);
            }

            @Override
            public void onError(Throwable e) {
                Log.d("Query Error: ", "" + e);

            }

            @Override
            public void onComplete() {
            }
        });


    }

    @Override
    public void deleteAllData() {

        //Delete all data from the database

        Observable<Integer> deleteDataObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                int teamsDeleted = contentResolver.delete(TeamEntry.CONTENT_URI, null, null);
                int matchesDeleted = contentResolver.delete(MatchEntry.CONTENT_URI, null, null);
                return teamsDeleted + matchesDeleted;
            }
        });

        deleteDataObservable.subscribeOn(mScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                mCompositeDisposable.add(d);
            }

            @Override
            public void onNext(Integer rowsDeleted) {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {
                mPresenter.dataDeleted();


            }
        });


    }

    @Override
    public void destroyModel() {

        //This method is called when the main activity is destroyed.  It will dispose of all disposables
        //within the composite disposable.

        mCompositeDisposable.dispose();
    }


}
