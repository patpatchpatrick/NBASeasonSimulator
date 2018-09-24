package io.github.patpatchpatrick.nbaseasonsim.mvp_utils;

public interface BaseView {

    //Base view interface that is implemented by all views

    void onSeasonInitialized();
    void onSeasonLoadedFromDb();
}
