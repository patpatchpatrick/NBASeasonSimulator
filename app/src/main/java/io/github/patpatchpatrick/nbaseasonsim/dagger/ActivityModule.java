package io.github.patpatchpatrick.nbaseasonsim.dagger;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.github.patpatchpatrick.nbaseasonsim.HomeScreen;
import io.github.patpatchpatrick.nbaseasonsim.data.SimulatorModel;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.BaseView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.ScoreView;
import io.github.patpatchpatrick.nbaseasonsim.mvp_utils.SimulatorMvpContract;
import io.github.patpatchpatrick.nbaseasonsim.presenter.SimulatorPresenter;

@Module
public class ActivityModule {

    //ActivityModule used for Dagger2
    //Provide other classes/objects with access to certain Activity Components listed within the module

    private final SimulatorPresenter presenter;
    private final SimulatorModel model;
    private final ContentResolver contentResolver;
    private final SharedPreferences sharedPreferences;
    private final Context context;
    private final BaseView baseView;
    private final ArrayList<BaseView> baseViews;
    private final ArrayList<ScoreView> scoreViews;

    public ActivityModule(HomeScreen homeScreen) {
        this.presenter = new SimulatorPresenter();
        this.model = new SimulatorModel();
        this.contentResolver = homeScreen.getContentResolver();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(homeScreen);
        this.context = homeScreen;
        this.baseView =  homeScreen;
        this.baseViews = new ArrayList<>();
        this.baseViews.add(baseView);
        this.scoreViews = new ArrayList<>();
    }

    @Provides
    @Singleton
    Context providesContext() {
        return context;
    }

    @Provides
    @Singleton
    SimulatorPresenter providesSimulatorPresenter() {
        return presenter;
    }

    @Provides
    @Singleton
    SimulatorMvpContract.SimulatorPresenter providesSimulatorMvpPresenter() {
        return presenter;
    }

    @Provides
    @Singleton
    SimulatorModel providesSimulatorModel() {
        return model;
    }

    @Provides
    @Singleton
    ContentResolver providesContentResolver() {
        return contentResolver;
    }

    @Provides
    @Singleton
    SharedPreferences providesSharedPreferences() {
        return sharedPreferences;
    }

    @Provides
    @Singleton
    BaseView providesBaseView() {
        return baseView;
    }

    @Provides
    @Singleton
    ArrayList<BaseView> providesBaseViews() {
        return baseViews;
    }

    @Provides
    @Singleton
    ArrayList<ScoreView> providesScoreViews() {
        return scoreViews;
    }







}
