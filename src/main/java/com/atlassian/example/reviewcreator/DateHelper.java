package com.atlassian.example.reviewcreator;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Calendar;

public class DateHelper {

    /**
     * Takes a start date and adds the specified number of working days to it.
     * E.g. when <code>start</code> is Friday and <code>workingDays</code> is
     * 2, the returned date will be the Tuesday following <code>start</code>.
     *
     * @param start
     * @param workingDays
     * @return
     */
    public static Date addWorkingDays(Date start, int workingDays) {

        /*
         * IMPLEMENTATION NOTE
         *
         * When your addition of working days carries over a Daylight Savings
         * change, expect the outcome in local time to be one hour off.
         *
         * For instance, when you do 2009-03-18T13:15:30Z + 5 working days on a
         * US server and interpret the result as local time, it will be next
         * Wednesday, but one hour earlier than the starting timestamp.
         *
         * This is because we're actually adding 24 * 7 hours to the starting
         * time and the weekend is one hour longer than normal.
         */

        if (workingDays < 0) {
            throw new IllegalArgumentException("Subtracting of working days is currently not supported.");
        } else if (workingDays == 0) {
            return start;

        } else {
            final GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(start);
            final int startDay = cal.get(Calendar.DAY_OF_WEEK);

            int weekendDays = Math.max(((workingDays / 5) - 1), 0) * 2;
            if (startDay == Calendar.SATURDAY) {
                weekendDays += 2;
            } else if (startDay == Calendar.SUNDAY) {
                weekendDays += 1;
            } else {
                weekendDays += (Calendar.FRIDAY - startDay) < workingDays ? 2 : 0;
            }

            cal.add(Calendar.DAY_OF_WEEK, workingDays + weekendDays);
            return cal.getTime();
        }
    }
}
