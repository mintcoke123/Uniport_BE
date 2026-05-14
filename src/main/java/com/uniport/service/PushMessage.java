package com.uniport.service;

import java.util.Map;

public class PushMessage {

    private final String token;
    private final String title;
    private final String body;
    private final Map<String, String> data;

    public PushMessage(String token, String title, String body, Map<String, String> data) {
        this.token = token;
        this.title = title;
        this.body = body;
        this.data = data != null ? Map.copyOf(data) : Map.of();
    }

    public String getToken() {
        return token;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getData() {
        return data;
    }
}
