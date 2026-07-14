package me.myogoo.myotus.api.experience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT;
import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.FLUID_XP;
import static me.myogoo.myotus.api.experience.ExperienceMath.ExperienceSource.PLAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceMathTest {
    @Test
    void exposesAppliedExperiencedAndFluidXpIdentifiers() {
        assertEquals("appex", ExperienceMath.APPLIED_EXPERIENCED_MOD_ID);
        assertEquals("appex:experience", ExperienceMath.APPLIED_EXPERIENCED_AE_KEY_ID);
        assertEquals("appex:experience_amount", ExperienceMath.APPLIED_EXPERIENCED_AMOUNT_COMPONENT_ID);
        assertEquals("fluid:xp", ExperienceMath.FLUID_XP_ID);
    }

    @Test
    void convertsMinecraftLevelsToTotalExperiencePoints() {
        assertEquals(0, ExperienceMath.totalExperienceForLevel(0));
        assertEquals(7, ExperienceMath.totalExperienceForLevel(1));
        assertEquals(315, ExperienceMath.totalExperienceForLevel(15));
        assertEquals(352, ExperienceMath.totalExperienceForLevel(16));
        assertEquals(394, ExperienceMath.totalExperienceForLevel(17));
        assertEquals(1395, ExperienceMath.totalExperienceForLevel(30));
        assertEquals(1507, ExperienceMath.totalExperienceForLevel(31));
        assertEquals(1628, ExperienceMath.totalExperienceForLevel(32));
    }

    @Test
    void convertsTotalExperiencePointsBackToLevelsAndProgress() {
        assertEquals(0, ExperienceMath.levelForTotalExperience(0));
        assertEquals(0, ExperienceMath.levelForTotalExperience(6));
        assertEquals(1, ExperienceMath.levelForTotalExperience(7));
        assertEquals(15, ExperienceMath.levelForTotalExperience(315));
        assertEquals(31, ExperienceMath.levelForTotalExperience(1627));
        assertEquals(32, ExperienceMath.levelForTotalExperience(1628));

        assertEquals(6, ExperienceMath.experienceIntoLevel(6));
        assertEquals(0, ExperienceMath.experienceIntoLevel(7));
        assertEquals(120, ExperienceMath.experienceIntoLevel(1627));
    }

    @Test
    void combinesAppliedExperiencedPointsAndFluidXpPoints() {
        assertEquals(42, ExperienceMath.totalExperience(40, 2));
        assertEquals(47, ExperienceMath.totalExperience(40, 7));
    }

    @Test
    void combinesPlayerFluidAndAppliedExperiencedAmountPoints() {
        assertEquals(57, ExperienceMath.totalExperience(10, 20, 27));
    }

    @Test
    void worksWithOnlyFluidXpWhenAppliedExperiencedIsAbsent() {
        assertEquals(7, ExperienceMath.totalExperience(0, 7));
        assertEquals(1, ExperienceMath.levelForTotalExperience(ExperienceMath.totalExperience(0, 7)));
        assertEquals(0, ExperienceMath.experienceIntoLevel(ExperienceMath.totalExperience(0, 7)));
    }

    @Test
    void choosesAnvilPriorityFromFluidAvailability() {
        assertEquals(List.of(PLAYER, APPLIED_EXPERIENCED_AMOUNT), ExperienceMath.anvilSourcePriority(0));
        assertEquals(List.of(PLAYER, FLUID_XP, APPLIED_EXPERIENCED_AMOUNT), ExperienceMath.anvilSourcePriority(1));
        assertEquals(List.of(PLAYER, APPLIED_EXPERIENCED_AMOUNT), ExperienceMath.anvilSourcePriority(false));
        assertEquals(List.of(PLAYER, FLUID_XP, APPLIED_EXPERIENCED_AMOUNT), ExperienceMath.anvilSourcePriority(true));
    }

    @Test
    void plansExperienceUsingDefaultAnvilPriority() {
        var experience = ExperienceMath.planConsumption(75,
                new ExperienceMath.ExperienceAmounts(30, 40, 50),
                ExperienceMath.DEFAULT_ANVIL_SOURCE_PRIORITY);

        assertTrue(experience.enough());
        assertTrue(experience.canPay());
        assertEquals(75, experience.totalUsed());
        assertEquals(new ExperienceMath.ExperienceAmounts(30, 40, 5), experience.usedAmounts());
        assertEquals(75, experience.required());
        assertEquals(120, experience.available());
        assertEquals(120, experience.spendable());
        assertEquals(30, experience.player());
        assertEquals(40, experience.fluidXp());
        assertEquals(5, experience.appliedExperiencedAmount());
        assertEquals(0, experience.missing());
        assertEquals(30, experience.used(PLAYER));
        assertEquals(40, experience.used(FLUID_XP));
        assertEquals(5, experience.used(APPLIED_EXPERIENCED_AMOUNT));
    }

    @Test
    void plansExperienceUsingCustomPriority() {
        var experience = ExperienceMath.planConsumption(75,
                new ExperienceMath.ExperienceAmounts(30, 40, 50),
                List.of(APPLIED_EXPERIENCED_AMOUNT, FLUID_XP, PLAYER));

        assertTrue(experience.enough());
        assertEquals(120, experience.spendable());
        assertEquals(0, experience.player());
        assertEquals(25, experience.fluidXp());
        assertEquals(50, experience.appliedExperiencedAmount());
    }

    @Test
    void reportsMissingExperienceWhenPrioritySourcesAreInsufficient() {
        var experience = ExperienceMath.planConsumption(75,
                new ExperienceMath.ExperienceAmounts(30, 40, 50), List.of(PLAYER, FLUID_XP));

        assertFalse(experience.enough());
        assertEquals(120, experience.available());
        assertEquals(70, experience.spendable());
        assertEquals(30, experience.player());
        assertEquals(40, experience.fluidXp());
        assertEquals(0, experience.appliedExperiencedAmount());
        assertEquals(5, experience.missing());
    }

    @Test
    void ignoresDuplicatePriorityEntriesAfterFirstUse() {
        var experience = ExperienceMath.planConsumption(70,
                new ExperienceMath.ExperienceAmounts(30, 40, 50), List.of(PLAYER, PLAYER, FLUID_XP));

        assertTrue(experience.enough());
        assertEquals(70, experience.spendable());
        assertEquals(30, experience.player());
        assertEquals(40, experience.fluidXp());
        assertEquals(0, experience.appliedExperiencedAmount());
    }

    @Test
    void plansFromNamedSourceAmountsWithoutAmbiguousPositionalArguments() {
        var amounts = new ExperienceMath.ExperienceAmounts(30, 40, 50);
        var plan = ExperienceMath.planConsumption(75, amounts,
                List.of(APPLIED_EXPERIENCED_AMOUNT, PLAYER, FLUID_XP));

        assertTrue(plan.canPay());
        assertEquals(50, plan.appliedExperiencedAmount());
        assertEquals(25, plan.player());
        assertEquals(0, plan.fluidXp());
        assertEquals(120, amounts.total());
        assertEquals(40, amounts.amount(FLUID_XP));
    }

    @Test
    void rejectsNegativeExperienceInputs() {
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperience(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperience(0, -1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperience(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperience(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperience(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.planConsumption(-1,
                ExperienceMath.ExperienceAmounts.ZERO, ExperienceMath.DEFAULT_ANVIL_SOURCE_PRIORITY));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.totalExperienceForLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.levelForTotalExperience(-1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.vanillaAnvilExperienceCost(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.vanillaAnvilExperienceCost(0, -1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.vanillaAnvilExperienceCost(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ExperienceMath.vanillaAnvilExperienceCost(30, 1, -0.01F));
        assertThrows(IllegalArgumentException.class,
                () -> ExperienceMath.vanillaAnvilExperienceCost(30, 1, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> ExperienceMath.vanillaAnvilExperienceCost(30, 1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.apothicAnvilExperienceCost(-1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.apothicEnchantingTableExperienceCost(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.apothicEnchantingTableExperienceCost(0, -1));
        assertThrows(IllegalArgumentException.class, () -> ExperienceMath.apothicLibraryPointsForLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> new ExperienceMath.ExperienceAmounts(-1, 0, 0));
    }

    @Test
    void handlesLargeExperienceInputsWithoutLinearScanningOrOverflow() {
        int level = ExperienceMath.levelForTotalExperience(Long.MAX_VALUE);

        assertEquals(1_431_655_783, level);
        assertEquals(9_223_372_031_843_981_383L, ExperienceMath.totalExperienceForLevel(level));
        assertEquals(5_010_794_424L, ExperienceMath.experienceIntoLevel(Long.MAX_VALUE));
        assertEquals(12_884_901_889L, ExperienceMath.experienceToNextLevel(level));
        assertThrows(ArithmeticException.class, () -> ExperienceMath.totalExperienceForLevel(level + 1));
        assertThrows(ArithmeticException.class, () -> ExperienceMath.totalExperienceForLevel(Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> ExperienceMath.experienceToNextLevel(Integer.MAX_VALUE));
    }

    @Test
    void computesVanillaAndApothicAnvilCostsSeparately() {
        assertEquals(ExperienceMath.totalExperienceForLevel(100) - ExperienceMath.totalExperienceForLevel(70),
                ExperienceMath.vanillaAnvilExperienceCost(100, 30));
        assertEquals(ExperienceMath.totalExperienceForLevel(30), ExperienceMath.apothicAnvilExperienceCost(30));
        assertTrue(ExperienceMath.vanillaAnvilExperienceCost(100, 30) > ExperienceMath.apothicAnvilExperienceCost(30));
        assertEquals(107, ExperienceMath.vanillaAnvilExperienceCost(30, 1));
        assertEquals(109, ExperienceMath.vanillaAnvilExperienceCost(30, 1, 0.5F));
        assertEquals(0, ExperienceMath.vanillaAnvilExperienceCost(30, 0, 0.75F));
    }

    @Test
    void computesApothicEnchantingTableOfferCosts() {
        assertEquals(ExperienceMath.experienceToNextLevel(29) - 1,
                ExperienceMath.apothicEnchantingTableExperienceCost(30, 0));
        assertEquals(ExperienceMath.experienceToNextLevel(29) + ExperienceMath.experienceToNextLevel(28) - 1,
                ExperienceMath.apothicEnchantingTableExperienceCost(30, 1));
        assertEquals(ExperienceMath.experienceToNextLevel(29) + ExperienceMath.experienceToNextLevel(28)
                        + ExperienceMath.experienceToNextLevel(27) - 1,
                ExperienceMath.apothicEnchantingTableExperienceCost(30, 2));
        assertEquals(0, ExperienceMath.apothicEnchantingTableExperienceCost(0, 2));
        assertEquals(ExperienceMath.totalExperienceForLevel(30) - 1,
                ExperienceMath.apothicEnchantingTableExperienceCost(30, Integer.MAX_VALUE));
    }

    @Test
    void computesApothicLibraryPointsSeparatelyFromRawXp() {
        assertEquals(0, ExperienceMath.apothicLibraryPointsForLevel(0));
        assertEquals(1, ExperienceMath.apothicLibraryPointsForLevel(1));
        assertEquals(2, ExperienceMath.apothicLibraryPointsForLevel(2));
        assertEquals(4, ExperienceMath.apothicLibraryPointsForLevel(3));
        assertEquals(8, ExperienceMath.apothicLibraryPointsForLevel(4));
        assertThrows(ArithmeticException.class, () -> ExperienceMath.apothicLibraryPointsForLevel(64));
        assertThrows(ArithmeticException.class, () -> ExperienceMath.apothicLibraryPointsForLevel(65));
    }
}
