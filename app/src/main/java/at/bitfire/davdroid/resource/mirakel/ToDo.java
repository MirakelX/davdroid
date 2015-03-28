package at.bitfire.davdroid.resource.mirakel;

import android.os.Process;
import android.text.format.Time;
import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.InvalidResourceException;
import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.resource.TimeZoneHelper;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ToDo extends Resource {
    private final static String TAG="ToDo";
    public final static String MIME_TYPE = "text/calendar";


    @Getter
    @Setter
    private String summary, location, description;

    @Getter	private DtStart dtStart;
    @Getter	@Setter	private RDate rdate;
    @Getter	@Setter	private RRule rrule;
    @Getter	@Setter	private ExDate exdate;
    @Getter	@Setter	private ExRule exrule;
    @Getter @Setter private Created created;
    @Getter @Setter private LastModified updated;

    @Getter	@Setter	private Boolean forPublic;
    @Getter	@Setter	private Status status;

    @Getter @Setter private boolean opaque;
    @Getter @Setter private Completed dateCompleted;

    @Getter
    private PropertyList categories= new PropertyList();
    public void addCategorie(final Categories categorie) {
        categories.add(categorie);
    }

    @Getter @Setter private Organizer organizer;
    @Getter private List<Attendee> attendees = new LinkedList<>();

    public void addAttendee(final Attendee attendee) {
        attendees.add(attendee);
    }

    @Getter private List<VAlarm> alarms = new LinkedList<>();
    public void addAlarm(final VAlarm alarm) {
        alarms.add(alarm);
    }

    @Getter	@Setter private Priority priority;

    @Getter	@Setter private PercentComplete completed;

    @Getter	@Setter private Due due;


    public ToDo(final String name, final String ETag){
        super(name,ETag);
    }

    public ToDo(final long localID, final String name, final String ETag){
        super(localID,name,ETag);
    }

    @Override
    public void initialize() {
        generateUID();
        name = uid.replace("@", "_");
    }



    protected void generateUID() {
        final UidGenerator generator = new UidGenerator(new SimpleHostInfo(DavSyncAdapter.getAndroidID()), String.valueOf(Process.myPid()));
        uid = generator.generateUid().getValue();
    }

    @Override
    public void parseEntity(@NonNull final InputStream entity, final AssetDownloader downloader) throws InvalidResourceException, IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = entity.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        final byte[] bytes= baos.toByteArray();
        final String str = new String(bytes, "UTF-8");
        Log.d(TAG,str);
        final net.fortuna.ical4j.model.Calendar ical;
        try {
            final CalendarBuilder builder = new CalendarBuilder();
            ical = builder.build(new ByteArrayInputStream(bytes));

            if (ical == null) {
                throw new InvalidResourceException("No iCalendar found");
            }
        } catch (final ParserException e) {
            throw new InvalidResourceException(e);
        }

        final ComponentList todos = ical.getComponents(Component.VTODO);
        if ((todos == null) || todos.isEmpty()) {
            throw new InvalidResourceException("Maybe VEVENT or so");
        }

        final VToDo todo = (VToDo) todos.get(0);
        if (todo.getUid() != null) {
            uid = todo.getUid().toString();
        } else {
            Log.w(TAG, "Received VTODO without UID, generating new one");
            generateUID();
        }
        categories=todo.getProperties(Property.CATEGORIES);

        if(todo.getStartDate()!=null) {
            dtStart = todo.getStartDate();
            TimeZoneHelper.validateTimeZone(dtStart);
        }

        rrule = (RRule) todo.getProperty(Property.RRULE);
        rdate = (RDate) todo.getProperty(Property.RDATE);
        exrule = (ExRule) todo.getProperty(Property.EXRULE);
        exdate = (ExDate) todo.getProperty(Property.EXDATE);
        dateCompleted=todo.getDateCompleted();

        if(todo.getCreated()!=null){
            created=todo.getCreated();
        }else{
            created=new Created();
            created.setDate(new Date(new java.util.Date()));
        }

        if(todo.getLastModified()!=null){
            updated=todo.getLastModified();
        }else{
            updated=new LastModified();
            updated.setDate(new Date(new java.util.Date()));
        }

        if (todo.getSummary() != null) {
            summary = todo.getSummary().getValue();
        }
        if (todo.getLocation() != null) {
            location = todo.getLocation().getValue();
        }
        if (todo.getDescription() != null) {
            description = todo.getDescription().getValue();
        }

        status = todo.getStatus();

        organizer = todo.getOrganizer();
        for (final Object o : todo.getProperties(Property.ATTENDEE)) {
            attendees.add((Attendee) o);
        }

        final Clazz classification = todo.getClassification();
        if (classification != null) {
            if (classification == Clazz.PUBLIC) {
                forPublic = true;
            } else if ((classification == Clazz.CONFIDENTIAL)
                    || (classification == Clazz.PRIVATE)) {
                forPublic = false;
            }
        }

        priority=todo.getPriority();
        completed=todo.getPercentComplete();
        if(todo.getDue()!=null) {
            due = todo.getDue();
            TimeZoneHelper.validateTimeZone(due);
        }
        Log.d(TAG,"parsed VTODO");
        alarms=todo.getAlarms();
    }


    @Override
    public ByteArrayOutputStream toEntity() throws IOException {
        final net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
        ical.getProperties().add(Version.VERSION_2_0);
        ical.getProperties().add(Constants.PRODUCT_ID);
        final VToDo todo = new VToDo();
        final PropertyList props = todo.getProperties();
        if (uid != null) {
            props.add(new Uid(uid));
        }

        if(dtStart!=null) {
            props.add(dtStart);
        }
        if (rrule != null) {
            props.add(rrule);
        }
        if (rdate != null) {
            props.add(rdate);
        }
        if (exrule != null) {
            props.add(exrule);
        }
        if (exdate != null) {
            props.add(exdate);
        }
        if ((summary != null) && !summary.isEmpty()) {
            props.add(new Summary(summary));
        }
        if ((location != null) && !location.isEmpty()) {
            props.add(new Location(location));
        }
        if ((description != null) && !description.isEmpty()) {
            props.add(new Description(description));
        }

        if (status != null) {
            props.add(status);
        }
        if (!opaque) {
            props.add(Transp.TRANSPARENT);
        }
        if (organizer != null) {
            props.add(organizer);
        }
        props.addAll(attendees);
        if (forPublic != null) {
            todo.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);
        }

        props.add(priority);
        if(due!=null) {
            props.add(due);
        }
        if(completed!=null) {
            props.add(completed);
        }
        if(dateCompleted!=null) {
            props.add(dateCompleted);
        }
        if(categories!=null){
            props.addAll(categories);
        }
        if(Status.VTODO_COMPLETED.equals(status.getValue())){
            props.add(new Completed(new DateTime(new java.util.Date())));
        }
        ical.getComponents().add(todo);
        Log.d(TAG,todo.toString());
        ByteArrayOutputStream os = null;
        try {
            final CalendarOutputter output = new CalendarOutputter(false);
            os = new ByteArrayOutputStream();
            output.output(ical, os);
        } catch (ValidationException e) {
            Log.e(TAG, "Generated invalid iCalendar",e);
        }
        return os;
    }

    public void setDue(final long tsDue, final String tzID) {
        if (tzID == null) { // all-day
            due = new Due(new Date(tsDue));
        } else {
            final DateTime due = new DateTime(tsDue);
            due.setTimeZone(TimeZoneHelper.getTimeZone(tzID));
            this.due = new Due(due);
        }
    }
    

    public long getDueInMillis() {
        if (!TimeZoneHelper.hasTime(due) && (due == null)) {
            final Calendar c = Calendar.getInstance(TimeZone
                    .getTimeZone(Time.TIMEZONE_UTC));
            if(due!=null) {
                c.setTime(due.getDate());
            }
            c.add(Calendar.DATE, 1);
            return c.getTimeInMillis();
        }
        return due.getDate().getTime();
    }

}
