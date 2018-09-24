package io.github.patpatchpatrick.nbaseasonsim;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.MatchEntry;
import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.ELORatingSystem;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;

public class WeeklyMatchesRecyclerViewAdapter extends RecyclerView.Adapter<WeeklyMatchesRecyclerViewAdapter.ViewHolder> {

    @Inject
    SimulatorModel mModel;

    @Inject
    Context mContext;

    @Inject
    SharedPreferences mSharedPrefs;

    Cursor dataCursor;
    Integer eloType;

    public WeeklyMatchesRecyclerViewAdapter() {

        //Inject with Dagger Activity Component to get access to model data
        HomeScreen.getActivityComponent().inject(this);

        eloType = mSharedPrefs.getInt(mContext.getString(R.string.settings_elo_type_key), mContext.getResources().getInteger(R.integer.settings_elo_type_current_season));

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View weeklyMatchesView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_weekly_matches_recyclerview_item, parent, false);
        return new WeeklyMatchesRecyclerViewAdapter.ViewHolder(weeklyMatchesView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        String teamOneString;
        String teamTwoString;

        dataCursor.moveToPosition(position);

        //Set team  one and two name and logo
        Typeface tf = ResourcesCompat.getFont(mContext, R.font.montserrat);
        Typeface tfBold = ResourcesCompat.getFont(mContext, R.font.montserrat_bold);
        teamOneString = dataCursor.getString(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE));
        teamTwoString = dataCursor.getString(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO));
        int scoreOne = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE));
        int scoreTwo = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE));
        int teamOneWonInt = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_ONE_WON));
        int matchComplete = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_COMPLETE));
        Double teamTwoVegasOdds = dataCursor.getDouble(dataCursor.getColumnIndexOrThrow(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS));


        //If the match is complete, set the text appropriately
        if (matchComplete == MatchEntry.MATCH_COMPLETE_YES) {
            holder.teamOneName.setText(teamOneString + " " + scoreOne);
            holder.teamTwoName.setText(teamTwoString + " " + scoreTwo);
            //Bold the winning team
            if (teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_YES) {
                holder.teamOneName.setTypeface(tfBold);
                holder.teamTwoName.setTypeface(tf);
            } else if (teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_NO) {
                holder.teamOneName.setTypeface(tf);
                holder.teamTwoName.setTypeface(tfBold);
            } else {
                holder.teamOneName.setTypeface(tf);
                holder.teamTwoName.setTypeface(tf);
            }
        } else {
            holder.teamOneName.setText(teamOneString);
            holder.teamTwoName.setText(teamTwoString);
            holder.teamOneName.setTypeface(tf);
            holder.teamTwoName.setTypeface(tf);
        }


        holder.teamOneLogo.setImageResource(mModel.getLogo(teamOneString));
        holder.teamTwoLogo.setImageResource(mModel.getLogo(teamTwoString));

        //Set string for ELO odds to win. This string is the short name of the home team (team two)
        //combined with the ELO odds of team two winning
        Team teamOne = mModel.getSimulatorTeam(teamOneString);
        Team teamTwo = mModel.getSimulatorTeam(teamTwoString);
        Double teamOneElo;
        Double teamTwoElo;
        //Set elo value based on user selected elo preference
        if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_last_season)) {
            teamOneElo = teamOne.getDefaultElo();
            teamTwoElo = teamTwo.getDefaultElo();
        } else if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_current_season)) {
            teamOneElo = teamOne.getFutureElo();
            teamTwoElo = teamTwo.getFutureElo();
        } else if (eloType == mContext.getResources().getInteger(R.integer.settings_elo_type_user)) {
            teamOneElo = teamOne.getUserElo();
            teamTwoElo = teamTwo.getUserElo();
        } else {
            teamOneElo = teamOne.getDefaultElo();
            teamTwoElo = teamTwo.getDefaultElo();
        }
        teamOneElo = teamOne.getElo();
        teamTwoElo = teamTwo.getElo();
        Double teamOneOddsToWin = ELORatingSystem.probabilityOfTeamOneWinning(teamOneElo, teamTwoElo, true);
        Double teamTwoOddsToWin = 1 - teamOneOddsToWin;
        Double teamOneOddsToWinPercent = teamOneOddsToWin * 100;
        Double teamTwoOddsToWinPercent = teamTwoOddsToWin * 100;

        String teamTwoShortName = teamTwo.getShortName();
        String teamOneShortName = teamOne.getShortName();
        DecimalFormat df = new DecimalFormat("#.##");
        if (teamTwoOddsToWinPercent >= 50) {
            holder.teamTwoEloOdds.setText(teamTwoShortName + " " + df.format(teamTwoOddsToWinPercent) + "%");
        } else {
            holder.teamTwoEloOdds.setText(teamOneShortName + " " + df.format(teamOneOddsToWinPercent) + "%");
        }
        holder.teamTwoEloOdds.setTypeface(tf);

        //Set the vegas line for the match
        //If team two is favored, the odds will be greater than 0 so show the absolute value of the odds with a - sign
        //If team one is favored, the odds will be less than  0 so show the absolute value of the odds with a + sign
        //If there are no odds, clear the textview
        if (teamTwoVegasOdds != MatchEntry.MATCH_NO_ODDS_SET) {
            if (teamTwoVegasOdds >= 0) {
                holder.teamTwoVegasOdds.setText(teamTwoShortName + " -" + Math.abs(teamTwoVegasOdds));
            } else {
                holder.teamTwoVegasOdds.setText(teamOneShortName + " -" + Math.abs(teamTwoVegasOdds));
            }
        } else {
            holder.teamTwoVegasOdds.setText("");
        }


        //Set colors of vegas odds line depending on whether or not the team beat the vegas odds
        Boolean favoredTeamBeatVegasOdds;
        if (matchComplete == MatchEntry.MATCH_COMPLETE_YES) {
            Double scoreDifferential;
            if (teamTwoVegasOdds >= 0) {
                scoreDifferential = (double) scoreTwo - (double) scoreOne;
                if (scoreDifferential >= teamTwoVegasOdds) {
                    favoredTeamBeatVegasOdds = true;
                } else {
                    favoredTeamBeatVegasOdds = false;
                }
            } else {
                scoreDifferential = (double) scoreOne - (double) scoreTwo;
                if (scoreDifferential >= Math.abs(teamTwoVegasOdds)) {
                    favoredTeamBeatVegasOdds = true;
                } else {
                    favoredTeamBeatVegasOdds = false;
                }
            }
            if (favoredTeamBeatVegasOdds) {
                holder.teamTwoVegasOdds.setTextColor(mContext.getResources().getColor(R.color.colorGreen));
            } else {
                holder.teamTwoVegasOdds.setTextColor(mContext.getResources().getColor(R.color.colorRed));
            }
        } else {
            holder.teamTwoVegasOdds.setTextColor(holder.teamOneName.getCurrentTextColor());
        }

        //Set colors of elo odds depending on whether or not the team beat the elo odds
        Boolean favoredTeamBeatEloPercent;
        Boolean teamDraw;
        if (matchComplete == MatchEntry.MATCH_COMPLETE_YES) {
            if (teamTwoOddsToWinPercent > 50 && teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_NO) {
                favoredTeamBeatEloPercent = true;
                teamDraw = false;
            } else if (teamTwoOddsToWinPercent > 50 && teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_YES) {
                favoredTeamBeatEloPercent = false;
                teamDraw = false;
            } else if (teamTwoOddsToWinPercent <= 50 && teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_NO) {
                favoredTeamBeatEloPercent = false;
                teamDraw = false;
            } else if (teamTwoOddsToWinPercent <= 50 && teamOneWonInt == MatchEntry.MATCH_TEAM_ONE_WON_YES) {
                favoredTeamBeatEloPercent = true;
                teamDraw = false;
            } else {
                favoredTeamBeatEloPercent = false;
                teamDraw = true;
            }
            if (favoredTeamBeatEloPercent && !teamDraw) {
                holder.teamTwoEloOdds.setTextColor(mContext.getResources().getColor(R.color.colorGreen));
            } else if (!teamDraw) {
                holder.teamTwoEloOdds.setTextColor(mContext.getResources().getColor(R.color.colorRed));
            } else {
                holder.teamTwoEloOdds.setTextColor(holder.teamOneName.getCurrentTextColor());
            }
        } else {
            holder.teamTwoEloOdds.setTextColor(holder.teamOneName.getCurrentTextColor());
        }


    }

    @Override
    public int getItemCount() {
        if (dataCursor == null) {
            return 0;
        } else {
            return dataCursor.getCount();
        }
    }

    public void swapCursor(Cursor cursor) {

        if (dataCursor == cursor) {
            return;
        }

        Cursor oldCursor = this.dataCursor;
        this.dataCursor = cursor;
        if (oldCursor != null) {
            oldCursor.close();
        }

        //When new data is swapped in, reload elo type preference
        eloType = mSharedPrefs.getInt(mContext.getString(R.string.settings_elo_type_key), mContext.getResources().getInteger(R.integer.settings_elo_type_current_season));
        this.notifyDataSetChanged();


    }

    class ViewHolder extends RecyclerView.ViewHolder {

        public TextView teamOneName;
        public TextView teamTwoName;
        public TextView teamTwoEloOdds;
        public TextView teamTwoVegasOdds;
        public ImageView teamOneLogo;
        public ImageView teamTwoLogo;


        public ViewHolder(View view) {
            super(view);

            teamOneName = (TextView) view.findViewById(R.id.weekly_matches_recycler_team_one_name);
            teamOneLogo = (ImageView) view.findViewById(R.id.weekly_matches_recycler_team_one_logo);
            teamTwoName = (TextView) view.findViewById(R.id.weekly_matches_recycler_team_two_name);
            teamTwoLogo = (ImageView) view.findViewById(R.id.weekly_matches_recycler_team_two_logo);
            teamTwoEloOdds = (TextView) view.findViewById(R.id.weekly_matches_elo_odds_value);
            teamTwoVegasOdds = (TextView) view.findViewById(R.id.weekly_matches_vegas_line_value);


        }
    }

}
