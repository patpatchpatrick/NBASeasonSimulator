package io.github.patpatchpatrick.nbaseasonsim;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.github.patpatchpatrick.nbaseasonsim.season_resources.ELORatingSystem;
import io.github.patpatchpatrick.nbaseasonsim.season_resources.Team;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Practice Unit Test with Mockito
 */

@RunWith(MockitoJUnitRunner.class)
public class SimulationUnitTest {

    @Mock
    Team teamOne;

    @Mock
    Team teamTwo;


    @Test
    public void simulation_isCorrect() {

        when(teamOne.getElo())
                .thenReturn((double)1500);
        when(teamTwo.getElo())
                .thenReturn((double)1500);

        double probTeamOneWin = ELORatingSystem.probabilityOfTeamOneWinning(teamOne.getElo(), teamTwo.getElo());
        boolean probProperlyDefined;
        if (probTeamOneWin >= 0 && probTeamOneWin <= 1){
            probProperlyDefined = true;
        } else {
            probProperlyDefined = false;
        }

        assertTrue(probProperlyDefined);
    }
}