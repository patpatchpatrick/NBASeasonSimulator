package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.net.Uri;

public interface Data {

    //Interface for callbacks necessary to update database
    //This interface is used by the presenter
    //Objects created by the presenter will use this interface to provide callbacks to the presenter
    //when the presenter needs to update the database
    //The presenter will then let the model know to update the database

    void updateMatchCallback(Match match, Uri uri);
    void updateMatchOddsCallback(Match match, Uri uri);
    void updateTeamCallback(Team team, Uri uri);


}
