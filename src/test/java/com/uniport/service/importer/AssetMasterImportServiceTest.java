package com.uniport.service.importer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetMasterImportServiceTest {

    @Test
    void importAll_runsDomesticAndUsImportsAndReturnsTotals() throws Exception {
        StockMasterImporterService domesticImporter = mock(StockMasterImporterService.class);
        UsAssetMasterImporterService usImporter = mock(UsAssetMasterImporterService.class);
        AssetMasterImportService service = new AssetMasterImportService(domesticImporter, usImporter);

        when(domesticImporter.importAll()).thenReturn(new ImportResult(2, 3, 1));
        when(usImporter.importAll()).thenReturn(new ImportResult(5, 7, 2));

        AssetMasterImportService.CombinedImportResult result = service.importAll();

        verify(domesticImporter).importAll();
        verify(usImporter).importAll();
        assertEquals(2, result.domestic().getInserted());
        assertEquals(5, result.us().getInserted());
        assertEquals(7, result.total().getInserted());
        assertEquals(10, result.total().getUpdated());
        assertEquals(3, result.total().getSkipped());
    }
}
