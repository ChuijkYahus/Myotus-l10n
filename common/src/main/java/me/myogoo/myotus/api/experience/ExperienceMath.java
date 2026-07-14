package me.myogoo.myotus.api.experience;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility methods and stable identifiers for experience values used by Myotus integrations.
 *
 * <p>Applied Experienced stores experience as raw vanilla experience points. This class keeps that
 * representation explicit and provides vanilla level conversion helpers so addons can safely bridge
 * player experience, Applied Experienced values, and AE/fluid entries such as {@code fluid:xp}.</p>
 */
public final class ExperienceMath {
    /** Applied Experienced mod id. */
    public static final String APPLIED_EXPERIENCED_MOD_ID = "appex";

    /** Applied Experienced AE key id for raw experience points. */
    public static final String APPLIED_EXPERIENCED_AE_KEY_ID = "appex:experience";

    /** Applied Experienced item/block data component id storing raw experience points. */
    public static final String APPLIED_EXPERIENCED_AMOUNT_COMPONENT_ID = "appex:experience_amount";

    /** AE/fluid entry id commonly used for XP fluid stacks. */
    public static final String FLUID_XP_ID = "fluid:xp";

    /** Common fluid tag used by Applied Experienced on NeoForge for experience-compatible fluids. */
    public static final String COMMON_EXPERIENCE_FLUID_TAG_ID = "c:experience";

    /** Consumption order for anvil-like UIs when fluid XP is available. */
    public static final List<ExperienceSource> DEFAULT_ANVIL_SOURCE_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            ExperienceSource.PLAYER,
            ExperienceSource.FLUID_XP,
            ExperienceSource.APPLIED_EXPERIENCED_AMOUNT));

    /** Consumption order for anvil-like UIs when no fluid XP source is available. */
    public static final List<ExperienceSource> ANVIL_SOURCE_PRIORITY_WITHOUT_FLUID_XP = Collections.unmodifiableList(Arrays.asList(
            ExperienceSource.PLAYER,
            ExperienceSource.APPLIED_EXPERIENCED_AMOUNT));

    private ExperienceMath() {
    }

    /**
     * Raw XP source types understood by Myotus' experience helper APIs.
     */
    public enum ExperienceSource {
        /** Raw points currently owned by the player. */
        PLAYER,
        /** Raw points represented by {@code fluid:xp}. */
        FLUID_XP,
        /** Raw points stored in Applied Experienced's {@code appex:experience_amount}. */
        APPLIED_EXPERIENCED_AMOUNT
    }

    /**
     * Immutable raw-XP balances grouped by source.
     *
     * <p>Values passed here must already be normalized to vanilla experience points. In particular,
     * fluid storage units such as mB must be converted by the caller before constructing this value.</p>
     *
     * @param player raw player experience points
     * @param fluidXp raw experience points represented by an explicitly converted fluid source
     * @param appliedExperiencedAmount raw Applied Experienced points
     */
    public record ExperienceAmounts(long player, long fluidXp, long appliedExperiencedAmount) {
        public static final ExperienceAmounts ZERO = new ExperienceAmounts(0, 0, 0);

        public ExperienceAmounts {
            requireNonNegative(player, "player");
            requireNonNegative(fluidXp, "fluidXp");
            requireNonNegative(appliedExperiencedAmount, "appliedExperiencedAmount");
        }

        /** Returns the amount available from {@code source}. */
        public long amount(ExperienceSource source) {
            Objects.requireNonNull(source, "source");
            return switch (source) {
                case PLAYER -> player;
                case FLUID_XP -> fluidXp;
                case APPLIED_EXPERIENCED_AMOUNT -> appliedExperiencedAmount;
            };
        }

        /** Returns the exact total of all source balances. */
        public long total() {
            return totalExperience(player, fluidXp, appliedExperiencedAmount);
        }
    }

    /**
     * Immutable Myotus XP spending result for multiple source pools in a caller-provided priority order.
     *
     * @param required raw XP points requested by the caller
     * @param available raw XP points available across all supplied sources, including non-priority sources
     * @param spendable raw XP points available from sources included in the priority list
     * @param missing raw XP points still missing after priority sources are considered
     * @param player raw player XP points to consume
     * @param fluidXp raw {@code fluid:xp} points to consume
     * @param appliedExperiencedAmount raw Applied Experienced amount points to consume
     */
    public record MyoExperience(long required, long available, long spendable, long missing, long player, long fluidXp,
            long appliedExperiencedAmount) {
        public MyoExperience {
            requireNonNegative(required, "required");
            requireNonNegative(available, "available");
            requireNonNegative(spendable, "spendable");
            requireNonNegative(missing, "missing");
            requireNonNegative(player, "player");
            requireNonNegative(fluidXp, "fluidXp");
            requireNonNegative(appliedExperiencedAmount, "appliedExperiencedAmount");

            long used = Math.addExact(Math.addExact(player, fluidXp), appliedExperiencedAmount);
            if (spendable > available) {
                throw new IllegalArgumentException("spendable must not exceed available");
            }
            if (used > spendable) {
                throw new IllegalArgumentException("used experience must not exceed spendable experience");
            }
            if (used > required || missing != required - used) {
                throw new IllegalArgumentException("used and missing experience must equal required experience");
            }
        }

        /** Returns {@code true} when all required XP can be paid from spendable sources. */
        public boolean enough() {
            return missing == 0;
        }

        /** Returns {@code true} when all required XP can be paid from spendable sources. */
        public boolean canPay() {
            return enough();
        }

        /** Returns the exact amount assigned across all sources. */
        public long totalUsed() {
            return Math.addExact(Math.addExact(player, fluidXp), appliedExperiencedAmount);
        }

        /** Returns the source-by-source amounts assigned by this plan. */
        public ExperienceAmounts usedAmounts() {
            return new ExperienceAmounts(player, fluidXp, appliedExperiencedAmount);
        }

        /** Returns the amount used for a specific source. */
        public long used(ExperienceSource source) {
            Objects.requireNonNull(source, "source");
            if (source == ExperienceSource.PLAYER) {
                return player;
            }
            if (source == ExperienceSource.FLUID_XP) {
                return fluidXp;
            }
            return appliedExperiencedAmount;
        }
    }

    /**
     * Adds Applied Experienced raw points and {@code fluid:xp} raw points into one vanilla XP total.
     *
     * @param appliedExperience raw points reported by Applied Experienced
     * @param fluidXp raw points represented by {@code fluid:xp}
     * @return combined raw vanilla experience points
     */
    public static long totalExperience(long appliedExperience, long fluidXp) {
        requireNonNegative(appliedExperience, "appliedExperience");
        requireNonNegative(fluidXp, "fluidXp");
        return Math.addExact(appliedExperience, fluidXp);
    }

    /**
     * Adds player XP, {@code fluid:xp}, and Applied Experienced amount raw points into one vanilla XP total.
     *
     * @param playerExperience raw player XP points
     * @param fluidXp raw points represented by {@code fluid:xp}
     * @param appliedExperienceAmount raw points stored by Applied Experienced's amount component
     * @return combined raw vanilla experience points
     */
    public static long totalExperience(long playerExperience, long fluidXp, long appliedExperienceAmount) {
        requireNonNegative(playerExperience, "playerExperience");
        requireNonNegative(fluidXp, "fluidXp");
        requireNonNegative(appliedExperienceAmount, "appliedExperienceAmount");
        return Math.addExact(Math.addExact(playerExperience, fluidXp), appliedExperienceAmount);
    }

    /**
     * Returns the anvil source priority for the caller's available XP pools.
     *
     * <p>Fluid XP is included only when the caller reports a positive raw fluid XP amount. This keeps
     * mod/fluid detection outside the math API and avoids guessing from registry ids.</p>
     *
     * @param fluidXp available raw {@code fluid:xp} points
     * @return immutable source priority for anvil-like consumption
     */
    public static List<ExperienceSource> anvilSourcePriority(long fluidXp) {
        requireNonNegative(fluidXp, "fluidXp");
        return anvilSourcePriority(fluidXp > 0);
    }

    /**
     * Returns the anvil source priority for an explicit fluid XP availability flag.
     *
     * @param includeFluidXp whether {@link ExperienceSource#FLUID_XP} should be considered
     * @return immutable source priority for anvil-like consumption
     */
    public static List<ExperienceSource> anvilSourcePriority(boolean includeFluidXp) {
        if (includeFluidXp) {
            return DEFAULT_ANVIL_SOURCE_PRIORITY;
        }
        return ANVIL_SOURCE_PRIORITY_WITHOUT_FLUID_XP;
    }

    /**
     * Creates a pure, non-mutating consumption plan from normalized raw-XP balances.
     *
     * @param requiredExperience raw XP points to spend
     * @param availableAmounts normalized raw-XP balances
     * @param sourcePriority source order to use; duplicate entries are ignored
     * @return immutable source-by-source consumption plan
     */
    public static MyoExperience planConsumption(long requiredExperience, ExperienceAmounts availableAmounts,
            List<ExperienceSource> sourcePriority) {
        requireNonNegative(requiredExperience, "requiredExperience");
        Objects.requireNonNull(availableAmounts, "availableAmounts");
        Objects.requireNonNull(sourcePriority, "sourcePriority");

        long availableExperience = availableAmounts.total();
        Map<ExperienceSource, Long> remainingBySource = new EnumMap<>(ExperienceSource.class);
        for (ExperienceSource source : ExperienceSource.values()) {
            remainingBySource.put(source, availableAmounts.amount(source));
        }

        Map<ExperienceSource, Long> usedBySource = new EnumMap<>(ExperienceSource.class);
        for (ExperienceSource source : ExperienceSource.values()) {
            usedBySource.put(source, 0L);
        }

        long remainingRequirement = requiredExperience;
        long spendableExperience = 0;
        Set<ExperienceSource> prioritySources = EnumSet.noneOf(ExperienceSource.class);
        for (ExperienceSource source : sourcePriority) {
            Objects.requireNonNull(source, "sourcePriority contains null");
            if (!prioritySources.add(source)) {
                continue;
            }
            long availableFromSource = remainingBySource.get(source);
            spendableExperience = Math.addExact(spendableExperience, availableFromSource);
            if (remainingRequirement == 0) {
                continue;
            }
            long used = Math.min(availableFromSource, remainingRequirement);
            usedBySource.put(source, used);
            remainingRequirement -= used;
        }

        return new MyoExperience(
                requiredExperience,
                availableExperience,
                spendableExperience,
                remainingRequirement,
                usedBySource.get(ExperienceSource.PLAYER),
                usedBySource.get(ExperienceSource.FLUID_XP),
                usedBySource.get(ExperienceSource.APPLIED_EXPERIENCED_AMOUNT));
    }

    /**
     * Returns the amount of raw vanilla experience needed to reach the start of {@code level}.
     *
     * @param level vanilla experience level
     * @return total raw experience points at that level boundary
     */
    public static long totalExperienceForLevel(int level) {
        requireNonNegative(level, "level");
        if (level <= 16) {
            return Math.addExact(Math.multiplyExact((long) level, level), Math.multiplyExact(6L, level));
        }
        if (level <= 31) {
            long squaredTerm = Math.multiplyExact(Math.multiplyExact(5L, level), level);
            long linearTerm = Math.multiplyExact(81L, level);
            return Math.floorDiv(Math.addExact(Math.subtractExact(squaredTerm, linearTerm), 720L), 2L);
        }
        long levelFactor = level;
        long growthFactor = Math.subtractExact(Math.multiplyExact(9L, level), 325L);
        if ((level & 1) == 0) {
            levelFactor /= 2L;
        } else {
            growthFactor /= 2L;
        }
        return Math.addExact(Math.multiplyExact(levelFactor, growthFactor), 2220L);
    }

    /**
     * Returns the vanilla level that contains {@code totalExperience}.
     *
     * @param totalExperience raw vanilla experience points
     * @return level at or below the supplied total
     */
    public static int levelForTotalExperience(long totalExperience) {
        requireNonNegative(totalExperience, "totalExperience");
        int low = 0;
        int high = Integer.MAX_VALUE;
        while (low < high) {
            int candidate = low + (int) (((long) high - low + 1L) / 2L);
            if (isLevelAtOrBelowExperience(candidate, totalExperience)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    /**
     * Returns how many raw points are already filled inside the current level.
     *
     * @param totalExperience raw vanilla experience points
     * @return progress points within {@link #levelForTotalExperience(long)}
     */
    public static long experienceIntoLevel(long totalExperience) {
        int level = levelForTotalExperience(totalExperience);
        return totalExperience - totalExperienceForLevel(level);
    }

    /**
     * Returns how many raw points are required to advance from {@code level} to the next level.
     *
     * @param level vanilla experience level
     * @return raw points required for the next level
     */
    public static long experienceToNextLevel(int level) {
        requireNonNegative(level, "level");
        if (level == Integer.MAX_VALUE) {
            throw new ArithmeticException("No representable level exists after Integer.MAX_VALUE");
        }
        if (level >= 30) {
            return Math.addExact(112L, Math.multiplyExact((long) level - 30L, 9L));
        }
        if (level >= 15) {
            return Math.addExact(37L, Math.multiplyExact((long) level - 15L, 5L));
        }
        return Math.addExact(7L, Math.multiplyExact((long) level, 2L));
    }

    /**
     * Returns raw XP consumed by vanilla anvil-like level costs from a player's current level.
     *
     * <p>Vanilla anvils remove levels from the player's current level, so a cost of 30 is much
     * larger for a level-100 player than for a level-30 player.</p>
     *
     * <p>This overload assumes the player's progress bar is empty. Use
     * {@link #vanillaAnvilExperienceCost(int, int, float)} when the current progress must be preserved.</p>
     *
     * @param currentLevel player's current vanilla experience level
     * @param levelCost displayed level cost
     * @return raw XP represented by removing {@code levelCost} levels from {@code currentLevel}
     * @throws IllegalArgumentException if {@code levelCost} exceeds {@code currentLevel}
     */
    public static long vanillaAnvilExperienceCost(int currentLevel, int levelCost) {
        return vanillaAnvilExperienceCost(currentLevel, levelCost, 0.0F);
    }

    /**
     * Returns raw XP consumed when vanilla removes levels while retaining the player's progress fraction.
     *
     * @param currentLevel player's current vanilla experience level
     * @param levelCost displayed level cost
     * @param experienceProgress current progress in the range {@code [0, 1)}
     * @return raw XP represented by the player's state change
     * @throws IllegalArgumentException if the level cost is unaffordable or the progress is invalid
     */
    public static long vanillaAnvilExperienceCost(int currentLevel, int levelCost, float experienceProgress) {
        requireNonNegative(currentLevel, "currentLevel");
        requireNonNegative(levelCost, "levelCost");
        requireValidExperienceProgress(experienceProgress);
        if (levelCost > currentLevel) {
            throw new IllegalArgumentException("levelCost must not exceed currentLevel");
        }

        int targetLevel = currentLevel - levelCost;
        long currentExperience = Math.addExact(totalExperienceForLevel(currentLevel),
                experiencePointsAtProgress(currentLevel, experienceProgress));
        long targetExperience = Math.addExact(totalExperienceForLevel(targetLevel),
                experiencePointsAtProgress(targetLevel, experienceProgress));
        return Math.subtractExact(currentExperience, targetExperience);
    }

    /**
     * Returns raw XP consumed by Apothic Enchanting/Apotheosis' optimal anvil level policy.
     *
     * <p>Apothic charges the raw XP required to reach the displayed level cost itself, instead of
     * subtracting that many levels from the player's current level.</p>
     *
     * @param levelCost displayed anvil level cost
     * @return raw XP for Apothic's anvil cost policy
     */
    public static long apothicAnvilExperienceCost(int levelCost) {
        requireNonNegative(levelCost, "levelCost");
        return totalExperienceForLevel(levelCost);
    }

    /**
     * Returns raw XP consumed by Apothic Enchanting's enchanting table slot policy.
     *
     * <p>Slot 0 charges the step XP for {@code level}, slot 1 adds {@code level - 1}, and slot 2
     * adds {@code level - 2}; Apothic then subtracts one raw point from the summed cost.</p>
     *
     * @param level generated enchanting level shown by the table
     * @param slot zero-based enchantment offer slot
     * @return raw XP cost for the Apothic enchanting table offer
     */
    public static long apothicEnchantingTableExperienceCost(int level, int slot) {
        requireNonNegative(level, "level");
        requireNonNegative(slot, "slot");
        long lowerBoundary = Math.max(0L, (long) level - slot - 1L);
        long cost = Math.subtractExact(totalExperienceForLevel(level),
                totalExperienceForLevel((int) lowerBoundary));
        return Math.max(0, cost - 1);
    }

    /**
     * Returns Apothic Enchanting Library points represented by an enchantment level.
     *
     * <p>These are not raw XP points. The library stores enchantment power as {@code 2^(level - 1)}
     * points, so callers should not mix this value with {@code fluid:xp} or Applied Experienced raw XP.</p>
     *
     * @param level enchantment level
     * @return Apothic library points for the supplied enchantment level
     */
    public static long apothicLibraryPointsForLevel(int level) {
        requireNonNegative(level, "level");
        if (level == 0) {
            return 0;
        }
        if (level >= Long.SIZE) {
            throw new ArithmeticException("Apothic library points overflow long for level " + level);
        }
        return 1L << (level - 1);
    }

    private static boolean isLevelAtOrBelowExperience(int level, long totalExperience) {
        try {
            return totalExperienceForLevel(level) <= totalExperience;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static long experiencePointsAtProgress(int level, float experienceProgress) {
        long needed = experienceToNextLevel(level);
        long points = needed <= Integer.MAX_VALUE
                ? Math.round(experienceProgress * (int) needed)
                : Math.round((double) experienceProgress * needed);
        return Math.min(points, needed - 1L);
    }

    private static void requireValidExperienceProgress(float experienceProgress) {
        if (!Float.isFinite(experienceProgress) || experienceProgress < 0.0F || experienceProgress >= 1.0F) {
            throw new IllegalArgumentException("experienceProgress must be finite and in [0, 1)");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
