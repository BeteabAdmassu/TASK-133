package com.eaglepoint.console.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Immutable record describing an offline update package discovered in the
 * update drop folder.
 *
 * <p>A package is a directory with:
 * <ul>
 *   <li>{@code manifest.json} — versioned metadata (see {@link Manifest})</li>
 *   <li>{@code <installerFile>} — the real Windows installer (typically a
 *       signed {@code .msi}).  {@code payload.zip} remains supported for
 *       the pre-installer-era packages.</li>
 *   <li>{@code manifest.sig} — detached Ed25519 signature over
 *       {@code manifest.json}</li>
 * </ul>
 */
public final class UpdatePackage {
    private final String packageName;
    private final String packagePath;
    private final Manifest manifest;
    private final String manifestSha256;
    private final boolean signaturePresent;

    public UpdatePackage(String packageName, String packagePath, Manifest manifest,
                          String manifestSha256, boolean signaturePresent) {
        this.packageName = packageName;
        this.packagePath = packagePath;
        this.manifest = manifest;
        this.manifestSha256 = manifestSha256;
        this.signaturePresent = signaturePresent;
    }

    public String getPackageName() { return packageName; }
    public String getPackagePath() { return packagePath; }
    public Manifest getManifest() { return manifest; }
    public String getManifestSha256() { return manifestSha256; }
    public boolean isSignaturePresent() { return signaturePresent; }

    /**
     * JSON shape of {@code manifest.json} inside the offline package.
     * Fields are intentionally flat / primitive so the file stays
     * human-inspectable before signature verification runs.
     *
     * <p>Installer-specific fields (introduced for the real {@code .msi}
     * execution pipeline):
     * <ul>
     *   <li>{@link #installerType} — currently only {@code MSI}.</li>
     *   <li>{@link #installerFile} — filename inside the package dir, e.g.
     *       {@code eaglepoint-1.2.0.msi}.  Runs through
     *       {@code msiexec /i}.</li>
     *   <li>{@link #productCode} / {@link #upgradeCode} — Windows Installer
     *       GUIDs used by {@code msiexec /x} during rollback.</li>
     *   <li>{@link #installArgs} — whitelisted {@code PROPERTY=VALUE}
     *       pairs.  Only upper-snake-case keys are accepted and values
     *       cannot contain shell metacharacters.</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Manifest {
        private String version;
        private String minPreviousVersion;
        /** Optional zip payload for non-installer test packages. */
        private String payloadFilename;
        private String payloadSha256;
        private long payloadSize;
        private String releaseNotes;
        private String signingKeyId;
        private List<String> files;
        /** MSI | (future: EXE, NONE). Required for install/rollback. */
        private String installerType;
        private String installerFile;
        private String productCode;
        private String upgradeCode;
        private List<String> installArgs;

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getMinPreviousVersion() { return minPreviousVersion; }
        public void setMinPreviousVersion(String v) { this.minPreviousVersion = v; }
        public String getPayloadFilename() { return payloadFilename; }
        public void setPayloadFilename(String payloadFilename) { this.payloadFilename = payloadFilename; }
        public String getPayloadSha256() { return payloadSha256; }
        public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
        public long getPayloadSize() { return payloadSize; }
        public void setPayloadSize(long payloadSize) { this.payloadSize = payloadSize; }
        public String getReleaseNotes() { return releaseNotes; }
        public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
        public String getSigningKeyId() { return signingKeyId; }
        public void setSigningKeyId(String signingKeyId) { this.signingKeyId = signingKeyId; }
        public List<String> getFiles() { return files; }
        public void setFiles(List<String> files) { this.files = files; }
        public String getInstallerType() { return installerType; }
        public void setInstallerType(String installerType) { this.installerType = installerType; }
        public String getInstallerFile() { return installerFile; }
        public void setInstallerFile(String installerFile) { this.installerFile = installerFile; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getUpgradeCode() { return upgradeCode; }
        public void setUpgradeCode(String upgradeCode) { this.upgradeCode = upgradeCode; }
        public List<String> getInstallArgs() { return installArgs; }
        public void setInstallArgs(List<String> installArgs) { this.installArgs = installArgs; }
    }
}
