/*
 * Copyright 2015 Jason J. (iamovrhere)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ovrhere.android.currencyconverter.ui.fragments;

import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.ovrhere.android.currencyconverter.R;
import com.ovrhere.android.currencyconverter.model.CurrencyResourceMap;
import com.ovrhere.android.currencyconverter.model.ExchangeRateUpdateLoader;
import com.ovrhere.android.currencyconverter.model.data.CurrencyConverterContract.DisplayOrderEntry;
import com.ovrhere.android.currencyconverter.model.data.CurrencyConverterContract.ExchangeRateEntry;
import com.ovrhere.android.currencyconverter.prefs.PreferenceUtils;
import com.ovrhere.android.currencyconverter.ui.adapters.CurrencyCursorAdapter;
import com.ovrhere.android.currencyconverter.ui.adapters.CurrencySpinnerAdapter;
import com.ovrhere.android.currencyconverter.utils.CompatClipboard;
import com.ovrhere.android.currencyconverter.utils.CurrencyCalculator;
import com.ovrhere.android.currencyconverter.utils.DateFormatter;
import com.ovrhere.android.currencyconverter.utils.KeyboardUtil;

/**
 * The main fragment where values are inputed and results shown.
 * @author Jason J.
 * @version 0.9.0-20150527
 */
public class MainFragment extends Fragment implements OnItemLongClickListener {	
	/** The class name used for bundles. */
	final static private String CLASS_NAME = MainFragment.class.getSimpleName();
	/** The log tag for errors. */
	final static private String LOGTAG = CLASS_NAME;
	/** Whether or not to debug. */
	final static private boolean DEBUG = false;
	
	/** The id for the exchange rate loader. */
	private static final int LOADER_EXCHANGE_RATE_UPDATE = 0;
	/** The id for the cursor loader. */ 
	private static final int LOADER_EXCHANGE_RATES = 1;
	
	/** Bundle key - Boolean. The key for saving the state of the message warning. */
	private static final String KEY_SHOW_WARNING = CLASS_NAME + ".KEY_SHOW_WARNING";
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// End constants
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	/** The from adapter. */
	private CurrencySpinnerAdapter mSourceCurrAdapter = null;
	/** The to adapter. */
	private CurrencySpinnerAdapter mDestCurrAdapter = null;
	
	/** The output list adapter. */
	private CurrencyCursorAdapter mOutputListAdapter = null;
	
	
	/** The shared preference handle. */
	private SharedPreferences mPrefs = null;
	/** The state of the view. */
	private boolean mViewBuilt = false;
	
	
	/** The source of conversion. Set by {@link #sourceItemSelectedListener} */
	private String mStartingCurrency = "";
	/** The target of conversion. Set by {@link #destItemSelectListener};
	 * can be empty for "View All". */
	private String mTargetCurrency = "";
		
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// Views start here
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	/** The spinner for the from Currency. */
	private Spinner sp_sourceCurr = null;
	/** The spinner for the to Currency. */
	private Spinner sp_destCurr = null;
	/** The current currency flag. */
	private ImageView img_currFlag = null;
	/** The current currency symbol. */
	private TextView tv_currSymbol = null;
	/** The current warning message. */
	private TextView tv_warning = null;
	/** The currency input. */
	private EditText et_currInput = null;
	
	/** The menu item of the refresh button to be animated with #mRotationAnim */
	private MenuItem mRefreshItem = null;
	/** The rotation animation to apply to {@link #mRefreshItem}. */
	private Animation mRotationAnim = null;
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// End members 
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	public MainFragment() {	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {	
		super.onSaveInstanceState(outState);
		if (tv_warning != null) {
			outState.putBoolean(KEY_SHOW_WARNING, tv_warning.getVisibility() == View.VISIBLE);
		}
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		
		mRotationAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.clockwise_spin);
		mRotationAnim.setRepeatCount(Animation.INFINITE);	
		
		mStartingCurrency = getResources().getStringArray(R.array.currConv_rateOrder)[0]; //grab the first element.  
	}
	

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		getLoaderManager().initLoader(LOADER_EXCHANGE_RATES, null, cursorLoaderCallback);
		
		if (needToUpdateExchangeRates()){
			fetchNewExchangeRates();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_main, container,
				false);
		
		initAdapters();
		initInputViews(rootView);		
		initOutputViews(rootView);
		initKeyboardHide(rootView);
		processSavedState(savedInstanceState);
		
		updateListOutput();
		mViewBuilt = true; 
		return rootView;
	}

	
	@Override
	public void onResume() {	
		super.onResume(); //our view is visible
		checkIfFetchingNewExchangeRates(false);
	}
	
	@Override
	public void onDestroyView() {	
		super.onDestroyView();
		mViewBuilt = false;
	}
	
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main_frag, menu);
        
        final Menu fMenu = menu;
        mRefreshItem = menu.findItem(R.id.action_refresh);
        
        MenuItemCompat.getActionView(mRefreshItem).setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {   
              fMenu.performIdentifierAction(mRefreshItem.getItemId(), 0);
           }
        });
        
        initCheckRunningAtLayoutTime(MenuItemCompat.getActionView(mRefreshItem));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_refresh:
                Log.d(LOGTAG, "Refreshing?");
                fetchNewExchangeRates();
                return true;
                
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenuInfo menuInfo) {
		  if (v.getId()==R.id.currConv_main_listView) {
		    AdapterView.AdapterContextMenuInfo info = 
		    		(AdapterView.AdapterContextMenuInfo)menuInfo;
		    
		    Cursor cursor = (Cursor) mOutputListAdapter.getItem(info.position);
		    if (cursor != null){
		    	String title = getString(R.string.currConv_context_currencyAction,
		    			cursor.getString(CurrencyCursorAdapter.COL_DESTINATION_CODE) );
		    	
		    	menu.setHeaderTitle(title);
			    menu.add(Menu.CATEGORY_SECONDARY, 0, 0, android.R.string.copy);
			    menu.add(Menu.CATEGORY_SECONDARY, 1, 1, 
			    		R.string.currConv_context_detailedCopy);
		    }
		  }
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getGroupId() == Menu.CATEGORY_SECONDARY){
			AdapterView.AdapterContextMenuInfo info = 
					(AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
			switch (item.getItemId()){
				case 0:
					int pos1 = info.position;
					copyConvertedValue(pos1, false);
					break;
				case 1:
					int pos2 = info.position;
					copyConvertedValue(pos2, true);
					break;
			}
		}
		return super.onContextItemSelected(item); 
	}
	

	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// Initializer helper methods
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	/** Performs actions that need to done after layout time. 
	 * @param view The view to listen on for layout */
	private void initCheckRunningAtLayoutTime(final View view) {
		final ViewTreeObserver vto = view.getViewTreeObserver();
		
		vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() { 
		    @SuppressLint("NewApi")
			@SuppressWarnings("deprecation")
			@Override 
		    public void onGlobalLayout() { 
		    	checkIfFetchingNewExchangeRates(false);
		    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
		    		view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		    	} else { //16 and up.
		    		view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		    	}
		    } 
		});
	}
	
	/** Processes the saved state, preferences and updates views accordingly.
	 * Assumes all views to be valid.
	 * @param savedInstanceState The current saved state.
	 * @see #initInputViews(View)
	 * @see #initOutputViews(View) 	 */
	private void processSavedState(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			if (savedInstanceState.getBoolean(KEY_SHOW_WARNING, false)) {
				checkUpdateIntervalAndWarn();
			}			
		}
		
		int sourceCurrSelect = 
				mPrefs.getInt( getString(R.string.currConv_pref_KEY_SOURCE_CURRENCY_INDEX), 
							0);
		int destCurrSelect = 
				mPrefs.getInt( getString(R.string.currConv_pref_KEY_DEST_CURRENCY_INDEX), 
							0);		
		
		sp_sourceCurr.setSelection(sourceCurrSelect);
		sp_destCurr.setSelection(destCurrSelect);
		
		String input = et_currInput.getText().toString();
				
		mOutputListAdapter.updateCurrentValue(convertToDouble(input));
	}
	
	/** Initializes the output views such as textviews, images & listview.
	 * Assumes adapters are valid.
	 * @param rootView The rootview to configure	
	 * @see #initAdapters()  */
	private void initOutputViews(View rootView) {
		ListView outputListView = (ListView) rootView.findViewById(R.id.currConv_main_listView);
		outputListView.setAdapter(mOutputListAdapter);
		outputListView.setOnItemLongClickListener(this);
		registerForContextMenu(outputListView);
				
		tv_currSymbol = (TextView) rootView.findViewById(R.id.currConv_main_text_currSymbol);
		tv_warning = (TextView) rootView.findViewById(R.id.currConv_main_text_warning);

		img_currFlag = (ImageView)  rootView.findViewById(R.id.currConv_main_image_currFlag);

		//show or hide flags, depending on bool.
		boolean showFlags = getResources().getBoolean(R.bool.currconv_showflags);
		img_currFlag.setVisibility(showFlags ?  View.VISIBLE : View.GONE);
	}
	
	/** Initializes all input views. Assumes adapters are valid.
	 * @param rootView The rootview to configure	
	 * @see #initAdapters()	 */
	private void initInputViews(View rootView) {
		sp_sourceCurr = (Spinner) rootView.findViewById(R.id.currConv_main_spinner_currencySource);
		sp_sourceCurr.setAdapter(mSourceCurrAdapter);
		sp_sourceCurr.setOnItemSelectedListener(sourceItemSelectedListener);
		
		sp_destCurr = (Spinner) rootView.findViewById(R.id.currConv_main_spinner_currencyDest);
		sp_destCurr.setAdapter(mDestCurrAdapter);
		sp_destCurr.setOnItemSelectedListener(destItemSelectListener);
		
		et_currInput = (EditText) rootView.findViewById(R.id.currConv_main_edittext_valueToConv);
		et_currInput.addTextChangedListener(valueInputListener);		
	}
	
	
	/** Initializes adapters for output list and spinners. */
	private void initAdapters() {
		mOutputListAdapter = new CurrencyCursorAdapter(getActivity(), null, 0);
		
		String[] entries = getResources().getStringArray(R.array.currConv_rateOrder);
		
		mSourceCurrAdapter = new CurrencySpinnerAdapter(
							 getActivity(), android.R.layout.simple_list_item_1, entries);
		
		
		mDestCurrAdapter = new CurrencySpinnerAdapter(
							getActivity(), 
							android.R.layout.simple_list_item_1, 
							entries,
							getString(R.string.currConv_spinner_dest_selectAll)
						);
		
	}
	
	
	/** Initializes the hiding of the keyboard for all non-edit-texts views.
	 * @param rootView The rootview to attach to.
	 */
	private void initKeyboardHide(View rootView){
		final FragmentActivity activity = getActivity();
		rootView.setOnTouchListener(new View.OnTouchListener() {	
				//this is will bubble until consumed (such as EditText)
				@SuppressLint("ClickableViewAccessibility")
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					KeyboardUtil.hideSoftKeyboard(activity);
					return false; //we aren't consume either
				}
			});
	}
	
	
	/** Updates source views to match source currency. */
	private void updateSourceCurrency(){
		if (sp_sourceCurr == null || tv_warning == null || tv_currSymbol == null || 
			img_currFlag == null ||getActivity() == null){
			return; //nothing can be set.
		}
		
		final String sourceCode = (String) sp_sourceCurr.getSelectedItem();
		final CurrencyResourceMap map = CurrencyResourceMap.valueOf(sourceCode.toUpperCase(Locale.US)); 
						
		tv_currSymbol.setText(map.mSymbol);
		int flagId = map.mFlagResId; 
		if (flagId >= 0){
			img_currFlag.setImageDrawable(getResources().getDrawable(flagId));
		}
		
		//whenever this changes, we change.
		updateListOutput();
	}

	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// Helper Methods
	////////////////////////////////////////////////////////////////////////////////////////////////

	/** Copies the converted currency at the given position. 
	 * @param position The position the item was selected from.
	 * @param detailed <code>true</code> to copy with multiple decimals places,
	 * <code>false</code> for the basic currency decimals.	 */
	private void copyConvertedValue(int position, boolean detailed) {
		Cursor output = (Cursor) mOutputListAdapter.getItem(position);
		
		if (output != null){
			String currencyCode = output.getString(CurrencyCursorAdapter.COL_DESTINATION_CODE);
			
			double inputAmount = convertToDouble(et_currInput.getText().toString());
			double rate = output.getDouble(CurrencyCursorAdapter.COL_EXCHANGE_RATE); 
			
			//produces: $ [converted value] CODE
			String value = 
					CurrencyCalculator.calculateAndFormat(currencyCode, inputAmount, rate, detailed);
			
			String label = getString(R.string.currConv_clipboard_label_copiedCurrency);
			CompatClipboard.copyToClipboard(getActivity(), label, value);
		}
	}
	
	/**
	 * Checks to see if the time since the last update exceeds that of the interval.
	 * @return <code>true</code> if time elapsed exceeds the update interval,
	 * <code>false</code> if no action is necessary.
	 */
	private boolean needToUpdateExchangeRates() {
		final long updateInterval = PreferenceUtils.getUpdateInterval(getActivity());		
		final long lastUpdate = PreferenceUtils.getLastUpdateTime(getActivity());
		final long interval =  System.currentTimeMillis() - lastUpdate;
		
		return updateInterval < interval;
	}

	/** Compares the update interval and last update time. If enough time has elapsed,
	 * it initiates the update and sets the warning. If not, it hides the warning.
	 * */
	private void checkUpdateIntervalAndWarn() {		
		final long lastUpdate = PreferenceUtils.getLastUpdateTime(getActivity());
		
		if (needToUpdateExchangeRates()) {
			String timestamp = DateFormatter.dateToRelativeDate(getActivity(), lastUpdate);
			tv_warning.setText(
					getString(R.string.currConv_cachedRate_warning, 
							timestamp)
							);
			tv_warning.setVisibility(View.VISIBLE);
		} else {
			tv_warning.setVisibility(View.GONE);
		}
	}	
	
	
	/** Parses input and sends it to adapter for calculation(s).
	 * @param input The input to strip & parse.		 */
	private void calculateOutput(String input) {
		double value = convertToDouble(input);
		mOutputListAdapter.updateCurrentValue(value);
	}	
	
	
	/** Sets an integer preference 
	 * @param stringRes The preference string resource
	 * @param value The value to insert.	 */
	private void putIntPref(int stringRes, int value){
		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putInt(getString(stringRes), value);
		editor.commit();
	}
	
	/** Initializes updates via the loader. */
	private void fetchNewExchangeRates(){
		getLoaderManager().initLoader(LOADER_EXCHANGE_RATE_UPDATE, null, updateCallback);
		animateRefreshButton(true);
	}
	
	/**
	 * Updates the output cursor.
	 */
	private void updateListOutput() {
		getLoaderManager().restartLoader(LOADER_EXCHANGE_RATES, null, cursorLoaderCallback);
	}
	
	/**
	 * Checks to see if loader is loading. If so, we initiate a progress spinner,
	 * otherwise we hide it.
	 * @param force Force the spinner to show
	 */
	private void checkIfFetchingNewExchangeRates(boolean force) {
		//if the loader exists, we are updating. Otherwise, we destroyed it in onLoadFinished.
		boolean currentlyUpdating = force || 
				getLoaderManager().getLoader(LOADER_EXCHANGE_RATE_UPDATE) != null;
		
		//if updating, rotate. Otherwise, clear animation.
		animateRefreshButton(currentlyUpdating);
	}

	/** Animates the refresh button. */
	private void animateRefreshButton(boolean animate) {
		if (mRotationAnim != null && mRefreshItem != null) {
			final View refreshView  = MenuItemCompat.getActionView(mRefreshItem);
		
			if (refreshView == null) {
				return; //if we cannot see it, we cannot touch it.
			}
			
			if (animate) {
				refreshView.post(new Runnable() {
					@Override
					public void run() {
						refreshView.startAnimation(mRotationAnim);					
					}
				});
			} else {
				refreshView.postDelayed(new Runnable() {
					@Override
					public void run() {
						refreshView.clearAnimation();					
					}
				}, 1500); //ensure it was seen
			}
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// Utility function
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	/** Converts input to pure double. Only 0-9 and "." allowed
	 * @param input The string input. If empty, returns 0.
	 * @return The parsed double. 	 */
	static private double convertToDouble(String input) {
		if (input.isEmpty()){ return 0.0d; }
		//scrub input from non-valid input
		return Double.parseDouble(input.replaceAll("[^\\.0-9]", ""));
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/// Implemented interfaces
	////////////////////////////////////////////////////////////////////////////////////////////////
	
	/** The input listener for when entering values. */
	private TextWatcher valueInputListener = new TextWatcher() {		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			//nothing to do
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
			//nothing to do
		}
		
		@Override
		public void afterTextChanged(Editable s) {
			calculateOutput(s.toString().trim());
		}
	};
	
	/** The selection listener for the "from" spinner. */
	private OnItemSelectedListener sourceItemSelectedListener = 
			new OnItemSelectedListener() {
		@Override
		public void onItemSelected(android.widget.AdapterView<?> parent, 
				View view, int position, long id) {
			mStartingCurrency = (String) mSourceCurrAdapter.getItem(position); 
					
			putIntPref(
					R.string.currConv_pref_KEY_SOURCE_CURRENCY_INDEX, 
					position);
			
			updateSourceCurrency();
		};
		@Override
		public void onNothingSelected(android.widget.AdapterView<?> parent) {
			//do nothing for now
		};
	};
	
	/** The selection listener for the "to" spinner. */
	private OnItemSelectedListener destItemSelectListener =
			new OnItemSelectedListener() {
		@Override
		public void onItemSelected(android.widget.AdapterView<?> parent, 
				View view, int position, long id) {	
						
			String item =  (String) mDestCurrAdapter.getItem(position);
			if (item != null) { //we have a target code
				mTargetCurrency = item;
			} else {
				mTargetCurrency = "";
			}
			
			putIntPref(
					R.string.currConv_pref_KEY_DEST_CURRENCY_INDEX, 
					position);
			
			updateListOutput();
		};
		@Override
		public void onNothingSelected(android.widget.AdapterView<?> parent) {
			//do nothing for now
		}
	};
	
	
	
	@Override
	public boolean onItemLongClick(android.widget.AdapterView<?> parent, 
			View view, int position, long id) {
		Cursor cursor = (Cursor) mOutputListAdapter.getItem(position);
		if (cursor != null){
			Log.d(LOGTAG, "Testing value: "+ cursor);
		}
		return false;
	};

	////////////////////////////////////////////////////////////////////////////////////////////////
	//// Starting Loader interface
	////////////////////////////////////////////////////////////////////////////////////////////////

	private LoaderManager.LoaderCallbacks<Cursor> cursorLoaderCallback = new LoaderManager.LoaderCallbacks<Cursor>() {
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
			final boolean viewAll = mTargetCurrency.isEmpty();
					
			Uri request = null;
			String sortBy = null;
			
			if (viewAll) {
				request = ExchangeRateEntry.buildExchangeRateWithSourceCurrency(mStartingCurrency);
				sortBy = DisplayOrderEntry.COLUMN_DEF_DISPLAY_ORDER +" ASC "; 
			} else {
				request = ExchangeRateEntry.buildExchangeRateFromSourceToDest(mStartingCurrency, mTargetCurrency);
				sortBy = null;
			}
			

			//Log.d(LOGTAG, "Uri: " + request.toString() + ", Sort: " + sortBy);
			
			return new CursorLoader(
					getActivity(), 
					request, 
					CurrencyCursorAdapter.CURRENCY_LIST_COLUMNS, 
					null, 
					null, 
					sortBy);
		}
		
		@Override
		public void onLoaderReset(Loader<Cursor> loaders) {
			mOutputListAdapter.swapCursor(null);			
		}
		
		@Override
		public void onLoadFinished(Loader<Cursor> loaders, Cursor cursor) {
			if (cursor != null) {
				mOutputListAdapter.swapCursor(cursor);
			}
			
		}
	};
	
	private LoaderManager.LoaderCallbacks<Void> updateCallback = new LoaderManager.LoaderCallbacks<Void>() {
		@Override
		public Loader<Void> onCreateLoader(int id, Bundle bundle) {
			checkIfFetchingNewExchangeRates(true);
			return new ExchangeRateUpdateLoader(getActivity());
		}
		
		@Override
		public void onLoaderReset(Loader<Void> loaders) {}
		
		@Override
		public void onLoadFinished(Loader<Void> loaders, Void theVoid) {
			getLoaderManager().destroyLoader(LOADER_EXCHANGE_RATE_UPDATE);
			checkIfFetchingNewExchangeRates(false);
			checkUpdateIntervalAndWarn();
		}
	};
	
}
