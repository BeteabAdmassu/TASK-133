package com.eaglepoint.console.export;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FingerprintUtil {

    public static String writeSha256Sidecar(Path file) throws Exception {
        String hash;
        try (InputStream is = Files.newInputStream(file)) {
            hash = DigestUtils.sha256Hex(is);
        }
        String sidecarContent = hash + "  " + file.getFileName().toString() + "\n";
        Path tmpFile = file.getParent().resolve(file.getFileName() + ".sha256.tmp");
        Path sidecarFile = file.getParent().resolve(file.getFileName() + ".sha256");
        Files.writeString(tmpFile, sidecarContent);
        Files.move(tmpFile, sidecarFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return hash;
    }
}
