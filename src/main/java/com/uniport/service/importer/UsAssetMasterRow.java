package com.uniport.service.importer;

public record UsAssetMasterRow(
        String assetId,
        String name,
        String symbol,
        String market
) {
}
