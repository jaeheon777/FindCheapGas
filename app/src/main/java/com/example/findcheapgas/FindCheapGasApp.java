package com.example.findcheapgas;

import android.app.Application;
import android.util.Log;

import com.example.findcheapgas.util.KeyHashUtil;
import com.kakao.vectormap.KakaoMapSdk;

public class FindCheapGasApp extends Application {

    private static final String TAG = "FindCheapGasApp";

    // 카카오맵 SDK 초기화 성공 여부. 지도 화면은 이 값을 보고 지도를 띄울지 정한다.
    private static boolean mapAvailable = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty()) {
            Log.w(TAG, "local.properties에 KAKAO_NATIVE_APP_KEY가 없어 지도가 표시되지 않습니다.");
        }
        // 지도를 띄우기 전에 한 번만 초기화하면 된다.
        // 네이티브 라이브러리(.so)가 없는 기기 등에서는 Error가 던져지므로 Throwable로 잡는다.
        try {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
            mapAvailable = true;
        } catch (Throwable t) {
            mapAvailable = false;
            Log.e(TAG, "카카오맵 SDK 초기화 실패. 지도를 사용할 수 없습니다.", t);
        }

        // 카카오 디벨로퍼스에 등록할 키 해시. 디버그 빌드에서만 출력한다.
        if (BuildConfig.DEBUG) {
            KeyHashUtil.logKeyHash(this);
        }
    }

    public static boolean isMapAvailable() {
        return mapAvailable;
    }
}
