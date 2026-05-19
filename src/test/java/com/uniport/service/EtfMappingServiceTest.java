package com.uniport.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtfMappingServiceTest {

    @Test
    void mapEtfs_mapsThemeToEtfCandidates() {
        EtfMappingService service = new EtfMappingService();

        List<MappedEtf> etfs = service.mapEtfs(List.of("반도체", "HBM"));

        assertTrue(etfs.stream().anyMatch(etf -> etf.name().equals("KODEX 반도체")));
        assertTrue(etfs.stream().allMatch(etf -> etf.symbol() != null && !etf.symbol().isBlank()));
        assertEquals("091160", etfs.stream()
                .filter(etf -> etf.name().equals("KODEX 반도체"))
                .findFirst()
                .orElseThrow()
                .symbol());
    }

    @Test
    void mapEtfs_deduplicatesOverlappingThemeAliases() {
        EtfMappingService service = new EtfMappingService();

        List<MappedEtf> etfs = service.mapEtfs(List.of("반도체", "HBM", "AI반도체"));

        assertEquals(List.of("KODEX 반도체", "TIGER 반도체"),
                etfs.stream().map(MappedEtf::name).toList());
    }

    @Test
    void mapEtfs_mapsAiAndBigTechAliases() {
        EtfMappingService service = new EtfMappingService();

        List<MappedEtf> etfs = service.mapEtfs(List.of("빅테크"));

        assertEquals(List.of("TIGER 미국테크TOP10", "KODEX 미국나스닥100"),
                etfs.stream().map(MappedEtf::name).toList());
        assertEquals(List.of("AI/빅테크", "AI/빅테크"),
                etfs.stream().map(MappedEtf::theme).toList());
    }

    @Test
    void mapEtfs_varargsIgnoresNullAndBlankThemes() {
        EtfMappingService service = new EtfMappingService();

        List<MappedEtf> etfs = service.mapEtfs("반도체", null, " ", "원전");

        assertEquals(List.of(
                        "KODEX 반도체",
                        "TIGER 반도체",
                        "ACE 원자력테마딥서치",
                        "HANARO 원자력iSelect"
                ),
                etfs.stream().map(MappedEtf::name).toList());
        assertTrue(etfs.stream().allMatch(etf -> etf.symbol() != null && !etf.symbol().isBlank()));
    }
}
