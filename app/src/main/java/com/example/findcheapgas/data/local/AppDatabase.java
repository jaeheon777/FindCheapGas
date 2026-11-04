package com.example.findcheapgas.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 앱 로컬 DB. 지금은 주변 주유소 조회 캐시와 상세정보 캐시만 있고, 즐겨찾기는 나중에 추가한다.
 * 버전 2: 상세정보 캐시 추가
 */
@Database(entities = {StationCacheEntity.class, StationDetailCacheEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "findcheapgas.db";

    private static volatile AppDatabase instance;

    public abstract StationCacheDao stationCacheDao();

    public abstract StationDetailCacheDao stationDetailCacheDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    // 지금은 다시 받으면 되는 캐시만 있어서, 버전이 바뀌면 지우고 새로 만든다.
                    // 즐겨찾기처럼 지우면 안 되는 데이터를 넣을 때는 Migration을 작성한다.
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
