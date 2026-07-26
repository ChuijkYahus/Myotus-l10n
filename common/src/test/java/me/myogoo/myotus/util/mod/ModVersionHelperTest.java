package me.myogoo.myotus.util.mod;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModVersionHelperTest {
    @Test
    void wildcardAndBlankRangesMatchAnyVersion() {
        var actualVersion = new DefaultArtifactVersion("9.9.9");

        assertTrue(ModVersionHelper.isVersionInRange(null, actualVersion));
        assertTrue(ModVersionHelper.isVersionInRange("", actualVersion));
        assertTrue(ModVersionHelper.isVersionInRange("   ", actualVersion));
        assertTrue(ModVersionHelper.isVersionInRange("*", actualVersion));
        assertTrue(ModVersionHelper.isVersionInRange(" * ", actualVersion));
    }

    @Test
    void plainVersionRequiresAnExactMatch() {
        assertTrue(ModVersionHelper.isVersionInRange("1.2.3",
                new DefaultArtifactVersion("1.2.3")));
        assertFalse(ModVersionHelper.isVersionInRange("1.2.3",
                new DefaultArtifactVersion("1.2.4")));
    }

    @Test
    void inclusiveAndExclusiveRangeBoundsAreRespected() {
        assertTrue(ModVersionHelper.isVersionInRange("[1.2.0,2.0.0)",
                new DefaultArtifactVersion("1.2.0")));
        assertTrue(ModVersionHelper.isVersionInRange("[1.2.0,2.0.0)",
                new DefaultArtifactVersion("1.9.9")));
        assertFalse(ModVersionHelper.isVersionInRange("[1.2.0,2.0.0)",
                new DefaultArtifactVersion("2.0.0")));
        assertFalse(ModVersionHelper.isVersionInRange("[1.2.0,2.0.0)",
                new DefaultArtifactVersion("1.1.9")));
    }

    @Test
    void invalidRangeDoesNotMatch() {
        assertFalse(ModVersionHelper.isVersionInRange("[1.0.0,broken",
                new DefaultArtifactVersion("1.0.0")));
    }

    @Test
    void minimumVersionUsesWildcardFallbackRecommendedVersionAndLowerBound() {
        assertEquals("0.0.0", ModVersionHelper.getMinimumVersion(null).toString());
        assertEquals("0.0.0", ModVersionHelper.getMinimumVersion("").toString());
        assertEquals("0.0.0", ModVersionHelper.getMinimumVersion("*").toString());
        assertEquals("1.2.3", ModVersionHelper.getMinimumVersion("1.2.3").toString());
        assertEquals("1.5.0", ModVersionHelper.getMinimumVersion("[1.5.0,2.0.0)").toString());
        assertEquals("0.0.0", ModVersionHelper.getMinimumVersion("[1.0.0,broken").toString());
    }

    @Test
    void intersectionsPreserveBoundsAndExactVersions() {
        assertEquals("1.2.3",
                ModVersionHelper.intersectVersionRanges(List.of("*", " 1.2.3 ")));
        assertEquals("[1.2.0,1.5.0]",
                ModVersionHelper.intersectVersionRanges(List.of(
                        "[1.0.0,1.5.0]",
                        "[1.2.0,2.0.0)")));
        assertEquals("[1.2.3]",
                ModVersionHelper.intersectVersionRanges(List.of(
                        "1.2.3",
                        "[1.0.0,2.0.0)")));
    }

    @Test
    void invalidAndDisjointIntersectionsFailInsteadOfBecomingUnbounded() {
        assertThrows(IllegalArgumentException.class,
                () -> ModVersionHelper.intersectVersionRanges(List.of(
                        "[1.0.0,2.0.0)",
                        "[2.0.0,3.0.0)")));
        assertThrows(IllegalArgumentException.class,
                () -> ModVersionHelper.intersectVersionRanges(List.of("[1.0.0,broken")));
    }
}
