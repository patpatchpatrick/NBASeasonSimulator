package io.github.patpatchpatrick.nbaseasonsim.season_resources;

import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;

public class Standings {

    public static void generateStandings() {
    }

    public static ArrayList<ArrayList<Team>> generateTestPlayoffTeams(ArrayList<Team> divisionWinnersAFC, ArrayList<Team> divisionWinnersNFC, ArrayList<Team> potentialAFCWildCardTeams, ArrayList<Team> potentialNFCWildCardTeams){

        ArrayList<Team> afcPlayoffDivisionWinners = generateDivisionWinnerSeedsArrayList(divisionWinnersAFC);
        ArrayList<Team> nfcPlayoffDivisionWinners  = generateDivisionWinnerSeedsArrayList(divisionWinnersNFC);

        //Generate two wildcard teams for each conference
        ArrayList<Team> afcPlayoffTeams = generateWildCardTeamsArrayList(potentialAFCWildCardTeams, afcPlayoffDivisionWinners);
        ArrayList<Team> nfcPlayoffTeams = generateWildCardTeamsArrayList(potentialNFCWildCardTeams, nfcPlayoffDivisionWinners);

        ArrayList<ArrayList<Team>> allPlayoffTeams = new ArrayList<>();

        allPlayoffTeams.add(afcPlayoffTeams);
        allPlayoffTeams.add(nfcPlayoffTeams);

        return allPlayoffTeams;
    }

    private static ArrayList<Team> generateWildCardTeamsArrayList(ArrayList<Team> potentialWildCardTeams, ArrayList<Team> divisionWinners) {

        //Sort the wildCardTeams by win loss percentage in descending order
        Collections.sort(potentialWildCardTeams, new Comparator<Team>() {
            @Override
            public int compare(Team teamOne, Team teamTwo) {
                return teamOne.getWinLossPct() > teamTwo.getWinLossPct() ? -1 : (teamOne.getWinLossPct() < teamTwo.getWinLossPct()) ? 1 : 0;
            }
        });

        //If the top team has a better win loss percentage than the second team, set it as a wildcard team
        //If the second team has a better win loss percentage than the third team, set it as a wildcard team as well
        //Otherwise, run a random draw to randomly select teams that have the same win loss percentage to determine the wildcards

        if (potentialWildCardTeams.get(0).getWinLossPct() > potentialWildCardTeams.get(1).getWinLossPct()) {
            potentialWildCardTeams.get(0).setPlayoffEligible(5);
            divisionWinners.add(potentialWildCardTeams.get(0));
            if (potentialWildCardTeams.get(1).getWinLossPct() > potentialWildCardTeams.get(2).getWinLossPct()) {
                potentialWildCardTeams.get(1).setPlayoffEligible(6);
                divisionWinners.add(potentialWildCardTeams.get(1));
            } else {
                divisionWinners = randomWildCardDrawArrayList(potentialWildCardTeams,  1,  divisionWinners);
            }
        } else {
            divisionWinners = randomWildCardDrawArrayList(potentialWildCardTeams, 2, divisionWinners);
        }

        return divisionWinners;

    }

    private static ArrayList<Team> randomWildCardDrawArrayList(ArrayList<Team> drawTeams, int numberOfTeamsToDraw, ArrayList<Team> playoffTeams) {

        //Run a random wildcard draw.  Select the "numberOfTeamsToDraw" from the drawTeams.

        int teamNumber;
        if (numberOfTeamsToDraw <= 1){
            teamNumber = 1;
        } else {
            teamNumber = 0;
        }

        //Add all of teams that have the same win loss percentage to the wildCardDrawTeams ArrayList
        //Then, randomly select 1 or 2 teams from this ArrayList as wildcard teams (depending on whether
        //the numberOfTeamsToDraw variable is set to 1 or 2.

        ArrayList<Team> wildCardDrawTeams = new ArrayList<>();
        wildCardDrawTeams.add(drawTeams.get(teamNumber));
        while (true) {
            if (teamNumber + 1 >= drawTeams.size()){
                Log.d("Max ", "Size reached");
                break;}
            if (drawTeams.get(teamNumber + 1).getWinLossPct() == drawTeams.get(teamNumber).getWinLossPct()) {
                wildCardDrawTeams.add(drawTeams.get(teamNumber + 1));
            } else {
                break;
            }
            teamNumber++;
        }
        int wildCardDrawSize = wildCardDrawTeams.size();
        int randomWildCardTeam = (int) (Math.random() * wildCardDrawSize);
        Team wildCardTeamSix = wildCardDrawTeams.get(randomWildCardTeam);
        wildCardTeamSix.setPlayoffEligible(6);

        if (numberOfTeamsToDraw > 1) {
            wildCardDrawTeams.remove(randomWildCardTeam);
            wildCardDrawSize = wildCardDrawTeams.size();
            randomWildCardTeam = (int) (Math.random() * wildCardDrawSize);
            Team wildCardTeamFive = wildCardDrawTeams.get(randomWildCardTeam);
            wildCardTeamFive.setPlayoffEligible(5);
            playoffTeams.add(wildCardTeamFive);
        }

        playoffTeams.add(wildCardTeamSix);

        return playoffTeams;
    }

    private static ArrayList<Team> generateDivisionWinnerSeedsArrayList(ArrayList<Team> divisionWinners) {

        ArrayList<Team> playoffSeeds = new ArrayList<>();

        //Shuffle the arraylist to randomize it, then sort by division win percentage to determine playoff order

        Collections.shuffle(divisionWinners);

        //Sort the division winners by win loss percentage in descending order
        Collections.sort(divisionWinners, new Comparator<Team>() {
            @Override
            public int compare(Team teamOne, Team teamTwo) {
                return teamOne.getWinLossPct() > teamTwo.getWinLossPct() ? -1 : (teamOne.getWinLossPct() < teamTwo.getWinLossPct()) ? 1 : 0;
            }
        });


        //Set playoff order on all 4 division winners
        for (int i = 0; i <= 3; i++){
            divisionWinners.get(i).setPlayoffEligible(i + 1);
            playoffSeeds.add(divisionWinners.get(i));
        }

        return playoffSeeds;


    }

    public static void generatePlayoffTeams(Cursor standingsCursor, HashMap<String, Team> teams) {

        ArrayList<Team> potentialAFCWildCardTeams = new ArrayList<>();
        ArrayList<Team> potentialNFCWildCardTeams = new ArrayList<>();
        ArrayList<Team> divisionWinnersAFC = new ArrayList<>();
        ArrayList<Team> divisionWinnersNFC = new ArrayList<>();

        standingsCursor.moveToPosition(-1);
        int i = 4;
        while (standingsCursor.moveToNext()) {

            String teamName = standingsCursor.getString(standingsCursor.getColumnIndexOrThrow(TeamEntry.COLUMN_TEAM_NAME));
            Team team = teams.get(teamName);


            //For every 4 teams, the first team is the division winner and is playoff eligible
            //Mark the team as playoff eligible division winner
            int teamConference = team.getConference();

            if (i % 4 == 0) {

                if (teamConference == TeamEntry.CONFERENCE_WESTERN){
                    divisionWinnersAFC.add(team);
                } else {
                    divisionWinnersNFC.add(team);
                }

                //If team is not the division winner, update them as not playoff eligible and
                // add them to the correct conference ArrayList of potential wildcard teams
            } else if (teamConference == TeamEntry.CONFERENCE_WESTERN) {
                team.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
                potentialAFCWildCardTeams.add(team);
            } else {
                team.setPlayoffEligible(TeamEntry.PLAYOFF_NOT_ELIGIBLE);
                potentialNFCWildCardTeams.add(team);
            }
            i++;
        }

        generateDivisionWinnerSeeds(divisionWinnersAFC);
        generateDivisionWinnerSeeds(divisionWinnersNFC);

        //Generate two wildcard teams for each conference
        generateWildCardTeams(potentialAFCWildCardTeams);
        generateWildCardTeams(potentialNFCWildCardTeams);

        standingsCursor.close();

    }

    private static void generateWildCardTeams(ArrayList<Team> potentialWildCardTeams) {

        //Sort the wildCardTeams by win loss percentage in descending order
        Collections.sort(potentialWildCardTeams, new Comparator<Team>() {
            @Override
            public int compare(Team teamOne, Team teamTwo) {
                return teamOne.getWinLossPct() > teamTwo.getWinLossPct() ? -1 : (teamOne.getWinLossPct() < teamTwo.getWinLossPct()) ? 1 : 0;
            }
        });

        //If the top team has a better win loss percentage than the second team, set it as a wildcard team
        //If the second team has a better win loss percentage than the third team, set it as a wildcard team as well
        //Otherwise, run a random draw to randomly select teams that have the same win loss percentage to determine the wildcards

        if (potentialWildCardTeams.get(0).getWinLossPct() > potentialWildCardTeams.get(1).getWinLossPct()) {
            potentialWildCardTeams.get(0).setPlayoffEligible(5);
            if (potentialWildCardTeams.get(1).getWinLossPct() > potentialWildCardTeams.get(2).getWinLossPct()) {
                potentialWildCardTeams.get(1).setPlayoffEligible(6);
            } else {
                randomWildCardDraw(potentialWildCardTeams,  1);
            }
        } else {
           randomWildCardDraw(potentialWildCardTeams, 2);
        }
        
    }
    
    private static void randomWildCardDraw(ArrayList<Team> drawTeams, int numberOfTeamsToDraw) {

        //Run a random wildcard draw.  Select the "numberOfTeamsToDraw" from the drawTeams.

        int teamNumber;
        if (numberOfTeamsToDraw <= 1){
            teamNumber = 1;
        } else {
            teamNumber = 0;
        }

        //Add all of teams that have the same win loss percentage to the wildCardDrawTeams ArrayList
        //Then, randomly select 1 or 2 teams from this ArrayList as wildcard teams (depending on whether
        //the numberOfTeamsToDraw variable is set to 1 or 2.

        ArrayList<Team> wildCardDrawTeams = new ArrayList<>();
        wildCardDrawTeams.add(drawTeams.get(teamNumber));
        while (true) {
            if (teamNumber + 1 >= drawTeams.size()){
                Log.d("Max ", "Size reached");
                break;}
            if (drawTeams.get(teamNumber + 1).getWinLossPct() == drawTeams.get(teamNumber).getWinLossPct()) {
                wildCardDrawTeams.add(drawTeams.get(teamNumber + 1));
            } else {
                break;
            }
            teamNumber++;
        }
        int wildCardDrawSize = wildCardDrawTeams.size();
        int randomWildCardTeam = (int) (Math.random() * wildCardDrawSize);
        wildCardDrawTeams.get(randomWildCardTeam).setPlayoffEligible(6);

        if (numberOfTeamsToDraw > 1) {
            wildCardDrawTeams.remove(randomWildCardTeam);
            wildCardDrawSize = wildCardDrawTeams.size();
            randomWildCardTeam = (int) (Math.random() * wildCardDrawSize);
            wildCardDrawTeams.get(randomWildCardTeam).setPlayoffEligible(5);
        }
        
    }

    private static void generateDivisionWinnerSeeds(ArrayList<Team> divisionWinners){

        //Shuffle the arraylist to randomize it, then sort by division win percentage to determine playoff order

        Collections.shuffle(divisionWinners);

        //Sort the division winners by win loss percentage in descending order
        Collections.sort(divisionWinners, new Comparator<Team>() {
            @Override
            public int compare(Team teamOne, Team teamTwo) {
                return teamOne.getWinLossPct() > teamTwo.getWinLossPct() ? -1 : (teamOne.getWinLossPct() < teamTwo.getWinLossPct()) ? 1 : 0;
            }
        });


        //Set playoff order on all 4 division winners
        for (int i = 0; i <= 3; i++){
            divisionWinners.get(i).setPlayoffEligible(i + 1);
        }



    }

}
