/*
 * Copyright 2014 Pierre Chabardes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vtnetzwelt.androidrtc.receivers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.vtnetzwelt.androidrtc.ExitActivity;
import com.vtnetzwelt.androidrtc.RtcActivity;

import java.util.Date;

import static com.vtnetzwelt.androidrtc.RtcActivity.EMERGENCY_CALLER;
import static com.vtnetzwelt.androidrtc.RtcActivity.IS_EMERGENCY_CALLER_ADDED;
import static com.vtnetzwelt.androidrtc.RtcActivity.START_CAMERA_BROADCAST;


public class CallReceiver extends PhonecallReceiver {


    @Override
    protected void onIncomingCallStarted(Context ctx, String number, Date start) {
    }

    @Override
    protected void onOutgoingCallStarted(Context context, String number, Date start) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = sharedPreferences.getBoolean(IS_EMERGENCY_CALLER_ADDED, false);
        String s = sharedPreferences.getString(EMERGENCY_CALLER, null);

        if (sharedPreferences.getBoolean(IS_EMERGENCY_CALLER_ADDED, false)
                && sharedPreferences.getString(EMERGENCY_CALLER, null) != null
                && number.contains(sharedPreferences.getString(EMERGENCY_CALLER, null))) {
            //setResultData(null);
            RtcActivity.emergency_call_active = true;
            sharedPreferences.edit().putBoolean(START_CAMERA_BROADCAST, true).apply();
            Intent intentHome = new Intent(context, RtcActivity.class);
            intentHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intentHome.putExtra("startCameraBroadcast", true);
            intentHome.setAction(Intent.ACTION_MAIN);
            intentHome.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(intentHome);
        }
    }

    @Override
    protected void onIncomingCallEnded(Context ctx, String number, Date start, Date end) {
    }

    @Override
    protected void onOutgoingCallEnded(Context context, String number, Date start, Date end) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean(START_CAMERA_BROADCAST, false).apply();
        try {
            ExitActivity.exitApplicationAndRemoveFromRecent(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onMissedCall(Context ctx, String number, Date start) {
    }

}