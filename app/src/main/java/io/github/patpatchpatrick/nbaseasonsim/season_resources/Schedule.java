package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import java.util.ArrayList;

public class Schedule {

    private ArrayList<Week> mWeeks;

    public Schedule(){
        mWeeks = new ArrayList<Week>();
    }

    public void addWeek(Week week){
        mWeeks.add(week);
    }

    public Week getWeek(int weekNumber) {

        //Subtract 1 from requested week to match ArrayList order
        //Return requested week
        weekNumber--;
        return mWeeks.get(weekNumber);

    }
}
