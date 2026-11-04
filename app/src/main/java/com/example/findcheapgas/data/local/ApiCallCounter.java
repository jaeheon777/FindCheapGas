package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 오늘 오피넷 API를 실제로 호출한 횟수 (하루 300회 제한).
 * 날짜가 바뀌면 0부터 다시 센다. 가짜 데이터와 캐시는 세지 않는다.
 * 실제 호출 직전에 acquire()를 부르고, 오늘 이미 300회를 불렀으면 호출하지 않도록 DailyLimitReachedException을 던진다.
 */
public class ApiCallCounter {

    public static final int DAILY_LIMIT = 300;

    private static final String PREFS_NAME = "api_usage";
    private static final String KEY_DATE = "opinet_date";
    private static final String KEY_COUNT = "opinet_count";

    /** 날짜와 횟수를 저장하는 곳. 앱에서는 SharedPreferences, 테스트에서는 메모리를 쓴다. */
    interface Storage {
        String getDate();

        int getCount();

        void save(String date, int count);
    }

    private final Storage storage;
    private final Supplier<String> today;

    public ApiCallCounter(Context context) {
        this(new PreferencesStorage(context), ApiCallCounter::today);
    }

    ApiCallCounter(Storage storage, Supplier<String> today) {
        this.storage = storage;
        this.today = today;
    }

    /**
     * 호출 1회를 기록하고 오늘 누적 횟수를 돌려준다.
     * 오늘 이미 하루 한도만큼 불렀으면 기록하지 않고 DailyLimitReachedException을 던진다.
     */
    public synchronized int acquire() throws DailyLimitReachedException {
        int count = getTodayCount();
        if (count >= DAILY_LIMIT) {
            throw new DailyLimitReachedException(count);
        }
        storage.save(today.get(), count + 1);
        return count + 1;
    }

    public synchronized int getTodayCount() {
        if (!today.get().equals(storage.getDate())) {
            return 0;
        }
        return storage.getCount();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static final class PreferencesStorage implements Storage {
        private final SharedPreferences prefs;

        PreferencesStorage(Context context) {
            prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public String getDate() {
            return prefs.getString(KEY_DATE, null);
        }

        @Override
        public int getCount() {
            return prefs.getInt(KEY_COUNT, 0);
        }

        @Override
        public void save(String date, int count) {
            prefs.edit()
                    .putString(KEY_DATE, date)
                    .putInt(KEY_COUNT, count)
                    .apply();
        }
    }
}
