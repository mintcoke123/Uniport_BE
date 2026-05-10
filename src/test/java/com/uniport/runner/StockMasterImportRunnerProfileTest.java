package com.uniport.runner;

import com.uniport.service.importer.AssetMasterImportService;
import com.uniport.service.importer.StockMasterImportRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * importer 프로필에서만 StockMasterImportRunner 빈이 등록되는지 검증.
 * 기본 프로필에서는 빈이 없어야 하므로, 기본 프로필로 기동해 runner 빈 부재를 확인.
 */
class StockMasterImportRunnerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AssetMasterImportService.class, () -> mock(AssetMasterImportService.class))
            .withBean(ConfigurableApplicationContext.class, () -> mock(ConfigurableApplicationContext.class))
            .withUserConfiguration(RunnerConfig.class);

    @Test
    void defaultProfile_runnerBeanNotPresent() {
        contextRunner.run(context -> {
            boolean hasRunner = context.getBeanNamesForType(StockMasterImportRunner.class).length > 0;
            assertFalse(hasRunner, "StockMasterImportRunner must not be registered when importer profile is not active");
        });
    }

    @Test
    void importerProfile_runnerBeanPresent() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("importer"))
                .run(context -> assertEquals(1, context.getBeanNamesForType(StockMasterImportRunner.class).length));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(StockMasterImportRunner.class)
    static class RunnerConfig {
    }
}
