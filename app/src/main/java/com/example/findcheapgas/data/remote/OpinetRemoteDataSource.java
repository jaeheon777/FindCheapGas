package com.example.findcheapgas.data.remote;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.CoordinateConverter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * 실제 오피넷 API를 호출한다. 백그라운드 스레드에서만 부른다.
 * 요청 URL에 API 키가 들어가므로 HTTP 로그는 남기지 않는다.
 */
public class OpinetRemoteDataSource {

    private static final String BASE_URL = "https://www.opinet.co.kr/api/";
    private static final long TIMEOUT_SECONDS = 10;

    private final OpinetApi api;

    public OpinetRemoteDataSource() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        api = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .build()
                .create(OpinetApi.class);
    }

    public static boolean hasApiKey() {
        return !BuildConfig.OPINET_API_KEY.isEmpty();
    }

    /**
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<OpinetAroundResponse.Oil> fetchAround(StationQuery query, CoordinateConverter.Katec katec)
            throws IOException, OpinetException {
        Response<ResponseBody> response = api.getAroundStations(
                BuildConfig.OPINET_API_KEY,
                String.format(Locale.US, "%.2f", katec.x),
                String.format(Locale.US, "%.2f", katec.y),
                query.getRadiusMeters(),
                query.getFuelType().getCode(),
                query.getSortOrder().getCode(),
                "json").execute();

        try (ResponseBody body = response.isSuccessful() ? response.body() : response.errorBody()) {
            if (!response.isSuccessful()) {
                throw new OpinetException("HTTP " + response.code());
            }
            if (body == null) {
                throw new OpinetException("빈 응답");
            }
            return OpinetAroundResponse.parse(body.string());
        }
    }
}
