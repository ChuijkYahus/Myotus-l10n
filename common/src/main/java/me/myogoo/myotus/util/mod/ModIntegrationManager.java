package me.myogoo.myotus.util.mod;

import me.myogoo.myotus.api.annotation.MyoMod;
import me.myogoo.myotus.api.annotation.MyoMod.IntegrationMode;
import me.myogoo.myotus.api.integration.MyoCustomCondition;
import me.myogoo.myotus.dto.MyoModDto;
import me.myogoo.myotus.dto.MyoModInfo;
import me.myogoo.myotus.platform.mod.IModList;
import me.myogoo.myotus.util.MyoLogger;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner;
import me.myogoo.myotus.util.reflect.annotation.AnnotationTypes;
import me.myogoo.myotus.util.reflect.SafeClass;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModIntegrationManager {
    private static volatile State state = State.empty();

    private ModIntegrationManager() {
    }

    public static void setModList(IModList modList) {
        IModList nextModList = Objects.requireNonNull(modList, "modList");
        try {
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations =
                    registerMyoModAnnotations();
            Map<MyoModDto, Class<? extends Annotation>> activeIntegrations =
                    rebuildActiveIntegrations(nextModList, registeredIntegrations);
            state = new State(nextModList, registeredIntegrations, activeIntegrations);
        } finally {
            AnnotationScanner.invalidateIntegrationCaches();
        }
    }

    public static MyoModDto get(String id) {
        State snapshot = state;
        return snapshot.activeIntegrations().keySet().stream()
                .filter(mod -> matches(mod, id))
                .findFirst()
                .orElse(null);
    }

    public static Class<? extends Annotation> getClass(MyoModDto mod) {
        State snapshot = state;
        return snapshot.activeIntegrations().entrySet().stream()
                .filter(entry -> entry.getKey().isSameRegistration(mod))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public static boolean isLoaded(Class<? extends Annotation> annotationClass) {
        State snapshot = state;
        return isLoaded(snapshot, annotationClass);
    }

    public static boolean isLoaded(MyoModDto mod) {
        State snapshot = state;
        return snapshot.activeIntegrations().keySet().stream()
                .anyMatch(active -> active.isSameRegistration(mod));
    }

    public static boolean isLoaded(Type annotationType) {
        State snapshot = state;
        Class<?> annotationClass = SafeClass.forType(annotationType);
        if (annotationClass == null || !annotationClass.isAnnotation()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> typedAnnotationClass = (Class<? extends Annotation>) annotationClass;
        return isLoaded(snapshot, typedAnnotationClass);
    }

    public static boolean isLoaded(String id) {
        State snapshot = state;
        return snapshot.activeIntegrations().keySet().stream()
                .anyMatch(mod -> matches(mod, id));
    }

    public static boolean isRegistered(String id) {
        State snapshot = state;
        return snapshot.registeredIntegrations().values().stream()
                .anyMatch(mod -> mod.matches(id));
    }

    public static boolean isRegistered(Class<? extends Annotation> annotationClass) {
        State snapshot = state;
        return snapshot.registeredIntegrations().keySet().stream()
                .anyMatch(registered -> AnnotationTypes.matches(annotationClass, registered)
                        || AnnotationTypes.matches(registered, annotationClass));
    }

    public static Class<? extends Annotation> getClass(String id) {
        State snapshot = state;
        var active = snapshot.activeIntegrations().entrySet().stream()
                .filter(entry -> matches(entry.getKey(), id))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        if (active.size() == 1) {
            return active.get(0);
        }
        if (!active.isEmpty()) {
            return null;
        }

        var registered = snapshot.registeredIntegrations().values().stream()
                .filter(mod -> mod.matches(id))
                .map(MyoModRegistration::annotationClass)
                .distinct()
                .toList();
        return registered.size() == 1 ? registered.get(0) : null;
    }

    public static Map<MyoModDto, Class<? extends Annotation>> getActiveIntegrations() {
        State snapshot = state;
        return snapshot.activeIntegrations();
    }

    public static List<RegisteredIntegration> getRegisteredIntegrations() {
        State snapshot = state;
        return snapshot.registeredIntegrations().values().stream()
                .map(registration -> new RegisteredIntegration(
                        registration.modId(),
                        registration.annotationClass(),
                        registration.aliases(),
                        registration.versionRange(),
                        registration.mode(),
                        isLoaded(snapshot, registration.annotationClass())))
                .toList();
    }

    private static boolean isLoaded(State snapshot, Class<? extends Annotation> annotationClass) {
        return snapshot.activeIntegrations().values().stream()
                .anyMatch(active -> AnnotationTypes.matches(annotationClass, active)
                        || AnnotationTypes.matches(active, annotationClass));
    }

    private static Map<Class<? extends Annotation>, MyoModRegistration> registerMyoModAnnotations() {
        Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations = new LinkedHashMap<>();
        for (AnnotationScanner.ScannedAnnotation annotation : AnnotationScanner.getMyoModAnnotations()) {
            Class<?> annotationClass = SafeClass.forType(annotation.clazz());
            if (annotationClass == null || !annotationClass.isAnnotation()) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Annotation> typedAnnotationClass = (Class<? extends Annotation>) annotationClass;
            if (registeredIntegrations.containsKey(typedAnnotationClass)) {
                continue;
            }

            MyoMod myoMod = typedAnnotationClass.getAnnotation(MyoMod.class);
            if (myoMod == null) {
                continue;
            }
            registeredIntegrations.put(typedAnnotationClass,
                    MyoModRegistration.fromAnnotation(typedAnnotationClass, myoMod));
        }
        validateAliases(registeredIntegrations);
        return registeredIntegrations;
    }

    private static Map<MyoModDto, Class<? extends Annotation>> rebuildActiveIntegrations(
            IModList modList,
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations) {
        Map<MyoModDto, Class<? extends Annotation>> activeIntegrations = new LinkedHashMap<>();
        Map<String, Set<String>> aliasesByModId = aliasesByModId(registeredIntegrations);
        Map<String, String> versionRangesByModId = versionRangesByModId(modList, registeredIntegrations);
        Map<String, List<MyoModDto>> activeByGroup = new LinkedHashMap<>();
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            MyoModDto mod = finalizeRegistration(
                    modList,
                    registration,
                    aliasesForActiveRegistration(registration, aliasesByModId),
                    versionRangeForActiveRegistration(registration, versionRangesByModId));
            if (mod != null) {
                activeByGroup.computeIfAbsent(activationGroupKey(registration), ignored -> new ArrayList<>()).add(mod);
            }
        }

        for (List<MyoModDto> mods : activeByGroup.values()) {
            List<MyoModDto> overrides = mods.stream()
                    .filter(mod -> mod.getMode() == IntegrationMode.OVERRIDE)
                    .toList();
            if (!overrides.isEmpty()) {
                overrides.forEach(mod -> activate(activeIntegrations, mod));
                continue;
            }

            mods.forEach(mod -> activate(activeIntegrations, mod));
        }
        return activeIntegrations;
    }

    private static String activationGroupKey(MyoModRegistration registration) {
        if (registration.hasCustomCondition() && registration.mode() != IntegrationMode.EXTENDED) {
            return registration.annotationClass().getName();
        }
        return registration.modId();
    }

    private static void activate(Map<MyoModDto, Class<? extends Annotation>> activeIntegrations, MyoModDto mod) {
        activeIntegrations.put(mod, mod.getAnnotationClass());
    }

    private static MyoModDto finalizeRegistration(IModList modList, MyoModRegistration registration,
            Set<String> sharedAliases, String sharedVersionRange) {
        if (!modList.isLoaded(registration.modId())) {
            return null;
        }

        MyoModInfo modInfo = modList.getModInfoById(registration.modId());
        if (modInfo == null) {
            return null;
        }
        if (!testCustomCondition(registration, modInfo)) {
            return null;
        }
        if (!ModVersionHelper.isVersionInRange(sharedVersionRange, modInfo.version())) {
            throw new MyoModVersionMismatchException(modInfo, sharedVersionRange);
        }

        return new MyoModDto(registration.annotationClass(), modInfo, sharedAliases,
                sharedVersionRange, registration.mode());
    }

    private static Set<String> aliasesForActiveRegistration(MyoModRegistration registration,
            Map<String, Set<String>> aliasesByModId) {
        if (registration.hasCustomCondition()) {
            return registration.aliases();
        }
        return aliasesByModId.getOrDefault(registration.modId(), Set.of());
    }

    private static String versionRangeForActiveRegistration(MyoModRegistration registration,
            Map<String, String> versionRangesByModId) {
        if (registration.hasCustomCondition()) {
            return registration.versionRange();
        }
        return versionRangesByModId.getOrDefault(registration.modId(), registration.versionRange());
    }

    private static Map<String, Set<String>> aliasesByModId(
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations) {
        Map<String, Set<String>> aliasesByModId = new LinkedHashMap<>();
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            if (registration.hasCustomCondition()) {
                continue;
            }
            aliasesByModId.computeIfAbsent(registration.modId(), ignored -> new LinkedHashSet<>())
                    .addAll(registration.aliases());
        }
        return aliasesByModId;
    }

    private static Map<String, String> versionRangesByModId(
            IModList modList,
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations) {
        Map<String, List<String>> rangesByModId = new LinkedHashMap<>();
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            if (registration.hasCustomCondition() || !modList.isLoaded(registration.modId())) {
                continue;
            }
            rangesByModId.computeIfAbsent(registration.modId(), ignored -> new ArrayList<>())
                    .add(registration.versionRange());
        }

        Map<String, String> mergedRangesByModId = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : rangesByModId.entrySet()) {
            try {
                mergedRangesByModId.put(entry.getKey(), ModVersionHelper.intersectVersionRanges(entry.getValue()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "MyoMod integrations for '%s' declare incompatible version ranges: %s"
                                .formatted(entry.getKey(), entry.getValue()),
                        e);
            }
        }
        return mergedRangesByModId;
    }

    private static void validateAliases(
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations) {
        Map<String, String> aliasOwners = new HashMap<>();
        Map<String, Class<? extends Annotation>> aliasOwnerClasses = new HashMap<>();
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            for (String alias : registration.aliases()) {
                String previousModId = aliasOwners.putIfAbsent(alias, registration.modId());
                if (previousModId != null && !previousModId.equals(registration.modId())) {
                    throw new IllegalStateException(
                            "MyoMod alias '%s' is used for both '%s' (%s) and '%s' (%s)".formatted(
                                    alias,
                                    previousModId,
                                    aliasOwnerClasses.get(alias).getName(),
                                    registration.modId(),
                                    registration.annotationClass().getName()));
                }
                aliasOwnerClasses.putIfAbsent(alias, registration.annotationClass());
            }
        }

        Map<String, Class<? extends Annotation>> modIdOwners = new HashMap<>();
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            modIdOwners.putIfAbsent(registration.modId(), registration.annotationClass());
        }
        for (MyoModRegistration registration : registeredIntegrations.values()) {
            for (String alias : registration.aliases()) {
                Class<? extends Annotation> aliasedModIdOwner = modIdOwners.get(alias);
                if (aliasedModIdOwner != null && !alias.equals(registration.modId())) {
                    throw new IllegalStateException(
                            "MyoMod alias '%s' on '%s' (%s) collides with mod id '%s' declared by %s".formatted(
                                    alias,
                                    registration.modId(),
                                    registration.annotationClass().getName(),
                                    alias,
                                    aliasedModIdOwner.getName()));
                }
            }
        }
    }

    private static boolean testCustomCondition(MyoModRegistration registration, MyoModInfo modInfo) {
        Class<? extends MyoCustomCondition> conditionClass = registration.customConditionClass();
        if (conditionClass == MyoCustomCondition.class) {
            return true;
        }

        try {
            var constructor = conditionClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance().test(modInfo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MyoLogger.warn("Failed to evaluate MyoMod custom condition {} for {}",
                    conditionClass.getName(), registration.describe(), e);
            return false;
        }
    }

    private static boolean matches(MyoModDto mod, String id) {
        return mod.matches(id);
    }

    private record State(
            IModList modList,
            Map<Class<? extends Annotation>, MyoModRegistration> registeredIntegrations,
            Map<MyoModDto, Class<? extends Annotation>> activeIntegrations) {

        private State {
            modList = Objects.requireNonNull(modList, "modList");
            registeredIntegrations = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(registeredIntegrations, "registeredIntegrations")));
            activeIntegrations = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(activeIntegrations, "activeIntegrations")));
        }

        private static State empty() {
            return new State(IModList.EMPTY, Map.of(), Map.of());
        }
    }

    public record RegisteredIntegration(
            String modId,
            Class<? extends Annotation> annotationClass,
            Set<String> aliases,
            String versionRange,
            IntegrationMode mode,
            boolean active) {

        public RegisteredIntegration {
            aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
            versionRange = versionRange == null || versionRange.isBlank() ? "*" : versionRange;
            mode = mode == null ? IntegrationMode.DEFAULT : mode;
        }
    }

    private record MyoModRegistration(
            String modId,
            Class<? extends Annotation> annotationClass,
            Set<String> aliases,
            String versionRange,
            IntegrationMode mode,
            Class<? extends MyoCustomCondition> customConditionClass) {

        private MyoModRegistration {
            aliases = normalizeAliases(aliases);
            versionRange = versionRange == null || versionRange.isBlank() ? "*" : versionRange;
            mode = mode == null ? IntegrationMode.DEFAULT : mode;
            customConditionClass = customConditionClass == null ? MyoCustomCondition.class : customConditionClass;
        }

        static MyoModRegistration fromAnnotation(Class<? extends Annotation> annotationClass, MyoMod myoMod) {
            return new MyoModRegistration(
                    myoMod.value(),
                    annotationClass,
                    Set.of(myoMod.alias()),
                    myoMod.versionRange(),
                    myoMod.mode(),
                    myoMod.customCondition());
        }

        boolean hasCustomCondition() {
            return customConditionClass != MyoCustomCondition.class;
        }

        boolean matches(String id) {
            return id != null && (id.equals(modId) || aliases.contains(id));
        }

        String describe() {
            return "%s %s".formatted(modId, versionRange);
        }

        private static Set<String> normalizeAliases(Set<String> aliases) {
            if (aliases == null || aliases.isEmpty()) {
                return Set.of();
            }

            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    normalized.add(alias);
                }
            }
            return Set.copyOf(normalized);
        }
    }
}
