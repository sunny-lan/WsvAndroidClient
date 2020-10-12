package com.kust.websocketvpn.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {ConnectionSetting.class}, version = 1)
public abstract class WsvDb extends RoomDatabase {
    public abstract ConnectionSettingDao connectionSettingDao();


    private static volatile WsvDb INSTANCE;
    private static final int NUMBER_OF_THREADS = 1;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static WsvDb getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (WsvDb.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            WsvDb.class, "wsv_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
