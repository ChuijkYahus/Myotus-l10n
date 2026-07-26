package me.myogoo.myotus.util.mod;

import me.myogoo.myotus.api.annotation.MyoMod;
import me.myogoo.myotus.api.annotation.MyoMod.IntegrationMode;
import me.myogoo.myotus.api.integration.MyoCustomCondition;
import me.myogoo.myotus.dto.MyoModDto;
import me.myogoo.myotus.dto.MyoModInfo;
import me.myogoo.myotus.platform.mod.IModList;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner.ScannedAnnotation;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModIntegrationManagerTest {
    @BeforeEach
    void setUp() {
        clearManager();
    }

    @AfterEach
    void tearDown() {
        clearManager();
    }

    @Test
    void isLoadedAcceptsMetaAnnotatedAnnotationQueries() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));

        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(ExFirstIntegration.class));
        assertFalse(ModIntegrationManager.isLoaded(SecondIntegration.class));
    }

    @Test
    void isRegisteredAcceptsMetaAnnotatedAnnotationQueries() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(IModList.EMPTY);

        assertTrue(ModIntegrationManager.isRegistered(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isRegistered(ExFirstIntegration.class));
        assertFalse(ModIntegrationManager.isRegistered(SecondIntegration.class));
    }

    @Test
    void setModListRegistersMyoModAnnotationsFromScanData() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertTrue(ModIntegrationManager.isRegistered(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(ExFirstIntegration.class));
    }

    @Test
    void activeLookupsAcceptModIdNamespaceDisplayNameAnnotationAndAsmType() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of("first",
                modInfo("first", "first_namespace", "First Display", "1.0.0"))));

        MyoModDto mod = ModIntegrationManager.get("first_namespace");
        assertNotNull(mod);
        assertEquals("first", mod.getModId());
        assertSame(FirstIntegration.class, ModIntegrationManager.getClass("first"));
        assertSame(FirstIntegration.class, ModIntegrationManager.getClass("first_namespace"));
        assertSame(FirstIntegration.class, ModIntegrationManager.getClass("First Display"));
        assertTrue(ModIntegrationManager.isLoaded(Type.getType(FirstIntegration.class)));
        assertTrue(ModIntegrationManager.isLoaded("first"));
        assertTrue(ModIntegrationManager.isLoaded("first_namespace"));
        assertTrue(ModIntegrationManager.isLoaded("First Display"));
        assertFalse(ModIntegrationManager.isLoaded((String) null));
        assertNull(ModIntegrationManager.get("missing"));
        assertNull(ModIntegrationManager.getClass("missing"));
    }

    @Test
    void setModListClearsStaleRegistrationsAndActiveIntegrations() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));
        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));

        AnnotationScanner.setAnnotationProvider(Stream::empty);
        ModIntegrationManager.setModList(IModList.EMPTY);

        assertFalse(ModIntegrationManager.isRegistered(FirstIntegration.class));
        assertFalse(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertTrue(ModIntegrationManager.getActiveIntegrations().isEmpty());
    }

    @Test
    void failedReloadKeepsThePreviouslyPublishedState() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));

        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstConflictingRangeIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondConflictingRangeIntegration.class, ElementType.ANNOTATION_TYPE)));

        assertThrows(IllegalStateException.class,
                () -> ModIntegrationManager.setModList(modList("conflicting_ranges")));

        assertTrue(ModIntegrationManager.isRegistered(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertSame(FirstIntegration.class, ModIntegrationManager.getClass("first"));
        assertFalse(ModIntegrationManager.isRegistered(FirstConflictingRangeIntegration.class));
        assertFalse(ModIntegrationManager.isRegistered(SecondConflictingRangeIntegration.class));
        assertEquals(1, ModIntegrationManager.getActiveIntegrations().size());
    }

    @Test
    void readersKeepSeeingThePreviousStateWhileAReloadIsBeingBuilt() throws Exception {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));

        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch allowBuildToFinish = new CountDownLatch(1);
        IModList blockingModList = new IModList() {
            @Override
            public boolean isLoaded(String modId) {
                buildStarted.countDown();
                try {
                    if (!allowBuildToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to finish the test reload");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to finish the test reload", e);
                }
                return "first".equals(modId);
            }

            @Override
            public MyoModInfo getModInfoById(String modId) {
                return "first".equals(modId) ? modInfo("first", "1.0.0") : null;
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> reload = executor.submit(() -> ModIntegrationManager.setModList(blockingModList));
        try {
            assertTrue(buildStarted.await(5, TimeUnit.SECONDS));

            Map<MyoModDto, Class<? extends Annotation>> visibleState =
                    ModIntegrationManager.getActiveIntegrations();
            assertEquals(1, visibleState.size());
            assertTrue(visibleState.containsValue(FirstIntegration.class));
            assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
            assertSame(FirstIntegration.class, ModIntegrationManager.getClass("first"));

            allowBuildToFinish.countDown();
            reload.get(5, TimeUnit.SECONDS);
        } finally {
            allowBuildToFinish.countDown();
            executor.shutdownNow();
        }

        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
    }

    @Test
    void activeIntegrationsSnapshotIsReadOnly() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));

        assertThrows(UnsupportedOperationException.class,
                () -> ModIntegrationManager.getActiveIntegrations().clear());
        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
    }

    @Test
    void activeIntegrationsSnapshotDoesNotChangeWhenManagerIsReloaded() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));
        ModIntegrationManager.setModList(modList("first"));
        var snapshot = ModIntegrationManager.getActiveIntegrations();

        ModIntegrationManager.setModList(IModList.EMPTY);

        assertEquals(1, snapshot.size());
        assertTrue(snapshot.containsValue(FirstIntegration.class));
        assertTrue(ModIntegrationManager.getActiveIntegrations().isEmpty());
    }

    @Test
    void registeredIntegrationsSnapshotIncludesActiveAndInactiveRegistrations() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondIntegrationWithMyoMod.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        var registered = ModIntegrationManager.getRegisteredIntegrations();
        assertEquals(2, registered.size());
        assertEquals("first", registered.get(0).modId());
        assertTrue(registered.get(0).active());
        assertEquals("second_registered", registered.get(1).modId());
        assertFalse(registered.get(1).active());
        assertThrows(UnsupportedOperationException.class, () -> registered.clear());
    }

    @Test
    void duplicateScanEntriesForSameAnnotationAreIgnored() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstIntegration.class, ElementType.TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertEquals(1, ModIntegrationManager.getActiveIntegrations().size());
        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
    }

    @Test
    void nonAnnotationAsmTypesAreNeverLoaded() {
        assertFalse(ModIntegrationManager.isLoaded(Type.getType(String.class)));
    }

    @Test
    void getClassReturnsNullWhenLookupMatchesMultipleActiveIntegrationClasses() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstExtendedIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(FirstExtendedIntegration.class));
        assertNull(ModIntegrationManager.getClass("first"));
    }

    @Test
    void customConditionExceptionsPreventActivationWithoutBreakingOtherIntegrations() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(CustomThrowingIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(CustomTrueIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of(
                "custom_throwing", modInfo("custom_throwing", "1.0.0"),
                "custom_true", modInfo("custom_true", "1.0.0"))));

        assertFalse(ModIntegrationManager.isLoaded(CustomThrowingIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(CustomTrueIntegration.class));
    }

    @Test
    void missingModInfoDoesNotActivateEvenWhenModListReportsLoaded() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(FirstIntegration.class,
                ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(new IModList() {
            @Override
            public boolean isLoaded(String modId) {
                return true;
            }

            @Override
            public MyoModInfo getModInfoById(String modId) {
                return null;
            }
        });

        assertTrue(ModIntegrationManager.isRegistered(FirstIntegration.class));
        assertFalse(ModIntegrationManager.isLoaded(FirstIntegration.class));
    }

    @Test
    void blankAliasIsIgnoredForRegisteredAndActiveLookups() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(BlankAliasIntegration.class,
                ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("blank_alias"));

        assertTrue(ModIntegrationManager.isRegistered("blank_alias"));
        assertTrue(ModIntegrationManager.isLoaded("blank_alias"));
        assertFalse(ModIntegrationManager.isRegistered(""));
        assertFalse(ModIntegrationManager.isLoaded(""));
    }

    @Test
    void inactiveRegisteredAliasCanResolveItsAnnotationClass() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(ReAvaritiaIntegration.class,
                ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(IModList.EMPTY);

        assertTrue(ModIntegrationManager.isRegistered("re_avaritia"));
        assertFalse(ModIntegrationManager.isLoaded("re_avaritia"));
        assertSame(ReAvaritiaIntegration.class, ModIntegrationManager.getClass("re_avaritia"));
    }

    @Test
    void versionMismatchThrowsDedicatedException() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(RangeIntegration.class, ElementType.ANNOTATION_TYPE)));

        MyoModVersionMismatchException exception = assertThrows(MyoModVersionMismatchException.class,
                () -> ModIntegrationManager.setModList(modList(Map.of(
                        "range", modInfo("range", "range", "Range Mod", "1.5.0")))));

        assertEquals("range", exception.getModId());
        assertEquals("Range Mod", exception.getDisplayModName());
        assertEquals("2.0.0", exception.getMinimumVersion().toString());
        assertEquals("1.5.0", exception.getModVersion().toString());
        assertEquals("[2.0.0,)", exception.getVersionRange());
        assertTrue(exception.getMessage().contains("required range [2.0.0,)"));
    }

    @Test
    void incompatibleMergedRangesFailOnlyWhenTheTargetModIsLoaded() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstConflictingRangeIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondConflictingRangeIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(IModList.EMPTY);
        assertTrue(ModIntegrationManager.isRegistered(FirstConflictingRangeIntegration.class));
        assertTrue(ModIntegrationManager.isRegistered(SecondConflictingRangeIntegration.class));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ModIntegrationManager.setModList(modList("conflicting_ranges")));
        assertTrue(exception.getMessage().contains("conflicting_ranges"));
        assertTrue(exception.getMessage().contains("[1.0.0,2.0.0)"));
        assertTrue(exception.getMessage().contains("[2.0.0,3.0.0)"));
    }

    @Test
    void customConditionMustPassBeforeActivation() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(CustomFalseIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(CustomTrueIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of(
                "custom_false", modInfo("custom_false", "1.0.0"),
                "custom_true", modInfo("custom_true", "1.0.0"))));

        assertFalse(ModIntegrationManager.isLoaded(CustomFalseIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(CustomTrueIntegration.class));
    }

    @Test
    void overrideModeSuppressesOnlyAndExtendedIntegrationsForSameMod() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstExtendedIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstOverrideIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertFalse(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertFalse(ModIntegrationManager.isLoaded(FirstExtendedIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(FirstOverrideIntegration.class));
    }

    @Test
    void sameModIdUnconditionalIntegrationsAreMergedAndAllAnnotationClassesLoad() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondFirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstExtendedIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertTrue(ModIntegrationManager.isLoaded(FirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(SecondFirstIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(FirstExtendedIntegration.class));
    }

    @Test
    void sameModIdUnconditionalIntegrationsMergeAliasesAndIntersectVersionRanges() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstRangeAliasIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondRangeAliasIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of("merged_range_mod",
                modInfo("merged_range_mod", "1.1.0"))));

        assertTrue(ModIntegrationManager.isLoaded(FirstRangeAliasIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(SecondRangeAliasIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded("extended"));
        assertTrue(ModIntegrationManager.isLoaded("crafting"));

        MyoModDto activeMod = ModIntegrationManager.get("extended");
        assertNotNull(activeMod);
        assertEquals("[1.0.0,1.2.0]", activeMod.getVersionRange());
        assertTrue(activeMod.getAliases().contains("extended"));
        assertTrue(activeMod.getAliases().contains("crafting"));
        assertNull(ModIntegrationManager.getClass("extended"));
        assertNull(ModIntegrationManager.getClass("crafting"));
    }

    @Test
    void customConditionIntegrationsDoNotShareAliasesForTheSameModId() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(ReAvaritiaIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(AvaritiaNeoIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of("avaritia",
                modInfo("avaritia", "avaritia", "Re:Avaritia", "1.4.0"))));

        assertTrue(ModIntegrationManager.isRegistered("re_avaritia"));
        assertTrue(ModIntegrationManager.isRegistered("avaritia_neo"));
        assertTrue(ModIntegrationManager.isLoaded("re_avaritia"));
        assertFalse(ModIntegrationManager.isLoaded("avaritia_neo"));
        assertSame(ReAvaritiaIntegration.class, ModIntegrationManager.getClass("re_avaritia"));
        assertSame(AvaritiaNeoIntegration.class, ModIntegrationManager.getClass("avaritia_neo"));
        MyoModDto activeMod = ModIntegrationManager.get("re_avaritia");
        assertNotNull(activeMod);
        assertTrue(activeMod.getAliases().contains("re_avaritia"));
        assertFalse(activeMod.getAliases().contains("avaritia_neo"));
    }

    @Test
    void customConditionFiltersIntegrationBeforeVersionRangeIsChecked() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(ReAvaritiaIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(AvaritiaNeoIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList(Map.of("avaritia",
                modInfo("avaritia", "avaritia", "AvaritiaNeo", "1.3.0"))));

        assertFalse(ModIntegrationManager.isLoaded("re_avaritia"));
        assertTrue(ModIntegrationManager.isLoaded("avaritia_neo"));
    }

    @Test
    void customConditionVersionMismatchThrowsAfterConditionPasses() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(IncompatibleAvaritiaNeoIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(ReAvaritiaIntegration.class, ElementType.ANNOTATION_TYPE)));

        MyoModVersionMismatchException exception = assertThrows(MyoModVersionMismatchException.class,
                () -> ModIntegrationManager.setModList(modList(Map.of("avaritia",
                        modInfo("avaritia", "avaritia", "Avaritia", "1.1.5")))));

        assertEquals("avaritia", exception.getModId());
        assertEquals("Avaritia", exception.getDisplayModName());
        assertEquals("1.2.7", exception.getMinimumVersion().toString());
        assertEquals("1.1.5", exception.getModVersion().toString());
        assertEquals("[1.2.7,)", exception.getVersionRange());
    }

    @Test
    void customConditionedNonExtendedIntegrationsWithSameModIdAreDifferentActivationGroups() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(CustomConditionedDefaultForkIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(CustomConditionedOverrideForkIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("forked_mod"));

        assertTrue(ModIntegrationManager.isLoaded(CustomConditionedDefaultForkIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(CustomConditionedOverrideForkIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded("custom-default-fork"));
        assertTrue(ModIntegrationManager.isLoaded("custom-override-fork"));
        assertSame(CustomConditionedDefaultForkIntegration.class,
                ModIntegrationManager.getClass("custom-default-fork"));
        assertSame(CustomConditionedOverrideForkIntegration.class,
                ModIntegrationManager.getClass("custom-override-fork"));
    }

    @Test
    void duplicateAliasIsAllowedWhenIntegrationsUseTheSameModId() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstDuplicateAliasIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondDuplicateAliasIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("duplicate_alias_mod"));

        assertTrue(ModIntegrationManager.isRegistered("same_alias"));
        assertTrue(ModIntegrationManager.isLoaded("same_alias"));
        assertTrue(ModIntegrationManager.isLoaded(FirstDuplicateAliasIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(SecondDuplicateAliasIntegration.class));
        assertNull(ModIntegrationManager.getClass("same_alias"));
    }

    @Test
    void aliasMayMatchItsOwnModId() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(myoModAnnotation(SelfAliasIntegration.class,
                ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("self_alias"));

        assertTrue(ModIntegrationManager.isRegistered("self_alias"));
        assertTrue(ModIntegrationManager.isLoaded("self_alias"));
        assertSame(SelfAliasIntegration.class, ModIntegrationManager.getClass("self_alias"));
    }

    @Test
    void inactiveAliasesForSameModIdResolveToTheirRegisteredAnnotationClasses() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(ReAvaritiaIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(AvaritiaNeoIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(IModList.EMPTY);

        assertSame(ReAvaritiaIntegration.class, ModIntegrationManager.getClass("re_avaritia"));
        assertSame(AvaritiaNeoIntegration.class, ModIntegrationManager.getClass("avaritia_neo"));
    }

    @Test
    void overrideModeOwnsAllAliasesForTheSameModId() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstExtendedIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(FirstOverrideAliasIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("first"));

        assertTrue(ModIntegrationManager.isLoaded("first_override_alias"));
        assertSame(FirstOverrideAliasIntegration.class, ModIntegrationManager.getClass("first"));
        assertSame(FirstOverrideAliasIntegration.class, ModIntegrationManager.getClass("first_override_alias"));
    }

    @Test
    void sameModIdDefaultIntegrationsMergeAliasesAndMakeAliasClassLookupAmbiguous() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstAliasDefaultIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondAliasDefaultIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("default_alias_mod"));

        assertTrue(ModIntegrationManager.isLoaded("first_default_alias"));
        assertTrue(ModIntegrationManager.isLoaded("second_default_alias"));
        assertTrue(ModIntegrationManager.isLoaded(FirstAliasDefaultIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded(SecondAliasDefaultIntegration.class));
        assertNull(ModIntegrationManager.getClass("first_default_alias"));
        assertNull(ModIntegrationManager.getClass("second_default_alias"));
    }

    @Test
    void multipleExtendedIntegrationsMakeSharedAliasLookupAmbiguous() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstExtendedAliasIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondExtendedAliasIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("extended_alias_mod"));

        assertTrue(ModIntegrationManager.isLoaded("first_extended_alias"));
        assertTrue(ModIntegrationManager.isLoaded("second_extended_alias"));
        assertNull(ModIntegrationManager.getClass("first_extended_alias"));
        assertNull(ModIntegrationManager.getClass("second_extended_alias"));
    }

    @Test
    void aliasesFromInactiveSameModIdRegistrationsWithCustomConditionsDoNotMatchActiveIntegrations() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(ActiveAliasIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(ConditionBlockedAliasIntegration.class, ElementType.ANNOTATION_TYPE)));

        ModIntegrationManager.setModList(modList("conditional_alias_mod"));

        assertTrue(ModIntegrationManager.isLoaded(ActiveAliasIntegration.class));
        assertFalse(ModIntegrationManager.isLoaded(ConditionBlockedAliasIntegration.class));
        assertTrue(ModIntegrationManager.isLoaded("active_alias"));
        assertFalse(ModIntegrationManager.isLoaded("condition_blocked_alias"));
        assertSame(ConditionBlockedAliasIntegration.class, ModIntegrationManager.getClass("condition_blocked_alias"));
    }

    @Test
    void aliasCannotBeSharedByDifferentModIds() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstAliasConflictIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondAliasConflictIntegration.class, ElementType.ANNOTATION_TYPE)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ModIntegrationManager.setModList(IModList.EMPTY));
        assertTrue(exception.getMessage().contains("shared_alias"));
        assertTrue(exception.getMessage().contains("first_conflict"));
        assertTrue(exception.getMessage().contains("second_conflict"));
    }

    @Test
    void aliasCannotPointAtADifferentRegisteredModId() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(FirstAliasesSecondIntegration.class, ElementType.ANNOTATION_TYPE),
                myoModAnnotation(SecondIntegrationWithMyoMod.class, ElementType.ANNOTATION_TYPE)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ModIntegrationManager.setModList(IModList.EMPTY));
        assertTrue(exception.getMessage().contains("second_registered"));
        assertTrue(exception.getMessage().contains("first_aliases_second"));
        assertTrue(exception.getMessage().contains("collides with mod id"));
    }

    private static ScannedAnnotation myoModAnnotation(Class<? extends Annotation> annotationClass,
            ElementType targetType) {
        return new ScannedAnnotation(Type.getType(MyoMod.class), targetType, Type.getType(annotationClass));
    }

    private static IModList modList(String loadedModId) {
        return modList(Map.of(loadedModId, modInfo(loadedModId, "1.0.0")));
    }

    private static IModList modList(Map<String, MyoModInfo> loadedMods) {
        Map<String, MyoModInfo> mods = new HashMap<>(loadedMods);
        return new IModList() {
            @Override
            public boolean isLoaded(String modId) {
                return mods.containsKey(modId);
            }

            @Override
            public MyoModInfo getModInfoById(String modId) {
                return mods.get(modId);
            }
        };
    }

    private static MyoModInfo modInfo(String modId, String version) {
        return new MyoModInfo(modId, modId, modId, new DefaultArtifactVersion(version));
    }

    private static MyoModInfo modInfo(String modId, String namespace, String displayName, String version) {
        return new MyoModInfo(modId, namespace, displayName, new DefaultArtifactVersion(version));
    }

    private static void clearManager() {
        AnnotationScanner.setAnnotationProvider(Stream::empty);
        ModIntegrationManager.setModList(IModList.EMPTY);
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod("first")
    private @interface FirstIntegration {
    }

    @FirstIntegration
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExFirstIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first", mode = IntegrationMode.DEFAULT)
    private @interface SecondFirstIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first", mode = IntegrationMode.EXTENDED)
    private @interface FirstExtendedIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first", mode = IntegrationMode.OVERRIDE)
    private @interface FirstOverrideIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "range", versionRange = "[2.0.0,)")
    private @interface RangeIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "conflicting_ranges", versionRange = "[1.0.0,2.0.0)")
    private @interface FirstConflictingRangeIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "conflicting_ranges", versionRange = "[2.0.0,3.0.0)")
    private @interface SecondConflictingRangeIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "custom_false", customCondition = FalseCondition.class)
    private @interface CustomFalseIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "custom_true", customCondition = TrueCondition.class)
    private @interface CustomTrueIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "custom_throwing", customCondition = ThrowingCondition.class)
    private @interface CustomThrowingIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "blank_alias", alias = "")
    private @interface BlankAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "avaritia", alias = "re_avaritia", versionRange = "[1.3.9.6,)",
            customCondition = ReAvaritiaCondition.class)
    private @interface ReAvaritiaIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "avaritia", alias = "avaritia_neo", customCondition = AvaritiaNeoCondition.class)
    private @interface AvaritiaNeoIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "avaritia", alias = "incompatible-avaritia-neo", versionRange = "[1.2.7,)",
            customCondition = AvaritiaNeoCondition.class)
    private @interface IncompatibleAvaritiaNeoIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "forked_mod", alias = "custom-default-fork", customCondition = TrueCondition.class)
    private @interface CustomConditionedDefaultForkIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "forked_mod", alias = "custom-override-fork", mode = IntegrationMode.OVERRIDE,
            customCondition = TrueCondition.class)
    private @interface CustomConditionedOverrideForkIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "duplicate_alias_mod", alias = "same_alias")
    private @interface FirstDuplicateAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "duplicate_alias_mod", alias = "same_alias")
    private @interface SecondDuplicateAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "self_alias", alias = "self_alias")
    private @interface SelfAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first", alias = "first_override_alias", mode = IntegrationMode.OVERRIDE)
    private @interface FirstOverrideAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "default_alias_mod", alias = "first_default_alias", mode = IntegrationMode.DEFAULT)
    private @interface FirstAliasDefaultIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "default_alias_mod", alias = "second_default_alias", mode = IntegrationMode.DEFAULT)
    private @interface SecondAliasDefaultIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "merged_range_mod", alias = "extended", versionRange = "[1.0.0,1.3.0]")
    private @interface FirstRangeAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "merged_range_mod", alias = "crafting", versionRange = "[0.1.0,1.2.0]")
    private @interface SecondRangeAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "extended_alias_mod", alias = "first_extended_alias", mode = IntegrationMode.EXTENDED)
    private @interface FirstExtendedAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "extended_alias_mod", alias = "second_extended_alias", mode = IntegrationMode.EXTENDED)
    private @interface SecondExtendedAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "conditional_alias_mod", alias = "active_alias")
    private @interface ActiveAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "conditional_alias_mod", alias = "version_blocked_alias", versionRange = "[2.0.0,)")
    private @interface VersionBlockedAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "conditional_alias_mod", alias = "condition_blocked_alias", customCondition = FalseCondition.class)
    private @interface ConditionBlockedAliasIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first_conflict", alias = "shared_alias")
    private @interface FirstAliasConflictIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "second_conflict", alias = "shared_alias")
    private @interface SecondAliasConflictIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod(value = "first_aliases_second", alias = "second_registered")
    private @interface FirstAliasesSecondIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod("second_registered")
    private @interface SecondIntegrationWithMyoMod {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface SecondIntegration {
    }

    private static final class FalseCondition implements MyoCustomCondition {
        @Override
        public boolean test(MyoModInfo modInfo) {
            return false;
        }
    }

    private static final class TrueCondition implements MyoCustomCondition {
        @Override
        public boolean test(MyoModInfo modInfo) {
            return true;
        }
    }

    private static final class ThrowingCondition implements MyoCustomCondition {
        @Override
        public boolean test(MyoModInfo modInfo) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class ReAvaritiaCondition implements MyoCustomCondition {
        @Override
        public boolean test(MyoModInfo modInfo) {
            return "Re:Avaritia".equals(modInfo.displayName())
                    || "Re-Avaritia".equals(modInfo.displayName());
        }
    }

    private static final class AvaritiaNeoCondition implements MyoCustomCondition {
        @Override
        public boolean test(MyoModInfo modInfo) {
            return "Avaritia".equals(modInfo.displayName())
                    || "AvaritiaNeo".equals(modInfo.displayName());
        }
    }
}
