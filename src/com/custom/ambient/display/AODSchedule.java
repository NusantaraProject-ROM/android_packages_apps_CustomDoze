/*
 * Copyright (C) 2021 Yet Another AOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.custom.ambient.display;

import android.app.Fragment;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.TimePicker;

import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;

import java.time.format.DateTimeFormatter;
import java.time.LocalTime;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.OnMainSwitchChangeListener;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.collapsingtoolbar.R;

public class AODSchedule extends CollapsingToolbarBaseActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, getNewFragment())
                    .commit();
        }
    }

    private PreferenceFragment getNewFragment() {
        return new MainSettingsFragment();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment,
            Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(),
            preference.getExtras());
        getFragmentManager().beginTransaction().replace(
                R.id.content_frame, instantiate).addToBackStack(preference.getKey()).commit();

        return true;
    }

    public static class MainSettingsFragment extends PreferenceFragment implements
            Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
            OnMainSwitchChangeListener {

        static final int MODE_DISABLED = 0;
        static final int MODE_NIGHT = 1;
        static final int MODE_TIME = 2;
        static final int MODE_MIXED_SUNSET = 3;
        static final int MODE_MIXED_SUNRISE = 4;

        private static final String MODE_KEY = "doze_always_on_auto_mode";
        private static final String SINCE_PREF_KEY = "doze_always_on_auto_since";
        private static final String TILL_PREF_KEY = "doze_always_on_auto_till";

        private Context mContext;

        private DropDownPreference mModePref;
        private Preference mSincePref;
        private Preference mTillPref;

        private MainSwitchPreference mSwitchBar;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            setPreferencesFromResource(R.xml.always_on_display_schedule, rootKey);

            mContext = getActivity();

            boolean enabled = Utils.isAoDEnabled(mContext);

            mSwitchBar = (MainSwitchPreference) findPreference(Utils.AOD_KEY);
            mSwitchBar.addOnSwitchChangeListener(this);
            mSwitchBar.setChecked(enabled);

            ContentResolver resolver = getActivity().getContentResolver();

            mSincePref = findPreference(SINCE_PREF_KEY);
            mSincePref.setOnPreferenceClickListener(this);
            mTillPref = findPreference(TILL_PREF_KEY);
            mTillPref.setOnPreferenceClickListener(this);

            int mode = Settings.Secure.getIntForUser(resolver,
                    MODE_KEY, MODE_DISABLED, UserHandle.USER_CURRENT);
            mModePref = (DropDownPreference) findPreference(MODE_KEY);
            mModePref.setValue(String.valueOf(mode));
            mModePref.setSummary(mModePref.getEntry());
            mModePref.setOnPreferenceChangeListener(this);

            updateTimeEnablement(mode);
            updateTimeSummary(mode);
        }

        @Override
        public void onSwitchChanged(Switch switchView, boolean isChecked) {
            mSwitchBar.setChecked(isChecked);
            Utils.enableAoD(isChecked, mContext);
            mModePref.setEnabled(isChecked);
            mSincePref.setEnabled(isChecked);
            mTillPref.setEnabled(isChecked);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int value = Integer.parseInt((String) newValue);
            int index = mModePref.findIndexOfValue((String) newValue);
            mModePref.setSummary(mModePref.getEntries()[index]);
            Settings.Secure.putIntForUser(getActivity().getContentResolver(),
                    MODE_KEY, value, UserHandle.USER_CURRENT);
            updateTimeEnablement(value);
            updateTimeSummary(value);
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            String[] times = getCustomTimeSetting();
            boolean isSince = preference == mSincePref;
            int hour, minute;
            TimePickerDialog.OnTimeSetListener listener = (view, hourOfDay, minute1) -> {
                updateTimeSetting(isSince, hourOfDay, minute1);
            };
            if (isSince) {
                String[] sinceValues = times[0].split(":", 0);
                hour = Integer.parseInt(sinceValues[0]);
                minute = Integer.parseInt(sinceValues[1]);
            } else {
                String[] tillValues = times[1].split(":", 0);
                hour = Integer.parseInt(tillValues[0]);
                minute = Integer.parseInt(tillValues[1]);
            }
            TimePickerDialog dialog = new TimePickerDialog(getContext(), listener,
                    hour, minute, DateFormat.is24HourFormat(getContext()));
            dialog.show();
            return true;
        }

        private String[] getCustomTimeSetting() {
            String value = Settings.Secure.getStringForUser(getActivity().getContentResolver(),
                    Settings.Secure.DOZE_ALWAYS_ON_AUTO_TIME, UserHandle.USER_CURRENT);
            if (value == null || value.equals("")) value = "20:00,07:00";
            return value.split(",", 0);
        }

        private void updateTimeEnablement(int mode) {
            mSincePref.setVisible(mode == MODE_TIME || mode == MODE_MIXED_SUNRISE);
            mTillPref.setVisible(mode == MODE_TIME || mode == MODE_MIXED_SUNSET);
        }

        private void updateTimeSummary(int mode) {
            updateTimeSummary(getCustomTimeSetting(), mode);
        }

        private void updateTimeSummary(String[] times, int mode) {
            if (mode == MODE_DISABLED) {
                mSincePref.setSummary("-");
                mTillPref.setSummary("-");
                return;
            }

            if (mode == MODE_NIGHT) {
                mSincePref.setSummary(R.string.always_on_display_schedule_sunset);
                mTillPref.setSummary(R.string.always_on_display_schedule_sunrise);
                return;
            }

            String outputFormat = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm a";
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime sinceDT = LocalTime.parse(times[0], formatter);
            LocalTime tillDT = LocalTime.parse(times[1], formatter);

            if (mode == MODE_MIXED_SUNSET) {
                mSincePref.setSummary(R.string.always_on_display_schedule_sunset);
                mTillPref.setSummary(tillDT.format(outputFormatter));
            } else if (mode == MODE_MIXED_SUNRISE) {
                mTillPref.setSummary(R.string.always_on_display_schedule_sunrise);
                mSincePref.setSummary(sinceDT.format(outputFormatter));
            } else {
                mSincePref.setSummary(sinceDT.format(outputFormatter));
                mTillPref.setSummary(tillDT.format(outputFormatter));
            }
        }

        private void updateTimeSetting(boolean since, int hour, int minute) {
            String[] times = getCustomTimeSetting();
            String nHour = "";
            String nMinute = "";
            if (hour < 10) nHour += "0";
            if (minute < 10) nMinute += "0";
            nHour += String.valueOf(hour);
            nMinute += String.valueOf(minute);
            times[since ? 0 : 1] = nHour + ":" + nMinute;
            Settings.Secure.putStringForUser(getActivity().getContentResolver(),
                    Settings.Secure.DOZE_ALWAYS_ON_AUTO_TIME,
                    times[0] + "," + times[1], UserHandle.USER_CURRENT);
            updateTimeSummary(times, Integer.parseInt(mModePref.getValue()));
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }
}
