package com.example.findcheapgas.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** 앱 로컬 DB. 지금은 주유소 조회 캐시만 있고, 즐겨찾기와 최근 검색어는 나중에 추가한다. */
@Database(entities = {StationCacheEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "findcheapgas.db";

    private static volatile AppDatabase instance;

    public abstract StationCacheDao stationCacheDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .build();
                }
            }
        }
        return instance;
    }
}
