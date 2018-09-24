package io.github.patpatchpatrick.nbaseasonsim;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.inject.Inject;

import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;

public class EloRecyclerViewAdapter extends RecyclerView.Adapter<EloRecyclerViewAdapter.ViewHolder> {

    private ArrayList<Team> mTeamArrayList;

    @Inject
    SimulatorModel mModel;


    @NonNull
    @Override
    public EloRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View eloView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_elo_item, parent, false);
        return new EloRecyclerViewAdapter.ViewHolder(eloView, new EloEditTextListener());
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        //Get the current team based on the recyclerview position
        //Set the team name and elo in the holder textview and edittext
        //Pass the currentTeam to the holder editTextListener so that the team ELO can be updated

        Team currentTeam = mTeamArrayList.get(position);

        String teamName = currentTeam.getName();
        double teamElo = currentTeam.getElo();

        DecimalFormat df = new DecimalFormat("#0.00");
        String teamEloString = df.format(teamElo);

        holder.eloEditTextListener.updateTeam(currentTeam);
        holder.teamNameTextView.setText(teamName);
        holder.teamEloEditText.setText(teamEloString);
        holder.teamLogo.setImageResource(mModel.getLogo(teamName));




    }

    public EloRecyclerViewAdapter() {

        //Inject with Dagger Activity Component to get access to model data
        HomeScreen.getActivityComponent().inject(this);

        mTeamArrayList = mModel.getSimulatorTeamArrayList();

    }


    @Override
    public int getItemCount() {
        return (mTeamArrayList == null) ? 0 : mTeamArrayList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        public TextView teamNameTextView;
        public EditText teamEloEditText;
        public ImageView teamLogo;
        public EloEditTextListener eloEditTextListener;

        public ViewHolder(View view, EloEditTextListener eloEditTextListener) {
            super(view);

            teamNameTextView = (TextView) view.findViewById(R.id.team_name_textview);
            teamEloEditText = (EditText) view.findViewById(R.id.team_elo_edittext);
            teamLogo = (ImageView) view.findViewById(R.id.elo_list_item_team_logo);
            this.eloEditTextListener = eloEditTextListener;
            teamEloEditText.addTextChangedListener(this.eloEditTextListener);


        }

    }

    private class EloEditTextListener implements TextWatcher {
        //Custom EloEditTextListener used to update team's elo values if the recyclerview team
        //elo edittext string is changed
        private Team mTeam;

        public void updateTeam(Team team){
            mTeam = team;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        }

        @Override
        public void onTextChanged(CharSequence newEloChar, int i, int i2, int i3) {
            //Change the team ELO value if text is changed
            //If there is a double parsing error, catch the error
            if (newEloChar.length() > 0) {
                try {
                    Double newElo = Double.parseDouble(newEloChar.toString().trim());
                    mTeam.setElo(newElo);
                } catch (NumberFormatException e) {
                    Log.d("Double Parse Err: ", "" + e);
                }
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    }
}

