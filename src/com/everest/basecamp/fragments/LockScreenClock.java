/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.everest.basecamp.fragments;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SearchIndexable
public class LockScreenClock extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "LockScreenClock";

    private static final String MAIN_WIDGET_1_KEY = "main_custom_widgets1";
    private static final String MAIN_WIDGET_2_KEY = "main_custom_widgets2";
    private static final String EXTRA_WIDGET_1_KEY = "custom_widgets1";
    private static final String EXTRA_WIDGET_2_KEY = "custom_widgets2";
    private static final String EXTRA_WIDGET_3_KEY = "custom_widgets3";
    private static final String EXTRA_WIDGET_4_KEY = "custom_widgets4";
    private static final String KEY_APPLY_CHANGE_BUTTON = "apply_change_button";

    private Preference mMainWidget1;
    private Preference mMainWidget2;
    private Preference mExtraWidget1;
    private Preference mExtraWidget2;
    private Preference mExtraWidget3;
    private Preference mExtraWidget4;
    private Button mApplyChange;

    private Map<Preference, String> widgetKeysMap = new HashMap<>();
    private Map<Preference, String> initialWidgetKeysMap = new HashMap<>();

    private SwitchPreferenceCompat mLockScreenWidgetsEnabledPref;
    private List<Preference> mWidgetPreferences;

    private Preference mClockStyle;
    private Preference mClockFontPref;
    private Preference mDeviceInfoWidgetPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.basecamp_lockscreen_clock);

        mMainWidget1 = findPreference(MAIN_WIDGET_1_KEY);
        mMainWidget2 = findPreference(MAIN_WIDGET_2_KEY);
        mExtraWidget1 = findPreference(EXTRA_WIDGET_1_KEY);
        mExtraWidget2 = findPreference(EXTRA_WIDGET_2_KEY);
        mExtraWidget3 = findPreference(EXTRA_WIDGET_3_KEY);
        mExtraWidget4 = findPreference(EXTRA_WIDGET_4_KEY);
        mDeviceInfoWidgetPref = (Preference) findPreference("lockscreen_display_widgets");

        mWidgetPreferences = Arrays.asList(mMainWidget1, mMainWidget2, mExtraWidget1, mExtraWidget2, mExtraWidget3, mExtraWidget4, mDeviceInfoWidgetPref);

        final boolean mClockStyleEnabled = Settings.System.getIntForUser(getActivity().getContentResolver(), "clock_style", 0, UserHandle.USER_CURRENT) != 0;

        mClockFontPref = (Preference) findPreference("android.theme.customization.lockscreen_clock_font");

        mClockStyle = (Preference) findPreference("clock_style");
        mClockStyle.setOnPreferenceChangeListener(this);
        mClockFontPref.setVisible(!mClockStyleEnabled);

        final boolean isLsWidgetsEnabled = Settings.System.getIntForUser(getActivity().getContentResolver(), "lockscreen_widgets_enabled", 0, UserHandle.USER_CURRENT) != 0;

        if (!isLsWidgetsEnabled) {
            showWidgetPreferences(false);
        } else {
            showWidgetPreferences(true);
            for (Preference widgetPref : mWidgetPreferences) {
                widgetPref.setOnPreferenceChangeListener(this);
                widgetKeysMap.put(widgetPref, "");
            }
            final String mainWidgets = Settings.System.getString(getActivity().getContentResolver(), "lockscreen_widgets");
            final String extraWidgets = Settings.System.getString(getActivity().getContentResolver(), "lockscreen_widgets_extras");
            setWidgetValues(mainWidgets, mMainWidget1, mMainWidget2);
            setWidgetValues(extraWidgets, mExtraWidget1, mExtraWidget2, mExtraWidget3, mExtraWidget4);
        }

        mLockScreenWidgetsEnabledPref = (SwitchPreferenceCompat) findPreference("lockscreen_widgets_enabled");
        mLockScreenWidgetsEnabledPref.setChecked(isLsWidgetsEnabled);
        mLockScreenWidgetsEnabledPref.setOnPreferenceChangeListener(this);

        LayoutPreference preference = findPreference(KEY_APPLY_CHANGE_BUTTON);
        mApplyChange = (Button) preference.findViewById(R.id.apply_change);
        mApplyChange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateWidgetPreferences();
                saveInitialPreferences();
                mApplyChange.setEnabled(false);
            }
        });
        saveInitialPreferences();
        mApplyChange.setEnabled(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        restoreInitialPreferences();
    }

    @Override
    public void onStop() {
        super.onStop();
        restoreInitialPreferences();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        restoreInitialPreferences();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (widgetKeysMap.containsKey(preference)) {
            widgetKeysMap.put(preference, String.valueOf(newValue));
            mApplyChange.setEnabled(hasChanges());
            return true;
        } else if (preference == mLockScreenWidgetsEnabledPref) {
            boolean isEnabled = (boolean) newValue;
            showWidgetPreferences(isEnabled);
            mLockScreenWidgetsEnabledPref.setChecked(isEnabled);
            return true;
        } else if (preference == mClockStyle) {
            int clockStyleValue = Integer.parseInt((String) newValue);
            boolean clockStyleEnabled = clockStyleValue != 0;
            mClockFontPref.setVisible(!clockStyleEnabled);
            return true;
        }
        return false;
    }

    private void showWidgetPreferences(boolean isEnabled) {
        for (Preference widgetPref : mWidgetPreferences) {
            widgetPref.setVisible(isEnabled);
        }
    }

    private void setWidgetValues(String widgets, Preference... preferences) {
        if (widgets == null) {
            return;
        }
        List<String> widgetList = Arrays.asList(widgets.split(","));
        for (int i = 0; i < preferences.length && i < widgetList.size(); i++) {
            widgetKeysMap.put(preferences[i], widgetList.get(i).trim());
        }
    }

    private void updateWidgetPreferences() {
        List<String> mainWidgetsList = Arrays.asList(widgetKeysMap.get(mMainWidget1), widgetKeysMap.get(mMainWidget2));
        List<String> extraWidgetsList = Arrays.asList(widgetKeysMap.get(mExtraWidget1), widgetKeysMap.get(mExtraWidget2), widgetKeysMap.get(mExtraWidget3), widgetKeysMap.get(mExtraWidget4));

        mainWidgetsList = replaceEmptyWithNone(mainWidgetsList);
        extraWidgetsList = replaceEmptyWithNone(extraWidgetsList);

        String mainWidgets = TextUtils.join(",", mainWidgetsList);
        String extraWidgets = TextUtils.join(",", extraWidgetsList);

        Settings.System.putString(getActivity().getContentResolver(), "lockscreen_widgets", mainWidgets);
        Settings.System.putString(getActivity().getContentResolver(), "lockscreen_widgets_extras", extraWidgets);
    }

    private List<String> replaceEmptyWithNone(List<String> inputList) {
        return inputList.stream()
                .map(s -> TextUtils.isEmpty(s) ? "none" : s)
                .collect(Collectors.toList());
    }

    private void saveInitialPreferences() {
        for (Preference widgetPref : mWidgetPreferences) {
            String value = widgetKeysMap.get(widgetPref);
            initialWidgetKeysMap.put(widgetPref, value);
        }
    }

    private void restoreInitialPreferences() {
        for (Map.Entry<Preference, String> entry : initialWidgetKeysMap.entrySet()) {
            widgetKeysMap.put(entry.getKey(), entry.getValue());
        }
        updateWidgetPreferences();
    }

    private boolean hasChanges() {
        for (Map.Entry<Preference, String> entry : initialWidgetKeysMap.entrySet()) {
            Preference pref = entry.getKey();
            String initialValue = entry.getValue();
            String currentValue = widgetKeysMap.get(pref);
            if (!TextUtils.equals(initialValue, currentValue)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.EVEREST;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.basecamp_lockscreen_clock) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    return keys;
                }
            };
}
