package com.uniport.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FirebasePushMessageSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushMessageSender.class);

    @Override
    public PushSendResult send(PushMessage message) {
        if (message == null || message.getToken() == null || message.getToken().isBlank()) {
            return PushSendResult.failure();
        }

        Message firebaseMessage = Message.builder()
                .setToken(message.getToken())
                .setNotification(Notification.builder()
                        .setTitle(message.getTitle())
                        .setBody(message.getBody())
                        .build())
                .putAllData(message.getData())
                .build();

        try {
            FirebaseMessaging.getInstance().send(firebaseMessage);
            log.info("[push] FCM send succeeded tokenLength={} type={}",
                    message.getToken().length(), message.getData().get("type"));
            return PushSendResult.success();
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode errorCode = ex.getMessagingErrorCode();
            log.warn("[push] FCM send failed code={} tokenLength={} type={}",
                    errorCode, message.getToken().length(), message.getData().get("type"));
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                return PushSendResult.invalidToken();
            }
            return PushSendResult.failure();
        } catch (RuntimeException ex) {
            log.warn("[push] FCM send failed before request tokenLength={} type={} message={}",
                    message.getToken().length(), message.getData().get("type"), ex.getMessage());
            return PushSendResult.failure();
        }
    }
}
