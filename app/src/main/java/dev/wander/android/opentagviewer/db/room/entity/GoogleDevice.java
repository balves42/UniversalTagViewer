package dev.wander.android.opentagviewer.db.room.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores Google Find My Device "devices" (e.g. trackers) as returned by the FMD server.
 * These are NOT Apple beacons and do not have a plist.
 */
@Entity(tableName = "GoogleDevices")
public class GoogleDevice {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "canonic_id")
    public String canonicId;

    @ColumnInfo(name = "name")
    public String name;

    /**
     * Not provided by the FMD server at the moment; we store a default one so UI can show something.
     */
    @ColumnInfo(name = "emoji")
    public String emoji;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    @ColumnInfo(name = "last_update")
    public long lastUpdate;

    @ColumnInfo(name = "is_removed")
    public boolean isRemoved;

    public GoogleDevice(@NonNull String canonicId, String name, String emoji, long addedAt, long lastUpdate, boolean isRemoved) {
        this.canonicId = canonicId;
        this.name = name;
        this.emoji = emoji;
        this.addedAt = addedAt;
        this.lastUpdate = lastUpdate;
        this.isRemoved = isRemoved;
    }
}
