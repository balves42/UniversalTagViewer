package dev.wander.android.opentagviewer.db.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import dev.wander.android.opentagviewer.db.room.entity.GoogleDevice;

@Dao
public interface GoogleDeviceDao {

    @Query("SELECT * FROM GoogleDevices WHERE is_removed = 0")
    List<GoogleDevice> getAllActive();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(GoogleDevice... devices);

    @Query("UPDATE GoogleDevices SET is_removed = 1, last_update = :now WHERE canonic_id = :canonicId")
    void setRemoved(String canonicId, long now);
}
