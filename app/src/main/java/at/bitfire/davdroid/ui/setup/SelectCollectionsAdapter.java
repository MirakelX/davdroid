/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;

import at.bitfire.davdroid.mirakel.R;
import at.bitfire.davdroid.resource.ServerInfo;
import lombok.Getter;

public class SelectCollectionsAdapter extends BaseAdapter implements ListAdapter {
	final static int TYPE_ADDRESS_BOOKS_HEADING = 0,
		TYPE_ADDRESS_BOOKS_ROW = 1,
		TYPE_CALENDARS_HEADING = 2,
		TYPE_CALENDARS_ROW = 3,
		TYPE_TODO_LIST_HEADING = 4,
		TYPE_TODO_LIST_ROW = 5;
	private static final String TAG = "SelectCollectionsAdapter";

    protected Context context;
	protected ServerInfo serverInfo;
	@Getter protected int nAddressBooks,
			       nAddressbookHeadings,
			       nCalendars,
			       nCalendarHeadings,
			       nToDoLists,
			       nToDoListsHeadings;
	
	
	public SelectCollectionsAdapter(Context context, ServerInfo serverInfo) {
		this.context = context;
		
		this.serverInfo = serverInfo;
		nAddressBooks = (serverInfo.getAddressBooks() == null) ? 0 : serverInfo.getAddressBooks().size();
		nAddressbookHeadings = (nAddressBooks == 0) ? 0 : 1;
		nCalendars = (serverInfo.getCalendars() == null) ? 0 : serverInfo.getCalendars().size();
		nCalendarHeadings = (nCalendars == 0) ? 0 : 1;
		nToDoLists = (serverInfo.getTodoLists() == null) ? 0 : serverInfo.getTodoLists().size();
		nToDoListsHeadings = (nToDoLists == 0) ? 0 : 1;
	}
	
	
	// item data
	
	@Override
	public int getCount() {
		return nAddressbookHeadings + nAddressBooks + nCalendarHeadings + nCalendars+nToDoLists+nToDoListsHeadings;
	}

	@Override
	public Object getItem(int position) {

        switch (getItemViewType(position)){
            case TYPE_ADDRESS_BOOKS_ROW:
                return serverInfo.getAddressBooks().get(position - 1);
            case TYPE_CALENDARS_ROW:
                return serverInfo.getCalendars().get(position - nAddressBooks - 2);
            case TYPE_TODO_LIST_ROW:
                return serverInfo.getTodoLists().get(position - nAddressBooks - nCalendars - 3);
            case TYPE_TODO_LIST_HEADING:
            case TYPE_ADDRESS_BOOKS_HEADING:
            case TYPE_CALENDARS_HEADING:
            default:
                Log.wtf(TAG, "unsupported type");
        }
        Log.wtf(TAG, "pos: " +position);
        Log.wtf(TAG,"calcount: "+nCalendars);
        Log.wtf(TAG,"addrcount: "+nAddressBooks);
        Log.wtf(TAG,"todocount: "+nToDoLists);
		return null;
	}
	
	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	
	// item views

	@Override
	public int getViewTypeCount() {
		return 6;
	}

	@Override
	public int getItemViewType(int p) {
		int position = p;
		if ((nAddressbookHeadings != 0) && (position == 0)) {
			return TYPE_ADDRESS_BOOKS_HEADING;
		}
		position -= nAddressbookHeadings;
		if ((nAddressbookHeadings != 0) && (position >= 0) && (position < nAddressBooks)) {
			return TYPE_ADDRESS_BOOKS_ROW;
		}
		position-=nAddressBooks;
		if ((nCalendarHeadings != 0) && (position == 0)) {
			return TYPE_CALENDARS_HEADING;
		}
		position-=nCalendarHeadings;
		if ((nCalendarHeadings != 0) && (position >= 0) && (position < nCalendars)) {
			return TYPE_CALENDARS_ROW;
		}
		position-=nCalendars;
		if ((nToDoListsHeadings != 0) && (position == 0)) {
			return TYPE_TODO_LIST_HEADING;
		}
		position-=nToDoListsHeadings;
		if ((nToDoListsHeadings != 0) && (position >= 0) && (position < nToDoLists)) {
			return TYPE_TODO_LIST_ROW;
		}
		position-=nToDoLists;

		return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	@SuppressLint("InflateParams")
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		
		// step 1: get view (either by creating or recycling)
		if (v == null) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (getItemViewType(position)) {
			case TYPE_ADDRESS_BOOKS_HEADING:
				v = inflater.inflate(R.layout.setup_address_books_heading, parent, false);
				break;
			case TYPE_ADDRESS_BOOKS_ROW:
				v = inflater.inflate(android.R.layout.simple_list_item_single_choice, null);
				v.setPadding(0, 8, 0, 8);
				break;
			case TYPE_CALENDARS_HEADING:
				v = inflater.inflate(R.layout.setup_calendars_heading, parent, false);
				break;
		    case TYPE_CALENDARS_ROW:
		    case TYPE_TODO_LIST_ROW:
				v = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null);
				v.setPadding(0, 8, 0, 8);
		        break;
		    case TYPE_TODO_LIST_HEADING:
		        //TODO change this!!!
		        v = inflater.inflate(R.layout.setup_todo_list_heading, parent, false);
		        break;
			}
		}
		
		// step 2: fill view with content
		switch (getItemViewType(position)) {
		case TYPE_ADDRESS_BOOKS_ROW:
			setContent((CheckedTextView)v, R.drawable.addressbook, (ServerInfo.ResourceInfo)getItem(position));
			break;
		case TYPE_CALENDARS_ROW:
			setContent((CheckedTextView)v, R.drawable.calendar, (ServerInfo.ResourceInfo)getItem(position));
		    break;
		case TYPE_TODO_LIST_ROW:
		    setContent((CheckedTextView)v, R.drawable.ic_mirakel, (ServerInfo.ResourceInfo)getItem(position));
		    break;
		}
		
		return v;
	}
	
	protected void setContent(CheckedTextView view, int collectionIcon, ServerInfo.ResourceInfo info) {
		// set layout and icons
		view.setCompoundDrawablesWithIntrinsicBounds(collectionIcon, 0, info.isReadOnly() ? R.drawable.ic_read_only : 0, 0);
		view.setCompoundDrawablePadding(10);
		
		// set text		
		String title = "<b>" + info.getTitle() + "</b>";
		if (info.isReadOnly())
			title = title + " (" + context.getString(R.string.setup_read_only) + ")";
		
		String description = info.getDescription();
		if (description == null)
			description = info.getURL();
		
		// FIXME escape HTML
		view.setText(Html.fromHtml(title + "<br/>" + description));
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		int type = getItemViewType(position);
		return (type == TYPE_ADDRESS_BOOKS_ROW || type == TYPE_CALENDARS_ROW||type==TYPE_TODO_LIST_ROW);
	}
}
