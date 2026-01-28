package dev.wander.android.opentagviewer.db.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import dev.wander.android.opentagviewer.db.room.dao.BeaconNamingRecordDao;
import dev.wander.android.opentagviewer.db.room.dao.DailyHistoryFetchRecordDao;
import dev.wander.android.opentagviewer.db.room.dao.GoogleDeviceDao;
import dev.wander.android.opentagviewer.db.room.dao.ImportDao;
import dev.wander.android.opentagviewer.db.room.dao.LocationReportDao;
import dev.wander.android.opentagviewer.db.room.dao.OwnedBeaconDao;
import dev.wander.android.opentagviewer.db.room.dao.UserBeaconOptionsDao;
import dev.wander.android.opentagviewer.db.room.entity.BeaconNamingRecord;
import dev.wander.android.opentagviewer.db.room.entity.DailyHistoryFetchRecord;
import dev.wander.android.opentagviewer.db.room.entity.GoogleDevice;
import dev.wander.android.opentagviewer.db.room.entity.Import;
import dev.wander.android.opentagviewer.db.room.entity.LocationReport;
import dev.wander.android.opentagviewer.db.room.entity.OwnedBeacon;
import dev.wander.android.opentagviewer.db.room.entity.UserBeaconOptions;

@Database(
        entities = {
                Import.class,
                BeaconNamingRecord.class,
                OwnedBeacon.class,
                LocationReport.class,
                DailyHistoryFetchRecord.class,
                UserBeaconOptions.class,
                GoogleDevice.class
        },
        version = 2
)
public abstract class OpenTagViewerDatabase extends RoomDatabase {
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create a new table for Google FMD devices (separate from Apple beacons/plists).
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `GoogleDevices` (" +
                            "`canonic_id` TEXT NOT NULL, " +
                            "`name` TEXT, " +
                            "`emoji` TEXT, " +
                            "`added_at` INTEGER NOT NULL, " +
                            "`last_update` INTEGER NOT NULL, " +
                            "`is_removed` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`canonic_id`)" +
                            ")"
            );
        }
    };
    private static OpenTagViewerDatabase INSTANCE = null;

    public static OpenTagViewerDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                            context,
                            OpenTagViewerDatabase.class,
                            "opentagviewer-db")
                    .addMigrations(MIGRATION_1_2)
                    .build();
        }

        return INSTANCE;
    }

    public abstract ImportDao importDao();
    public abstract BeaconNamingRecordDao beaconNamingRecordDao();
    public abstract OwnedBeaconDao ownedBeaconDao();
    public abstract LocationReportDao locationReportDao();
    public abstract DailyHistoryFetchRecordDao dailyHistoryFetchRecordDao();
    public abstract UserBeaconOptionsDao userBeaconOptionsDao();
    public abstract GoogleDeviceDao googleDeviceDao();
}