package com.example.findcheapgas.data.remote.kakao;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 카카오 로컬 API. Authorization 헤더(KakaoAK {REST 키})는 KakaoLocalDataSource의 OkHttp 인터셉터가 붙인다.
 * 본문을 그대로 받고 KakaoLocalResponse로 직접 해석한다.
 */
public interface KakaoLocalApi {

    /**
     * 키워드로 장소 검색.
     *
     * @param size 한 번에 받을 개수 (1~15)
     */
    @GET("v2/local/search/keyword.json")
    Call<ResponseBody> searchKeyword(
            @Query("query") String query,
            @Query("size") int size);

    /**
     * 주소 검색. 키워드 검색 결과가 없을 때 한 번 더 해 본다.
     *
     * @param size 한 번에 받을 개수 (1~30)
     */
    @GET("v2/local/search/address.json")
    Call<ResponseBody> searchAddress(
            @Query("query") String query,
            @Query("size") int size);
}
