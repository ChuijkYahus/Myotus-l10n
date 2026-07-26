package me.myogoo.myotus.util;

import me.myogoo.myotus.api.annotation.MyoMod;
import me.myogoo.myotus.dto.MyoModInfo;
import me.myogoo.myotus.platform.mod.IModList;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner.ScannedAnnotation;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationScannerTest {
    private static final ScannedAnnotation FIRST_ON_STRING = new ScannedAnnotation(
            Type.getType(FirstAnnotation.class), Type.getType(String.class));
    private static final ScannedAnnotation FIRST_ON_INTEGER = new ScannedAnnotation(
            Type.getType(FirstAnnotation.class), Type.getType(Integer.class));
    private static final ScannedAnnotation EXTENDED_FIRST_ON_LONG = new ScannedAnnotation(
            Type.getType(ExtendedFirstAnnotation.class), Type.getType(Long.class));
    private static final ScannedAnnotation SECOND_ON_LIST = new ScannedAnnotation(
            Type.getType(SecondAnnotation.class), Type.getType(List.class));

    @Mock
    private Supplier<Stream<ScannedAnnotation>> annotationProvider;

    @BeforeEach
    void setUp() {
        AnnotationScanner.setAnnotationProvider(Stream::empty);
        ModIntegrationManager.setModList(IModList.EMPTY);
    }

    @AfterEach
    void tearDown() {
        AnnotationScanner.setAnnotationProvider(Stream::empty);
        ModIntegrationManager.setModList(IModList.EMPTY);
    }

    @Test
    void annotationsAreCachedDeduplicatedAndUnmodifiable() {
        var calls = new AtomicInteger();
        AnnotationScanner.setAnnotationProvider(() -> {
            calls.incrementAndGet();
            return Stream.of(FIRST_ON_STRING, FIRST_ON_INTEGER, SECOND_ON_LIST, FIRST_ON_STRING);
        });

        var firstRead = AnnotationScanner.getAnnotations();
        var secondRead = AnnotationScanner.getAnnotations();

        assertEquals(List.of(FIRST_ON_STRING, FIRST_ON_INTEGER, SECOND_ON_LIST), List.copyOf(firstRead));
        assertSame(firstRead, secondRead);
        assertEquals(1, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> firstRead.add(SECOND_ON_LIST));
    }

    @Test
    void findFiltersByAnnotationClassAndType() {
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                FIRST_ON_STRING, EXTENDED_FIRST_ON_LONG, FIRST_ON_INTEGER, SECOND_ON_LIST));

        assertEquals(List.of(FIRST_ON_STRING, EXTENDED_FIRST_ON_LONG, FIRST_ON_INTEGER),
                List.copyOf(AnnotationScanner.find(FirstAnnotation.class)));
        assertEquals(List.of(EXTENDED_FIRST_ON_LONG),
                List.copyOf(AnnotationScanner.find(ExtendedFirstAnnotation.class)));
        assertEquals(List.of(SECOND_ON_LIST),
                List.copyOf(AnnotationScanner.find(Type.getType(SecondAnnotation.class))));
    }

    @Test
    void settingProviderClearsCache() {
        var calls = new AtomicInteger();
        AnnotationScanner.setAnnotationProvider(() -> {
            calls.incrementAndGet();
            return Stream.of(FIRST_ON_STRING);
        });

        var cached = AnnotationScanner.getAnnotations();
        AnnotationScanner.setAnnotationProvider(() -> {
            calls.incrementAndGet();
            return Stream.of(SECOND_ON_LIST);
        });
        var refreshed = AnnotationScanner.getAnnotations();

        assertNotSame(cached, refreshed);
        assertEquals(List.of(SECOND_ON_LIST), List.copyOf(refreshed));
        assertEquals(2, calls.get());
    }

    @Test
    void scannedAnnotationExposesClassNameFromAsmType() {
        assertEquals(String.class.getName(), FIRST_ON_STRING.className());
    }

    @Test
    void annotationProviderCanBeMocked() {
        when(annotationProvider.get()).thenReturn(Stream.of(FIRST_ON_STRING));

        AnnotationScanner.setAnnotationProvider(annotationProvider);

        assertEquals(List.of(FIRST_ON_STRING), List.copyOf(AnnotationScanner.getAnnotations()));
        assertEquals(List.of(FIRST_ON_STRING), List.copyOf(AnnotationScanner.getAnnotations()));
        verify(annotationProvider).get();
        verifyNoMoreInteractions(annotationProvider);
    }

    @Test
    void findActiveRequiresAllIntegrationAnnotationsOnSameTargetToBeLoaded() {
        var activeTarget = new ScannedAnnotation(
                Type.getType(FirstAnnotation.class), ElementType.TYPE, Type.getType(ActiveTarget.class));
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                myoModAnnotation(LoadedIntegration.class),
                myoModAnnotation(MissingIntegration.class),
                activeTarget,
                scanned(LoadedIntegration.class, ActiveTarget.class),
                scanned(FirstAnnotation.class, PartiallyActiveTarget.class),
                scanned(LoadedIntegration.class, PartiallyActiveTarget.class),
                scanned(MissingIntegration.class, PartiallyActiveTarget.class)));
        ModIntegrationManager.setModList(modList("loaded"));

        assertEquals(List.of(activeTarget), List.copyOf(AnnotationScanner.findActive(FirstAnnotation.class)));
    }

    @Test
    void integrationDependentCachesRefreshWhenTheLoadedModSetChanges() {
        var loadedMyoMod = myoModAnnotation(LoadedIntegration.class);
        var missingMyoMod = myoModAnnotation(MissingIntegration.class);
        var loadedTarget = scanned(LoadedIntegration.class, ActiveTarget.class);
        var missingTarget = scanned(MissingIntegration.class, PartiallyActiveTarget.class);
        AnnotationScanner.setAnnotationProvider(() -> Stream.of(
                loadedMyoMod,
                missingMyoMod,
                loadedTarget,
                missingTarget));

        ModIntegrationManager.setModList(modList("loaded"));
        assertEquals(List.of(loadedMyoMod), List.copyOf(AnnotationScanner.getMyoModActiveAnnotations()));
        assertEquals(List.of(loadedTarget), List.copyOf(AnnotationScanner.getActiveIntegrationAnnotations()));

        ModIntegrationManager.setModList(modList("missing"));
        assertEquals(List.of(missingMyoMod), List.copyOf(AnnotationScanner.getMyoModActiveAnnotations()));
        assertEquals(List.of(missingTarget), List.copyOf(AnnotationScanner.getActiveIntegrationAnnotations()));
    }

    private static ScannedAnnotation myoModAnnotation(Class<?> annotationClass) {
        return new ScannedAnnotation(Type.getType(MyoMod.class), ElementType.ANNOTATION_TYPE, Type.getType(annotationClass));
    }

    private static ScannedAnnotation scanned(Class<?> annotationClass, Class<?> targetClass) {
        return new ScannedAnnotation(Type.getType(annotationClass), ElementType.TYPE, Type.getType(targetClass));
    }

    private static IModList modList(String loadedModId) {
        return new IModList() {
            @Override
            public boolean isLoaded(String modId) {
                return loadedModId.equals(modId);
            }

            @Override
            public MyoModInfo getModInfoById(String modId) {
                if (!isLoaded(modId)) {
                    return null;
                }
                return new MyoModInfo(modId, modId, modId, new DefaultArtifactVersion("1.0.0"));
            }
        };
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface FirstAnnotation {
    }

    @FirstAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExtendedFirstAnnotation {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface SecondAnnotation {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod("loaded")
    private @interface LoadedIntegration {
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @MyoMod("missing")
    private @interface MissingIntegration {
    }

    private static final class ActiveTarget {
    }

    private static final class PartiallyActiveTarget {
    }
}
