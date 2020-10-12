package com.kust.websocketvpn.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ConnectionSettingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void add(ConnectionSetting setting);

    @Delete
    void delete(ConnectionSetting setting);

    @Update
    void saveChanges(ConnectionSetting setting);

    @Query("SELECT * FROM connection_settings")
    LiveData<List<ConnectionSetting>> getAll();
}
