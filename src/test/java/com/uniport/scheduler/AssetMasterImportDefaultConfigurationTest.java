package com.uniport.scheduler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetMasterImportDefaultConfigurationTest {

    @Test
    void assetMasterStartupImports_defaultToDisabledForDeployResponsiveness() throws IOException {
        String applicationYaml = readApplicationYaml();

        assertTrue(
                applicationYaml.contains("enabled: ${STOCK_MASTER_IMPORT_ENABLED:false}"),
                "KRX stock master import must default to disabled and be enabled explicitly by env"
        );
        assertTrue(
                applicationYaml.contains("enabled: ${US_ASSET_MASTER_IMPORT_ENABLED:false}"),
                "US asset master import must default to disabled and be enabled explicitly by env"
        );
    }

    private String readApplicationYaml() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            assertNotNull(inputStream, "application.yaml must be available on the test classpath");
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
