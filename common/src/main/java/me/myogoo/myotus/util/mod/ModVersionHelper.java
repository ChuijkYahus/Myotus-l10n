package me.myogoo.myotus.util.mod;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Collection;

public final class ModVersionHelper {
    private ModVersionHelper() {
    }

    public static boolean isVersionInRange(String versionRange, ArtifactVersion actualVersion) {
        if (isUnbounded(versionRange)) {
            return true;
        }
        if (actualVersion == null) {
            return false;
        }

        try {
            VersionRange range = parseVersionRange(versionRange);
            return range.containsVersion(actualVersion);
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    public static ArtifactVersion getMinimumVersion(String versionRange) {
        if (isUnbounded(versionRange)) {
            return new DefaultArtifactVersion("0.0.0");
        }

        try {
            VersionRange range = parseVersionRange(versionRange);
            if (range.getRecommendedVersion() != null) {
                return range.getRecommendedVersion();
            }
            if (!range.getRestrictions().isEmpty()) {
                ArtifactVersion lowerBound = range.getRestrictions().get(0).getLowerBound();
                if (lowerBound != null) {
                    return lowerBound;
                }
            }
        } catch (InvalidVersionSpecificationException ignored) {
        }

        return new DefaultArtifactVersion("0.0.0");
    }

    public static String intersectVersionRanges(Collection<String> versionRanges) {
        if (versionRanges == null || versionRanges.isEmpty()) {
            return "*";
        }

        VersionRange merged = null;
        String singleRange = null;
        int boundedRangeCount = 0;
        for (String versionRange : versionRanges) {
            if (isUnbounded(versionRange)) {
                continue;
            }

            String normalizedRange = versionRange.trim();
            VersionRange range;
            try {
                range = parseVersionRange(normalizedRange);
            } catch (InvalidVersionSpecificationException e) {
                throw new IllegalArgumentException("Invalid version range: " + versionRange, e);
            }

            boundedRangeCount++;
            if (boundedRangeCount == 1) {
                singleRange = normalizedRange;
            }
            merged = merged == null ? range : merged.restrict(range);
            if (merged.getRecommendedVersion() == null && merged.getRestrictions().isEmpty()) {
                throw new IllegalArgumentException("Version ranges do not overlap: " + versionRanges);
            }
        }

        if (merged == null) {
            return "*";
        }
        return boundedRangeCount == 1 ? singleRange : formatVersionRange(merged);
    }

    private static boolean isUnbounded(String versionRange) {
        return versionRange == null || versionRange.isBlank() || versionRange.trim().equals("*");
    }

    private static VersionRange parseVersionRange(String versionRange)
            throws InvalidVersionSpecificationException {
        String normalized = versionRange.trim();
        if (!normalized.startsWith("[") && !normalized.startsWith("(")) {
            normalized = "[" + normalized + "]";
        }
        return VersionRange.createFromVersionSpec(normalized);
    }

    private static String formatVersionRange(VersionRange versionRange) {
        if (versionRange.getRestrictions().size() == 1) {
            var restriction = versionRange.getRestrictions().get(0);
            ArtifactVersion lowerBound = restriction.getLowerBound();
            ArtifactVersion upperBound = restriction.getUpperBound();
            if (lowerBound != null
                    && upperBound != null
                    && restriction.isLowerBoundInclusive()
                    && restriction.isUpperBoundInclusive()
                    && lowerBound.compareTo(upperBound) == 0) {
                return "[" + lowerBound + "]";
            }
        }
        return versionRange.toString();
    }
}
