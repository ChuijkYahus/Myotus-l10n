package me.myogoo.myotus.util.reflect.annotation;

import me.myogoo.myotus.api.annotation.MyoMod;
import me.myogoo.myotus.api.annotation.itemList.emi.EMI;
import me.myogoo.myotus.api.annotation.itemList.jei.JEI;
import me.myogoo.myotus.api.annotation.itemList.rei.REI;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import me.myogoo.myotus.util.reflect.SafeClass;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AnnotationScanner {
    private static final Type MYO_MOD_TYPE = Type.getType(MyoMod.class);
    private static final List<Type> ITEM_LIST_MARKERS = List.of(
            Type.getType(JEI.class),
            Type.getType(EMI.class),
            Type.getType(REI.class));

    private static volatile Supplier<Stream<ScannedAnnotation>> annotationProvider = Stream::empty;
    private static volatile Set<ScannedAnnotation> cachedAnnotations;
    private static volatile Set<ScannedAnnotation> myoModAnnotations;
    private static volatile Set<ScannedAnnotation> myoModActiveAnnotations;
    private static volatile Set<ScannedAnnotation> itemListAnnotations;
    private static volatile Set<ScannedAnnotation> activeIntegrationAnnotations;

    private AnnotationScanner() {
    }

    public static synchronized void setAnnotationProvider(Supplier<Stream<ScannedAnnotation>> provider) {
        annotationProvider = Objects.requireNonNull(provider, "provider");
        clearCache();
    }

    public static synchronized Set<ScannedAnnotation> getAnnotations() {
        Set<ScannedAnnotation> annotations = cachedAnnotations;
        if (annotations == null) {
            annotations = immutableSet(annotationProvider.get());
            cachedAnnotations = annotations;
        }
        return annotations;
    }

    public static synchronized Set<ScannedAnnotation> getMyoModAnnotations() {
        Set<ScannedAnnotation> annotations = myoModAnnotations;
        if (annotations == null) {
            annotations = immutableSet(getAnnotations().stream()
                    .filter(annotation -> annotation.annotationType().equals(MYO_MOD_TYPE))
                    .filter(annotation -> {
                        Class<?> annotationClass = SafeClass.forType(annotation.clazz());
                        return annotationClass != null && annotationClass.isAnnotation();
                    }));
            myoModAnnotations = annotations;
        }
        return annotations;
    }

    public static synchronized Set<ScannedAnnotation> getMyoModActiveAnnotations() {
        Set<ScannedAnnotation> annotations = myoModActiveAnnotations;
        if (annotations == null) {
            annotations = immutableSet(getMyoModAnnotations().stream()
                    .filter(annotation -> {
                        Class<? extends Annotation> annotationClass = annotationClass(annotation.clazz());
                        return annotationClass != null && ModIntegrationManager.isLoaded(annotationClass);
                    }));
            myoModActiveAnnotations = annotations;
        }
        return annotations;
    }

    public static synchronized Set<ScannedAnnotation> getActiveIntegrationAnnotations() {
        Set<ScannedAnnotation> annotations = activeIntegrationAnnotations;
        if (annotations == null) {
            annotations = immutableSet(getAnnotations().stream()
                    .filter(annotation -> annotation.targetType() == ElementType.TYPE)
                    .filter(annotation -> isIntegrationAnnotation(annotation.annotationType()))
                    .filter(annotation -> ModIntegrationManager.isLoaded(annotation.annotationType())));
            activeIntegrationAnnotations = annotations;
        }
        return annotations;
    }

    public static Set<ScannedAnnotation> getModAnnotations() {
        return getActiveIntegrationAnnotations();
    }

    public static synchronized Set<ScannedAnnotation> getItemListAnnotations() {
        Set<ScannedAnnotation> annotations = itemListAnnotations;
        if (annotations == null) {
            annotations = immutableSet(getAnnotations().stream()
                    .filter(annotation -> ITEM_LIST_MARKERS.stream()
                            .anyMatch(marker -> AnnotationTypes.matches(annotation.annotationType(), marker))));
            itemListAnnotations = annotations;
        }
        return annotations;
    }

    public static Set<ScannedAnnotation> find(Class<? extends Annotation> annotationClass) {
        return find(Type.getType(annotationClass));
    }

    public static Set<ScannedAnnotation> find(Type annotationType) {
        return immutableSet(getAnnotations().stream()
                .filter(annotation -> AnnotationTypes.matches(annotation.annotationType(), annotationType)));
    }

    public static Set<ScannedAnnotation> findActive(Class<? extends Annotation> annotationClass) {
        return findActive(Type.getType(annotationClass));
    }

    public static Set<ScannedAnnotation> findActive(Type annotationType) {
        return immutableSet(find(annotationType).stream()
                .filter(AnnotationScanner::isTargetActive));
    }

    static synchronized void clearCache() {
        cachedAnnotations = null;
        myoModAnnotations = null;
        myoModActiveAnnotations = null;
        itemListAnnotations = null;
        activeIntegrationAnnotations = null;
    }

    /**
     * Clears only scan results whose contents depend on the currently active mod integrations.
     */
    public static synchronized void invalidateIntegrationCaches() {
        myoModActiveAnnotations = null;
        activeIntegrationAnnotations = null;
    }

    private static boolean isTargetActive(ScannedAnnotation annotation) {
        List<ScannedAnnotation> integrationAnnotations = getAnnotations().stream()
                .filter(candidate -> candidate.targetType() == ElementType.TYPE)
                .filter(candidate -> candidate.clazz().equals(annotation.clazz()))
                .filter(candidate -> isIntegrationAnnotation(candidate.annotationType()))
                .toList();

        return integrationAnnotations.isEmpty()
                || integrationAnnotations.stream()
                        .allMatch(candidate -> ModIntegrationManager.isLoaded(candidate.annotationType()));
    }

    private static boolean isIntegrationAnnotation(Type annotationType) {
        Class<? extends Annotation> annotationClass = annotationClass(annotationType);
        return annotationClass != null
                && (annotationClass.isAnnotationPresent(MyoMod.class)
                        || ModIntegrationManager.isRegistered(annotationClass));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> annotationClass(Type type) {
        Class<?> clazz = SafeClass.forType(type);
        if (clazz == null || !clazz.isAnnotation()) {
            return null;
        }
        return (Class<? extends Annotation>) clazz;
    }

    private static Set<ScannedAnnotation> immutableSet(Stream<ScannedAnnotation> annotations) {
        LinkedHashSet<ScannedAnnotation> result = annotations.collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(result);
    }

    public record ScannedAnnotation(Type annotationType, ElementType targetType, Type clazz) {
        public ScannedAnnotation(Type annotationType, Type clazz) {
            this(annotationType, ElementType.TYPE, clazz);
        }

        public Type targetClass() {
            return clazz;
        }

        public String className() {
            return clazz.getClassName();
        }
    }
}
