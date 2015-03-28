package at.bitfire.davdroid.resource;

import android.text.format.Time;
import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateProperty;

import org.apache.commons.lang.StringUtils;

import java.io.StringReader;
import java.util.SimpleTimeZone;


public final class TimeZoneHelper {

    private final static TimeZoneRegistry tzRegistry = new DefaultTimeZoneRegistryFactory().createRegistry();
    private static final String TAG = "TimeZoneHelper";

    public static String getTzId(DateProperty date) {
        if (date.isUtc() || !hasTime(date))
            return Time.TIMEZONE_UTC;
        else if (date.getTimeZone() != null)
            return date.getTimeZone().getID();
        else if (date.getParameter(Value.TZID) != null)
            return date.getParameter(Value.TZID).getValue();

        // fallback
        return Time.TIMEZONE_UTC;
    }

    /* guess matching Android timezone ID */
    public static void validateTimeZone(DateProperty date) {
        if (date.isUtc() || !hasTime(date))
            return;

        String tzID = getTzId(date);
        if (tzID == null)
            return;

        String localTZ = null;
        String availableTZs[] = SimpleTimeZone.getAvailableIDs();

        // first, try to find an exact match (case insensitive)
        for (String availableTZ : availableTZs)
            if (tzID.equalsIgnoreCase(availableTZ)) {
                localTZ = availableTZ;
                break;
            }

        // if that doesn't work, try to find something else that matches
        if (localTZ == null) {
            Log.w(TAG, "Coulnd't find time zone with matching identifiers, trying to guess");
            for (String availableTZ : availableTZs)
                if (StringUtils.indexOfIgnoreCase(tzID, availableTZ) != -1) {
                    localTZ = availableTZ;
                    break;
                }
        }

        // if that doesn't work, use UTC as fallback
        if (localTZ == null) {
            Log.e(TAG, "Couldn't identify time zone, using UTC as fallback");
            localTZ = Time.TIMEZONE_UTC;
        }

        Log.d(TAG, "Assuming time zone " + localTZ + " for " + tzID);
        date.setTimeZone(tzRegistry.getTimeZone(localTZ));
    }

    public static String TimezoneDefToTzId(String timezoneDef) throws IllegalArgumentException {
        try {
            if (timezoneDef != null) {
                CalendarBuilder builder = new CalendarBuilder();
                net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader(timezoneDef));
                VTimeZone timezone = (VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE);
                return timezone.getTimeZoneId().getValue();
            }
        } catch (Exception ex) {
            Log.w(TAG, "Can't understand time zone definition, ignoring", ex);
        }
        throw new IllegalArgumentException();
    }

    public static boolean hasTime(final DateProperty date) {
        return date.getDate() instanceof DateTime;
    }


    public static TimeZone getTimeZone(final String tzID) {
        return tzRegistry.getTimeZone(tzID);
    }
}
