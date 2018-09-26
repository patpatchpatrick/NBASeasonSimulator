package io.github.patpatchpatrick.nbaseasonsim;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.BaseView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.ScoreView;
import io.github.patpatchpatrick.nbaseasonsim.presenter.SimulatorPresenter;

public class NextWeekMatchesActivity extends AppCompatActivity implements BaseView, ScoreView {

    @Inject
    SimulatorPresenter mPresenter;

    private AdView mAdView;
    RecyclerView mNextWeekMatchesRecyclerView;
    WeeklyMatchesRecyclerViewAdapter mNextWeekMatchesRecyclerViewAdapter;
    int weekNumber;
    ImageView mNextWeekButton;
    ImageView mPreviousWeekButton;
    TextView mWeekNumberHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_next_week_matches);
        getWindow().setBackgroundDrawable(null);
        getSupportActionBar().hide();

        weekNumber = 1;

        //Inject with dagger
        HomeScreen.getActivityComponent().inject(this);

        //Load the AdView to display banner advertisement
        AdRequest adRequest= new AdRequest.Builder().build();
        mAdView = (AdView) this.findViewById(R.id.nextWeekMatchesActivityAdView);
        mAdView.loadAd(adRequest);

        //Add this activity as a baseview for the presenter to notify
        mPresenter.addBaseView(this);
        mPresenter.addScoreView(this);

        // Set up the weekly matches recyclerview
        mNextWeekMatchesRecyclerView = (RecyclerView) findViewById(R.id.next_week_matches_recyclerview);
        mNextWeekMatchesRecyclerView.setHasFixedSize(true);
        mNextWeekMatchesRecyclerView.setLayoutManager(new LinearLayoutManager(NextWeekMatchesActivity.this));
        mNextWeekMatchesRecyclerViewAdapter = new WeeklyMatchesRecyclerViewAdapter();
        mNextWeekMatchesRecyclerView.setAdapter(mNextWeekMatchesRecyclerViewAdapter);

        // Set up week change buttons
        mNextWeekButton = (ImageView) findViewById(R.id.weekly_matches_arrow_right);
        mPreviousWeekButton = (ImageView) findViewById(R.id.weekly_matches_arrow_left);

        // Set up the week number header
        mWeekNumberHeader = (TextView) findViewById(R.id.weekly_matches_week_number_header);
        mWeekNumberHeader.setText("Week " + weekNumber);

        //Load current season data
        mPresenter.loadCurrentSeasonMatches();
        //Query the matches to be displayed
        mPresenter.queryCurrentSeasonMatches(weekNumber, true, SimulatorModel.QUERY_FROM_NEXT_WEEK_MATCHES_ACTIVITY);

        mNextWeekButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //If the week is less than 17, increase week number and query the next week's matches
                if (weekNumber < 24) {
                    weekNumber++;
                    mPresenter.queryCurrentSeasonMatches(weekNumber, true, SimulatorModel.QUERY_FROM_NEXT_WEEK_MATCHES_ACTIVITY);
                    mWeekNumberHeader.setText("Week " + weekNumber);
                }
            }
        });

        mPreviousWeekButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //If the week is greater than 1, decrease week number and query the prior week's matches
                if (weekNumber > 1) {
                    weekNumber--;
                    mPresenter.queryCurrentSeasonMatches(weekNumber, true, SimulatorModel.QUERY_FROM_NEXT_WEEK_MATCHES_ACTIVITY);
                    mWeekNumberHeader.setText("Week " + weekNumber);
                }
            }
        });

    }


    @Override
    public void onSeasonInitialized() {

    }

    @Override
    public void onSeasonLoadedFromDb() {

    }

    @Override
    public void onDisplayScores(int weekNumber, Cursor cursor, String scoresWeekNumberHeader, int queriedFrom) {
        //If scores/matches were queried from this activity, swap in the cursor into the recyclerview adapter to display them
        if (queriedFrom == SimulatorModel.QUERY_FROM_NEXT_WEEK_MATCHES_ACTIVITY) {
            mNextWeekMatchesRecyclerViewAdapter.swapCursor(cursor);
        }
    }

    @Override
    public void onDisplayStandings(int standingsType, Cursor cursor, int queriedFrom) {

    }

    private void initializeTheme() {
        //Set the initial theme of the app based on shared prefs theme
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String appTheme = sharedPrefs.getString(getString(R.string.settings_theme_key), getString(R.string.settings_theme_value_default));
        setTheme(getTheme(appTheme));

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
}
