package io.github.patpatchpatrick.nbaseasonsim;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Animatable;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.text.DecimalFormat;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.dagger.ActivityComponent;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.MatchEntry;
import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.ScoreView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.SimulatorMvpContract;
import io.github.patpatchpatrick.nbaseasonsim.presenter.SimulatorPresenter;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;

public class SimulatorActivity extends AppCompatActivity implements SimulatorMvpContract.SimulatorView, ScoreView {

    @Inject
    SimulatorPresenter mPresenter;

    @Inject
    SimulatorModel mModel;

    @Inject
    SharedPreferences mSharedPreferences;


    ImageView mAnimatedFootball;
    Animatable mAnimatedFootballAnimatable;
    Button mSimulateSeason;
    Button mSimulateWeek;
    Button mStartPlayoffs;
    Button mResetButton;
    TextView mWeekNumberHeader;
    RecyclerView mScoresRecyclerView;
    RecyclerView mStandingsRecyclerView;
    private AdView mAdView;
    StandingsRecyclerViewAdapter mStandingsRecyclerViewAdapter;
    ScoresRecyclerViewAdapter mScoresRecyclerViewAdapter;
    private static ActivityComponent mActivityComponent;
    private static boolean mResetInProgress = false;

    public final static int STANDINGS_TYPE_REGULAR_SEASON = 1;
    public final static int STANDINGS_TYPE_PLAYOFFS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setBackgroundDrawable(null);

        //Set up custom action bar
        final ViewGroup actionBarLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.actionbar_custom, null);
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(actionBarLayout);


        // Build out dagger activity component
        // Inject the mainActivity, presenter and model
        mActivityComponent = HomeScreen.getActivityComponent();
        mActivityComponent.inject(this);

        //Load the AdView to display banner advertisement
        AdRequest adRequest= new AdRequest.Builder().build();
        mAdView = (AdView) this.findViewById(R.id.simActivityAdView);
        mAdView.loadAd(adRequest);

        //Set the view on the presenter
        mPresenter.setView(this);
        mPresenter.addBaseView(this);
        mPresenter.addScoreView(this);

        mWeekNumberHeader = (TextView) findViewById(R.id.week_number_header);
        mSimulateSeason = (Button) findViewById(R.id.simulate_season_button);
        mSimulateWeek = (Button) findViewById(R.id.simulate_week_button);
        mStartPlayoffs = (Button) findViewById(R.id.start_playoffs_button);
        mResetButton = (Button) findViewById(R.id.reset_button);

        //Set up loading animation
        mAnimatedFootball = (ImageView) findViewById(R.id.simulate_activity_football_animation);
        mAnimatedFootballAnimatable = (Animatable) mAnimatedFootball.getDrawable();
        if (mAnimatedFootballAnimatable.isRunning()) {
            mAnimatedFootballAnimatable.stop();
        }

        setUpSharedPreferences();

        // Set up the scores/matches recyclerview
        mScoresRecyclerView = (RecyclerView) findViewById(R.id.scores_recycler_view);
        mScoresRecyclerView.setHasFixedSize(true);
        mScoresRecyclerView.setLayoutManager(new LinearLayoutManager(SimulatorActivity.this));
        mScoresRecyclerViewAdapter = new ScoresRecyclerViewAdapter();
        mScoresRecyclerView.setAdapter(mScoresRecyclerViewAdapter);

        // Set up the standings recyclerview
        mStandingsRecyclerView = (RecyclerView) findViewById(R.id.standings_recycler_view);
        mStandingsRecyclerView.setHasFixedSize(true);
        mStandingsRecyclerView.setLayoutManager(new LinearLayoutManager(SimulatorActivity.this));
        mStandingsRecyclerViewAdapter = new StandingsRecyclerViewAdapter();
        mStandingsRecyclerView.setAdapter(mStandingsRecyclerViewAdapter);


        //Load the season from the database
        if (mPresenter.getCurrentWeek() >= 1 && !mPresenter.getPlayoffsStarted()) {
            //If the current week is greater than one and playoffs haven't started, the season has been partially
            //simulated, so load the already simulated data
            mPresenter.loadAlreadySimulatedData();
        } else if (mPresenter.getPlayoffsStarted() && !playoffsComplete()) {
            mPresenter.loadAlreadySimulatedPlayoffData();
        } else {
            setUpViews();
        }


        //simulateSeasonXTimes(10000);

        mSimulateSeason.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Simulate season
                //Set views to not ready to simulate while the season simulates
                setViewsNotReadyToSimulate();
                mPresenter.simulateSeason();

            }
        });

        mSimulateWeek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Simulate the week
                //Disable the buttons until week is finished simulating
                setViewsNotReadyToSimulate();

                //Simulate either a regular week or playoff week depending on whether or not playoffs have started
                if (!mPresenter.getPlayoffsStarted()) {
                    mPresenter.simulateWeek();
                }
                if (mPresenter.getPlayoffsStarted()) {
                    mPresenter.simulatePlayoffWeek();
                }
            }
        });

        mStartPlayoffs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Start the playoffs
                mPresenter.setPlayoffsStarted(true);
                setPlayoffsStartedPreference(true);
                mPresenter.initiatePlayoffs();
            }
        });

        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Reset the season
                resetSeason();
            }
        });


    }

    private void simulateSeasonXTimes(int numTimes) {
        //Class to simulate the season as many times as necessary to collect playoff data
        //Class is manually run by developer every week to update playoff percentages
        mPresenter.mCurrentTestSimulations = 0;
        mPresenter.mTotalTestSimulations = numTimes;
        mPresenter.mTestSimulation = true;
        mPresenter.simulateTestSeason();
    }

    private void initializeTheme() {
        //Set the initial theme of the app based on shared prefs theme
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String appTheme = sharedPreferences.getString(getString(R.string.settings_theme_key), getResources().getString(R.string.settings_theme_value_default));
        setTheme(getTheme(appTheme));

    }

    private void resetSeason() {

        //Reset the season
        //Reset all shared preference values

        mResetInProgress = true;
        invalidateOptionsMenu();
        setSeasonInitializedPreference(false);
        mPresenter.setPlayoffsStarted(false);
        setPlayoffsStartedPreference(mPresenter.getPlayoffsStarted());
        SimulatorPresenter.setCurrentWeek(0);
        setCurrentWeekPreference(0);
        setScoreStringPreference("");
        mWeekNumberHeader.setText("");
        mScoresRecyclerViewAdapter.swapCursor(null);
        mStandingsRecyclerViewAdapter.swapCursor(SimulatorActivity.STANDINGS_TYPE_REGULAR_SEASON, null);
        mPresenter.resetSeason();


    }

    private void setSeasonInitializedPreference(boolean seasonInitialized) {
        //Set season initialized boolean preference
        SimulatorPresenter.setSeasonInitialized(seasonInitialized);
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putBoolean(getString(R.string.settings_season_initialized_key), seasonInitialized);
        prefs.commit();
    }

    private void setPlayoffsStartedPreference(boolean playoffsStarted) {
        //Set playoffs started boolean preference
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putBoolean(getString(R.string.settings_playoffs_started_key), playoffsStarted);
        prefs.commit();

    }

    private void setScoreStringPreference(String scoreString) {
        //Set score string preference that holds playoff scores
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putString(getString(R.string.settings_score_string_key), scoreString).apply();
        prefs.commit();
    }

    public void setCurrentWeekPreference(int currentWeek) {
        //Set current week preference when week is updated
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(getString(R.string.settings_simulator_week_num_key), currentWeek).apply();
        prefs.commit();
    }

    @Override
    public void simulateAnotherTestWeek() {

        //If total number of current simulations is less than total number requested, continue running simulations
        //If not, complete the test simulation

        mPresenter.mCurrentTestSimulations++;
        if (mPresenter.mCurrentTestSimulations < mPresenter.mTotalTestSimulations){
            mPresenter.resetSimulatorTeamCurrentSeasonElos();
            mPresenter.resetSimulatorTeamWinsLosses();
            mPresenter.simulateTestSeason();
        } else {
            Log.d("TEST",  "SIMULATION COMPLETE!!!!");
            DecimalFormat df = new DecimalFormat("#.##");
            for (Team team : mModel.getSimulatorTeamArrayList()){
                Log.d("DATA", team.getShortName() + df.format(team.getMadePlayoffs() * 100) + "-" + df.format(team.getWonDivision()*100) +  "-" + df.format(team.getWonConference()*100) + "-" + df.format(team.getWonSuperBowl()*100));
            }
        }




    }

    private String getScoreStringPreference() {
        return mSharedPreferences.getString(getString(R.string.settings_score_string_key), "no data");
    }

    private void setUpSharedPreferences() {

        //Get default shared pref values and set other variables accordingly
        Boolean seasonInitialized = mSharedPreferences.getBoolean(getString(R.string.settings_season_initialized_key), getResources().getBoolean(R.bool.pref_season_initialized_default));
        mPresenter.setPlayoffsStarted(mSharedPreferences.getBoolean(getString(R.string.settings_playoffs_started_key), getResources().getBoolean(R.bool.pref_playoffs_started_default)));
        SimulatorPresenter.setSeasonInitialized(seasonInitialized);
        int currentWeek = mSharedPreferences.getInt(getString(R.string.settings_simulator_week_num_key), 1);
        SimulatorPresenter.setCurrentWeek(currentWeek);
    }

    @Override
    public void onPriorSimulatedDataLoaded() {
        if (regularSeasonIsComplete()) {
            setViewsNotReadyToSimulate();
        } else {
            setViewsReadyToSimulate();
        }
    }

    @Override
    public void onDataDeleted() {
        //After data is deleted from database when season is reset, re-initialize the next season

        setViewsNotReadyToSimulate();
        mPresenter.initializeSeason();
    }


    @Override
    public void onDisplayScores(int weekNumber, Cursor cursor, String scoresWeekNumberHeader, int queriedFrom) {


        //Callback received from presenter to display scores after they are loaded
        //If scores were queried from the simulator activity,  swap in the scores cursor to display scores
        //Set week number header
        //Set up views now that week was simulated

        if (queriedFrom == SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY) {

                mScoresRecyclerViewAdapter.swapCursor(cursor);
                mWeekNumberHeader.setText(scoresWeekNumberHeader);
                setUpViews();




        }

    }

    @Override
    public void onDisplayStandings(int standingsType, Cursor cursor, int queriedFrom) {

        if (queriedFrom == SimulatorModel.QUERY_FROM_SIMULATOR_ACTIVITY) {

            //Callback received from presenter to display standings after they are loaded
            //Swap standings cursor into standings recyclerView to display standings
            mStandingsRecyclerViewAdapter.swapCursor(standingsType, cursor);

            //Otherwise, set up the views now that the week was simulated
                setUpViews();


        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setViewsReadyToSimulate() {

        //App is ready for simulation, set buttons to enabled and stop loading animator

        mSimulateWeek.setVisibility(View.VISIBLE);
        mSimulateSeason.setVisibility(View.VISIBLE);
        mSimulateSeason.setEnabled(true);
        mSimulateWeek.setEnabled(true);
        mResetButton.setVisibility(View.GONE);
        if (mAnimatedFootballAnimatable.isRunning()) {
            mAnimatedFootballAnimatable.stop();
        }
    }

    private void setViewsNotReadyToSimulate() {

        //App is not ready for simulation, set buttons to disabled and start loading animator

        //If the regular season is complete, enable the "Start Playoffs" button

        mSimulateSeason.setEnabled(false);
        mSimulateWeek.setEnabled(false);
        mSimulateSeason.setVisibility(View.INVISIBLE);
        mSimulateWeek.setVisibility(View.INVISIBLE);
        mResetButton.setVisibility(View.GONE);
        if (!regularSeasonIsComplete()) {
            mAnimatedFootballAnimatable.start();
        }
        if (regularSeasonIsComplete() && !mPresenter.getPlayoffsStarted()) {
            mSimulateSeason.setVisibility(View.GONE);
            mStartPlayoffs.setVisibility(View.VISIBLE);
            if (mAnimatedFootballAnimatable.isRunning()) {
                mAnimatedFootballAnimatable.stop();
            }
        } else if (regularSeasonIsComplete() && !playoffsComplete()) {
            mAnimatedFootballAnimatable.start();
        }
    }

    private void setViewsPlayoffs() {

        //Playoffs have begun, set up the views for the playoffs
        //Hide the start playoffs button, and enable the simulate week button
        mStartPlayoffs.setVisibility(View.GONE);
        mSimulateSeason.setVisibility(View.INVISIBLE);
        mResetButton.setVisibility(View.GONE);
        mSimulateWeek.setEnabled(true);
        mSimulateWeek.setVisibility(View.VISIBLE);
        if (mAnimatedFootballAnimatable.isRunning()) {
            mAnimatedFootballAnimatable.stop();
        }
    }

    private void setViewsPlayoffsComplete() {

        //Playoffs are complete, hide all buttons except for the reset button
        mSimulateSeason.setVisibility(View.INVISIBLE);
        mSimulateWeek.setVisibility(View.GONE);
        mStartPlayoffs.setVisibility(View.GONE);
        mResetButton.setVisibility(View.VISIBLE);


    }

    private Boolean regularSeasonIsComplete() {
        if (SimulatorPresenter.getCurrentWeek() > 17) {
            return true;
        } else {
            return false;
        }
    }

    private Boolean playoffsComplete() {
        if (SimulatorPresenter.getCurrentWeek() > MatchEntry.MATCH_WEEK_NBA_FINALS) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.primary_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.primary_settings:
                Intent startEloValuesActivity = new Intent(SimulatorActivity.this, EloValuesActivity.class);
                //Add the theme to the intent so that the eloValues activity has correct theme
                startEloValuesActivity.putExtra("theme", getTheme(mSharedPreferences.getString(getString(R.string.settings_theme_key), getString(R.string.settings_theme_value_default))));
                startActivity(startEloValuesActivity);
                return true;
            case R.id.reset_menu_button:
                resetSeason();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem resetButton = menu.findItem(R.id.reset_menu_button);
        MenuItem eloButton = menu.findItem(R.id.primary_settings);

        //If the app reset is in progress, disable reset button and elo buttons so reset is not interfered with.
        if (mResetInProgress) {
            resetButton.setEnabled(false);
            eloButton.setEnabled(false);
            return true;
        } else {
            resetButton.setEnabled(true);
            eloButton.setEnabled(true);
            return true;
        }
    }

    private int getTheme(String themeValue) {
        //Return the actual theme style that corresponds with the theme sharedPrefs String value
        if (themeValue.equals(getString(R.string.settings_theme_value_default))) {
            return R.style.DarkAppTheme;
        } else if (themeValue.equals(getString(R.string.settings_theme_value_grey))) {
            return R.style.GreyAppTheme;

        } else if (themeValue.equals(getString(R.string.settings_theme_value_purple))) {
            return R.style.PurpleAppTheme;

        } else if (themeValue.equals(getString(R.string.settings_theme_value_blue))) {
            return R.style.AppTheme;
        } else {
            return R.style.DarkAppTheme;
        }
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(resid);
    }

    public static ActivityComponent getActivityComponent() {
        return mActivityComponent;
    }

    @Override
    public void onSeasonInitialized() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                //If season initialized call is received within simulateActivity, then the season
                //has been reset so restart the activity

                mResetInProgress = false;
                if (!mPresenter.mTestSimulation) {
                    SimulatorActivity.this.recreate();
                } else {

                    //METHOD USED WHEN RUNNING TEST SIMULATIONS FOR PLAYOFF PREDICTIONS
                    if (mPresenter.mTestSimulation == true) {
                        mPresenter.mCurrentTestSimulations++;
                        //If the current number of test simulations is equal to the total number, then stop simulation
                        //If not, keep simulating the season
                        if (mPresenter.mCurrentTestSimulations == mPresenter.mTotalTestSimulations) {
                            Log.d("TEST", "TEST SIM COMPLETE!");
                        } else {
                            Log.d("TIMES SIM", "" + mPresenter.mCurrentTestSimulations);
                            mPresenter.simulateSeason();
                        }
                    }
                }


            }

        });


    }

    @Override
    public void onSeasonLoadedFromDb() {
    }

    private void setUpViews() {


        if (regularSeasonIsComplete()) {
            //If the regular season is complete, set up the views for playoffs depending on if playoffs
            //have started or playoffs have completed
            if (!mPresenter.getPlayoffsStarted()) {
                setViewsNotReadyToSimulate();
                Log.d("SETVIEW", "NOTREADYSIM");
            }
            if (mPresenter.getPlayoffsStarted()) {
                if (playoffsComplete()) {
                    if (mAnimatedFootballAnimatable.isRunning()) {
                        mAnimatedFootballAnimatable.stop();
                    }
                    setViewsPlayoffsComplete();
                    Log.d("SETVIEW", "PLAYOFFSCOMPLETE");
                } else {
                    setViewsPlayoffs();
                    Log.d("SETVIEW", "PLAYOFFS");
                }
            }
        } else {
            setViewsReadyToSimulate();
            Log.d("SETVIEW", "READYTOSIM");
        }

    }

    private Boolean getSeasonInitializedPref() {
        //Return the preference value for if the season has been initialized
        return mSharedPreferences.getBoolean(getString(R.string.settings_season_initialized_key), getResources().getBoolean(R.bool.pref_season_initialized_default));
    }

}
