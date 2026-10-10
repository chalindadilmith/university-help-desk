package com.helpdesk.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the free-text schedule an admin types for a module offering ("Tue/Thu 13:00-15:00",
 * "Mon/Wed 9-11am") so overlapping modules can be detected. Text it cannot understand yields
 * no slots, which means "no clash check" rather than an error.
 */
public final class ScheduleParser {

    public record Slot(String day, int startMinute, int endMinute) {
        public boolean overlaps(Slot other) {
            return day.equals(other.day) && startMinute < other.endMinute && other.startMinute < endMinute;
        }

        public String describe() {
            return day + " " + hhmm(startMinute) + "-" + hhmm(endMinute);
        }
    }

    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Pattern DAY = Pattern.compile("\\b(mon|tue|wed|thu|fri|sat|sun)[a-z]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s*(?:-|to|–)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE);

    private ScheduleParser() { }

    public static List<Slot> parse(String text) {
        List<Slot> slots = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return slots;
        }
        Matcher range = RANGE.matcher(text);
        if (!range.find()) {
            return slots;
        }
        int start = minutes(range.group(1), range.group(2));
        int end = minutes(range.group(4), range.group(5));
        String startMeridian = range.group(3);
        String endMeridian = range.group(6);
        if (endMeridian != null) {
            end = applyMeridian(end, endMeridian);
            if (startMeridian != null) {
                start = applyMeridian(start, startMeridian);
            } else {
                start = applyMeridian(start, endMeridian);
                if (start >= end && endMeridian.equalsIgnoreCase("pm")) {
                    start -= 12 * 60; // e.g. "11-1pm" means 11am to 1pm
                }
            }
        } else if (startMeridian != null) {
            start = applyMeridian(start, startMeridian);
        }
        if (start < 0 || end <= start || end > 24 * 60) {
            return slots;
        }
        String before = text.substring(0, range.start());
        Matcher day = DAY.matcher(before);
        while (day.find()) {
            String d = day.group(1).toLowerCase(Locale.ROOT);
            if (DAYS.contains(d)) {
                slots.add(new Slot(d, start, end));
            }
        }
        return slots;
    }

    /** The first overlapping pair of slots between two schedules, or null if they do not clash. */
    public static Slot firstClash(String scheduleA, String scheduleB) {
        for (Slot a : parse(scheduleA)) {
            for (Slot b : parse(scheduleB)) {
                if (a.overlaps(b)) {
                    return a;
                }
            }
        }
        return null;
    }

    private static int minutes(String hour, String minute) {
        int h = Integer.parseInt(hour);
        int m = minute == null ? 0 : Integer.parseInt(minute);
        return h > 24 || m > 59 ? -1 : h * 60 + m;
    }

    private static int applyMeridian(int minutes, String meridian) {
        if (minutes < 0) {
            return minutes;
        }
        int h = minutes / 60;
        boolean pm = meridian.equalsIgnoreCase("pm");
        if (h == 12) {
            h = pm ? 12 : 0;
        } else if (pm) {
            h += 12;
        }
        return h * 60 + minutes % 60;
    }

    private static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
