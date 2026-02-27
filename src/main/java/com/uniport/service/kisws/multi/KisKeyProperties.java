package com.uniport.service.kisws.multi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * KIS 멀티키 설정. prefix: kis
 * kis.keys 리스트로 키별 appkey/appsecret 바인딩.
 */
@ConfigurationProperties(prefix = "kis")
public class KisKeyProperties {

    private List<KeyCredential> keys = new ArrayList<>();

    public List<KeyCredential> getKeys() {
        return keys;
    }

    public void setKeys(List<KeyCredential> keys) {
        this.keys = keys != null ? keys : new ArrayList<>();
    }
}
