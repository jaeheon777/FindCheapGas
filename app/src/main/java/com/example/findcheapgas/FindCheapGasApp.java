package com.example.findcheapgas;

import android.app.Application;
import android.util.Log;

import com.example.findcheapgas.util.KeyHashUtil;
import com.kakao.vectormap.KakaoMapSdk;

public class FindCheapGasApp extends Application {

    private static final String TAG = "FindCheapGasApp";

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty()) {
            Log.w(TAG, "local.properties에 KAKAO_NATIVE_APP_KEY가 없어 지도가 표시되지 않습니다.");
        }
        // 지도를 띄우기 전에 한 번만 초기화하면 된다.
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);

        // 카카오 디벨로퍼스에 등록할 키 해시. 디버그 빌드에서만 출력한다.
        if (BuildConfig.DEBUG) {
            KeyHashUtil.logKeyHash(this);
        }
    }
}
