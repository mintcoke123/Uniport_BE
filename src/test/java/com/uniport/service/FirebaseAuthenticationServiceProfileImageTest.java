package com.uniport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FirebaseAuthenticationServiceProfileImageTest {

    @Test
    void firebasePictureDoesNotReplaceAppManagedProfileOptionImage() {
        boolean shouldReplace = FirebaseAuthenticationService.shouldReplaceProfileImageFromFirebase(
                "https://uniportbe-production.up.railway.app/assets/mypage/profile-options/fox.png",
                "https://lh3.googleusercontent.com/firebase-profile.png"
        );

        assertFalse(shouldReplace);
    }

    @Test
    void firebasePictureDoesNotReplaceRegularExternalProfileImage() {
        boolean shouldReplace = FirebaseAuthenticationService.shouldReplaceProfileImageFromFirebase(
                "https://example.com/old-profile.png",
                "https://lh3.googleusercontent.com/firebase-profile.png"
        );

        assertFalse(shouldReplace);
    }
}
