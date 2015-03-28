package at.bitfire.davdroid.resource.mirakel;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.Status;

import org.apache.commons.lang.StringUtils;
import org.dmfs.provider.tasks.TaskContract;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalStorageException;
import at.bitfire.davdroid.resource.RecordNotFoundException;
import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.resource.ServerInfo;
import lombok.Cleanup;
import lombok.Getter;

public class LocalTodoList extends LocalCollection<ToDo> {

    private static final String TAG="LocalTodoList";
    private final Context ctx;
    private long id;

    protected static String COLLECTION_COLUMN_CTAG = TaskContract.TaskLists.SYNC1;


    public LocalTodoList(final Account account, final ContentProviderClient providerClient, final long id, final String url, final Context ctx) {
        super(account, providerClient);
        this.id = id;
        this.url = url;
        this.ctx=ctx;
    }
    public static void create(final Account account, final ContentResolver resolver, final ServerInfo.ResourceInfo info, final Context ctx) throws LocalStorageException, RecordNotFoundException {
        final ContentProviderClient client = resolver.acquireContentProviderClient(listsUri(account,ctx));
        if (client == null)
            throw new LocalStorageException("No Calendar Provider found (Calendar app disabled?)");

        int color = 0xFFC3EA6E;		// fallback: "DAVdroid green"
        if (info.getColor() != null) {
            final Pattern p = Pattern.compile("#(\\p{XDigit}{6})(\\p{XDigit}{2})?");
            final Matcher m = p.matcher(info.getColor());
            if (m.find()) {
                final int color_rgb = Integer.parseInt(m.group(1), 16);
                final int color_alpha = (m.group(2) != null) ? (Integer.parseInt(m.group(2), 16) & 0xFF) : 0xFF;
                color = (color_alpha << 24) | color_rgb;
            }
        }

        final ContentValues values = new ContentValues();
        values.put(TaskContract.TaskLists.SYNC_ENABLED, true);
        values.put(TaskContract.TaskLists.ACCOUNT_NAME, account.name);
        values.put(TaskContract.TaskLists.ACCOUNT_TYPE, account.type);
        values.put(TaskContract.TaskLists._SYNC_ID, info.getURL());
        values.put(TaskContract.TaskLists.LIST_NAME, info.getTitle());
        values.put(TaskContract.TaskLists.LIST_COLOR, color);
        //values.put(TaskContract.TaskLists.OWNER, account.name);
        values.put(TaskContract.TaskLists.VISIBLE, 1);
        //values.put(TaskContract.TaskLists.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_ALERT);

        if (info.isReadOnly())
            return;

        Log.i(TAG, "Inserting calendar: " + values.toString() + " -> " + listsUri(account,ctx).toString());
        try {
            client.insert(listsUri(account, ctx), values);
        } catch(RemoteException e) {
            throw new LocalStorageException(e);
        }
    }

    public static Uri listsUri(Account account, Context ctx) throws RecordNotFoundException {
        List<Uri> uris=todoURI(ctx,account, TaskContract.TaskLists.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }

    public static LocalTodoList[] findAll(final Account account, final ContentProviderClient providerClient, final Context ctx) throws RemoteException, RecordNotFoundException {
        @Cleanup final Cursor cursor = providerClient.query(listsUri(account, ctx),
                new String[] { TaskContract.TaskLists._ID, TaskContract.TaskLists._SYNC_ID },
                TaskContract.TaskLists.ACCOUNT_NAME+"=?", new String[]{account.name}, null);

        final LinkedList<LocalTodoList> lists = new LinkedList<LocalTodoList>();
        while ((cursor != null) && cursor.moveToNext()) {
            lists.add(new LocalTodoList(account, providerClient, cursor.getInt(0), cursor.getString(1), ctx));
        }
        return lists.toArray(new LocalTodoList[lists.size()]);
    }


    @Override
    protected Uri entriesURI() throws RecordNotFoundException{
        final List<Uri> uris= todoURI(ctx, account, TaskContract.Tasks.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }
    @Override
    protected String entryColumnAccountType()	{ return TaskContract.Tasks.ACCOUNT_TYPE; }
    @Override
    protected String entryColumnAccountName()	{ return TaskContract.Tasks.ACCOUNT_NAME; }
    @Override
    protected String entryColumnParentID()		{ return TaskContract.Tasks.LIST_ID; }
    @Override
    protected String entryColumnID()			{ return TaskContract.Tasks._ID; }
    @Override
    protected String entryColumnRemoteName()	{ return TaskContract.Tasks._SYNC_ID; }
    @Override
    protected String entryColumnETag()			{ return TaskContract.Tasks.SYNC1; }
    @Override
    protected String entryColumnDirty()			{ return TaskContract.Tasks._DIRTY; }
    @Override
    protected String entryColumnDeleted()		{ return TaskContract.Tasks._DELETED; }
    @Override
    protected String entryColumnUID()			{ return TaskContract.Tasks.SYNC2; }

    @Getter
    protected String url, cTag;

    @Override
    public long getId(){
        return id;
    }


    @Override
    public void setCTag(final String cTag) throws RecordNotFoundException {
        pendingOperations.add(ContentProviderOperation
                .newUpdate(ContentUris.withAppendedId(listsURI(), id))
                .withValue(COLLECTION_COLUMN_CTAG, cTag).build());
    }

    @Override
    public void populate(final Resource record) throws LocalStorageException {
        try {
            @Cleanup final Cursor cursor = providerClient.query(entriesURI(),
                    new String[]{
                        /* 0 */TaskContract.Tasks.TITLE, TaskContract.Tasks.LOCATION, TaskContract.Tasks.DESCRIPTION,
                            TaskContract.Tasks.DUE, TaskContract.Tasks.STATUS, TaskContract.Tasks.PRIORITY, entryColumnID(),
                            entryColumnAccountName(), entryColumnRemoteName(), entryColumnETag(), TaskContract.Tasks.PERCENT_COMPLETE},
                    entryColumnID() + "=?", new String[]{String.valueOf(record.getLocalID())}, null);

            final ToDo t = (ToDo) record;
            if ((cursor != null) && cursor.moveToFirst()) {
                t.setUid(cursor.getString(8));

                t.setSummary(cursor.getString(0));
                t.setLocation(cursor.getString(1));
                t.setDescription(cursor.getString(2));
                if (!cursor.isNull(3)) {
                    //Mirakel saves times in utc and transforms this dates to the current timezone
                    t.setDue(cursor.getLong(3), TimeZone.getDefault().getID());
                }
                // status
                switch (cursor.getInt(4)) {
                    case TaskContract.Tasks.STATUS_COMPLETED:
                        t.setStatus(Status.VTODO_COMPLETED);
                        t.setDateCompleted(new Completed(new DateTime(new Date())));
                        break;
                    case TaskContract.Tasks.STATUS_CANCELLED:
                        t.setStatus(Status.VTODO_CANCELLED);
                        break;
                    case TaskContract.Tasks.STATUS_IN_PROCESS:
                        t.setStatus(Status.VTODO_IN_PROCESS);
                        break;
                    case TaskContract.Tasks.STATUS_NEEDS_ACTION:
                    default:
                        t.setStatus(Status.VTODO_NEEDS_ACTION);
                        break;

                }
                t.setPriority(new Priority(cursor.getInt(5)));
                t.setCompleted(new PercentComplete(cursor.getInt(10)));

                try {
                    @Cleanup Cursor c = providerClient.query(propertyUri(), new String[]{TaskContract.Property.Category.DATA1}, TaskContract.Properties.MIMETYPE + "=? AND " + TaskContract.Properties.TASK_ID + "=?",
                            new String[]{"vnd.android.cursor.item/category", String.valueOf(record.getLocalID())}, null);
                    while (c.moveToNext()) {
                        t.addCategorie(new Categories(c.getString(0)));
                    }
                } catch (RemoteException e) {
                    Log.wtf(TAG, "Query provider failed");
                }
            }
        } catch (final RemoteException | RecordNotFoundException e) {
            throw new LocalStorageException(e);
        }

    }

    @Override
    public ToDo newResource(final long localID, final String resourceName, final String eTag) {
        return new ToDo(localID, resourceName, eTag);
    }

    @Override
    public void deleteAllExceptRemoteNames(final Resource[] remoteResources) {
        final String where;
        if (remoteResources.length != 0) {
            final List<String> sqlFileNames = new LinkedList<>();
            for (final Resource res : remoteResources) {
                sqlFileNames.add(DatabaseUtils.sqlEscapeString(res.getName()));
            }
            where = entryColumnRemoteName() + " NOT IN ("
                    + StringUtils.join(sqlFileNames, ",") + ')';
        } else {
            where = entryColumnRemoteName() + " IS NOT NULL";
        }
        ContentProviderOperation.Builder builder = null;
        try {
            builder = ContentProviderOperation.newDelete(entriesURI())
                    .withSelection(
                            entryColumnParentID() + "=? AND (" + where + ')',
                            new String[]{String.valueOf(id)});
        } catch (RecordNotFoundException e) {
            Log.wtf(TAG,"failed to delete all expect remote",e);
            return;
        }
        pendingOperations.add(builder.withYieldAllowed(true).build());
    }

    @Override
    protected ContentProviderOperation.Builder buildEntry(ContentProviderOperation.Builder builder, final Resource resource, final boolean insert) {
        final ToDo todo = (ToDo)resource;
        if((todo.getCreated() == null) || (todo.getUpdated() == null)){
            Log.wtf(TAG,"somehow this task does not exists");
            return builder;
        }
        builder = builder.withValue(TaskContract.Tasks.TITLE, todo.getSummary())
                .withValue(TaskContract.Tasks.SYNC1, todo.getETag())
                .withValue(entryColumnUID(), todo.getUid())
                .withValue(TaskContract.Tasks.CREATED, todo.getCreated().getDate().getTime())
                .withValue(TaskContract.Tasks.LAST_MODIFIED, todo.getUpdated().getDate().getTime())
                .withValue(TaskContract.Tasks._SYNC_ID, todo.getName());
        if(insert){
            builder.withValue(TaskContract.Tasks.LIST_ID, id);
        }
        if((todo.getStatus() != null) && (todo.getDateCompleted() == null)){
            final Status status=todo.getStatus();
            if(Objects.equals(status, Status.VTODO_CANCELLED)){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_CANCELLED);
            }else if(Objects.equals(status, Status.VTODO_COMPLETED)){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_COMPLETED);
            }else if(Objects.equals(status, Status.VTODO_IN_PROCESS)){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_IN_PROCESS);
            }else if(Objects.equals(status, Status.VTODO_NEEDS_ACTION)){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_NEEDS_ACTION);
            }else{
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_DEFAULT);
            }
        }else{
            builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_DEFAULT);
        }
        // .withValue(Tasks.U, value)//TODO uid??
        if (todo.getDue() != null) {
            builder = builder.withValue(TaskContract.Tasks.DUE, todo.getDueInMillis()).withValue(TaskContract.Tasks.IS_ALLDAY,1);
        }
        if (todo.getPriority() != null)
            builder = builder.withValue(TaskContract.Tasks.PRIORITY, todo.getPriority()
                    .getLevel());
        if (todo.getDescription() != null)
            builder = builder.withValue(TaskContract.Tasks.DESCRIPTION,
                    todo.getDescription());
        if(todo.getCompleted()!=null){
            builder.withValue(TaskContract.Tasks.PERCENT_COMPLETE, todo.getCompleted().getPercentage());
        }else{
            builder.withValue(TaskContract.Tasks.PERCENT_COMPLETE, 0);
        }
        if(todo.getDateCompleted()!=null){
            builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_COMPLETED);
        }
        return builder;
    }

    @Override
    protected void addDataRows(final Resource resource, final long localID, final int backrefIdx){
        final ToDo todo = (ToDo) resource;
        for (final Object category : todo.getCategories()) {
            final Iterator categoryList =((Categories)category).getCategories().iterator();
            while (categoryList.hasNext()){
                try {
                    pendingOperations.add(buildCategory(
                            newDataInsertBuilder(propertyUri(), TaskContract.Properties.TASK_ID, localID, backrefIdx), (String) categoryList.next())
                            .build());
                } catch (final LocalStorageException e) {
                    Log.wtf(TAG,"entry not found while adding data row: ",e);
                }
            }
        }
    }

    protected ContentProviderOperation.Builder buildCategory(ContentProviderOperation.Builder builder, String category) {
        return builder.withValue(TaskContract.Property.Category.CATEGORY_NAME, category)
                .withValue(TaskContract.Property.Category.MIMETYPE, "vnd.android.cursor.item/category");

    }

    protected Uri propertyUri() throws LocalStorageException {
        List<Uri> uris= todoURI(ctx,account, TaskContract.Properties.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new LocalStorageException("No Taskprovider found");
        }
        return uris.get(0);
    }


    @Override
    protected void removeDataRows(Resource resource) throws LocalStorageException{
        try {
            @Cleanup final Cursor c = providerClient.query(propertyUri(), new String[]{TaskContract.Property.Category.CATEGORY_ID},
                    TaskContract.Properties.MIMETYPE + "=? AND " + TaskContract.Properties.TASK_ID + "=?",
                    new String[]{"vnd.android.cursor.item/category", String.valueOf(resource.getLocalID())}, null);
            while (c.moveToNext()) {
                pendingOperations.add(
                        ContentProviderOperation.newDelete(ContentUris.withAppendedId(propertyUri(), c.getLong(0))).build());
            }
        }catch (final RemoteException e){
            throw new LocalStorageException("Something went wrong while deleting categories",e);
        }

    }

    private Uri alarmUri() throws LocalStorageException {
        List<Uri> uris= todoURI(ctx, account, TaskContract.Alarms.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new LocalStorageException("No Taskprovider found");
        }
        return uris.get(0);
    }

    public static List<Uri> todoURI(final Context ctx,Account account,final String basePath) {
        final List<Uri> uris = new ArrayList<>();
        final PackageManager pm = ctx.getPackageManager();
        try {
            final PackageInfo mirakel = pm.getPackageInfo("de.azapps.mirakelandroid",
                    PackageManager.GET_PROVIDERS);
            if ((mirakel != null) && (mirakel.versionCode > 18)) {
                uris.add(Uri.parse("content://" + TaskContract.AUTHORITY + '/' + basePath)
                        .buildUpon()
                        .appendQueryParameter(TaskContract.ACCOUNT_NAME,
                                account.name)
                        .appendQueryParameter(TaskContract.ACCOUNT_TYPE,
                                account.type)
                        .appendQueryParameter(
                                TaskContract.CALLER_IS_SYNCADAPTER, "true")
                        .build());
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Mirakel not found");
        }catch (final RuntimeException e) {
            Log.wtf(TAG,Log.getStackTraceString(e));
        }
        try {
            final PackageInfo dmfs = pm.getPackageInfo("org.dmfs.provider.tasks",
                    PackageManager.GET_PROVIDERS);
            if (dmfs != null) {
                uris.add(Uri.parse("content://" + TaskContract.AUTHORITY_DMFS + '/' + basePath)
                        .buildUpon()
                        .appendQueryParameter(TaskContract.ACCOUNT_NAME,
                                account.name)
                        .appendQueryParameter(TaskContract.ACCOUNT_TYPE,
                                account.type)
                        .appendQueryParameter(
                                TaskContract.CALLER_IS_SYNCADAPTER, "true")
                        .build());
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "dmfs not found");
        }
        if (uris.isEmpty()) {
            //TODO show tost here
            //Toast.makeText(ctx, R.string.install_taskprovider,
            //		Toast.LENGTH_LONG).show();
        }
        return uris;
    }

    protected Uri listsURI() throws RecordNotFoundException {
        List<Uri> uris= todoURI(ctx, account, TaskContract.TaskLists.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }
}
