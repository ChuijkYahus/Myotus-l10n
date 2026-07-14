package me.myogoo.myotus.api;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.blaze3d.platform.InputConstants;
import me.myogoo.myotus.dto.MyoModDto;
import me.myogoo.myotus.api.command.argument.MyoArgumentAdapter;
import me.myogoo.myotus.api.experience.ExperienceMath;
import me.myogoo.myotus.api.experience.ExperienceStorageAdapter;
import me.myogoo.myotus.api.registrar.IConfigRegistrar;
import me.myogoo.myotus.api.registrar.ICreativeTabRegistrar;
import me.myogoo.myotus.client.gui.widgets.KeyBindingButton;
import me.myogoo.myotus.command.MyoCommandRegistrar;
import me.myogoo.myotus.menu.TerminalUpgradeHelper;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Static entry point for the Myotus public API.
 *
 * <p>Use this class when you need quick access to the shared API services without
 * storing the {@link IMyotusAPI} instance yourself.</p>
 *
 * <p>Optional integration markers are discovered from annotations such as
 * {@code @MyoMod}; this API exposes the runtime services used by integration
 * code after it is active.</p>
 */
public final class MyotusAPI {
    private static final IntegrationsApi INTEGRATIONS = IntegrationsApi.INSTANCE;
    private static final TerminalUpgradesApi TERMINAL_UPGRADES = TerminalUpgradesApi.INSTANCE;
    private static final ExperienceApi EXPERIENCE = ExperienceApi.INSTANCE;
    private static final CommandsApi COMMANDS = CommandsApi.INSTANCE;

    private static IMyotusAPI instance;

    private MyotusAPI() {
    }

    /**
     * Returns the shared Myotus API implementation.
     *
     * <p>This method throws if the API has not been initialized yet. External
     * integrations should normally call it during or after mod setup, not from
     * static initializers.</p>
     *
     * @return the shared API instance
     */
    public static IMyotusAPI get() {
        return Objects.requireNonNull(instance, "MyotusAPI has not been initialized yet!");
    }

    /** Returns the shared API when initialization has completed. */
    public static Optional<IMyotusAPI> tryGet() {
        return Optional.ofNullable(instance);
    }

    /**
     * Returns whether the API instance is ready to use.
     *
     * @return {@code true} if {@link #get()} can be called safely
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Returns the registrar for terminal configuration tabs.
     *
     * @return the config tab registrar
     */
    public static IConfigRegistrar configTabs() {
        return get().configTabs();
    }

    /**
     * Returns the registrar for entries in the shared Myotus creative tab.
     *
     * @return the creative tab registrar
     */
    public static ICreativeTabRegistrar creativeTabs() {
        return get().creativeTabs();
    }

    /**
     * Returns the API for Myotus-managed optional integrations.
     *
     * @return the integration API
     */
    public static IntegrationsApi integrations() {
        return INTEGRATIONS;
    }

    /**
     * Returns the API for terminal upgrade slot helpers.
     *
     * @return the terminal upgrade API
     */
    public static TerminalUpgradesApi terminalUpgrades() {
        return TERMINAL_UPGRADES;
    }

    /**
     * Returns helpers for Applied Experienced / {@code fluid:xp} calculations.
     *
     * @return the experience calculation API
     */
    public static ExperienceApi experience() {
        return EXPERIENCE;
    }

    /** Returns the API for extending annotation-driven command arguments. */
    public static CommandsApi commands() {
        return COMMANDS;
    }

    public static final class CommandsApi {
        private static final CommandsApi INSTANCE = new CommandsApi();

        private CommandsApi() {
        }

        /** Registers the Brigadier adapter used for parameters of exactly {@code valueType}. */
        public <T> void registerArgument(Class<T> valueType, MyoArgumentAdapter<? extends T> adapter) {
            MyoCommandRegistrar.registerAdapter(
                    Objects.requireNonNull(valueType, "valueType"),
                    Objects.requireNonNull(adapter, "adapter"));
        }
    }

    /**
     * Public API for Myotus-managed optional integrations.
     */
    public static final class IntegrationsApi {
        private static final IntegrationsApi INSTANCE = new IntegrationsApi();

        private IntegrationsApi() {
        }

        /** Returns the active integration matching {@code id}, if one is available. */
        public Optional<MyoModDto> find(String id) {
            return Optional.ofNullable(ModIntegrationManager.get(id));
        }

        /** Returns the unambiguous annotation registered for {@code id}, if one exists. */
        public Optional<Class<? extends Annotation>> findAnnotation(String id) {
            return Optional.ofNullable(ModIntegrationManager.getClass(id));
        }

        /** Returns the annotation class that activated {@code integration}, if it is still active. */
        public Optional<Class<? extends Annotation>> findAnnotation(MyoModDto integration) {
            return Optional.ofNullable(ModIntegrationManager.getClass(integration));
        }

        public boolean isLoaded(String id) {
            return ModIntegrationManager.isLoaded(id);
        }

        public boolean isLoaded(Class<? extends Annotation> annotationClass) {
            return ModIntegrationManager.isLoaded(annotationClass);
        }

        public boolean isLoaded(Type annotationType) {
            return ModIntegrationManager.isLoaded(annotationType);
        }

        public boolean isLoaded(MyoModDto mod) {
            return ModIntegrationManager.isLoaded(mod);
        }

        public boolean isRegistered(String id) {
            return ModIntegrationManager.isRegistered(id);
        }

        public boolean isRegistered(Class<? extends Annotation> annotationClass) {
            return ModIntegrationManager.isRegistered(annotationClass);
        }

        public Map<MyoModDto, Class<? extends Annotation>> activeIntegrations() {
            return ModIntegrationManager.getActiveIntegrations();
        }
    }

    /**
     * Public API for terminal upgrade slot helpers.
     *
     * <p>Prefer this entry point over importing Myotus internal menu classes.</p>
     */
    public static final class TerminalUpgradesApi {
        private static final TerminalUpgradesApi INSTANCE = new TerminalUpgradesApi();

        private TerminalUpgradesApi() {
        }

        public List<ItemStack> installedUpgrades(MEStorageMenu menu) {
            return TerminalUpgradeHelper.getInstalledUpgrades(menu);
        }

        public Set<Item> installedUpgradeItems(MEStorageMenu menu) {
            return TerminalUpgradeHelper.getInstalledUpgradeItems(menu);
        }

        public List<ItemStack> availableUpgradeCards(MEStorageMenu menu) {
            return TerminalUpgradeHelper.getAvailableUpgradeCards(menu);
        }

        public List<Component> availableUpgradeTooltip(MEStorageMenu menu) {
            return TerminalUpgradeHelper.getAvailableUpgradeTooltip(menu);
        }

        public boolean hasUpgrade(MEStorageMenu menu, Item upgradeItem) {
            return TerminalUpgradeHelper.hasUpgrade(menu, upgradeItem);
        }

        public boolean hasUpgrade(MEStorageMenu menu, ResourceLocation itemId) {
            return TerminalUpgradeHelper.hasUpgrade(menu, itemId);
        }

        public int countUpgrade(MEStorageMenu menu, Item upgradeItem) {
            return TerminalUpgradeHelper.countUpgrade(menu, upgradeItem);
        }

        public int countUpgrade(MEStorageMenu menu, ResourceLocation itemId) {
            return TerminalUpgradeHelper.countUpgrade(menu, itemId);
        }

        public boolean canInsertUpgrade(Iterable<ItemStack> installedStacks, int slot, ItemStack stack) {
            return TerminalUpgradeHelper.canInsertUpgrade(installedStacks, slot, stack);
        }
    }

    /**
     * Public API for raw vanilla experience calculations and stable experience integration ids.
     */
    public static final class ExperienceApi {
        private static final ExperienceApi INSTANCE = new ExperienceApi();
        private static final ExperienceStorageAdapter APPLIED_EXPERIENCED_STORAGE = new ExperienceStorageAdapter(
                ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT,
                ExperienceApi::isAppliedExperiencedKey,
                1);

        private ExperienceApi() {
        }

        public String appliedModId() {
            return ExperienceMath.APPLIED_EXPERIENCED_MOD_ID;
        }

        public String appliedAeKeyId() {
            return ExperienceMath.APPLIED_EXPERIENCED_AE_KEY_ID;
        }

        public String appliedAmountComponentId() {
            return ExperienceMath.APPLIED_EXPERIENCED_AMOUNT_COMPONENT_ID;
        }

        public String fluidXpId() {
            return ExperienceMath.FLUID_XP_ID;
        }

        /** Returns the built-in raw-XP adapter for Applied Experienced's custom AE key. */
        public ExperienceStorageAdapter appliedExperiencedStorage() {
            return APPLIED_EXPERIENCED_STORAGE;
        }

        /**
         * Creates an explicit fluid adapter. The conversion must come from the owning fluid integration's
         * configuration; experience fluid tags do not define a universal conversion rate.
         */
        public ExperienceStorageAdapter fluidStorage(Predicate<AEKey> matcher, long storageUnitsPerExperience) {
            Objects.requireNonNull(matcher, "matcher");
            return new ExperienceStorageAdapter(ExperienceMath.ExperienceSource.FLUID_XP,
                    key -> key instanceof AEFluidKey && matcher.test(key), storageUnitsPerExperience);
        }

        /** Creates a fluid adapter for Myotus' recognized XP-fluid tags using an explicit conversion. */
        public ExperienceStorageAdapter taggedFluidStorage(long storageUnitsPerExperience) {
            return fluidStorage(this::isXpFluid, storageUnitsPerExperience);
        }

        public List<ExperienceMath.ExperienceSource> anvilSourcePriority(long fluidXp) {
            return ExperienceMath.anvilSourcePriority(fluidXp);
        }

        public List<ExperienceMath.ExperienceSource> anvilSourcePriority(boolean includeFluidXp) {
            return ExperienceMath.anvilSourcePriority(includeFluidXp);
        }

        public List<ExperienceMath.ExperienceSource> defaultAnvilSourcePriority() {
            return ExperienceMath.DEFAULT_ANVIL_SOURCE_PRIORITY;
        }

        public long total(long appliedExperience, long fluidXp) {
            return ExperienceMath.totalExperience(appliedExperience, fluidXp);
        }

        public long total(long playerExperience, long fluidXp, long appliedExperienceAmount) {
            return ExperienceMath.totalExperience(playerExperience, fluidXp, appliedExperienceAmount);
        }

        /** Creates a pure, non-mutating payment plan from named raw-XP balances. */
        public ExperienceMath.MyoExperience plan(long requiredExperience,
                ExperienceMath.ExperienceAmounts availableAmounts,
                List<ExperienceMath.ExperienceSource> sourcePriority) {
            return ExperienceMath.planConsumption(requiredExperience, availableAmounts, sourcePriority);
        }

        /** Creates a pure, non-mutating payment plan using the default source priority. */
        public ExperienceMath.MyoExperience plan(long requiredExperience,
                ExperienceMath.ExperienceAmounts availableAmounts) {
            return plan(requiredExperience, availableAmounts, ExperienceMath.DEFAULT_ANVIL_SOURCE_PRIORITY);
        }

        public long totalForLevel(int level) {
            return ExperienceMath.totalExperienceForLevel(level);
        }

        public int levelForTotal(long total) {
            return ExperienceMath.levelForTotalExperience(total);
        }

        public long intoLevel(long total) {
            return ExperienceMath.experienceIntoLevel(total);
        }

        public long toNextLevel(int level) {
            return ExperienceMath.experienceToNextLevel(level);
        }

        public long vanillaAnvilCost(int currentLevel, int levelCost) {
            return ExperienceMath.vanillaAnvilExperienceCost(currentLevel, levelCost);
        }

        public long vanillaAnvilCost(int currentLevel, int levelCost, float experienceProgress) {
            return ExperienceMath.vanillaAnvilExperienceCost(currentLevel, levelCost, experienceProgress);
        }

        public long vanillaAnvilCost(Player player, int levelCost) {
            Objects.requireNonNull(player, "player");
            return vanillaAnvilCost(player.experienceLevel, levelCost, player.experienceProgress);
        }

        public long apothicAnvilCost(int levelCost) {
            return ExperienceMath.apothicAnvilExperienceCost(levelCost);
        }

        public long apothicEnchantingTableCost(int level, int slot) {
            return ExperienceMath.apothicEnchantingTableExperienceCost(level, slot);
        }

        public long apothicLibraryPointsForLevel(int level) {
            return ExperienceMath.apothicLibraryPointsForLevel(level);
        }

        public boolean hasNetworkSource(MEStorage storage, ExperienceMath.ExperienceSource source) {
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return adapter != null && hasNetworkSource(storage, adapter);
        }

        /** Returns whether a positive, complete XP unit is stored for an explicit adapter. */
        public boolean hasNetworkSource(MEStorage storage, ExperienceStorageAdapter adapter) {
            Objects.requireNonNull(adapter, "adapter");
            if (storage == null) {
                return false;
            }

            return stored(storage, adapter) > 0;
        }

        /**
         * Returns selectable/usable anvil XP source priority for the selected source.
         *
         * <p>Network-backed sources are included only when the current ME storage, energy source, and
         * action source can actually extract a positive amount. The player source is always available and
         * is placed first only when explicitly selected.</p>
         */
        public List<ExperienceMath.ExperienceSource> availableAnvilSourcePriority(IEnergySource energySource,
                MEStorage storage, IActionSource actionSource, ExperienceMath.ExperienceSource selectedSource) {
            return availableAnvilSourcePriority(energySource, storage, actionSource, selectedSource,
                    defaultStorageAdapters());
        }

        /**
         * Returns the usable priority with explicitly configured network adapters. A fluid source is only
         * considered when its conversion adapter is included.
         */
        public List<ExperienceMath.ExperienceSource> availableAnvilSourcePriority(IEnergySource energySource,
                MEStorage storage, IActionSource actionSource, ExperienceMath.ExperienceSource selectedSource,
                List<ExperienceStorageAdapter> storageAdapters) {
            Objects.requireNonNull(selectedSource, "selectedSource");
            Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters = adaptersBySource(storageAdapters);
            validateDisjointStorageAdapters(storage, adapters);

            var priority = new ArrayList<ExperienceMath.ExperienceSource>();
            var simulationEnergy = new CumulativeSimulatingEnergySource(energySource);
            if (selectedSource == ExperienceMath.ExperienceSource.PLAYER) {
                priority.add(ExperienceMath.ExperienceSource.PLAYER);
                addExtractableNetworkSource(priority, simulationEnergy, storage, actionSource,
                        adapters.get(ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT));
                addExtractableNetworkSource(priority, simulationEnergy, storage, actionSource,
                        adapters.get(ExperienceMath.ExperienceSource.FLUID_XP));
            } else {
                addExtractableNetworkSource(priority, simulationEnergy, storage, actionSource,
                        adapters.get(selectedSource));
                addExtractableNetworkSource(priority, simulationEnergy, storage, actionSource,
                        adapters.get(selectedSource == ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT
                                ? ExperienceMath.ExperienceSource.FLUID_XP
                                : ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT));
                priority.add(ExperienceMath.ExperienceSource.PLAYER);
            }
            return List.copyOf(priority);
        }

        public long playerRaw(Player player) {
            Objects.requireNonNull(player, "player");
            int needed = player.getXpNeededForNextLevel();
            int pointsIntoLevel = Mth.clamp(Math.round(player.experienceProgress * needed), 0,
                    Math.max(0, needed - 1));
            return Math.addExact(ExperienceMath.totalExperienceForLevel(player.experienceLevel), pointsIntoLevel);
        }

        public ExperienceMath.MyoExperience planPayment(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, Player player, long requiredExperience,
                List<ExperienceMath.ExperienceSource> sourcePriority) {
            return planPayment(energySource, storage, actionSource, playerRaw(player),
                    requiredExperience, sourcePriority);
        }

        public ExperienceMath.MyoExperience planPayment(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long playerExperience, long requiredExperience,
                List<ExperienceMath.ExperienceSource> sourcePriority) {
            return planPayment(energySource, storage, actionSource, playerExperience, requiredExperience,
                    sourcePriority, defaultStorageAdapters());
        }

        public ExperienceMath.MyoExperience planPayment(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, Player player, long requiredExperience,
                List<ExperienceMath.ExperienceSource> sourcePriority,
                List<ExperienceStorageAdapter> storageAdapters) {
            return planPayment(energySource, storage, actionSource, playerRaw(player), requiredExperience,
                    sourcePriority, storageAdapters);
        }

        public ExperienceMath.MyoExperience planPayment(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long playerExperience, long requiredExperience,
                List<ExperienceMath.ExperienceSource> sourcePriority,
                List<ExperienceStorageAdapter> storageAdapters) {
            Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters = adaptersBySource(storageAdapters);
            validateDisjointStorageAdapters(storage, adapters);
            var networkAmounts = extractableByPriority(energySource, storage, actionSource, sourcePriority, adapters);
            return ExperienceMath.planConsumption(requiredExperience,
                    new ExperienceMath.ExperienceAmounts(
                            playerExperience,
                            networkAmounts.get(ExperienceMath.ExperienceSource.FLUID_XP),
                            networkAmounts.get(ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT)),
                    sourcePriority);
        }

        private Map<ExperienceMath.ExperienceSource, Long> extractableByPriority(IEnergySource energySource,
                MEStorage storage, IActionSource actionSource, List<ExperienceMath.ExperienceSource> sourcePriority,
                Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters) {
            var amounts = new EnumMap<ExperienceMath.ExperienceSource, Long>(ExperienceMath.ExperienceSource.class);
            amounts.put(ExperienceMath.ExperienceSource.FLUID_XP, 0L);
            amounts.put(ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT, 0L);

            var prioritySources = orderedUniqueSources(sourcePriority);
            var simulationEnergy = new CumulativeSimulatingEnergySource(energySource);
            for (var source : prioritySources) {
                ExperienceStorageAdapter adapter = adapters.get(source);
                if (adapter != null) {
                    amounts.put(source, extractableAndReserveComplete(
                            simulationEnergy, storage, actionSource, adapter));
                }
            }

            for (var source : List.of(ExperienceMath.ExperienceSource.FLUID_XP,
                    ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT)) {
                if (!prioritySources.contains(source)) {
                    ExperienceStorageAdapter adapter = adapters.get(source);
                    amounts.put(source, adapter == null ? 0 : extractable(energySource, storage, actionSource, adapter));
                }
            }
            return amounts;
        }

        public boolean canConsume(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                Player player, long requiredExperience, List<ExperienceMath.ExperienceSource> sourcePriority) {
            return canConsume(energySource, storage, actionSource, player, requiredExperience, sourcePriority,
                    defaultStorageAdapters());
        }

        public boolean canConsume(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                Player player, long requiredExperience, List<ExperienceMath.ExperienceSource> sourcePriority,
                List<ExperienceStorageAdapter> storageAdapters) {
            Objects.requireNonNull(player, "player");
            requireNonNegative(requiredExperience, "requiredExperience");
            orderedUniqueSources(sourcePriority);
            Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters = adaptersBySource(storageAdapters);
            validateDisjointStorageAdapters(storage, adapters);
            if (player.getAbilities().instabuild) {
                return true;
            }
            var payment = planPayment(energySource, storage, actionSource, player, requiredExperience,
                    sourcePriority, storageAdapters);
            if (payment.player() > Integer.MAX_VALUE) {
                return false;
            }
            return payment.enough() && canExtractPlannedStorage(energySource, storage, actionSource, payment,
                    sourcePriority, adapters);
        }

        /**
         * Consumes required raw XP from ME storage and the player according to {@code sourcePriority}.
         *
         * <p>The method plans from current extractable network amounts, simulates every planned network
         * extraction, modulates network extraction first, and deducts player XP last. A failed preflight
         * does not mutate state. AE2 does not provide a transaction spanning multiple storage adapters, so
         * callers that supply more than one network adapter must serialize competing mutations themselves.</p>
         */
        public boolean consume(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                Player player, long requiredExperience, List<ExperienceMath.ExperienceSource> sourcePriority) {
            return consume(energySource, storage, actionSource, player, requiredExperience, sourcePriority,
                    defaultStorageAdapters());
        }

        public boolean consume(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                Player player, long requiredExperience, List<ExperienceMath.ExperienceSource> sourcePriority,
                List<ExperienceStorageAdapter> storageAdapters) {
            Objects.requireNonNull(player, "player");
            requireNonNegative(requiredExperience, "requiredExperience");
            orderedUniqueSources(sourcePriority);
            Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters = adaptersBySource(storageAdapters);
            validateDisjointStorageAdapters(storage, adapters);
            if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
                return false;
            }
            if (player.getAbilities().instabuild) {
                return true;
            }

            var payment = planPayment(energySource, storage, actionSource, player, requiredExperience,
                    sourcePriority, storageAdapters);
            PlayerExperienceDebit playerDebit = preparePlayerDebit(serverPlayer, payment.player());
            if (!payment.enough() || !canExtractPlannedStorage(energySource, storage, actionSource, payment,
                    sourcePriority, adapters) || playerDebit == null) {
                return false;
            }

            for (var source : orderedUniqueSources(sourcePriority)) {
                if (source == ExperienceMath.ExperienceSource.PLAYER) {
                    continue;
                }
                long amount = payment.used(source);
                ExperienceStorageAdapter adapter = adapters.get(source);
                if (amount > 0 && (adapter == null
                        || !extractExact(energySource, storage, actionSource, amount, adapter))) {
                    return false;
                }
            }

            applyPlayerDebit(serverPlayer, playerDebit);
            return true;
        }

        private void addExtractableNetworkSource(List<ExperienceMath.ExperienceSource> sources,
                IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                ExperienceStorageAdapter adapter) {
            if (adapter == null || sources.contains(adapter.source())) {
                return;
            }
            if (extractableAndReserveComplete(energySource, storage, actionSource, adapter) > 0) {
                sources.add(adapter.source());
            }
        }

        private long extractableAndReserveComplete(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, ExperienceStorageAdapter adapter) {
            if (!(energySource instanceof CumulativeSimulatingEnergySource cumulativeEnergy)) {
                return extractable(energySource, storage, actionSource, adapter);
            }

            double checkpoint = cumulativeEnergy.checkpoint();
            long completeExperience = extractable(cumulativeEnergy, storage, actionSource, adapter);
            cumulativeEnergy.restore(checkpoint);
            if (completeExperience == 0) {
                return 0;
            }
            if (!canExtract(cumulativeEnergy, storage, actionSource, completeExperience, adapter)) {
                cumulativeEnergy.restore(checkpoint);
                return 0;
            }
            return completeExperience;
        }

        private boolean canExtractPlannedStorage(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, ExperienceMath.MyoExperience payment,
                List<ExperienceMath.ExperienceSource> sourcePriority,
                Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters) {
            IEnergySource simulationEnergy = new CumulativeSimulatingEnergySource(energySource);
            for (var source : orderedUniqueSources(sourcePriority)) {
                long amount = payment.used(source);
                ExperienceStorageAdapter adapter = adapters.get(source);
                if (source != ExperienceMath.ExperienceSource.PLAYER && amount > 0 && (adapter == null
                        || !canExtract(simulationEnergy, storage, actionSource, amount, adapter))) {
                    return false;
                }
            }
            return true;
        }

        private Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adaptersBySource(
                List<ExperienceStorageAdapter> storageAdapters) {
            Objects.requireNonNull(storageAdapters, "storageAdapters");
            var adapters = new EnumMap<ExperienceMath.ExperienceSource, ExperienceStorageAdapter>(
                    ExperienceMath.ExperienceSource.class);
            for (ExperienceStorageAdapter adapter : storageAdapters) {
                Objects.requireNonNull(adapter, "storageAdapters contains null");
                if (adapters.putIfAbsent(adapter.source(), adapter) != null) {
                    throw new IllegalArgumentException("Duplicate storage adapter for " + adapter.source());
                }
            }
            return adapters;
        }

        private void validateDisjointStorageAdapters(MEStorage storage,
                Map<ExperienceMath.ExperienceSource, ExperienceStorageAdapter> adapters) {
            if (storage == null || adapters.size() < 2) {
                return;
            }
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getLongValue() <= 0) {
                    continue;
                }
                ExperienceMath.ExperienceSource matchedSource = null;
                for (ExperienceStorageAdapter adapter : adapters.values()) {
                    if (!adapter.matches(entry.getKey())) {
                        continue;
                    }
                    if (matchedSource != null) {
                        throw new IllegalArgumentException("AE storage key " + entry.getKey().getId()
                                + " matches multiple experience adapters: " + matchedSource + " and "
                                + adapter.source());
                    }
                    matchedSource = adapter.source();
                }
            }
        }

        private List<ExperienceStorageAdapter> defaultStorageAdapters() {
            return List.of(APPLIED_EXPERIENCED_STORAGE);
        }

        private ExperienceStorageAdapter defaultStorageAdapter(ExperienceMath.ExperienceSource source) {
            Objects.requireNonNull(source, "source");
            return source == ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT
                    ? APPLIED_EXPERIENCED_STORAGE
                    : null;
        }

        private List<ExperienceMath.ExperienceSource> orderedUniqueSources(
                List<ExperienceMath.ExperienceSource> sourcePriority) {
            Objects.requireNonNull(sourcePriority, "sourcePriority");
            var sources = new ArrayList<ExperienceMath.ExperienceSource>();
            for (var source : sourcePriority) {
                Objects.requireNonNull(source, "sourcePriority contains null");
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }
            return sources;
        }

        /**
         * Returns the raw XP amount visibly stored in ME storage for the given source.
         *
         * <p>This is a storage snapshot only. It does not prove that the amount can be extracted with
         * the supplied grid energy or action source. Use {@link #canExtract} or
         * {@link #extractExact} for payment decisions.</p>
         */
        public long stored(MEStorage storage, ExperienceMath.ExperienceSource source) {
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return adapter == null ? 0 : stored(storage, adapter);
        }

        public long stored(MEStorage storage, ExperienceStorageAdapter adapter) {
            Objects.requireNonNull(adapter, "adapter");
            if (storage == null) {
                return 0;
            }
            long storageUnits = 0;
            KeyCounter availableStacks = storage.getAvailableStacks();
            for (var entry : availableStacks) {
                if (entry.getLongValue() > 0 && adapter.matches(entry.getKey())) {
                    storageUnits = Math.addExact(storageUnits, entry.getLongValue());
                }
            }
            return adapter.toExperience(storageUnits);
        }

        /**
         * Returns the amount that the current energy source and storage report as extractable in a
         * simulation pass. This is useful for UI estimates; payment code should still use
         * {@link #extractExact} and check its boolean result.
         */
        public long extractable(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, ExperienceMath.ExperienceSource source) {
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return adapter == null ? 0 : extractable(energySource, storage, actionSource, adapter);
        }

        public long extractable(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, ExperienceStorageAdapter adapter) {
            return extract(energySource, storage, actionSource, stored(storage, adapter), adapter, Actionable.SIMULATE);
        }

        public boolean canExtract(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long amount, ExperienceMath.ExperienceSource source) {
            requireNonNegative(amount, "amount");
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return amount == 0 || adapter != null && canExtract(energySource, storage, actionSource, amount, adapter);
        }

        public boolean canExtract(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long amount, ExperienceStorageAdapter adapter) {
            requireNonNegative(amount, "amount");
            return amount == 0 || extract(energySource, storage, actionSource, amount, adapter,
                    Actionable.SIMULATE) == amount;
        }

        /**
         * Extracts exactly {@code amount} raw XP from matching ME storage entries when possible.
         *
         * <p>The method simulates first and only modulates when the simulation can satisfy the full
         * request. It returns {@code false} if the simulation or the real extraction does not remove the
         * full amount. Like AE2 storage operations generally, it cannot roll back a concurrent partial
         * extraction that happens after a successful simulation, so callers must treat {@code false} as
         * payment failure.</p>
         */
        public boolean extractExact(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long amount, ExperienceMath.ExperienceSource source) {
            requireNonNegative(amount, "amount");
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return amount == 0 || adapter != null && extractExact(energySource, storage, actionSource, amount, adapter);
        }

        public boolean extractExact(IEnergySource energySource, MEStorage storage,
                IActionSource actionSource, long amount, ExperienceStorageAdapter adapter) {
            requireNonNegative(amount, "amount");
            Objects.requireNonNull(adapter, "adapter");
            if (amount == 0) {
                return true;
            }
            if (!canExtract(energySource, storage, actionSource, amount, adapter)) {
                return false;
            }
            return extract(energySource, storage, actionSource, amount, adapter,
                    Actionable.MODULATE) == amount;
        }

        /**
         * Best-effort extraction helper. The return value is the raw XP amount actually extracted; callers
         * that need payment semantics must compare it with the requested amount or prefer
         * {@link #extractExact}.
         */
        public long extract(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                long amount, ExperienceMath.ExperienceSource source, Actionable actionable) {
            requireNonNegative(amount, "amount");
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return amount == 0 || adapter == null ? 0
                    : extract(energySource, storage, actionSource, amount, adapter, actionable);
        }

        public long extract(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                long amount, ExperienceStorageAdapter adapter, Actionable actionable) {
            requireNonNegative(amount, "amount");
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(actionable, "actionable");
            if (amount == 0 || storage == null) {
                return 0;
            }
            Objects.requireNonNull(energySource, "energySource");
            Objects.requireNonNull(actionSource, "actionSource");

            long requestedStorageUnits = adapter.toStorageUnits(amount);
            if (actionable == Actionable.MODULATE) {
                long simulatedStorageUnits = extractStorageUnits(energySource, storage, actionSource,
                        requestedStorageUnits, adapter, Actionable.SIMULATE);
                long completeExperience = adapter.toExperience(simulatedStorageUnits);
                if (completeExperience == 0) {
                    return 0;
                }
                requestedStorageUnits = adapter.toStorageUnits(completeExperience);
            }
            long extractedStorageUnits = extractStorageUnits(energySource, storage, actionSource,
                    requestedStorageUnits, adapter, actionable);
            return adapter.toExperience(extractedStorageUnits);
        }

        private long extractStorageUnits(IEnergySource energySource, MEStorage storage, IActionSource actionSource,
                long requestedStorageUnits, ExperienceStorageAdapter adapter, Actionable actionable) {
            IEnergySource extractionEnergy = actionable == Actionable.SIMULATE
                    && !(energySource instanceof CumulativeSimulatingEnergySource)
                            ? new CumulativeSimulatingEnergySource(energySource)
                            : energySource;
            long extracted = 0;
            KeyCounter availableStacks = storage.getAvailableStacks();
            for (var entry : availableStacks) {
                AEKey key = entry.getKey();
                if (entry.getLongValue() <= 0 || !adapter.matches(key)) {
                    continue;
                }
                long remaining = requestedStorageUnits - extracted;
                if (remaining <= 0) {
                    break;
                }
                long toExtract = Math.min(remaining, entry.getLongValue());
                extracted = Math.addExact(extracted, StorageHelper.poweredExtraction(extractionEnergy, storage, key,
                        toExtract, actionSource, actionable));
            }
            return extracted;
        }

        public boolean matchesSource(AEKey key, ExperienceMath.ExperienceSource source) {
            Objects.requireNonNull(key, "key");
            ExperienceStorageAdapter adapter = defaultStorageAdapter(source);
            return adapter != null && adapter.matches(key);
        }

        public boolean matchesSource(AEKey key, ExperienceStorageAdapter adapter) {
            return Objects.requireNonNull(adapter, "adapter").matches(Objects.requireNonNull(key, "key"));
        }

        public boolean isXpFluid(AEKey key) {
            if (!(key instanceof AEFluidKey fluidKey)) {
                return false;
            }
            return EXPERIENCE_FLUID_TAGS.stream().anyMatch(fluidKey::isTagged);
        }

        private static boolean isAppliedExperiencedKey(AEKey key) {
            return key != null
                    && ExperienceMath.APPLIED_EXPERIENCED_AE_KEY_ID.equals(key.getId().toString())
                    && ExperienceMath.APPLIED_EXPERIENCED_AE_KEY_ID.equals(key.getType().getId().toString());
        }

        private PlayerExperienceDebit preparePlayerDebit(ServerPlayer player, long amount) {
            requireNonNegative(amount, "playerExperience");
            if (amount > Integer.MAX_VALUE) {
                return null;
            }
            long current = playerRaw(player);
            if (amount > current) {
                return null;
            }
            long target = current - amount;
            int targetLevel = ExperienceMath.levelForTotalExperience(target);
            long points = ExperienceMath.experienceIntoLevel(target);
            if (points > Integer.MAX_VALUE) {
                return null;
            }
            return new PlayerExperienceDebit((int) amount, targetLevel, (int) points);
        }

        private void applyPlayerDebit(ServerPlayer player, PlayerExperienceDebit debit) {
            if (debit.amount() == 0) {
                return;
            }
            player.setExperienceLevels(debit.targetLevel());
            player.setExperiencePoints(debit.pointsIntoLevel());
            player.totalExperience = (int) Math.max(0L, (long) player.totalExperience - debit.amount());
            player.increaseScore(-debit.amount());
        }

        private static void requireNonNegative(long value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be non-negative");
            }
        }

        private record PlayerExperienceDebit(int amount, int targetLevel, int pointsIntoLevel) {
        }

        private static final class CumulativeSimulatingEnergySource implements IEnergySource {
            private final IEnergySource delegate;
            private double reserved;

            private CumulativeSimulatingEnergySource(IEnergySource delegate) {
                this.delegate = delegate;
            }

            private double checkpoint() {
                return reserved;
            }

            private void restore(double checkpoint) {
                if (checkpoint < 0 || checkpoint > reserved) {
                    throw new IllegalArgumentException("Invalid cumulative energy checkpoint");
                }
                reserved = checkpoint;
            }

            @Override
            public double extractAEPower(double amount, Actionable mode, PowerMultiplier usePowerMultiplier) {
                Objects.requireNonNull(mode, "mode");
                Objects.requireNonNull(usePowerMultiplier, "usePowerMultiplier");
                if (amount <= 0) {
                    return 0;
                }
                if (mode == Actionable.MODULATE) {
                    throw new IllegalStateException("CumulativeSimulatingEnergySource is simulation-only");
                }
                var energySource = Objects.requireNonNull(this.delegate, "energySource");
                double totalRequest = this.reserved + amount;
                double availableForTotal = Math.min(totalRequest,
                        energySource.extractAEPower(totalRequest, Actionable.SIMULATE, usePowerMultiplier));
                double availableAfterReserved = Math.max(0, availableForTotal - this.reserved);
                double extracted = Math.min(amount, availableAfterReserved);
                this.reserved += extracted;
                return extracted;
            }
        }

        private static final List<TagKey<Fluid>> EXPERIENCE_FLUID_TAGS = List.of(
                xpFluidTag("c", "experience"),
                xpFluidTag("c", "fluid_xp"),
                xpFluidTag("c", "fluid_experience"),
                xpFluidTag("c", "experience_fluid"),
                xpFluidTag("forge", "experience"),
                xpFluidTag("forge", "fluid_xp"),
                xpFluidTag("forge", "fluid_experience"),
                xpFluidTag("forge", "experience_fluid"));

        private static TagKey<Fluid> xpFluidTag(String namespace, String path) {
            return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(namespace, path));
        }
    }

    /**
     * Client-only public API.
     */
    public static final class Client {
        private Client() {
        }

        public static final class Widgets {
            private Widgets() {
            }

            public static AbstractButton keyBindingButton(Component label,
                    Consumer<List<InputConstants.Key>> changeListener) {
                return new KeyBindingButton(label, changeListener);
            }

            public static AbstractButton keyBindingButton(Component label, InputConstants.Key initialKey,
                    Consumer<List<InputConstants.Key>> changeListener) {
                return new KeyBindingButton(label, initialKey, changeListener);
            }
        }
    }

    /**
     * Internal bootstrap hook used by Myotus to install the API implementation.
     *
     * @param api the API implementation
     */
    public static void _setInstance(IMyotusAPI api) {
        if (instance != null) {
            throw new IllegalStateException("MyotusAPI instance is already set!");
        }
        instance = Objects.requireNonNull(api, "api");
    }
}
