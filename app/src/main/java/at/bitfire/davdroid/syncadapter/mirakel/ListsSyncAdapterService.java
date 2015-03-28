/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter.mirakel;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.RecordNotFoundException;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.resource.mirakel.CalDavList;
import at.bitfire.davdroid.resource.mirakel.LocalTodoList;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;

public class ListsSyncAdapterService extends Service {
	private static SyncAdapter syncAdapter;
	
	
	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new SyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter.close();
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder(); 
	}
	

	private static class SyncAdapter extends DavSyncAdapter {
		private final static String TAG = "davdroid.ListsSyncAdapter";
		private Context ctx;
		
		private SyncAdapter(Context context) {
			super(context);
			this.ctx=context;
		}
		@Override
		protected Map<LocalCollection<?>, RemoteCollection<?>> getSyncPairs(Account account, ContentProviderClient provider) {
			AccountSettings settings = new AccountSettings(getContext(), account);
			String	userName = settings.getUserName(),
					password = settings.getPassword();
			boolean preemptive = settings.getPreemptiveAuth();

			try {
				Map<LocalCollection<?>, RemoteCollection<?>> map = new HashMap<LocalCollection<?>, RemoteCollection<?>>();
				for (LocalTodoList todoList : LocalTodoList.findAll(account, provider,ctx)) {
                    if(todoList.getUrl()!=null) {
                        RemoteCollection<?> dav = new CalDavList(httpClient, todoList.getUrl(), userName, password, preemptive);
                        map.put(todoList, dav);
                    }
				}
				return map;
			} catch (final RemoteException ex) {
				Log.e(TAG, "Couldn't find local todo list", ex);
			} catch (final URISyntaxException ex) {
				Log.e(TAG, "Couldn't build calendar URI", ex);
			}catch (final RecordNotFoundException ex){
                Log.e(TAG, "No Taskprovider found", ex);
            }

			return null;
		}
	}
}
