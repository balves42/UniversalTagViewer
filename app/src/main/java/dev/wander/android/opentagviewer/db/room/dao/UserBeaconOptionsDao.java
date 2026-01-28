package dev.wander.android.opentagviewer.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import dev.wander.android.opentagviewer.db.room.entity.UserBeaconOptions;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface UserBeaconOptionsDao {
    @Query("SELECT * FROM UserBeaconOptions")
    List<UserBeaconOptions> getAll();

    @Query("SELECT * FROM UserBeaconOptions WHERE beacon_id = :beaconId")
    UserBeaconOptions getById(String beaconId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(UserBeaconOptions... options);

    @Query("SELECT * FROM UserBeaconOptions WHERE beacon_id = :beaconId LIMIT 1")
    Maybe<UserBeaconOptions> getByBeaconId(String beaconId);

    @Query("SELECT * FROM UserBeaconOptions WHERE beacon_id = :beaconId LIMIT 1")
    UserBeaconOptions getByBeaconIdSync(String beaconId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(UserBeaconOptions options);

    @Query("DELETE FROM UserBeaconOptions WHERE beacon_id = :beaconId")
    void deleteByBeaconIdSync(String beaconId);

}
