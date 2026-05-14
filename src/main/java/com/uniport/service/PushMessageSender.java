package com.uniport.service;

public interface PushMessageSender {
    PushSendResult send(PushMessage message);
}
