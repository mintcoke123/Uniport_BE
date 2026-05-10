package com.uniport.service.importer;

import org.springframework.stereotype.Service;

@Service
public class AssetMasterImportService {

    private final StockMasterImporterService stockMasterImporterService;
    private final UsAssetMasterImporterService usAssetMasterImporterService;

    public AssetMasterImportService(StockMasterImporterService stockMasterImporterService,
                                    UsAssetMasterImporterService usAssetMasterImporterService) {
        this.stockMasterImporterService = stockMasterImporterService;
        this.usAssetMasterImporterService = usAssetMasterImporterService;
    }

    public ImportResult importDomestic() throws Exception {
        return stockMasterImporterService.importAll();
    }

    public ImportResult importUs() {
        return usAssetMasterImporterService.importAll();
    }

    public CombinedImportResult importAll() throws Exception {
        ImportResult domestic = importDomestic();
        ImportResult us = importUs();
        return new CombinedImportResult(domestic, us);
    }

    public record CombinedImportResult(ImportResult domestic, ImportResult us) {

        public ImportResult total() {
            ImportResult total = ImportResult.empty();
            total.add(domestic);
            total.add(us);
            return total;
        }
    }
}
