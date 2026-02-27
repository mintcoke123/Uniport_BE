package com.uniport.service.kisws.multi;

/**
 * KIS 키 1개당 인증 정보.
 */
public class KeyCredential {

    private String id;
    private String appkey;
    private String appsecret;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppkey() {
        return appkey;
    }

    public void setAppkey(String appkey) {
        this.appkey = appkey;
    }

    public String getAppsecret() {
        return appsecret;
    }

    public void setAppsecret(String appsecret) {
        this.appsecret = appsecret;
    }
}
