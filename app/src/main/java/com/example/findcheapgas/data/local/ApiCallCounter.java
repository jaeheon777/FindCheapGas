package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 오늘 오피넷 API를 실제로 호출한 횟수 (하루 300회 제한 확인용).
 * 날짜가 바뀌면 0부터 다시 센다. 가짜 데이터와 캐시는 세지 않는다.
 */
public class ApiCallCounter {

    public static final int DAILY_LIMIT = 300;

    private static final String PREFS_NAME = "api_usage";
    private static final String KEY_DATE = "opinet_date";
    private static final String KEY_COUNT = "opinet_count";

    private final SharedPreferences prefs;

    public ApiCallCounter(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 호출 1회를 기록하고 오늘 누적 횟수를 돌려준다. */
    public synchronized int increment() {
        int count = getTodayCount() + 1;
        prefs.edit()
                .putString(KEY_DATE, today())
                .putInt(KEY_COUNT, count)
                .apply();
        return count;
    }

    public synchronized int getTodayCount() {
        if (!today().equals(prefs.getString(KEY_DATE, null))) {
            return 0;
        }
        return prefs.getInt(KEY_COUNT, 0);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
