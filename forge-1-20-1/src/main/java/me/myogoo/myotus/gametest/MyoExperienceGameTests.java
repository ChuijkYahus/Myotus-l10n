package me.myogoo.myotus.gametest;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.menu.ISubMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.ConfigManager;
import com.mojang.authlib.GameProfile;
import me.myogoo.myotus.Myotus;
import me.myogoo.myotus.api.ITerminalUpgradeCard;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.api.experience.ExperienceMath;
import me.myogoo.myotus.api.experience.ExperienceStorageAdapter;
import me.myogoo.myotus.init.MyoItems;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT;
import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.FLUID_XP;
import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.PLAYER;

@GameTestHolder(Myotus.MODID)
@PrefixGameTestTemplate(false)
public final class MyoExperienceGameTests {
    private static final long FLUID_UNITS_PER_EXPERIENCE = 250;

    private MyoExperienceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exposesStableExperienceIntegrationIds(GameTestHelper helper) {
        assertEquals(helper, "appex", MyotusAPI.experience().appliedModId(), "Applied Experienced mod id");
        assertEquals(helper, "appex:experience", MyotusAPI.experience().appliedAeKeyId(),
                "Applied Experienced AE key id");
        assertEquals(helper, "appex:experience_amount", MyotusAPI.experience().appliedAmountComponentId(),
                "Applied Experienced amount component id");
        assertEquals(helper, "fluid:xp", MyotusAPI.experience().fluidXpId(), "Fluid XP id");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaLevelConversionsRoundTripBoundaryValues(GameTestHelper helper) {
        assertEquals(helper, 0L, MyotusAPI.experience().totalForLevel(0), "Level 0 total XP");
        assertEquals(helper, 7L, MyotusAPI.experience().totalForLevel(1), "Level 1 total XP");
        assertEquals(helper, 315L, MyotusAPI.experience().totalForLevel(15), "Level 15 total XP");
        assertEquals(helper, 352L, MyotusAPI.experience().totalForLevel(16), "Level 16 total XP");
        assertEquals(helper, 1395L, MyotusAPI.experience().totalForLevel(30), "Level 30 total XP");
        assertEquals(helper, 1628L, MyotusAPI.experience().totalForLevel(32), "Level 32 total XP");

        assertEquals(helper, 0, MyotusAPI.experience().levelForTotal(6), "6 XP is still level 0");
        assertEquals(helper, 1, MyotusAPI.experience().levelForTotal(7), "7 XP reaches level 1");
        assertEquals(helper, 31, MyotusAPI.experience().levelForTotal(1627), "1627 XP is level 31");
        assertEquals(helper, 32, MyotusAPI.experience().levelForTotal(1628), "1628 XP reaches level 32");
        assertEquals(helper, 120L, MyotusAPI.experience().intoLevel(1627), "XP progress within level 31");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anvilPriorityIncludesFluidXpOnlyWhenAvailable(GameTestHelper helper) {
        assertEquals(helper, List.of(PLAYER, APPLIED_EXPERIENCED_AMOUNT), MyotusAPI.experience().anvilSourcePriority(0),
                "No fluid XP should skip the fluid source");
        assertEquals(helper, List.of(PLAYER, FLUID_XP, APPLIED_EXPERIENCED_AMOUNT),
                MyotusAPI.experience().anvilSourcePriority(1), "Positive fluid XP should include the fluid source");
        assertEquals(helper, List.of(PLAYER, APPLIED_EXPERIENCED_AMOUNT),
                MyotusAPI.experience().anvilSourcePriority(false), "Explicit false should skip fluid XP");
        assertEquals(helper, List.of(PLAYER, FLUID_XP, APPLIED_EXPERIENCED_AMOUNT),
                MyotusAPI.experience().anvilSourcePriority(true), "Explicit true should include fluid XP");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void myoExperienceUsesDefaultAnvilPriority(GameTestHelper helper) {
        ExperienceMath.MyoExperience experience = MyotusAPI.experience().plan(75,
                new ExperienceMath.ExperienceAmounts(30, 40, 50));

        assertTrue(helper, experience.enough(), "MyoExperience should be enough across all default sources");
        assertEquals(helper, 75L, experience.required(), "Required XP");
        assertEquals(helper, 120L, experience.available(), "Available XP");
        assertEquals(helper, 120L, experience.spendable(), "Spendable XP should include all default priority sources");
        assertEquals(helper, 30L, experience.player(), "Player XP should be exhausted first");
        assertEquals(helper, 40L, experience.fluidXp(), "Fluid XP should be exhausted second");
        assertEquals(helper, 5L, experience.appliedExperiencedAmount(),
                "Applied Experienced amount should pay the remainder");
        assertEquals(helper, 0L, experience.missing(), "Enough MyoExperience should not miss XP");
        assertEquals(helper, 40L, experience.used(FLUID_XP), "used(source) should match the source total");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void myoExperienceSupportsCustomPriorityAndMissingExperience(GameTestHelper helper) {
        var available = new ExperienceMath.ExperienceAmounts(30, 40, 50);
        ExperienceMath.MyoExperience customExperience = MyotusAPI.experience().plan(75, available,
                List.of(APPLIED_EXPERIENCED_AMOUNT, FLUID_XP, PLAYER));
        assertTrue(helper, customExperience.enough(), "Custom priority experience should be enough");
        assertEquals(helper, 0L, customExperience.player(), "Custom priority should leave player XP untouched");
        assertEquals(helper, 25L, customExperience.fluidXp(), "Fluid XP should pay the custom-priority remainder");
        assertEquals(helper, 50L, customExperience.appliedExperiencedAmount(),
                "Applied Experienced amount should be consumed first by custom priority");

        ExperienceMath.MyoExperience missingExperience = MyotusAPI.experience().plan(75, available,
                List.of(PLAYER, FLUID_XP));
        assertTrue(helper, !missingExperience.enough(), "MyoExperience should be missing XP when priority excludes a needed source");
        assertEquals(helper, 120L, missingExperience.available(), "Available XP should include non-priority sources");
        assertEquals(helper, 70L, missingExperience.spendable(), "Spendable XP should include priority sources only");
        assertEquals(helper, 30L, missingExperience.player(), "Missing experience should consume player XP");
        assertEquals(helper, 40L, missingExperience.fluidXp(), "Missing experience should consume fluid XP");
        assertEquals(helper, 0L, missingExperience.appliedExperiencedAmount(),
                "Excluded source should not be consumed");
        assertEquals(helper, 5L, missingExperience.missing(), "Missing XP should equal the unpaid remainder");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaAndApothicCostsStaySemanticallySeparate(GameTestHelper helper) {
        long vanillaCost = MyotusAPI.experience().vanillaAnvilCost(100, 30);
        long apothicCost = MyotusAPI.experience().apothicAnvilCost(30);

        assertEquals(helper, MyotusAPI.experience().totalForLevel(100)
                - MyotusAPI.experience().totalForLevel(70), vanillaCost,
                "Vanilla anvil cost should remove levels from the current player level");
        assertEquals(helper, MyotusAPI.experience().totalForLevel(30), apothicCost,
                "Apothic anvil cost should be the raw XP value of the displayed level cost");
        assertTrue(helper, vanillaCost > apothicCost, "High-level vanilla anvil cost should exceed Apothic cost");
        assertEquals(helper, 109L, MyotusAPI.experience().vanillaAnvilCost(30, 1, 0.5F),
                "Vanilla anvil cost should preserve the player's progress fraction");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void apothicEnchantingAndLibraryCalculationsStaySeparate(GameTestHelper helper) {
        long level30Step = MyotusAPI.experience().toNextLevel(29);
        long level29Step = MyotusAPI.experience().toNextLevel(28);
        long level28Step = MyotusAPI.experience().toNextLevel(27);

        assertEquals(helper, level30Step - 1, MyotusAPI.experience().apothicEnchantingTableCost(30, 0),
                "Slot 0 should charge the level step cost minus one");
        assertEquals(helper, level30Step + level29Step - 1,
                MyotusAPI.experience().apothicEnchantingTableCost(30, 1),
                "Slot 1 should sum two descending level step costs minus one");
        assertEquals(helper, level30Step + level29Step + level28Step - 1,
                MyotusAPI.experience().apothicEnchantingTableCost(30, 2),
                "Slot 2 should sum three descending level step costs minus one");
        assertEquals(helper, 16L, MyotusAPI.experience().apothicLibraryPointsForLevel(5),
                "Apothic library points should double per enchantment level");
        assertTrue(helper, MyotusAPI.experience().apothicLibraryPointsForLevel(5)
                        != MyotusAPI.experience().totalForLevel(5),
                "Apothic library points must not be treated as raw vanilla XP");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeFluidStorageUsesExplicitExperienceUnits(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        ExperienceStorageAdapter adapter = fluidAdapter(water);
        MutableStorage storage = new MutableStorage(water, 499);
        TrackingEnergySource energy = new TrackingEnergySource(2);

        assertEquals(helper, 125, water.getAmountPerOperation(),
                "Forge AE2 should charge fluid transfers in 125 mB operations");
        assertEquals(helper, 1L, MyotusAPI.experience().stored(storage, adapter),
                "499 mB should expose only one complete raw XP point");
        assertTrue(helper, MyotusAPI.experience().extractExact(energy, storage, IActionSource.empty(), 1, adapter),
                "One raw XP should extract exactly 250 mB");
        assertEquals(helper, 249L, storage.stored(water),
                "Partial fluid units must remain after exact extraction");
        assertEquals(helper, 0.0, energy.stored(), "A 250 mB extraction should consume two AE");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeFluidSimulationDoesNotMutateStorageOrEnergy(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        ExperienceStorageAdapter adapter = fluidAdapter(water);
        MutableStorage storage = new MutableStorage(water, 500);
        TrackingEnergySource energy = new TrackingEnergySource(4);

        assertEquals(helper, 2L, MyotusAPI.experience().extract(energy, storage, IActionSource.empty(), 2,
                adapter, Actionable.SIMULATE), "Simulation should report two extractable XP points");
        assertEquals(helper, 500L, storage.stored(water), "Simulation must not remove stored fluid");
        assertEquals(helper, 4.0, energy.stored(), "Simulation must not consume AE power");
        assertTrue(helper, energy.simulationCalls() > 0, "Simulation should query the AE2 energy source");
        assertEquals(helper, 0, energy.modulationCalls(), "Simulation must not modulate the AE2 energy source");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeFluidExactExtractionFailsAtomicallyWhenEnergyIsInsufficient(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        ExperienceStorageAdapter adapter = fluidAdapter(water);
        MutableStorage storage = new MutableStorage(water, 500);
        TrackingEnergySource energy = new TrackingEnergySource(3);

        assertTrue(helper, !MyotusAPI.experience().extractExact(energy, storage, IActionSource.empty(), 2, adapter),
                "Three AE must not pay the four-AE cost of 500 mB");
        assertEquals(helper, 500L, storage.stored(water),
                "Failed exact extraction must leave all matching storage untouched");
        assertEquals(helper, 3.0, energy.stored(),
                "Failed exact extraction must leave the energy source untouched");
        assertEquals(helper, 0, energy.modulationCalls(),
                "Failed exact extraction must stop after the simulation pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeFluidEnergySimulationIsCumulativeAcrossKeys(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);
        ExperienceStorageAdapter adapter = fluidAdapter(water, lava);
        MutableStorage storage = new MutableStorage(water, 250, lava, 250);
        TrackingEnergySource oneXpEnergy = new TrackingEnergySource(2);

        assertEquals(helper, 1L, MyotusAPI.experience().extractable(oneXpEnergy, storage,
                IActionSource.empty(), adapter),
                "A cumulative simulation must not reuse the same two AE for both fluid keys");
        assertEquals(helper, 250L, storage.stored(water), "Extractability checks must not mutate water storage");
        assertEquals(helper, 250L, storage.stored(lava), "Extractability checks must not mutate lava storage");
        assertEquals(helper, 2.0, oneXpEnergy.stored(), "Extractability checks must not consume AE power");

        TrackingEnergySource exactEnergy = new TrackingEnergySource(4);
        assertTrue(helper, MyotusAPI.experience().extractExact(exactEnergy, storage, IActionSource.empty(), 2,
                adapter), "Four AE should extract one XP from each matching key");
        assertEquals(helper, 0L, storage.stored(water), "Exact extraction should consume the water units");
        assertEquals(helper, 0L, storage.stored(lava), "Exact extraction should consume the lava units");
        assertEquals(helper, 0.0, exactEnergy.stored(), "Successful two-key extraction should consume four AE");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aePlanningReleasesEnergyForIncompleteExperienceUnits(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);
        MutableStorage storage = new MutableStorage(water, 250, lava, 125);
        TrackingEnergySource energy = new TrackingEnergySource(1.5);
        ExperienceStorageAdapter fluid = fluidAdapter(water);
        ExperienceStorageAdapter applied = new ExperienceStorageAdapter(
                APPLIED_EXPERIENCED_AMOUNT, lava::equals, 125);
        List<ExperienceStorageAdapter> adapters = List.of(fluid, applied);

        assertEquals(helper, List.of(APPLIED_EXPERIENCED_AMOUNT, PLAYER),
                MyotusAPI.experience().availableAnvilSourcePriority(energy, storage, IActionSource.empty(),
                        FLUID_XP, adapters),
                "An incomplete fluid unit must not reserve energy needed by the next source");

        var payment = MyotusAPI.experience().planPayment(energy, storage, IActionSource.empty(),
                0, 1, List.of(FLUID_XP, APPLIED_EXPERIENCED_AMOUNT, PLAYER), adapters);
        assertTrue(helper, payment.enough(), "The second adapter should still be able to pay one XP");
        assertEquals(helper, 0L, payment.fluidXp(), "The underpowered fluid adapter must pay nothing");
        assertEquals(helper, 1L, payment.appliedExperiencedAmount(), "The next adapter should pay one XP");
        assertEquals(helper, 0L, payment.player(), "No player fallback should be needed");
        assertEquals(helper, 250L, storage.stored(water), "Planning must not mutate water storage");
        assertEquals(helper, 125L, storage.stored(lava), "Planning must not mutate lava storage");
        assertEquals(helper, 1.5, energy.stored(), "Planning must not consume backing AE power");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeConsumeDebitsNetworkAndServerPlayerExactly(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        MutableStorage storage = new MutableStorage(water, 250);
        TrackingEnergySource energy = new TrackingEnergySource(2);
        ExperienceStorageAdapter fluid = fluidAdapter(water);
        ServerPlayer player = makeMenuPlayer(helper);
        player.giveExperiencePoints(165);
        int initialScore = player.getScore();

        assertTrue(helper, MyotusAPI.experience().consume(energy, storage, IActionSource.empty(), player,
                15, List.of(FLUID_XP, PLAYER), List.of(fluid)),
                "One network XP and fourteen player XP should complete the payment");

        long expectedPlayerExperience = 151;
        int expectedLevel = MyotusAPI.experience().levelForTotal(expectedPlayerExperience);
        long expectedPoints = MyotusAPI.experience().intoLevel(expectedPlayerExperience);
        double expectedProgress = (double) expectedPoints / player.getXpNeededForNextLevel();
        assertEquals(helper, expectedPlayerExperience, MyotusAPI.experience().playerRaw(player),
                "Player raw XP should be debited exactly");
        assertEquals(helper, expectedLevel, player.experienceLevel, "Player level after debit");
        assertEquals(helper, expectedProgress, player.experienceProgress, "Player progress after debit");
        assertEquals(helper, expectedPlayerExperience, player.totalExperience, "Player totalExperience after debit");
        assertEquals(helper, initialScore - 14, player.getScore(), "Player score should track the player XP debit");
        assertEquals(helper, 0L, storage.stored(water), "The network XP units should be extracted");
        assertEquals(helper, 0.0, energy.stored(), "The network extraction should consume exactly two AE");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aeExperiencePlanningRejectsOverlappingAdapters(GameTestHelper helper) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        MutableStorage storage = new MutableStorage(water, 250);
        ExperienceStorageAdapter fluid = fluidAdapter(water);
        ExperienceStorageAdapter alsoApplied = new ExperienceStorageAdapter(APPLIED_EXPERIENCED_AMOUNT,
                water::equals, FLUID_UNITS_PER_EXPERIENCE);

        assertThrows(helper, IllegalArgumentException.class, () -> MyotusAPI.experience().planPayment(
                new TrackingEnergySource(2), storage, IActionSource.empty(), 0, 1,
                List.of(FLUID_XP, APPLIED_EXPERIENCED_AMOUNT), List.of(fluid, alsoApplied)),
                "One AE key must not be claimed by multiple experience sources");
        assertEquals(helper, 250L, storage.stored(water),
                "Adapter validation must fail before storage is touched");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aeStorageMenuProvidesSafeTerminalUpgradeLifecycle(GameTestHelper helper) {
        ServerPlayer player = makeMenuPlayer(helper);
        MutableStorage storage = new MutableStorage();
        TestTerminalHost host = new TestTerminalHost(player, storage);
        MEStorageMenu menu = new MEStorageMenu(MEStorageMenu.TYPE, 1, player.getInventory(), host);
        var slots = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
        ItemStack card = new ItemStack(MyoItems.MYOTUS_UPGRADE_CARD.get());

        assertEquals(helper, 5, slots.size(), "Every ME storage menu should expose five Myotus upgrade slots");
        assertTrue(helper, !slots.get(0).mayPlace(new ItemStack(Items.DIAMOND)),
                "A normal item must be rejected by a Myotus upgrade slot");
        assertTrue(helper, card.getItem() instanceof ITerminalUpgradeCard,
                "The registered development card must implement the public upgrade API");
        assertTrue(helper, slots.get(0).mayPlace(card),
                "An ITerminalUpgradeCard should be accepted by an empty upgrade slot");

        slots.get(0).set(card.copy());
        assertTrue(helper, !slots.get(1).mayPlace(card.copy()),
                "A second copy of the same upgrade item must be rejected");

        List<ItemStack> installed = MyotusAPI.terminalUpgrades().installedUpgrades(menu);
        assertEquals(helper, 1, installed.size(), "The terminal API should report the installed card");
        installed.get(0).setCount(0);
        assertEquals(helper, 1, slots.get(0).getItem().getCount(),
                "Installed-upgrade snapshots must not expose the live slot stack");

        menu.broadcastChanges();
        menu.broadcastChanges();
        assertEquals(helper, 2, player.getInventory().countItem(Items.DIAMOND),
                "Insertion should dispatch one open and one tick callback, with no duplicate tick in the same tick");
        assertEquals(helper, 2, upgradeCallbackCount(slots.get(0).getItem()),
                "Open and tick callback data should be stored on the live card");

        MEStorageMenu persistedMenu = new MEStorageMenu(MEStorageMenu.TYPE, 2, player.getInventory(), host);
        ItemStack persistedCard = persistedMenu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT).get(0).getItem();
        assertEquals(helper, 2, upgradeCallbackCount(persistedCard),
                "A fresh menu should reload callback data from persistent player storage");
        assertEquals(helper, 2, player.getInventory().countItem(Items.DIAMOND),
                "Constructing a menu must not dispatch upgrade callbacks before its first broadcast");

        helper.runAfterDelay(1, () -> {
            menu.broadcastChanges();
            assertEquals(helper, 3, player.getInventory().countItem(Items.DIAMOND),
                    "The installed card should receive one callback on the next game tick");
            menu.removed(player);
            assertEquals(helper, 4, player.getInventory().countItem(Items.DIAMOND),
                    "Closing the actual ME storage menu should dispatch one close callback");
            MEStorageMenu reopenedMenu = new MEStorageMenu(MEStorageMenu.TYPE, 3, player.getInventory(), host);
            ItemStack reopenedCard = reopenedMenu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT).get(0).getItem();
            assertEquals(helper, 4, upgradeCallbackCount(reopenedCard),
                    "Tick and close callback data should survive reopening the menu");
            helper.succeed();
        });
    }

    private static int upgradeCallbackCount(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("myotus_test_callbacks") : 0;
    }

    private static ServerPlayer makeMenuPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "myotus-menu-gametest"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
            }
        };
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        return player;
    }

    private static ExperienceStorageAdapter fluidAdapter(AEFluidKey... keys) {
        return MyotusAPI.experience().fluidStorage(
                key -> {
                    for (AEFluidKey accepted : keys) {
                        if (accepted.equals(key)) {
                            return true;
                        }
                    }
                    return false;
                },
                FLUID_UNITS_PER_EXPERIENCE);
    }

    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void assertEquals(GameTestHelper helper, long expected, long actual, String message) {
        helper.assertTrue(expected == actual, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void assertEquals(GameTestHelper helper, int expected, int actual, String message) {
        helper.assertTrue(expected == actual, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void assertEquals(GameTestHelper helper, double expected, double actual, String message) {
        helper.assertTrue(Math.abs(expected - actual) < 0.000_001,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void assertEquals(GameTestHelper helper, Object expected, Object actual, String message) {
        helper.assertTrue(expected.equals(actual), message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void assertThrows(GameTestHelper helper, Class<? extends Throwable> expectedType,
            Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            helper.assertTrue(expectedType.isInstance(throwable),
                    message + ": expected=" + expectedType.getName() + ", actual=" + throwable.getClass().getName());
            return;
        }
        helper.assertTrue(false, message + ": expected " + expectedType.getName());
    }

    private static final class TrackingEnergySource implements IEnergySource {
        private double stored;
        private int simulationCalls;
        private int modulationCalls;

        private TrackingEnergySource(double stored) {
            this.stored = stored;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier usePowerMultiplier) {
            double extracted = Math.min(amount, this.stored);
            if (mode == Actionable.MODULATE) {
                this.stored -= extracted;
                this.modulationCalls++;
            } else {
                this.simulationCalls++;
            }
            return extracted;
        }

        private double stored() {
            return this.stored;
        }

        private int simulationCalls() {
            return this.simulationCalls;
        }

        private int modulationCalls() {
            return this.modulationCalls;
        }
    }

    private static final class MutableStorage implements MEStorage {
        private final LinkedHashMap<AEKey, Long> storedByKey = new LinkedHashMap<>();

        private MutableStorage() {
        }

        private MutableStorage(AEKey key, long amount) {
            this.storedByKey.put(key, amount);
        }

        private MutableStorage(AEKey firstKey, long firstAmount, AEKey secondKey, long secondAmount) {
            this.storedByKey.put(firstKey, firstAmount);
            this.storedByKey.put(secondKey, secondAmount);
        }

        private long stored(AEKey key) {
            return this.storedByKey.getOrDefault(key, 0L);
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
            long stored = stored(key);
            long extracted = Math.min(amount, stored);
            if (mode == Actionable.MODULATE) {
                this.storedByKey.put(key, stored - extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var entry : this.storedByKey.entrySet()) {
                if (entry.getValue() > 0) {
                    out.add(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("Myotus GameTest storage");
        }
    }

    private static final class TestTerminalHost extends ItemMenuHost implements ITerminalHost {
        private final MEStorage storage;
        private final IConfigManager configManager = new ConfigManager(() -> {
        });

        private TestTerminalHost(Player player, MEStorage storage) {
            super(player, null, new ItemStack(Items.COMPASS));
            this.storage = storage;
        }

        @Override
        public MEStorage getInventory() {
            return this.storage;
        }

        @Override
        public IConfigManager getConfigManager() {
            return this.configManager;
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
        }

        @Override
        public ItemStack getMainMenuIcon() {
            return getItemStack().copy();
        }
    }
}
