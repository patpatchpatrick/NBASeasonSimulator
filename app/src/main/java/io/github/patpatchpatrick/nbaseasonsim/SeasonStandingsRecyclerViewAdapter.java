package io.github.patpatchpatrick.nbaseasonsim;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;

public class SeasonStandingsRecyclerViewAdapter extends RecyclerView.Adapter<SeasonStandingsRecyclerViewAdapter.ViewHolder> {

    @Inject
    SimulatorModel mModel;

    @Inject
    Context mContext;


    Cursor dataCursor;
    int mStandingsType;

    public SeasonStandingsRecyclerViewAdapter() {

        //Inject with Dagger Activity Component to get access to model data
        HomeScreen.getActivityComponent().inject(this);

    }

    @NonNull
    @Override
    public SeasonStandingsRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View standingsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_season_standings_recyclerview_item, parent, false);
        return new SeasonStandingsRecyclerViewAdapter.ViewHolder(standingsView);
    }

    @Override
    public void onBindViewHolder(@NonNull SeasonStandingsRecyclerViewAdapter.ViewHolder holder, int position) {

        dataCursor.moveToPosition(position);

        if (mStandingsType == SimulatorActivity.STANDINGS_TYPE_REGULAR_SEASON) {
            //Display regular season standings

            //For every 4th team, the header should show the division
            if (position % 15 == 0) {
                int teamDivision = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_DIVISION));
                holder.standingsHeader.setVisibility(View.VISIBLE);
                holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
                if (position == 0) {
                    //For the first division, don't include a line break in header
                    holder.standingsHeader.setText("" + TeamEntry.getDivisionString(teamDivision));
                } else {
                    holder.standingsHeader.setText("\n" + TeamEntry.getDivisionString(teamDivision));
                }
            } else {
                holder.standingsHeader.setVisibility(View.GONE);
                holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
            }

            //Show the team details (record and playoff seed)
            String standingsDetails = "";
            String teamName = dataCursor.getString(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            String teamShortName = dataCursor.getString(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_SHORT_NAME));
            Team currentTeam = mModel.getCurrentSeasonTeam(teamName);
            Integer teamWinsInt = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_WINS));
            Integer teamLossesInt = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES));
            Integer teamDrawsInt = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS));
            String teamWins = Integer.toString(teamWinsInt);
            String teamLosses = Integer.toString(teamLossesInt);
            String teamDraws = Integer.toString(teamDrawsInt);
            Integer teamElo = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_ELO));
            int playoffSeed = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE));
            String playoffSeedString = Integer.toString(playoffSeed);

            standingsDetails += teamShortName + "  " + teamWins + " - " + teamLosses + " - " + teamDraws;

            //Check if it is the first week of the season by determining if a team has losses or wins
            Boolean firstWeekOfSeason = true;
            if (teamWinsInt != 0 || teamLossesInt != 0) {
                firstWeekOfSeason = false;
            }

            if (!firstWeekOfSeason) {
                //Bold the strings of playoff teams and add their playoff seed to standings
                if (playoffSeed > 0) {
                    standingsDetails += " (" + playoffSeedString + ")";
                    holder.standingsDetails.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    holder.standingsDetails.setTypeface(Typeface.DEFAULT);
                }
            }

            standingsDetails += "\nELO Rating: " + teamElo;

            String playoffOddsString = currentTeam.getPlayoffOddsString();
            String[] playoffOddsParts=  playoffOddsString.split("-");
            String playoffOdds = playoffOddsParts[0];
            Double playoffOddsDouble = Double.parseDouble(playoffOdds);
            String divisionOdds = playoffOddsParts[1];
            Double divisionOddsDouble = Double.parseDouble(divisionOdds);
            String conferenceOdds = playoffOddsParts[2];
            Double conferenceOddsDouble = Double.parseDouble(conferenceOdds);
            String superBowlOdds = playoffOddsParts[3];
            Double superBowlOddsDouble = Double.parseDouble(superBowlOdds);

            holder.playoffOddsTextView.setText(playoffOdds + "%");
            holder.divisionOddsTextView.setText(divisionOdds + "%");
            holder.conferenceOddsTextView.setText(conferenceOdds + "%");
            holder.superbowlOddsTextView.setText(superBowlOdds + "%");

            if (playoffOddsDouble > 90){
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen90));
            } else if (playoffOddsDouble > 80) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen80));
            } else if (playoffOddsDouble > 70) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen70));
            } else if (playoffOddsDouble > 60) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen60));
            } else if (playoffOddsDouble > 50) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen50));
            } else if (playoffOddsDouble > 40) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen40));
            } else if (playoffOddsDouble > 30) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen30));
            } else if (playoffOddsDouble > 20) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen20));
            } else if (playoffOddsDouble > 10) {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen10));
            } else {
                holder.playoffOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen00));
            }

            if (divisionOddsDouble > 90){
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen90));
            } else if (divisionOddsDouble > 80) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen80));
            } else if (divisionOddsDouble > 70) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen70));
            } else if (divisionOddsDouble > 60) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen60));
            } else if (divisionOddsDouble > 50) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen50));
            } else if (divisionOddsDouble > 40) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen40));
            } else if (divisionOddsDouble > 30) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen30));
            } else if (divisionOddsDouble > 20) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen20));
            } else if (divisionOddsDouble > 10) {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen10));
            } else {
                holder.divisionOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen00));
            }

            if (conferenceOddsDouble > 90){
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen90));
            } else if (conferenceOddsDouble > 80) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen80));
            } else if (conferenceOddsDouble > 70) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen70));
            } else if (conferenceOddsDouble > 60) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen60));
            } else if (conferenceOddsDouble > 50) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen50));
            } else if (conferenceOddsDouble > 40) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen40));
            } else if (conferenceOddsDouble > 30) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen30));
            } else if (conferenceOddsDouble > 20) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen20));
            } else if (conferenceOddsDouble > 10) {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen10));
            } else {
                holder.conferenceOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen00));
            }

            if (superBowlOddsDouble > 90){
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen90));
            } else if (superBowlOddsDouble > 80) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen80));
            } else if (superBowlOddsDouble > 70) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen70));
            } else if (superBowlOddsDouble > 60) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen60));
            } else if (superBowlOddsDouble > 50) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen50));
            } else if (superBowlOddsDouble > 40) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen40));
            } else if (superBowlOddsDouble > 30) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen30));
            } else if (superBowlOddsDouble > 20) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen20));
            } else if (superBowlOddsDouble > 10) {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen10));
            } else {
                holder.superbowlOddsTextView.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen00));
            }


            holder.standingsDetails.setText(standingsDetails);
            //Set font by default (currently font won't auto-set in layout so need to programmatically set it)
            holder.standingsDetails.setTypeface(ResourcesCompat.getFont(mContext, R.font.montserrat));

            holder.standingsTeamLogo.setImageResource(mModel.getLogo(teamName));

        }

        if (mStandingsType == SimulatorActivity.STANDINGS_TYPE_PLAYOFFS) {

            //Display playoff standings

            int remainingPlayoffTeams = dataCursor.getCount();

            //Display the correct standings headers depending on remaining playoff teams
            if (remainingPlayoffTeams == 12) {
                if (position == 0) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("AFC Playoff Standings");
                } else if (position == 6) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("NFC Playoff Standings");
                } else {
                    holder.standingsHeader.setVisibility(View.GONE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
                }
            } else if (remainingPlayoffTeams == 8) {
                if (position == 0) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("AFC Playoff Standings");
                } else if (position == 4) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("NFC Playoff Standings");
                } else {
                    holder.standingsHeader.setVisibility(View.GONE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
                }
            } else if (remainingPlayoffTeams == 4) {
                if (position == 0) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("AFC Playoff Standings");
                } else if (position == 2) {
                    holder.standingsHeader.setVisibility(View.VISIBLE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.VISIBLE);
                    holder.standingsHeader.setText("NFC Playoff Standingsr");
                } else {
                    holder.standingsHeader.setVisibility(View.GONE);
                    holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                    holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                    holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                    holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
                }
            } else {
                holder.standingsHeader.setVisibility(View.GONE);
                holder.playoffOddsHeaderTextView.setVisibility(View.GONE);
                holder.divisionOddsHeaderTextView.setVisibility(View.GONE);
                holder.conferenceOddsHeaderTextView.setVisibility(View.GONE);
                holder.superBowlOddsHeaderTextView.setVisibility(View.GONE);
            }

            //Show the team details (name and playoff seed)
            String standingsDetails = "";
            String teamName = dataCursor.getString(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            String teamShortName = dataCursor.getString(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_SHORT_NAME));
            int playoffSeed = dataCursor.getInt(dataCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE));
            String playoffSeedString = Integer.toString(playoffSeed);
            standingsDetails += teamShortName;
            standingsDetails += " (" + playoffSeedString + ")";

            holder.standingsDetails.setTypeface(Typeface.DEFAULT);
            holder.standingsDetails.setText(standingsDetails);

            holder.standingsTeamLogo.setImageResource(mModel.getLogo(teamName));


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

    public void swapCursor(int standingsType, Cursor cursor) {

        mStandingsType = standingsType;

        if (dataCursor == cursor) {
            return;
        }

        Cursor oldCursor = this.dataCursor;
        this.dataCursor = cursor;
        if (oldCursor != null) {
            oldCursor.close();
        }

        this.notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        public TextView standingsHeader;
        public TextView standingsDetails;
        public TextView playoffOddsTextView;
        public TextView divisionOddsTextView;
        public TextView conferenceOddsTextView;
        public TextView superbowlOddsTextView;
        public TextView playoffOddsHeaderTextView;
        public TextView divisionOddsHeaderTextView;
        public TextView conferenceOddsHeaderTextView;
        public TextView superBowlOddsHeaderTextView;
        public ImageView standingsTeamLogo;


        public ViewHolder(View view) {
            super(view);

            standingsHeader = view.findViewById(R.id.season_standings_recycler_header);
            standingsDetails = view.findViewById(R.id.season_standings_details_text_view);
            standingsTeamLogo = view.findViewById(R.id.season_standings_recycler_team_logo);
            playoffOddsTextView = view.findViewById(R.id.season_playoff_odds_text_view);
            divisionOddsTextView = view.findViewById(R.id.season_division_odds_text_view);
            conferenceOddsTextView = view.findViewById(R.id.season_conference_odds_text_view);
            superbowlOddsTextView = view.findViewById(R.id.season_superbowl_odds_text_view);
            playoffOddsHeaderTextView = view.findViewById(R.id.season_playoff_odds_header_text_view);
            divisionOddsHeaderTextView =  view.findViewById(R.id.season_division_odds_header_text_view);
            conferenceOddsHeaderTextView = view.findViewById(R.id.season_conference_odds_header_text_view);
            superBowlOddsHeaderTextView =  view.findViewById(R.id.season_superbowl_odds_header_text_view);

        }
    }

}
