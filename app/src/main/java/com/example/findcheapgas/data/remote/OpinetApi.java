package com.example.findcheapgas.data.remote;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 오피넷 유가정보 API.
 * 한도 초과 등으로 JSON이 아닌 응답이 올 수 있어서 본문을 그대로 받고 OpinetAroundResponse.parse로 직접 해석한다.
 */
public interface OpinetApi {

    /**
     * 반경 내 주유소.
     *
     * @param x      KATEC X 좌표
     * @param y      KATEC Y 좌표
     * @param radius 반경(m, 최대 5000)
     * @param prodcd 유종 코드
     * @param sort   1 가격순, 2 거리순
     */
    @GET("aroundAll.do")
    Call<ResponseBody> getAroundStations(
            @Query("code") String apiKey,
            @Query("x") String x,
            @Query("y") String y,
            @Query("radius") int radius,
            @Query("prodcd") String prodcd,
            @Query("sort") int sort,
            @Query("out") String out);
}
