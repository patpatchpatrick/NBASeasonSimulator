package io.github.patpatchpatrick.nbaseasonsim;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.presenter.SimulatorPresenter;

public class EloValuesActivity extends AppCompatActivity {
    //Activity to edit team ELO values

    @Inject
    SharedPreferences mSharedPreferences;

    @Inject
    SimulatorPresenter mSimulatorPresenter;

    private RecyclerView mRecyclerView;
    private Spinner mEloTypeSpinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Set up theme before creating activity
        initializeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elo_values);
        getWindow().setBackgroundDrawable(null);
        getSupportActionBar().hide();

        //Inject with Dagger Activity Component to get access to presenter
        HomeScreen.getActivityComponent().inject(this);

        // Find recyclerView for list of team names and elos and set linearLayoutManager and recyclerAdapter on recyclerView
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view_elo);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(EloValuesActivity.this));
        final EloRecyclerViewAdapter eloRecyclerAdapter = new EloRecyclerViewAdapter();
        mRecyclerView.setAdapter(eloRecyclerAdapter);

        //Set up elo type selection spinner
        final ArrayList<String> eloTypeArrayList = new ArrayList<>();
        eloTypeArrayList.add("Current Season Elo Values");
        eloTypeArrayList.add("Last Season Elo Values");
        eloTypeArrayList.add("Manually Set Elo Values");

        mEloTypeSpinner = (Spinner) findViewById(R.id.elo_type_spinner);
        ArrayAdapter<String> eloTypeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, eloTypeArrayList);
        mEloTypeSpinner.setAdapter(eloTypeAdapter);

        //Set spinner position based on user elo preference
        int currentEloPref = getEloPreference();
        if (currentEloPref == getResources().getInteger(R.integer.settings_elo_type_current_season)){
            mEloTypeSpinner.setSelection(0);
        } else if (currentEloPref == getResources().getInteger(R.integer.settings_elo_type_last_season)){
            mEloTypeSpinner.setSelection(1);
        } else if (currentEloPref == getResources().getInteger(R.integer.settings_elo_type_user)){
            mEloTypeSpinner.setSelection(2);
        }

        //Set on item selected listener for elo type spinner
        //Depending on which elo type is selected, set team elo values accordingly
        mEloTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String eloTypeString = eloTypeArrayList.get(i);
                if (eloTypeString == "Current Season Elo Values"){
                    mSimulatorPresenter.resetSimulatorTeamCurrentSeasonElos();
                    eloRecyclerAdapter.notifyDataSetChanged();
                    setUseFutureElosPref();
                } else if (eloTypeString == "Last Season Elo Values"){
                    mSimulatorPresenter.resetSimulatorTeamLastSeasonElos();
                    eloRecyclerAdapter.notifyDataSetChanged();
                    setUseLastSeasonElosPref();
                } else if (eloTypeString == "Manually Set Elo Values") {
                    mSimulatorPresenter.setTeamUserElos();
                    setUseUserElosPref();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });



    }

    private void initializeTheme() {
        //Set the initial theme of the app based on shared prefs theme
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String appTheme = sharedPreferences.getString(getString(R.string.settings_theme_key), getResources().getString(R.string.settings_theme_value_default));
        setTheme(getTheme(appTheme));
    }

    private int getEloPreference(){
        return mSharedPreferences.getInt(getString(R.string.settings_elo_type_key), getResources().getInteger(R.integer.settings_elo_type_current_season));
    }

    private void setUseFutureElosPref(){
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(getString(R.string.settings_elo_type_key), getResources().getInteger(R.integer.settings_elo_type_current_season));
        prefs.commit();
    }

    private void setUseLastSeasonElosPref(){
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(getString(R.string.settings_elo_type_key), getResources().getInteger(R.integer.settings_elo_type_last_season));
        prefs.commit();
    }

    private void setUseUserElosPref(){
        SharedPreferences.Editor prefs = mSharedPreferences.edit();
        prefs.putInt(getString(R.string.settings_elo_type_key), getResources().getInteger(R.integer.settings_elo_type_user));
        prefs.commit();
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
