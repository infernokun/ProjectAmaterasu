package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.models.entities.ctf.Flag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A wrong answer that is nearly a flag: right apart from letter case, spaces around the answer, the
 * lab's flag wrapper (missing, or added to a bare flag), or, for flags longer than 8 characters, up to
 * two typos. Says why, so whoever runs the lab can decide whether to give credit.
 */
public final class NearMiss {
    static final int LONG_FLAG = 8;
    static final int MAX_TYPOS = 2;

    private NearMiss() {
    }

    /** The flag a wrong answer nearly matched, and why it is a near miss. */
    public record Match(String flagId, String flag, List<String> reasons) {
    }

    /** The closest flag the answer nearly matches, if any. Answers that match a flag are not near misses. */
    public static Optional<Match> check(String submitted, List<Flag> flags, String surroundTag) {
        if (submitted == null || submitted.isBlank() || flags == null) {
            return Optional.empty();
        }
        String[] wrapper = FlagService.wrapperParts(surroundTag);
        return flags.stream()
                .filter(flag -> flag.getFlag() != null && !flag.getFlag().isBlank())
                .filter(flag -> !FlagService.accepts(flag, submitted, surroundTag))
                .map(flag -> check(submitted, flag, wrapper))
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(match -> match.reasons().size()));
    }

    static Optional<Match> check(String submitted, Flag flag, String[] wrapper) {
        Parts answer = split(submitted.trim(), wrapper);
        Parts expected = split(flag.getFlag().trim(), wrapper);
        boolean caseSensitive = !Boolean.FALSE.equals(flag.getCaseSensitive());
        List<String> reasons = new ArrayList<>();
        String format = wrapper != null ? wrapper[0] + "…" + wrapper[1] : null;

        if (!Boolean.TRUE.equals(flag.getSurroundWithTag())) {
            if (expected.wrapped() && !answer.wrapped()) {
                reasons.add("Missing the " + format + " wrapper");
            } else if (!expected.wrapped() && answer.wrapped()) {
                reasons.add("Wrapped in " + format + ", but this flag has no wrapper");
            }
        }

        String inner = answer.inner();
        if (!inner.equals(inner.strip())) {
            reasons.add("Spaces around the answer");
            inner = inner.strip();
        }
        String target = expected.inner().strip();
        boolean wrongCaseWrapper = answer.wrapped()
                && !answer.prefix().equals(expected.wrapped() ? expected.prefix() : wrapper[0]);
        if (inner.equals(target)) {
            if (caseSensitive && wrongCaseWrapper) {
                reasons.add("Wrong letter case");
            }
        } else if (inner.equalsIgnoreCase(target)) {
            if (caseSensitive) {
                reasons.add("Wrong letter case");
            }
        } else if (target.length() > LONG_FLAG) {
            int distance = distance(inner.toLowerCase(Locale.ROOT), target.toLowerCase(Locale.ROOT), MAX_TYPOS);
            if (distance > MAX_TYPOS) {
                return Optional.empty();
            }
            reasons.add(distance == 1 ? "One character off" : distance + " characters off");
        } else {
            return Optional.empty();
        }
        return reasons.isEmpty() ? Optional.empty() : Optional.of(new Match(flag.getId(), flag.getFlag(), reasons));
    }

    /** An answer with the lab's wrapper taken off, when it has one (matched ignoring case). */
    record Parts(String inner, boolean wrapped, String prefix) {
    }

    static Parts split(String text, String[] wrapper) {
        if (wrapper != null && !wrapper[0].isEmpty() && text.length() >= wrapper[0].length() + wrapper[1].length()
                && text.regionMatches(true, 0, wrapper[0], 0, wrapper[0].length())
                && text.regionMatches(true, text.length() - wrapper[1].length(), wrapper[1], 0, wrapper[1].length())) {
            return new Parts(text.substring(wrapper[0].length(), text.length() - wrapper[1].length()), true,
                    text.substring(0, wrapper[0].length()));
        }
        return new Parts(text, false, "");
    }

    /** Levenshtein distance, or limit + 1 once it is past the limit. */
    static int distance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int best = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                best = Math.min(best, current[j]);
            }
            if (best > limit) {
                return limit + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
