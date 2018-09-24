package io.github.patpatchpatrick.nbaseasonsim.dagger;

import javax.inject.Singleton;

import dagger.Component;
import io.github.patpatchpatrick.nbaseasonsim.EloRecyclerViewAdapter;
import io.github.patpatchpatrick.nbaseasonsim.EloValuesActivity;
import io.github.patpatchpatrick.nbaseasonsim.HomeScreen;
import io.github.patpatchpatrick.nbaseasonsim.SimulatorActivity;
import io.github.patpatchpatrick.nbaseasonsim.MatchPredictorActivity;
import io.github.patpatchpatrick.nbaseasonsim.NextWeekMatchesActivity;
import io.github.patpatchpatrick.nbaseasonsim.ScoresRecyclerViewAdapter;
import io.github.patpatchpatrick.nbaseasonsim.SeasonStandingsRecyclerViewAdapter;
import io.github.patpatchpatrick.nbaseasonsim.SettingsFragment;
import io.github.patpatchpatrick.nbaseasonsim.StandingsActivity;
import io.github.patpatchpatrick.nbaseasonsim.StandingsRecyclerViewAdapter;
import io.github.patpatchpatrick.nbaseasonsim.WeeklyMatchesRecyclerViewAdapter;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimDbHelper;
import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.presenter.SimulatorPresenter;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Match;

@Singleton @Component(modules = { ActivityModule.class})
public interface ActivityComponent {

    //Dagger ActivityComponent (used for SimulatorActivity objects)
    //The inject methods below indicate which objects we will inject the module data into

    void inject(HomeScreen homeScreen);
    void inject(EloValuesActivity eloValuesActivity);
    void inject(SimulatorActivity simulatorActivity);
    void inject(MatchPredictorActivity matchPredictorActivity);
    void inject(NextWeekMatchesActivity nextWeekMatchesActivity);
    void inject(StandingsActivity standingsActivity);
    void inject(SimulatorPresenter simulatorPresenter);
    void inject(SimulatorModel simulatorModel);
    void inject(Match match);
    void inject(EloRecyclerViewAdapter eloRecyclerViewAdapter);
    void inject(ScoresRecyclerViewAdapter scoresRecyclerViewAdapter);
    void inject(StandingsRecyclerViewAdapter standingsRecyclerViewAdapter);
    void inject(SeasonStandingsRecyclerViewAdapter seasonStandingsRecyclerViewAdapter);
    void inject(WeeklyMatchesRecyclerViewAdapter weeklyMatchesRecyclerViewAdapter);
    void inject(SettingsFragment settingsFragment);
    void inject(SeasonSimDbHelper seasonSimDbHelper);

}
