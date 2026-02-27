package com.uniport.service.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * zip 파일 압축 해제 후 .mst 파일 경로 반환.
 */
@Component
public class ZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);

    /**
     * zipPath를 targetDir에 풀고, 첫 번째 .mst 파일의 경로를 반환.
     * targetDir은 호출자가 삭제해야 함.
     */
    public Path extractMst(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipFile zip = new ZipFile(zipPath.toFile(), ZipFile.OPEN_READ, java.nio.charset.StandardCharsets.ISO_8859_1)) {
            Optional<ZipEntry> mstEntry = zip.stream()
                    .filter(e -> !e.isDirectory() && e.getName().toLowerCase().endsWith(".mst"))
                    .findFirst();
            if (mstEntry.isEmpty()) {
                throw new IOException("No .mst file in zip: " + zipPath);
            }
            ZipEntry entry = mstEntry.get();
            String fileName = Path.of(entry.getName()).getFileName().toString();
            Path mstPath = targetDir.resolve(fileName);
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, mstPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Extracted mst: {}", mstPath);
            return mstPath;
        }
    }
}
