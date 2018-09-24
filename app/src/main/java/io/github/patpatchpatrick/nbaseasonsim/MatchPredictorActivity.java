package io.github.patpatchpatrick.nbaseasonsim;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.ELORatingSystem;

public class MatchPredictorActivity extends AppCompatActivity {

    @Inject
    SimulatorModel mModel;

    @Inject
    SharedPreferences mSharedPrefs;

    private AdView mAdView;
    private Button mSimulateMatchButton;
    private ImageView mSwapTeamsButton;
    private Spinner mTeamOneSpinner;
    private Spinner mTeamTwoSpinner;
    private ImageView mTeamOneLogo;
    private ImageView mTeamTwoLogo;
    private TextView mTeamOneEloValue;
    private TextView mTeamTwoEloValue;
    private TextView mTeamOneOddsValue;
    private TextView mTeamTwoOddsValue;
    private TextView mTeamOneScoreValue;
    private TextView mTeamTwoScoreValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_predictor);
        getWindow().setBackgroundDrawable(null);
        getSupportActionBar().hide();

        //Inject with dagger
        HomeScreen.getActivityComponent().inject(this);

        //Load the AdView to display banner advertisement
        AdRequest adRequest= new AdRequest.Builder().build();
        mAdView = (AdView) this.findViewById(R.id.matchPredictorActivityAdView);
        mAdView.loadAd(adRequest);


        mSimulateMatchButton = (Button) findViewById(R.id.match_predict_simulate_button);
        mSwapTeamsButton = (ImageView) findViewById(R.id.match_predict_swap_icon);

        mTeamOneLogo = (ImageView) findViewById(R.id.match_predict_team_one_image);
        mTeamTwoLogo = (ImageView) findViewById(R.id.match_predict_team_two_image);

        mTeamOneEloValue = (TextView) findViewById(R.id.match_predict_team_one_elo_value);
        mTeamTwoEloValue = (TextView) findViewById(R.id.match_predict_team_two_elo_value);

        mTeamOneOddsValue = (TextView) findViewById(R.id.match_predict_team_one_odds_value);
        mTeamTwoOddsValue = (TextView) findViewById(R.id.match_predict_team_two_odds_value);

        mTeamOneScoreValue = (TextView) findViewById(R.id.match_predict_team_one_score_value);
        mTeamTwoScoreValue = (TextView) findViewById(R.id.match_predict_team_two_score_value);


        //Get resources for spinners and values
        final ArrayList<String> teamNameArrayList = mModel.getTeamNameArrayList();
        final Integer eloType = mSharedPrefs.getInt(getString(R.string.settings_elo_type_key), getResources().getInteger(R.integer.settings_elo_type_current_season));

        //Set up team one spinner with arraylist of team names from model
        mTeamOneSpinner = findViewById(R.id.match_predict_team_one_spinner);
        ArrayAdapter<String> teamOneNameAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, teamNameArrayList);
        mTeamOneSpinner.setAdapter(teamOneNameAdapter);

        mTeamOneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String teamName = teamNameArrayList.get(i);
                mTeamOneLogo.setImageResource(mModel.getLogo(teamName));

                //Set elo value based on selected team
                DecimalFormat df = new DecimalFormat("#.##");
                if (eloType == getResources().getInteger(R.integer.settings_elo_type_last_season)) {
                    mTeamOneEloValue.setText("" + mModel.getCurrentSeasonTeam(teamName).getDefaultElo());
                } else if (eloType == getResources().getInteger(R.integer.settings_elo_type_current_season)) {
                    mTeamOneEloValue.setText("" + df.format(mModel.getCurrentSeasonTeam(teamName).getElo()));
                } else if (eloType == getResources().getInteger(R.integer.settings_elo_type_user)) {
                    mTeamOneEloValue.setText("" + mModel.getCurrentSeasonTeam(teamName).getUserElo());
                }

                //Reset team odds textviews when elos are changed
                setTeamOdds();

                mTeamOneScoreValue.setText("0");
                mTeamTwoScoreValue.setText("0");

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Set up team two spinner with arraylist of team names from model
        mTeamTwoSpinner = findViewById(R.id.match_predict_team_two_spinner);
        ArrayAdapter<String> teamTwoNameAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, teamNameArrayList);
        mTeamTwoSpinner.setAdapter(teamTwoNameAdapter);

        mTeamTwoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String teamName = teamNameArrayList.get(i);
                mTeamTwoLogo.setImageResource(mModel.getLogo(teamName));

                //Set elo value based on selected team
                DecimalFormat df = new DecimalFormat("#.##");
                if (eloType == getResources().getInteger(R.integer.settings_elo_type_last_season)) {
                    mTeamTwoEloValue.setText("" + mModel.getCurrentSeasonTeam(teamName).getDefaultElo());
                } else if (eloType == getResources().getInteger(R.integer.settings_elo_type_current_season)) {
                    mTeamTwoEloValue.setText("" + df.format(mModel.getCurrentSeasonTeam(teamName).getElo()));
                } else if (eloType == getResources().getInteger(R.integer.settings_elo_type_user)) {
                    mTeamTwoEloValue.setText("" + mModel.getCurrentSeasonTeam(teamName).getUserElo());
                }

                //Reset team odds textviews when elos are changed
                setTeamOdds();

                mTeamOneScoreValue.setText("0");
                mTeamTwoScoreValue.setText("0");
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mSwapTeamsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int spinnerOnePosition = mTeamOneSpinner.getSelectedItemPosition();
                int spinnerTwoPosition = mTeamTwoSpinner.getSelectedItemPosition();
                mTeamOneSpinner.setSelection(spinnerTwoPosition);
                mTeamTwoSpinner.setSelection(spinnerOnePosition);

            }
        });

        mSimulateMatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simulateMatch();
            }
        });

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

    private void setTeamOdds(){
        String teamOneEloValue =  mTeamOneEloValue.getText().toString().trim();
        String teamTwoEloValue =  mTeamTwoEloValue.getText().toString().trim();
        if (!teamOneEloValue.isEmpty() && !teamTwoEloValue.isEmpty() && teamOneEloValue != null && teamTwoEloValue != null){
            Double teamOneElo = Double.valueOf(mTeamOneEloValue.getText().toString().trim());
            Double teamTwoElo = Double.valueOf(mTeamTwoEloValue.getText().toString().trim());
            Double teamOneOddsToWin = ELORatingSystem.probabilityOfTeamOneWinning(teamOneElo, teamTwoElo, true);
            Double teamTwoOddsToWin = 1 - teamOneOddsToWin;
            Double teamOneOddsPercent = teamOneOddsToWin * 100;
            Double teamTwoOddsPercent = teamTwoOddsToWin * 100;

            DecimalFormat df = new DecimalFormat("#.##");

            mTeamOneOddsValue.setText("" + df.format(teamOneOddsPercent) + "%");
            mTeamTwoOddsValue.setText("" + df.format(teamTwoOddsPercent) + "%");
        }

    }

    private void simulateMatch(){
        String teamOneEloValue =  mTeamOneEloValue.getText().toString().trim();
        String teamTwoEloValue =  mTeamTwoEloValue.getText().toString().trim();
        Double teamOneElo = Double.valueOf(mTeamOneEloValue.getText().toString().trim());
        Double teamTwoElo = Double.valueOf(mTeamTwoEloValue.getText().toString().trim());
        //Simulate matches and get arraylist of integers in return.  The first value is the teamOneScore, second value is TeamTwoScore
        ArrayList<Integer> simulatedMatchScores = ELORatingSystem.simulateMatchNoDbUpdates(teamOneElo, teamTwoElo);
        mTeamOneScoreValue.setText("" + simulatedMatchScores.get(0));
        mTeamTwoScoreValue.setText("" + simulatedMatchScores.get(1));


    }
}
