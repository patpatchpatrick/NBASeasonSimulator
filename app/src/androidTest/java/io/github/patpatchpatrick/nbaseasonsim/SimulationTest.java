package io.github.patpatchpatrick.nbaseasonsim;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class SimulationTest {

    @Rule
    public ActivityTestRule<SimulatorActivity> mainActivityActivityTestRule =
            new ActivityTestRule<>(SimulatorActivity.class);

    @Test
    public void clickSimulateButton_SetViewsNotReadySim(){
        onView(withId(R.id.simulate_week_button)).perform(click());

        onView(withId(R.id.simulate_week_button)).check(matches(isDisplayed()));
        onView(withId(R.id.simulate_season_button)).check(matches(isDisplayed()));

    }
}
