package com.example.findcheapgas.model;

/** 주유소 조회 실패 원인 */
public enum StationError {
    NETWORK,  // 인터넷 연결 없음, 시간 초과 등
    OPINET    // 오피넷이 오류나 알 수 없는 응답을 보냄 (하루 호출 한도 초과 포함)
}
