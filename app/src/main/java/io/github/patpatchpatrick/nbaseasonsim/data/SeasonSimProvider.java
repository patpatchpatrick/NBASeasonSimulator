package io.github.patpatchpatrick.nbaseasonsim.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.TeamEntry;
import io.github.patpatchpatrick.nbaseasonsim.data.SeasonSimContract.MatchEntry;

public class SeasonSimProvider extends ContentProvider{

    private static final int TEAM = 100;
    private static final int TEAM_ID = 101;
    private static final int MATCH = 200;
    private static final int MATCH_ID = 201;


    //URI matcher to handle different URIs input into provider
    public static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(SeasonSimContract.CONTENT_AUTHORITY, SeasonSimContract.PATH_TEAM, TEAM);
        sUriMatcher.addURI(SeasonSimContract.CONTENT_AUTHORITY, SeasonSimContract.PATH_TEAM + "/#", TEAM_ID);
        sUriMatcher.addURI(SeasonSimContract.CONTENT_AUTHORITY, SeasonSimContract.PATH_MATCH, MATCH);
        sUriMatcher.addURI(SeasonSimContract.CONTENT_AUTHORITY, SeasonSimContract.PATH_MATCH + "/#", MATCH_ID);
    }

    //Tag for log messages
    public static final String LOG_TAG = SeasonSimProvider.class.getSimpleName();
    private SeasonSimDbHelper mDbHelper;
    
    @Override
    public boolean onCreate() {
        //Create new instance of SeasonSimDbHelper to access database.
        mDbHelper = new SeasonSimDbHelper((getContext()));
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        //Get readable database
        SQLiteDatabase database = mDbHelper.getReadableDatabase();

        //Cursor to hold results of query
        Cursor cursor;

        //Match the URI
        int match = sUriMatcher.match(uri);
        switch (match) {
            case TEAM:
                //Query the table directly with the given inputs
                cursor = database.query(TeamEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case TEAM_ID:
                //Query the table for a specific team ID
                selection = TeamEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                cursor = database.query(TeamEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case MATCH:
                //Query the table directly with the given inputs
                cursor = database.query(MatchEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case MATCH_ID:
                //Query the table for a specific match ID
                selection = MatchEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                cursor = database.query(MatchEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
        }

        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);

        switch (match) {
            case TEAM:
                return TeamEntry.CONTENT_LIST_TYPE;
            case TEAM_ID:
                return TeamEntry.CONTENT_ITEM_TYPE;
            case MATCH:
                return MatchEntry.CONTENT_LIST_TYPE;
            case MATCH_ID:
                return MatchEntry.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case TEAM:
                return insertTeam(uri, contentValues);
            case MATCH:
                return insertMatch(uri, contentValues);
            default:
                //Insert is not supported for a specific TEAM ID, will hit default exception
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    private Uri insertTeam(Uri uri, ContentValues values) {

        // Checks to determine values are ok before inserting into database
        // Check to ensure name is not null
        String teamName = values.getAsString(TeamEntry.COLUMN_TEAM_NAME);
        if (teamName == null) {
            throw new IllegalArgumentException("Team requires a name");
        }

        // Check to ensure short name is not null
        String teamShortName = values.getAsString(TeamEntry.COLUMN_TEAM_SHORT_NAME);
        if (teamShortName == null) {
            throw new IllegalArgumentException("Team requires a short name");
        }

        // Check that the elo is valid
        Double elo = values.getAsDouble(TeamEntry.COLUMN_TEAM_ELO);
        if (elo == null) {
            throw new IllegalArgumentException("Team requires valid elo");
        }

        // Check that the default elo is valid
        Double defaultElo = values.getAsDouble(TeamEntry.COLUMN_TEAM_DEFAULT_ELO);
        if (defaultElo == null) {
            throw new IllegalArgumentException("Team requires valid elo");
        }

        // Check that the user elo is valid
        Double userElo = values.getAsDouble(TeamEntry.COLUMN_TEAM_USER_ELO);
        if (userElo == null) {
            throw new IllegalArgumentException("Team requires valid elo");
        }

        // Check that the team ranking is valid
        Double teamRanking = values.getAsDouble(TeamEntry.COLUMN_TEAM_RANKING);
        if (teamRanking == null) {
            throw new IllegalArgumentException("Team requires valid ranking");
        }

        // Check that the offensive rating is valid
        Double offRating = values.getAsDouble(TeamEntry.COLUMN_TEAM_OFF_RATING);
        if (offRating == null) {
            throw new IllegalArgumentException("Team requires valid offensive rating");
        }

        // Check that the defensive rating is valid
        Double defRating = values.getAsDouble(TeamEntry.COLUMN_TEAM_DEF_RATING);
        if (defRating == null) {
            throw new IllegalArgumentException("Team requires valid defensive rating");
        }

        // Check that the team wins int is valid
        Integer teamWins = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_WINS);
        if (teamWins == null) {
            throw new IllegalArgumentException("Team requires valid team wins variable");
        }

        // Check that the team losses int is valid
        Integer teamLosses = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES);
        if (teamLosses == null) {
            throw new IllegalArgumentException("Team requires valid team losses variable");
        }

        // Check that the team draws int is valid
        Integer teamDraws = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS);
        if (teamDraws == null) {
            throw new IllegalArgumentException("Team requires valid team draws variable");
        }

        // Check that the team division int is valid
        Integer teamDivision = values.getAsInteger(TeamEntry.COLUMN_TEAM_DIVISION);
        if (teamDivision == null) {
            throw new IllegalArgumentException("Team requires valid team division variable");
        }

        //If data is valid, insert data into SQL database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insert(TeamEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        //Notify any listeners that the data has changed for the URI
        getContext().getContentResolver().notifyChange(uri, null);

        return ContentUris.withAppendedId(uri, id);
    }

    private Uri insertMatch(Uri uri, ContentValues values) {

        // Checks to determine values are ok before inserting into database
        // Check to ensure name is not null
        String teamOne = values.getAsString(MatchEntry.COLUMN_MATCH_TEAM_ONE);
        if (teamOne == null) {
            throw new IllegalArgumentException("Team requires a name");
        }

        // Checks to determine values are ok before inserting into database
        // Check to ensure name is not null
        String teamTwo = values.getAsString(MatchEntry.COLUMN_MATCH_TEAM_TWO);
        if (teamTwo == null) {
            throw new IllegalArgumentException("Team requires a name");
        }

        // Checks to determine values are ok before inserting into database
        // Check to ensure week is not null
        Integer week = values.getAsInteger(MatchEntry.COLUMN_MATCH_WEEK);
        if (week == null) {
            throw new IllegalArgumentException("Week is null");
        }

        //If data is valid, insert data into SQL database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insert(MatchEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        //Notify any listeners that the data has changed for the URI
        getContext().getContentResolver().notifyChange(uri, null);

        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {

        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        final int match = sUriMatcher.match(uri);

        int rowsDeleted;

        switch (match) {
            case TEAM:
                rowsDeleted = database.delete(TeamEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case TEAM_ID:
                selection = TeamEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                rowsDeleted = database.delete(TeamEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case MATCH:
                rowsDeleted = database.delete(MatchEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case MATCH_ID:
                selection = MatchEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                rowsDeleted = database.delete(MatchEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }

        //If 1 or more rows were deleted, notify all listeners that data at the given URI has changed
        if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsDeleted;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String selection, @Nullable String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case TEAM:
                return updateTeam(uri, contentValues, selection, selectionArgs);
            case TEAM_ID:
                // For the TEAM_ID code, extract out the ID from the URI,
                // so we know which row to update. Selection will be "_id=?" and selection
                // arguments will be a String array containing the actual ID.
                selection = TeamEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateTeam(uri, contentValues, selection, selectionArgs);
            case MATCH:
                return updateMatch(uri, contentValues, selection,  selectionArgs);
            case MATCH_ID:
                // For the MATCH_ID code, extract out the ID from the URI,
                // so we know which row to update. Selection will be "_id=?" and selection
                // arguments will be a String array containing the actual ID.
                selection = MatchEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateMatch(uri, contentValues, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }

    private int updateTeam(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        // If the TeamEntry.Name key is present,
        // check that the name value is not null.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_NAME)) {
            String teamName = values.getAsString(TeamEntry.COLUMN_TEAM_NAME);
            if (teamName == null) {
                throw new IllegalArgumentException("Team requires a name");
            }
        }

        // If the TeamEntry.ELO key is present,
        // check that the elo value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_ELO)) {
            Double elo = values.getAsDouble(TeamEntry.COLUMN_TEAM_ELO);
            if (elo == null) {
                throw new IllegalArgumentException("Team requires valid elo double");
            }
        }

        // If the TeamEntry.User ELO key is present,
        // check that the elo value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_USER_ELO)) {
            Double userElo = values.getAsDouble(TeamEntry.COLUMN_TEAM_USER_ELO);
            if (userElo == null) {
                throw new IllegalArgumentException("Team requires valid elo double");
            }
        }

        // If the TeamEntry.Offensive Rating key is present,
        // check that the off rating value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_OFF_RATING)) {
            Double offRating = values.getAsDouble(TeamEntry.COLUMN_TEAM_OFF_RATING);
            if (offRating == null) {
                throw new IllegalArgumentException("Team requires valid off rating int");
            }
        }

        // If the TeamEntry.Defensive Rating key is present,
        // check that the def rating value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_DEF_RATING)) {
            Double defRating = values.getAsDouble(TeamEntry.COLUMN_TEAM_DEF_RATING);
            if (defRating == null) {
                throw new IllegalArgumentException("Team requires valid def rating int");
            }
        }

        // If the TeamEntry.Current Wins key is present,
        // check that the current wins value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_CURRENT_WINS)) {
            Integer currentWins = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_WINS);
            if (currentWins == null) {
                throw new IllegalArgumentException("Team requires valid current wins rating int");
            }
        }

        // If the TeamEntry.Current Losses key is present,
        // check that the current losses value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES)) {
            Integer currentLosses = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_LOSSES);
            if (currentLosses == null) {
                throw new IllegalArgumentException("Team requires valid current losses rating int");
            }
        }

        // If the TeamEntry.Current Draws key is present,
        // check that the current draws value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS)) {
            Integer currentDraws = values.getAsInteger(TeamEntry.COLUMN_TEAM_CURRENT_DRAWS);
            if (currentDraws == null) {
                throw new IllegalArgumentException("Team requires valid current draws rating int");
            }
        }

        // If the TeamEntry.Win Loss Percent key is present,
        // check that the winLossPct value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT)) {
            Double winLossPct = values.getAsDouble(TeamEntry.COLUMN_TEAM_WIN_LOSS_PCT);
            if (winLossPct == null) {
                throw new IllegalArgumentException("Team requires valid win loss pct");
            }
        }

        // If the TeamEntry.Division Wins key is present,
        // check that the wins value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_DIV_WINS)) {
            Integer divWins = values.getAsInteger(TeamEntry.COLUMN_TEAM_DIV_WINS);
            if (divWins == null) {
                throw new IllegalArgumentException("Team requires valid div wins value");
            }
        }

        // If the TeamEntry.Division Losses key is present,
        // check that the losses value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_DIV_LOSSES)) {
            Integer divLosses = values.getAsInteger(TeamEntry.COLUMN_TEAM_DIV_LOSSES);
            if (divLosses == null) {
                throw new IllegalArgumentException("Team requires valid div losses value");
            }
        }

        // If the TeamEntry.Division Win Loss Percent key is present,
        // check that the winLossPct value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT)) {
            Double divWinLossPct = values.getAsDouble(TeamEntry.COLUMN_TEAM_DIV_WIN_LOSS_PCT);
            if (divWinLossPct == null) {
                throw new IllegalArgumentException("Team requires valid div win loss pct");
            }
        }

        // If the TeamEntry.Team Division key is present,
        // check that the current division value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_DIVISION)) {
            Integer teamDivision = values.getAsInteger(TeamEntry.COLUMN_TEAM_DIVISION);
            if (teamDivision == null) {
                throw new IllegalArgumentException("Team requires valid division int");
            }
        }

        // If the TeamEntry.Team Conference key is present,
        // check that the current conference value is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_CONFERENCE)) {
            Integer teamConference = values.getAsInteger(TeamEntry.COLUMN_TEAM_CONFERENCE);
            if (teamConference == null) {
                throw new IllegalArgumentException("Team requires valid conference int");
            }
        }

        // If the TeamEntry.Playoff Eligible key is present,
        // check that the current playoff eligibility int is valid.
        if (values.containsKey(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE)) {
            Integer playoffEligible = values.getAsInteger(TeamEntry.COLUMN_TEAM_PLAYOFF_ELIGIBILE);
            if (playoffEligible == null) {
                throw new IllegalArgumentException("Team requires valid playoff eligibility int");
            }
        }

        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Otherwise, get writeable database to update the data
        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(TeamEntry.TABLE_NAME, values, selection, selectionArgs);

        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        // Return the number of rows updated
        return rowsUpdated;
    }

    private int updateMatch(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        // If the MatchEntry TEAM ONE key is present,
        // check that the name value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_TEAM_ONE)) {
            String teamOne = values.getAsString(MatchEntry.COLUMN_MATCH_TEAM_ONE);
            if (teamOne == null) {
                throw new IllegalArgumentException("Team requires a name");
            }
        }

        // If the MatchEntry TEAM TWO key is present,
        // check that the name value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_TEAM_TWO)) {
            String teamTwo = values.getAsString(MatchEntry.COLUMN_MATCH_TEAM_TWO);
            if (teamTwo == null) {
                throw new IllegalArgumentException("Team requires a name");
            }
        }

        // If the MatchEntry TEAM ONE SCORE key is present,
        // check that the score value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE)) {
            Integer teamOneScore = values.getAsInteger(MatchEntry.COLUMN_MATCH_TEAM_ONE_SCORE);
            if (teamOneScore == null) {
                throw new IllegalArgumentException("Team one score is null");
            }
        }

        // If the MatchEntry TEAM TWO SCORE key is present,
        // check that the score value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE)) {
            Integer teamTwoScore = values.getAsInteger(MatchEntry.COLUMN_MATCH_TEAM_TWO_SCORE);
            if (teamTwoScore == null) {
                throw new IllegalArgumentException("Team two score is null");
            }
        }

        // If the MatchEntry TEAM TWO ODDS key is present,
        // check that the odds value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS)) {
            Double teamTwoOdds = values.getAsDouble(MatchEntry.COLUMN_MATCH_TEAM_TWO_ODDS);
            if (teamTwoOdds == null) {
                throw new IllegalArgumentException("Team two odds is null");
            }
        }

        // If the MatchEntry WEEK key is present,
        // check that the name value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_WEEK)) {
            Integer week = values.getAsInteger(MatchEntry.COLUMN_MATCH_WEEK);
            if (week == null) {
                throw new IllegalArgumentException("Week is null");
            }
        }

        // If the MatchEntry COMPLETE key is present,
        // check that the name value is not null.
        if (values.containsKey(MatchEntry.COLUMN_MATCH_COMPLETE)) {
            Integer matchComplete = values.getAsInteger(MatchEntry.COLUMN_MATCH_COMPLETE);
            if (matchComplete == null) {
                throw new IllegalArgumentException("Match complete value is null");
            }
        }

        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Otherwise, get writeable database to update the data
        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(MatchEntry.TABLE_NAME, values, selection, selectionArgs);

        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        // Return the number of rows updated
        return rowsUpdated;
    }

}
