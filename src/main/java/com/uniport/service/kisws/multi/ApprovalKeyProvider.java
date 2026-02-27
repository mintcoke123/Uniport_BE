package com.uniport.service.kisws.multi;

/**
 * 키별 WebSocket approval_key 제공.
 * KisApprovalKeyService가 구현하며, 키별 캐싱/갱신 담당.
 */
public interface ApprovalKeyProvider {

    /**
     * 해당 키의 실시간(웹소켓) 접속키 반환. 없거나 만료 시 발급 후 반환.
     */
    String getApprovalKey(String keyId);

    /**
     * 해당 키가 설정되어 있는지 여부.
     */
    boolean isKeyConfigured(String keyId);
}
