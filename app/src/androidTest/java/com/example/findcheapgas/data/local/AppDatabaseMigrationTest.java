package com.example.findcheapgas.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * DB 버전을 올려도 앱이 열리고 데이터가 지켜지는지 확인한다. 기준은 app/schemas에 내보낸 스키마 JSON이다.
 * 실제 기기나 에뮬레이터에서 connectedDebugAndroidTest로 실행한다.
 */
@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {

    private static final String TEST_DB = "migration-test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DB);
    }

    /** 버전 3 DB를 4로 올리면 즐겨찾기 표가 생기고, 스키마가 4.json과 같다. */
    @Test
    public void migrate3To4_createsFavoriteTable() throws Exception {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3)) {
            ContentValues cache = new ContentValues();
            cache.put("cacheKey", "detail|mock|A0900001");
            cache.put("detailJson", "{}");
            cache.put("savedAt", 1L);
            db.insert("station_detail_cache", SQLiteDatabase.CONFLICT_REPLACE, cache);
        }

        // validateDroppedTables = true: 4.json과 표·열이 하나라도 다르면 실패한다.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 4, true,
                AppDatabase.MIGRATION_3_4)) {
            assertTrue(tableExists(db, "favorite_station"));
            // 3 → 4는 캐시를 지우지 않는다.
            assertEquals(1, count(db, "station_detail_cache"));
        }
    }

    /** 앱이 쓰는 설정(AppDatabase.builder)으로 3 → 4 DB를 열고, 즐겨찾기를 저장하고 읽을 수 있다. */
    @Test
    public void migrate3To4_openWithAppBuilder() throws Exception {
        helper.createDatabase(TEST_DB, 3).close();

        AppDatabase db = AppDatabase.builder(context, TEST_DB).build();
        helper.closeWhenFinished(db);
        FavoriteStationDao dao = db.favoriteStationDao();
        dao.insert(new FavoriteStationEntity("A0900001", "테스트주유소", "SKE", null,
                314000, 544000, 37.5, 127.03, 1000L));
        assertEquals(1, dao.count());
        assertEquals("테스트주유소", dao.find("A0900001").name);
    }

    /** 캐시만 있던 버전 2 DB는 지우고 버전 4로 새로 만든다 (fallbackToDestructiveMigrationFrom(1, 2)). */
    @Test
    public void version2_isRecreated() {
        File file = context.getDatabasePath(TEST_DB);
        file.getParentFile().mkdirs();
        try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            old.execSQL("CREATE TABLE station_cache (cacheKey TEXT PRIMARY KEY NOT NULL, stationsJson TEXT NOT NULL, "
                    + "savedAt INTEGER NOT NULL)");
            old.setVersion(2);
        }

        AppDatabase db = AppDatabase.builder(context, TEST_DB).build();
        helper.closeWhenFinished(db);
        SupportSQLiteDatabase opened = db.getOpenHelper().getWritableDatabase();
        assertEquals(4, opened.getVersion());
        assertTrue(tableExists(opened, "favorite_station"));
        assertEquals(0, db.favoriteStationDao().count());
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new Object[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private static int count(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM " + table)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
