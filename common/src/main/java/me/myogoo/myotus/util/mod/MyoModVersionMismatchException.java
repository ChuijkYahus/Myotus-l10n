package me.myogoo.myotus.util.mod;

import me.myogoo.myotus.dto.MyoModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.Objects;

public final class MyoModVersionMismatchException extends RuntimeException {
    private final String modId;
    private final String displayModName;
    private final ArtifactVersion minimumVersion;
    private final ArtifactVersion modVersion;
    private final String versionRange;

    public MyoModVersionMismatchException(MyoModInfo modInfo, String versionRange) {
        super("MyoMod version mismatch for %s: required range %s, found %s".formatted(
                Objects.requireNonNull(modInfo, "modInfo").displayName(),
                normalizeVersionRange(versionRange),
                modInfo.version()));
        this.modId = modInfo.modId();
        this.displayModName = modInfo.displayName();
        this.minimumVersion = ModVersionHelper.getMinimumVersion(versionRange);
        this.modVersion = modInfo.version();
        this.versionRange = normalizeVersionRange(versionRange);
    }

    public String getModId() {
        return modId;
    }

    public String getDisplayModName() {
        return displayModName;
    }

    public ArtifactVersion getMinimumVersion() {
        return minimumVersion;
    }

    public ArtifactVersion getModVersion() {
        return modVersion;
    }

    public String getVersionRange() {
        return versionRange;
    }

    private static String normalizeVersionRange(String versionRange) {
        return versionRange == null || versionRange.isBlank() ? "*" : versionRange.trim();
    }
}
