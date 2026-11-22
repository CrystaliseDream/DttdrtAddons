package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.features.mining.CrystalHollowsTracker.Crystal;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker.CrystalLocation;
import com.dttdrt.addons.util.NotificationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Pure client-side Wishing Compass 3D triangulation solver (ported from NEU's algorithm).
 * Captures two-pass HAPPY_VILLAGER particle trails from two compass uses >= 8 blocks apart,
 * computes the closest midpoint between the two 3D rays, identifies the target structure,
 * and marks it on the Crystal Hollows Map HUD.
 */
public class CrystalWishingCompassSolver {
    private enum SolverState {
        NOT_STARTED,
        PROCESSING_FIRST_USE,
        NEED_SECOND_COMPASS,
        PROCESSING_SECOND_USE,
        SOLVED
    }

    private enum CompassState {
        WAITING_FOR_FIRST_PARTICLE,
        COMPUTING_LAST_PARTICLE,
        COMPLETED,
        FAILED_TIMEOUT
    }

    public enum CompassTarget {
        GOBLIN_QUEEN("Goblin Queen's Den", CrystalLocation.GOBLIN_QUEENS_DEN),
        GOBLIN_KING("King Yolkar", CrystalLocation.KING_YOLKAR),
        BAL("Khazad-dum (Bal)", CrystalLocation.KHAZAD_DUM),
        JUNGLE_TEMPLE("Jungle Temple", CrystalLocation.JUNGLE_TEMPLE),
        ODAWA("Odawa", CrystalLocation.ODAWA),
        PRECURSOR_CITY("Lost Precursor City", CrystalLocation.LOST_PRECURSOR_CITY),
        MINES_OF_DIVAN("Mines of Divan", CrystalLocation.MINES_OF_DIVAN),
        CRYSTAL_NUCLEUS("Crystal Nucleus", null);

        public final String label;
        public final CrystalLocation mapLocation;

        CompassTarget(String label, CrystalLocation mapLocation) {
            this.label = label;
            this.mapLocation = mapLocation;
        }
    }

    private enum HollowsZone {
        CRYSTAL_NUCLEUS,
        JUNGLE,
        MITHRIL_DEPOSITS,
        GOBLIN_HOLDOUT,
        PRECURSOR_REMNANTS,
        MAGMA_FIELDS
    }

    private static final CrystalWishingCompassSolver INSTANCE = new CrystalWishingCompassSolver();

    public static CrystalWishingCompassSolver getInstance() {
        return INSTANCE;
    }

    // Hollows & zone bounding boxes (exact Crystal Hollows coordinates)
    private static final AABB NUCLEUS_BB = new AABB(462, 63, 461, 564, 181, 565);
    private static final AABB HOLLOWS_BB = new AABB(201, 30, 201, 824, 189, 824);

    private static final AABB PRECURSOR_REMNANTS_BB = new AABB(512, 63, 512, 824, 189, 824);
    private static final AABB MITHRIL_DEPOSITS_BB = new AABB(512, 63, 201, 824, 189, 513);
    private static final AABB GOBLIN_HOLDOUT_BB = new AABB(201, 63, 512, 513, 189, 824);
    private static final AABB JUNGLE_BB = new AABB(201, 63, 201, 513, 189, 513);
    private static final AABB MAGMA_FIELDS_BB = new AABB(201, 30, 201, 824, 64, 824);

    // Structure sizes for quadrant overlap filtering
    private static final double PRECURSOR_CITY_SIZE = 107;
    private static final double GOBLIN_KING_SIZE = 59;
    private static final double GOBLIN_QUEEN_SIZE = 108;
    private static final double JUNGLE_TEMPLE_SIZE = 108;
    private static final double ODAWA_SIZE = 54;
    private static final double MINES_OF_DIVAN_SIZE = 108;

    private static final Vec3 JUNGLE_DOOR_OFFSET = new Vec3(-57, 36, -21);
    private static final double MAX_DISTANCE_BETWEEN_PARTICLES = 0.6;
    private static final double MAX_DISTANCE_FROM_USE_TO_FIRST_PARTICLE = 9.0;
    private static final double MINIMUM_DISTANCE_SQ_BETWEEN_COMPASSES = 64.0;
    private static final long ALL_PARTICLES_MAX_MILLIS = 5000L;

    private SolverState solverState = SolverState.NOT_STARTED;
    private CompassUse firstCompass;
    private CompassUse secondCompass;
    private EnumSet<CompassTarget> possibleTargets;

    private CrystalWishingCompassSolver() {}

    public synchronized void reset() {
        solverState = SolverState.NOT_STARTED;
        firstCompass = null;
        secondCompass = null;
        possibleTargets = null;
    }

    public synchronized void onWishingCompassUsed(Minecraft mc, BlockPos playerPos) {
        if (!CrystalHollowsTracker.isInCrystalHollows()) return;

        long now = System.currentTimeMillis();
        if (solverState == SolverState.SOLVED) {
            reset();
        }

        if (solverState == SolverState.PROCESSING_FIRST_USE || solverState == SolverState.PROCESSING_SECOND_USE) {
            CompassUse active = (solverState == SolverState.PROCESSING_SECOND_USE) ? secondCompass : firstCompass;
            if (active != null && now - active.whenUsedMillis > ALL_PARTICLES_MAX_MILLIS) {
                reset();
            } else {
                return;
            }
        }

        if (solverState == SolverState.NOT_STARTED) {
            Vec3 posVec = new Vec3(playerPos.getX(), playerPos.getY(), playerPos.getZ());
            if (NUCLEUS_BB.contains(posVec)) {
                NotificationUtils.sendClientChatMessage(Component.literal("§eWishing Compass must be used outside Crystal Nucleus."));
                return;
            }
            firstCompass = new CompassUse(playerPos, now);
            possibleTargets = calculatePossibleTargets(mc);
            solverState = SolverState.PROCESSING_FIRST_USE;
        } else if (solverState == SolverState.NEED_SECOND_COMPASS) {
            if (firstCompass.whereUsed.distSqr(playerPos) < MINIMUM_DISTANCE_SQ_BETWEEN_COMPASSES) {
                NotificationUtils.sendClientChatMessage(Component.literal("§eMove at least 8 blocks away for the 2nd Wishing Compass reading."));
                return;
            }

            HollowsZone z1 = getZoneForCoords(new Vec3(firstCompass.whereUsed.getX(), firstCompass.whereUsed.getY(), firstCompass.whereUsed.getZ()));
            HollowsZone z2 = getZoneForCoords(new Vec3(playerPos.getX(), playerPos.getY(), playerPos.getZ()));
            EnumSet<CompassTarget> currentPossible = calculatePossibleTargets(mc);
            if (z1 != z2 || !currentPossible.equals(possibleTargets)) {
                reset();
                firstCompass = new CompassUse(playerPos, now);
                possibleTargets = currentPossible;
                solverState = SolverState.PROCESSING_FIRST_USE;
                NotificationUtils.sendClientChatMessage(Component.literal("§eZone or target changed; started new Wishing Compass reading."));
                return;
            }

            secondCompass = new CompassUse(playerPos, now);
            solverState = SolverState.PROCESSING_SECOND_USE;
        }
    }

    public synchronized void onHappyVillagerParticle(double x, double y, double z) {
        if (solverState != SolverState.PROCESSING_FIRST_USE && solverState != SolverState.PROCESSING_SECOND_USE) {
            return;
        }

        long now = System.currentTimeMillis();
        CompassUse current = (solverState == SolverState.PROCESSING_FIRST_USE) ? firstCompass : secondCompass;
        if (current == null) return;

        current.processParticle(x, y, z, now);
        if (current.state == CompassState.FAILED_TIMEOUT) {
            NotificationUtils.sendClientChatMessage(Component.literal("§cWishing Compass particle capture timed out."));
            reset();
            return;
        }
        if (current.state != CompassState.COMPLETED) {
            return;
        }

        if (solverState == SolverState.PROCESSING_FIRST_USE) {
            solverState = SolverState.NEED_SECOND_COMPASS;
            NotificationUtils.sendClientChatMessage(Component.literal("§a[1/2] Compass ray recorded. Move 8+ blocks away and use again."));
            return;
        }

        // Second compass completed: compute 3D skew-line intersection midpoint
        Vec3 solution = closestMidpointBetweenRays(
                firstCompass.firstParticle, firstCompass.lastParticle,
                secondCompass.firstParticle, secondCompass.lastParticle
        );

        if (solution == null || !HOLLOWS_BB.contains(solution)) {
            NotificationUtils.sendClientChatMessage(Component.literal("§cCould not triangulate Wishing Compass target. Try a wider angle."));
            reset();
            return;
        }

        Vec3 dir1 = firstCompass.lastParticle.subtract(firstCompass.firstParticle).normalize();
        Vec3 toSol1 = solution.subtract(firstCompass.firstParticle).normalize();
        Vec3 dir2 = secondCompass.lastParticle.subtract(secondCompass.firstParticle).normalize();
        Vec3 toSol2 = solution.subtract(secondCompass.firstParticle).normalize();

        if (dir1.dot(toSol1) <= 0 || dir2.dot(toSol2) <= 0) {
            NotificationUtils.sendClientChatMessage(Component.literal("§cWishing Compass rays diverged. Try another position."));
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        EnumSet<CompassTarget> targets = filterTargetsBySolution(
                getZoneForCoords(new Vec3(firstCompass.whereUsed.getX(), firstCompass.whereUsed.getY(), firstCompass.whereUsed.getZ())),
                CrystalHollowsTracker.getFoundCrystals(mc),
                possibleTargets,
                solution
        );

        if (targets.size() == 1 && targets.contains(CompassTarget.JUNGLE_TEMPLE)) {
            solution = solution.add(JUNGLE_DOOR_OFFSET);
        }

        solverState = SolverState.SOLVED;
        reportAndMarkSolution(targets, solution);
    }

    private void reportAndMarkSolution(EnumSet<CompassTarget> targets, Vec3 solution) {
        if (NUCLEUS_BB.contains(solution)) {
            CrystalHollowsTracker.setActiveWishingWaypoint(null);
            NotificationUtils.sendClientChatMessage(Component.literal("§bWishing Compass points to §dCrystal Nucleus §7(513, 107, 513)"));
            return;
        }

        StringBuilder names = new StringBuilder();
        boolean mappedToLocation = false;
        for (CompassTarget t : targets) {
            if (names.length() > 0) names.append(" or ");
            names.append(t.label);
            if (targets.size() == 1 && t.mapLocation != null) {
                CrystalHollowsTracker.setManualLocation(t.mapLocation, solution.x, solution.y, solution.z);
                mappedToLocation = true;
            }
        }

        if (mappedToLocation) {
            CrystalHollowsTracker.setActiveWishingWaypoint(null);
        } else {
            CrystalHollowsTracker.setActiveWishingWaypoint(
                    new CrystalHollowsTracker.CustomWaypoint(names.toString(), solution.x, solution.y, solution.z, 0xFF55FFFF));
        }

        String coords = String.format("%.0f, %.0f, %.0f", solution.x, solution.y, solution.z);
        NotificationUtils.sendClientChatMessage(Component.literal("§bWishing Compass -> §e" + names + " §7(" + coords + ")"));
    }

    private EnumSet<CompassTarget> calculatePossibleTargets(Minecraft mc) {
        EnumSet<CompassTarget> candidates = EnumSet.of(CompassTarget.CRYSTAL_NUCLEUS);
        EnumSet<Crystal> found = CrystalHollowsTracker.getFoundCrystals(mc);

        for (Crystal c : Crystal.values()) {
            if (found.contains(c)) continue;
            switch (c) {
                case JADE -> candidates.add(CompassTarget.MINES_OF_DIVAN);
                case AMBER -> candidates.add(
                        CrystalHollowsTracker.isKingsScentPresent(mc)
                                ? CompassTarget.GOBLIN_QUEEN
                                : CompassTarget.GOBLIN_KING);
                case TOPAZ -> candidates.add(CompassTarget.BAL);
                case AMETHYST -> candidates.add(
                        CrystalHollowsTracker.isJungleKeyInInventory(mc)
                                ? CompassTarget.JUNGLE_TEMPLE
                                : CompassTarget.ODAWA);
                case SAPPHIRE -> candidates.add(CompassTarget.PRECURSOR_CITY);
            }
        }
        return candidates;
    }

    private static EnumSet<CompassTarget> filterTargetsBySolution(
            HollowsZone usedZone,
            EnumSet<Crystal> foundCrystals,
            EnumSet<CompassTarget> initialCandidates,
            Vec3 sol
    ) {
        EnumSet<CompassTarget> filtered = initialCandidates.clone();
        if (getZoneForCoords(sol) == HollowsZone.CRYSTAL_NUCLEUS) {
            return filtered;
        }
        filtered.remove(CompassTarget.CRYSTAL_NUCLEUS);

        if (filtered.contains(CompassTarget.BAL) && sol.y > 75) {
            filtered.remove(CompassTarget.BAL);
        }
        if (filtered.contains(CompassTarget.GOBLIN_KING) && (sol.y < 82 || sol.y > 168)) {
            filtered.remove(CompassTarget.GOBLIN_KING);
        }
        if (filtered.contains(CompassTarget.GOBLIN_QUEEN) && (sol.y < 125 || sol.y > 140)) {
            filtered.remove(CompassTarget.GOBLIN_QUEEN);
        }
        if (filtered.contains(CompassTarget.JUNGLE_TEMPLE) && (sol.y < 72 || sol.y > 81)) {
            filtered.remove(CompassTarget.JUNGLE_TEMPLE);
        }
        if (filtered.contains(CompassTarget.ODAWA) && (sol.y < 73 || sol.y > 155)) {
            filtered.remove(CompassTarget.ODAWA);
        }
        if (filtered.contains(CompassTarget.PRECURSOR_CITY) && (sol.y < 121 || sol.y > 130)) {
            filtered.remove(CompassTarget.PRECURSOR_CITY);
        }
        if (filtered.contains(CompassTarget.MINES_OF_DIVAN) && (sol.y < 97 || sol.y > 102)) {
            filtered.remove(CompassTarget.MINES_OF_DIVAN);
        }

        // Quadrant bounding checks
        if (filtered.contains(CompassTarget.GOBLIN_KING)
                && (sol.x > GOBLIN_HOLDOUT_BB.maxX + GOBLIN_KING_SIZE || sol.z < GOBLIN_HOLDOUT_BB.minZ - GOBLIN_KING_SIZE)) {
            filtered.remove(CompassTarget.GOBLIN_KING);
        }
        if (filtered.contains(CompassTarget.GOBLIN_QUEEN)
                && (sol.x > GOBLIN_HOLDOUT_BB.maxX + GOBLIN_QUEEN_SIZE || sol.z < GOBLIN_HOLDOUT_BB.minZ - GOBLIN_QUEEN_SIZE)) {
            filtered.remove(CompassTarget.GOBLIN_QUEEN);
        }
        if (filtered.contains(CompassTarget.JUNGLE_TEMPLE)
                && (sol.x > JUNGLE_BB.maxX + JUNGLE_TEMPLE_SIZE || sol.z > JUNGLE_BB.maxZ + JUNGLE_TEMPLE_SIZE)) {
            filtered.remove(CompassTarget.JUNGLE_TEMPLE);
        }
        if (filtered.contains(CompassTarget.ODAWA)
                && (sol.x > JUNGLE_BB.maxX + ODAWA_SIZE || sol.z > JUNGLE_BB.maxZ + ODAWA_SIZE)) {
            filtered.remove(CompassTarget.ODAWA);
        }
        if (filtered.contains(CompassTarget.PRECURSOR_CITY)
                && (sol.x < PRECURSOR_REMNANTS_BB.minX - PRECURSOR_CITY_SIZE || sol.z < PRECURSOR_REMNANTS_BB.minZ - PRECURSOR_CITY_SIZE)) {
            filtered.remove(CompassTarget.PRECURSOR_CITY);
        }
        if (filtered.contains(CompassTarget.MINES_OF_DIVAN)
                && (sol.x < MITHRIL_DEPOSITS_BB.minX - MINES_OF_DIVAN_SIZE || sol.z > MITHRIL_DEPOSITS_BB.maxZ + MINES_OF_DIVAN_SIZE)) {
            filtered.remove(CompassTarget.MINES_OF_DIVAN);
        }

        if (filtered.contains(CompassTarget.JUNGLE_TEMPLE)
                && filtered.contains(CompassTarget.BAL)
                && !foundCrystals.contains(Crystal.AMETHYST)
                && usedZone == HollowsZone.JUNGLE) {
            filtered.remove(CompassTarget.BAL);
        }

        return filtered;
    }

    private static HollowsZone getZoneForCoords(Vec3 pos) {
        if (NUCLEUS_BB.contains(pos)) return HollowsZone.CRYSTAL_NUCLEUS;
        if (MAGMA_FIELDS_BB.contains(pos)) return HollowsZone.MAGMA_FIELDS;
        if (JUNGLE_BB.contains(pos)) return HollowsZone.JUNGLE;
        if (MITHRIL_DEPOSITS_BB.contains(pos)) return HollowsZone.MITHRIL_DEPOSITS;
        if (GOBLIN_HOLDOUT_BB.contains(pos)) return HollowsZone.GOBLIN_HOLDOUT;
        if (PRECURSOR_REMNANTS_BB.contains(pos)) return HollowsZone.PRECURSOR_REMNANTS;
        return HollowsZone.CRYSTAL_NUCLEUS;
    }

    /**
     * Computes the midpoint of the shortest segment between two 3D lines (A1->A2 and B1->B2).
     */
    private static Vec3 closestMidpointBetweenRays(Vec3 a1, Vec3 a2, Vec3 b1, Vec3 b2) {
        Vec3 u = a2.subtract(a1);
        Vec3 v = b2.subtract(b1);
        Vec3 w = a1.subtract(b1);

        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;

        if (Math.abs(denom) < 1e-6) {
            return null; // Parallel rays
        }

        double sc = (b * e - c * d) / denom;
        double tc = (a * e - b * d) / denom;

        Vec3 closestOnA = a1.add(u.scale(sc));
        Vec3 closestOnB = b1.add(v.scale(tc));
        return closestOnA.add(closestOnB).scale(0.5);
    }

    private static class CompassUse {
        final BlockPos whereUsed;
        final long whenUsedMillis;
        CompassState state = CompassState.WAITING_FOR_FIRST_PARTICLE;
        Vec3 firstParticle;
        Vec3 previousParticle;
        Vec3 lastParticle;

        CompassUse(BlockPos whereUsed, long whenUsedMillis) {
            this.whereUsed = whereUsed;
            this.whenUsedMillis = whenUsedMillis;
        }

        void processParticle(double x, double y, double z, long now) {
            if (state == CompassState.COMPLETED || state == CompassState.FAILED_TIMEOUT) return;
            if (now - whenUsedMillis > ALL_PARTICLES_MAX_MILLIS) {
                state = CompassState.FAILED_TIMEOUT;
                return;
            }

            Vec3 cur = new Vec3(x, y, z);
            Vec3 origin = new Vec3(whereUsed.getX(), whereUsed.getY(), whereUsed.getZ());

            if (state == CompassState.WAITING_FOR_FIRST_PARTICLE) {
                if (cur.distanceTo(origin) < MAX_DISTANCE_FROM_USE_TO_FIRST_PARTICLE) {
                    firstParticle = cur;
                    previousParticle = cur;
                    state = CompassState.COMPUTING_LAST_PARTICLE;
                }
                return;
            }

            if (cur.distanceTo(previousParticle) <= MAX_DISTANCE_BETWEEN_PARTICLES) {
                previousParticle = cur;
                return;
            }

            if (cur.distanceTo(firstParticle) <= MAX_DISTANCE_BETWEEN_PARTICLES) {
                lastParticle = previousParticle;
                state = CompassState.COMPLETED;
            }
        }
    }
}
