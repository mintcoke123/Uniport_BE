package com.uniport.config;

import com.uniport.entity.User;

public class FirebaseAuthenticatedUser {

    private final User user;
    private final String firebaseUid;
    private final String email;

    public FirebaseAuthenticatedUser(User user, String firebaseUid, String email) {
        this.user = user;
        this.firebaseUid = firebaseUid;
        this.email = email;
    }

    public User getUser() {
        return user;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }
}
