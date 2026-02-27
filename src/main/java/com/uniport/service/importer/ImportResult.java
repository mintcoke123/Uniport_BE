package com.uniport.service.importer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 종목 마스터 Import 결과 집계.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResult {

    private int inserted;
    private int updated;
    private int skipped;

    public static ImportResult empty() {
        return new ImportResult(0, 0, 0);
    }

    public void add(ImportResult other) {
        this.inserted += other.inserted;
        this.updated += other.updated;
        this.skipped += other.skipped;
    }
}
