package com.dttdrt.addons.features.party;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Hypixel Party Chat `!vote` and `!putoff [seconds]` commands.
 * Flow:
 * 1. On `!vote`, transfers party to the initiator (`/p transfer <initiator>`).
 * 2. Listens for 15s (extendable via `!putoff <sec>`, max total 50s / max param 35s).
 * 3. Non-leader members who type `n`/`N` are kicked (`/p kick <name>`); `y`/`Y` or no response are skipped.
 * 4. Executes `/p warp`.
 * 5. 3 seconds after `/p warp`, invites all kicked members back (`/p invite <name>`) and ends.
 */
public class PartyVoteFeature implements Feature {

    private enum State {
        IDLE,
        VOTING,
        KICKING_AND_WARPING,
        WAIT_REINVITE,
        INVITING
    }

    private enum VoteChoice {
        YES,
        NO
    }

    private static final long DEFAULT_VOTE_MS = 15_000L;
    private static final long MAX_TOTAL_VOTE_MS = 50_000L;
    private static final int MAX_PUTOFF_SECONDS = 35;
    private static final long REINVITE_DELAY_MS = 3_000L;
    private static final long COMMAND_INTERVAL_MS = 350L;

    private static final Pattern PARTY_CHAT_PATTERN =
            Pattern.compile("^Party > (?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16}):\\s*(.+)$");
    private static final Pattern PUTOFF_PATTERN =
            Pattern.compile("^!putoff\\s+(-?\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_CODE_PATTERN =
            Pattern.compile("§[0-9a-fA-FKLMNORklmnor]");
    private static final Pattern PARTY_MEMBERS_COUNT_PATTERN =
            Pattern.compile("^Party Members \\((\\d+)\\):?\\s*(.*)$");
    private static final Pattern PARTY_ROSTER_LINE_PATTERN =
            Pattern.compile("^Party (?:Leader|Moderators|Members):\\s*(.+)$");
    private static final Pattern PARTY_JOINED_PATTERN =
            Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16}) joined the party\\.$");
    private static final Pattern PARTY_LEFT_PATTERN =
            Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16}) (?:has left|has been removed from|was kicked from) the party\\..*$");
    private static final Pattern YOU_JOINED_PARTY_PATTERN =
            Pattern.compile("^You have joined (?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16})'s party!.*$");
    private static final Pattern VOTE_STARTED_CHAT_PATTERN =
            Pattern.compile("^Vote started by ([A-Za-z0-9_]{1,16})!.*$");
    private static final Pattern TOKEN_PLAYER_PATTERN =
            Pattern.compile("(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16})");

    private static final long STREAK_SETTLE_DELAY_MS = 1_200L;
    private static final long STREAK_WARP_TIMEOUT_MS = 12_000L;
    private static final long STREAK_ROUND_EXPIRE_MS = 90_000L;
    private static final long AUTO_PARTY_LIST_COOLDOWN_MS = 30_000L;

    private static final class StreakRound {
        boolean active = false;
        boolean resolved = false;
        long startedAtMs = 0L;
        int startPartyCount = 0;
        boolean startedInsideMineshaft = false;
        boolean selfVotedNo = false;
        boolean warpDetected = false;
        long warpObservedAtMs = 0L;
        boolean capacityFailDetected = false;
        boolean enteredMineshaft = false;
        long enteredAtMs = 0L;
        ClientLevel preWarpLevel = null;
        final Set<String> votedNoUsers = new HashSet<>();

        void begin(int partyCount, boolean inMineshaftNow, ClientLevel levelNow, long now) {
            active = true;
            resolved = false;
            startedAtMs = now;
            startPartyCount = partyCount;
            startedInsideMineshaft = inMineshaftNow;
            selfVotedNo = false;
            warpDetected = false;
            warpObservedAtMs = 0L;
            capacityFailDetected = false;
            enteredMineshaft = false;
            enteredAtMs = 0L;
            preWarpLevel = levelNow;
            votedNoUsers.clear();
        }

        void clear() {
            active = false;
            resolved = false;
            votedNoUsers.clear();
            preWarpLevel = null;
        }
    }

    private static final Deque<String> COMMAND_QUEUE = new ArrayDeque<>();
    private static final Map<String, VoteChoice> VOTES = new LinkedHashMap<>();
    private static final Map<String, String> KICK_TARGETS = new LinkedHashMap<>();
    private static final List<String> KICKED_PLAYERS = new ArrayList<>();
    private static final Set<String> PARTY_MEMBERS = new HashSet<>();
    private static final StreakRound STREAK_ROUND = new StreakRound();

    private static State state = State.IDLE;
    private static String leaderName = "";
    private static long voteStartMs = 0L;
    private static long voteDeadlineMs = 0L;
    private static int totalPutoffSeconds = 0;
    private static long warpDispatchedMs = 0L;
    private static long lastCommandMs = 0L;
    private static int reportedPartyCount = 0;
    private static long lastAutoPartyListMs = 0L;

    @Override
    public String getId() {
        return "party_vote";
    }

    @Override
    public String getName() {
        return "Party Vote";
    }

    @Override
    public String getCategory() {
        return "Gemstone ESP";
    }

    @Override
    public void init() {
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (!isEnabled()) {
            if (state != State.IDLE) reset();
            return;
        }
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            if (state != State.IDLE) reset();
            return;
        }

        long now = System.currentTimeMillis();

        // Dispatch queued commands with safe spacing
        if (!COMMAND_QUEUE.isEmpty() && now - lastCommandMs >= COMMAND_INTERVAL_MS) {
            String cmd = COMMAND_QUEUE.pollFirst();
            if (cmd != null && !cmd.isEmpty()) {
                ClientPacketListener conn = mc.getConnection();
                conn.sendCommand(cmd);
                lastCommandMs = now;
                if ("p warp".equalsIgnoreCase(cmd)) {
                    warpDispatchedMs = now;
                    if (STREAK_ROUND.active && !STREAK_ROUND.warpDetected) {
                        STREAK_ROUND.warpDetected = true;
                        STREAK_ROUND.warpObservedAtMs = now;
                    }
                }
            }
        }

        tickMineshaftStreakRound(mc, now);

        switch (state) {
            case VOTING -> {
                if (now >= voteDeadlineMs || hasAllPartyMembersVoted(mc)) {
                    KICKED_PLAYERS.clear();
                    KICKED_PLAYERS.addAll(KICK_TARGETS.values());
                    for (String p : KICKED_PLAYERS) {
                        COMMAND_QUEUE.addLast("p kick " + p);
                    }
                    COMMAND_QUEUE.addLast("p warp");
                    warpDispatchedMs = 0L;
                    state = State.KICKING_AND_WARPING;
                }
            }
            case KICKING_AND_WARPING -> {
                if (COMMAND_QUEUE.isEmpty() && warpDispatchedMs > 0L) {
                    if (KICKED_PLAYERS.isEmpty()) {
                        reset();
                    } else {
                        state = State.WAIT_REINVITE;
                    }
                }
            }
            case WAIT_REINVITE -> {
                if (now - warpDispatchedMs >= REINVITE_DELAY_MS) {
                    for (String p : KICKED_PLAYERS) {
                        COMMAND_QUEUE.addLast("p invite " + p);
                    }
                    state = State.INVITING;
                }
            }
            case INVITING -> {
                if (COMMAND_QUEUE.isEmpty()) {
                    reset();
                }
            }
            case IDLE -> {}
        }
    }

    public static void onChatMessage(String rawText) {
        if (!WhitelistManager.isAuthorized() || rawText == null || rawText.isEmpty()) return;

        String clean = COLOR_CODE_PATTERN.matcher(rawText).replaceAll("").trim();
        if (clean.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        String selfName = mc != null && mc.player != null ? mc.player.getGameProfile().name() : "";

        updatePartyTrackingFromSystemChat(clean, selfName);
        if (!ModConfig.getInstance().partyVoteEnabled) return;

        long now = System.currentTimeMillis();
        observeWarpAndCapacityMessages(clean, now);

        Matcher m = PARTY_CHAT_PATTERN.matcher(clean);
        if (!m.matches()) return;

        String sender = m.group(1);
        String msg = m.group(2).trim();
        addPartyMember(sender);
        if (!selfName.isEmpty()) {
            addPartyMember(selfName);
        }

        if (msg.equalsIgnoreCase("!vote")) {
            beginStreakRoundIfReady(mc, now);
        } else if (VOTE_STARTED_CHAT_PATTERN.matcher(msg).matches() && !STREAK_ROUND.active) {
            beginStreakRoundIfReady(mc, now);
        }

        if (STREAK_ROUND.active && !STREAK_ROUND.resolved) {
            observeVoteChoiceForStreak(sender, selfName, msg);
        }

        if (state == State.IDLE) {
            if (msg.equalsIgnoreCase("!vote")) {
                if (!sender.equalsIgnoreCase(selfName)) {
                    // Only transfer leadership to the initiator and do NOT run the vote flow on non-initiator clients
                    COMMAND_QUEUE.addLast("p transfer " + sender);
                } else {
                    startVote(sender, selfName);
                }
            }
            return;
        }

        if (state == State.VOTING) {
            Matcher pm = PUTOFF_PATTERN.matcher(msg);
            if (pm.matches()) {
                try {
                    int requested = Integer.parseInt(pm.group(1));
                    int remainingCap = Math.max(0, MAX_PUTOFF_SECONDS - totalPutoffSeconds);
                    int addSec = Math.max(0, Math.min(remainingCap, requested));
                    long remSec = Math.max(0L, (voteDeadlineMs - System.currentTimeMillis() + 999L) / 1000L);
                    if (addSec > 0) {
                        totalPutoffSeconds += addSec;
                        long maxDeadline = voteStartMs + MAX_TOTAL_VOTE_MS;
                        voteDeadlineMs = Math.min(maxDeadline, voteDeadlineMs + addSec * 1000L);
                        remSec = Math.max(0L, (voteDeadlineMs - System.currentTimeMillis() + 999L) / 1000L);
                        COMMAND_QUEUE.addLast("pc Vote extended by " + addSec + "s ("
                                + remSec + "s left, " + totalPutoffSeconds + "/" + MAX_PUTOFF_SECONDS + "s extended).");
                    } else if (requested > 0 && remainingCap == 0) {
                        COMMAND_QUEUE.addLast("pc Max extension (35s) reached (" + remSec + "s left).");
                    }
                } catch (NumberFormatException ignored) {}
                return;
            }

            if (sender.equalsIgnoreCase(leaderName) || sender.equalsIgnoreCase(selfName)) {
                return;
            }

            String key = sender.toLowerCase(Locale.ROOT);
            if (msg.equalsIgnoreCase("y")) {
                VOTES.put(key, VoteChoice.YES);
                KICK_TARGETS.remove(key);
            } else if (msg.equalsIgnoreCase("n")) {
                VOTES.put(key, VoteChoice.NO);
                KICK_TARGETS.put(key, sender);
            }
        }
    }

    private static void startVote(String initiator, String selfName) {
        reset();
        leaderName = initiator;
        voteStartMs = System.currentTimeMillis();
        voteDeadlineMs = voteStartMs + DEFAULT_VOTE_MS;
        state = State.VOTING;
        addPartyMember(selfName);
        addPartyMember(initiator);

        if (!initiator.equalsIgnoreCase(selfName)) {
            COMMAND_QUEUE.addLast("p transfer " + initiator);
        }
        if ((reportedPartyCount < 1 || PARTY_MEMBERS.size() < reportedPartyCount)
                && voteStartMs - lastAutoPartyListMs >= AUTO_PARTY_LIST_COOLDOWN_MS) {
            lastAutoPartyListMs = voteStartMs;
            COMMAND_QUEUE.addLast("p list");
        }
        COMMAND_QUEUE.addLast("pc Vote started by " + initiator
                + "! Type 'y' to stay or 'n' to skip warp (15s). Use !putoff <sec> (max 35s).");
    }

    private static boolean hasAllPartyMembersVoted(Minecraft mc) {
        if (state != State.VOTING || reportedPartyCount < 2) {
            return false;
        }
        String selfLower = mc.player != null
                ? mc.player.getGameProfile().name().toLowerCase(Locale.ROOT)
                : "";
        String leaderLower = leaderName.toLowerCase(Locale.ROOT);
        if (PARTY_MEMBERS.size() >= reportedPartyCount) {
            int expectedVoters = 0;
            for (String member : PARTY_MEMBERS) {
                if (member.equals(leaderLower) || (!selfLower.isEmpty() && member.equals(selfLower))) {
                    continue;
                }
                expectedVoters++;
                if (!VOTES.containsKey(member)) {
                    return false;
                }
            }
            return expectedVoters > 0;
        }
        return VOTES.size() >= reportedPartyCount - 1;
    }

    private static void reset() {
        state = State.IDLE;
        leaderName = "";
        voteStartMs = 0L;
        voteDeadlineMs = 0L;
        totalPutoffSeconds = 0;
        warpDispatchedMs = 0L;
        VOTES.clear();
        KICK_TARGETS.clear();
        KICKED_PLAYERS.clear();
        COMMAND_QUEUE.clear();
    }

    private static int getPartyCountEstimate() {
        return Math.max(reportedPartyCount, PARTY_MEMBERS.size());
    }

    private static void addPartyMember(String name) {
        if (name == null) return;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (!clean.isEmpty()) {
            PARTY_MEMBERS.add(clean);
            if (STREAK_ROUND.active) {
                STREAK_ROUND.startPartyCount = Math.max(STREAK_ROUND.startPartyCount, getPartyCountEstimate());
            }
        }
    }

    private static void addPartyMembersFromSegment(String segment) {
        if (segment == null || segment.isEmpty()) return;
        String[] parts = segment.split("[\u25CF,]");
        for (String part : parts) {
            Matcher pm = TOKEN_PLAYER_PATTERN.matcher(part.trim());
            if (pm.find()) {
                addPartyMember(pm.group(1));
            }
        }
    }

    private static void updatePartyTrackingFromSystemChat(String clean, String selfName) {
        if (clean.contains("You left the party.")
                || clean.contains("You have been kicked from the party")
                || clean.contains("The party was disbanded")
                || clean.contains("has disbanded the party")
                || clean.contains("You are not currently in a party")) {
            PARTY_MEMBERS.clear();
            reportedPartyCount = 0;
            STREAK_ROUND.clear();
            return;
        }

        Matcher joinedSelf = YOU_JOINED_PARTY_PATTERN.matcher(clean);
        if (joinedSelf.matches()) {
            PARTY_MEMBERS.clear();
            reportedPartyCount = 0;
            addPartyMember(selfName);
            addPartyMember(joinedSelf.group(1));
            return;
        }

        Matcher cntMatcher = PARTY_MEMBERS_COUNT_PATTERN.matcher(clean);
        if (cntMatcher.matches()) {
            try {
                reportedPartyCount = Integer.parseInt(cntMatcher.group(1));
            } catch (NumberFormatException ignored) {}
            PARTY_MEMBERS.clear();
            addPartyMember(selfName);
            addPartyMembersFromSegment(cntMatcher.group(2));
            if (STREAK_ROUND.active) {
                STREAK_ROUND.startPartyCount = Math.max(STREAK_ROUND.startPartyCount, getPartyCountEstimate());
            }
            return;
        }

        Matcher rosterLine = PARTY_ROSTER_LINE_PATTERN.matcher(clean);
        if (rosterLine.matches()) {
            addPartyMember(selfName);
            addPartyMembersFromSegment(rosterLine.group(1));
            return;
        }

        Matcher joinedOther = PARTY_JOINED_PATTERN.matcher(clean);
        if (joinedOther.matches()) {
            addPartyMember(joinedOther.group(1));
            if (reportedPartyCount > 0) {
                reportedPartyCount = Math.max(reportedPartyCount + 1, PARTY_MEMBERS.size());
            }
            return;
        }

        Matcher leftOther = PARTY_LEFT_PATTERN.matcher(clean);
        if (leftOther.matches()) {
            String leftName = leftOther.group(1).toLowerCase(Locale.ROOT);
            PARTY_MEMBERS.remove(leftName);
            VOTES.remove(leftName);
            KICK_TARGETS.remove(leftName);
            if (reportedPartyCount > 0) {
                reportedPartyCount = Math.max(PARTY_MEMBERS.size(), reportedPartyCount - 1);
            }
        }
    }

    private static void observeWarpAndCapacityMessages(String clean, long now) {
        if (!STREAK_ROUND.active || STREAK_ROUND.resolved) return;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("summoning you to")
                || lower.contains("summoned you to")
                || lower.contains("warping you to")
                || lower.contains("sending to server")) {
            STREAK_ROUND.warpDetected = true;
            if (STREAK_ROUND.warpObservedAtMs == 0L) {
                STREAK_ROUND.warpObservedAtMs = now;
            }
        }
        if (lower.contains("more than 4 players")
                || lower.contains("too many players")
                || lower.contains("party is too big")
                || lower.contains("mineshaft is full")
                || lower.contains("this server is full")
                || lower.contains("couldn't warp you")
                || lower.contains("cannot warp into")) {
            STREAK_ROUND.capacityFailDetected = true;
            STREAK_ROUND.warpDetected = true;
            if (STREAK_ROUND.warpObservedAtMs == 0L) {
                STREAK_ROUND.warpObservedAtMs = now;
            }
        }
    }

    private static void beginStreakRoundIfReady(Minecraft mc, long now) {
        if (!ModConfig.getInstance().mineshaftStreakEnabled) {
            STREAK_ROUND.clear();
            return;
        }
        if (mc != null && PARTY_MEMBERS.size() < 2 && reportedPartyCount < 2
                && mc.getConnection() != null
                && now - lastAutoPartyListMs >= AUTO_PARTY_LIST_COOLDOWN_MS) {
            lastAutoPartyListMs = now;
            mc.getConnection().sendCommand("p list");
        }
        STREAK_ROUND.begin(
                getPartyCountEstimate(),
                CrystalHollowsTracker.isInMineshaft(),
                mc != null ? mc.level : null,
                now
        );
    }

    private static void observeVoteChoiceForStreak(String sender, String selfName, String msg) {
        String key = sender.toLowerCase(Locale.ROOT);
        if (msg.equalsIgnoreCase("n")) {
            STREAK_ROUND.votedNoUsers.add(key);
            if (!selfName.isEmpty() && sender.equalsIgnoreCase(selfName)) {
                STREAK_ROUND.selfVotedNo = true;
            }
        } else if (msg.equalsIgnoreCase("y")) {
            STREAK_ROUND.votedNoUsers.remove(key);
            if (!selfName.isEmpty() && sender.equalsIgnoreCase(selfName)) {
                STREAK_ROUND.selfVotedNo = false;
            }
        }
        STREAK_ROUND.startPartyCount = Math.max(STREAK_ROUND.startPartyCount, getPartyCountEstimate());
    }

    private static void tickMineshaftStreakRound(Minecraft mc, long now) {
        if (!STREAK_ROUND.active || STREAK_ROUND.resolved) return;

        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.mineshaftStreakEnabled) {
            STREAK_ROUND.clear();
            return;
        }

        if (mc.level != null && STREAK_ROUND.preWarpLevel != null && mc.level != STREAK_ROUND.preWarpLevel) {
            STREAK_ROUND.warpDetected = true;
            if (STREAK_ROUND.warpObservedAtMs == 0L) {
                STREAK_ROUND.warpObservedAtMs = now;
            }
        }

        boolean inShaftNow = CrystalHollowsTracker.isInMineshaft();
        if (inShaftNow) {
            if (!STREAK_ROUND.startedInsideMineshaft) {
                if (!STREAK_ROUND.enteredMineshaft) {
                    STREAK_ROUND.enteredMineshaft = true;
                    STREAK_ROUND.enteredAtMs = now;
                }
            } else if (STREAK_ROUND.warpDetected && !STREAK_ROUND.capacityFailDetected
                    && mc.level != null && mc.level.players().size() > 1) {
                if (!STREAK_ROUND.enteredMineshaft) {
                    STREAK_ROUND.enteredMineshaft = true;
                    STREAK_ROUND.enteredAtMs = now;
                }
            }
        }

        if (STREAK_ROUND.startPartyCount < 5) {
            if ((STREAK_ROUND.warpDetected && now - STREAK_ROUND.warpObservedAtMs >= STREAK_WARP_TIMEOUT_MS)
                    || now - STREAK_ROUND.startedAtMs >= STREAK_ROUND_EXPIRE_MS) {
                STREAK_ROUND.clear();
            }
            return;
        }

        if (STREAK_ROUND.selfVotedNo && !STREAK_ROUND.enteredMineshaft) {
            if ((STREAK_ROUND.warpDetected && now - STREAK_ROUND.warpObservedAtMs >= 5_000L)
                    || now - STREAK_ROUND.startedAtMs >= STREAK_ROUND_EXPIRE_MS) {
                STREAK_ROUND.clear();
            }
            return;
        }

        if (STREAK_ROUND.enteredMineshaft) {
            if (now - STREAK_ROUND.enteredAtMs < STREAK_SETTLE_DELAY_MS) {
                return;
            }
            int noCount = STREAK_ROUND.votedNoUsers.size();
            int remainingPlayers = Math.max(0, STREAK_ROUND.startPartyCount - noCount);
            boolean allRemainingViaNo = (noCount >= 1) && (remainingPlayers <= 4) && !STREAK_ROUND.capacityFailDetected;
            if (!allRemainingViaNo) {
                cfg.mineshaftStreak = Math.max(0, cfg.mineshaftStreak) + 1;
                ModConfig.save();
                String announcement = formatStreakAnnouncement(cfg.mineshaftStreakMessage, cfg.mineshaftStreak);
                if (!announcement.isEmpty()) {
                    COMMAND_QUEUE.addLast("pc " + announcement);
                }
            }
            STREAK_ROUND.resolved = true;
            STREAK_ROUND.clear();
            return;
        }

        boolean failedByCapacity = STREAK_ROUND.capacityFailDetected
                && (STREAK_ROUND.warpObservedAtMs == 0L || now - STREAK_ROUND.warpObservedAtMs >= 1_500L);
        boolean failedByWarpTimeout = STREAK_ROUND.warpDetected
                && (now - STREAK_ROUND.warpObservedAtMs >= STREAK_WARP_TIMEOUT_MS);
        if (failedByCapacity || failedByWarpTimeout) {
            cfg.mineshaftStreak = 0;
            ModConfig.save();
            STREAK_ROUND.resolved = true;
            STREAK_ROUND.clear();
            return;
        }

        if (now - STREAK_ROUND.startedAtMs >= STREAK_ROUND_EXPIRE_MS) {
            STREAK_ROUND.clear();
        }
    }

    private static String formatStreakAnnouncement(String template, int streak) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String replaced = template.contains("%d")
                ? template.replace("%d", String.valueOf(streak))
                : (template + " " + streak);
        return replaced.replace("\r", "").replace("\n", "").trim();
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().partyVoteEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().partyVoteEnabled = enabled;
        ModConfig.save();
    }
}
