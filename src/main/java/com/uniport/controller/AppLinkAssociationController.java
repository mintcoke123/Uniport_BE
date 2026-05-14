package com.uniport.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppLinkAssociationController {

    private static final String ASSET_LINKS_JSON = """
            [
              {
                "relation": [
                  "delegate_permission/common.handle_all_urls"
                ],
                "target": {
                  "namespace": "android_app",
                  "package_name": "com.crazyenough.uniport",
                  "sha256_cert_fingerprints": [
                    "1C:42:50:20:7F:3A:5B:62:01:97:83:D0:C6:65:81:CA:AC:41:35:20:B6:42:F1:72:F6:C2:E4:4F:C3:88:2E:EE"
                  ]
                }
              }
            ]
            """;

    private static final String APPLE_APP_SITE_ASSOCIATION_JSON = """
            {
              "applinks": {
                "apps": [],
                "details": [
                  {
                    "appID": "LU9899WD2P.com.crazyenough.uniport",
                    "paths": [
                      "/matching-room*",
                      "/friend-invite*",
                      "/friends/requests*"
                    ]
                  }
                ]
              }
            }
            """;

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAssetLinks() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ASSET_LINKS_JSON);
    }

    @GetMapping(value = {
            "/.well-known/apple-app-site-association",
            "/apple-app-site-association"
    }, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAppleAppSiteAssociation() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(APPLE_APP_SITE_ASSOCIATION_JSON);
    }
}
