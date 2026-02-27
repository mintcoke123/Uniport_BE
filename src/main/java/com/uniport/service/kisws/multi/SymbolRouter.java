package com.uniport.service.kisws.multi;

import java.util.List;

/**
 * 종목코드 기준으로 안정적으로 KeyContext 한 개 선택.
 * stockCode.hashCode() 기반 라우팅으로 동일 종목은 항상 같은 키로 배분.
 */
public final class SymbolRouter {

    private SymbolRouter() {
    }

    /**
     * contexts가 비어 있으면 null. stockCode가 null/blank면 0 사용.
     * 인덱스: (hash & 0x7fffffff) % size (Math.abs(hash % size) 사용 금지).
     */
    public static KeyContext pick(List<KeyContext> contexts, String stockCode) {
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        int hash = (stockCode == null || stockCode.isBlank()) ? 0 : stockCode.hashCode();
        int index = (hash & 0x7FFFFFFF) % contexts.size();
        return contexts.get(index);
    }
}
