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

package com.vtnetzwelt.androidrtc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Location;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.vtnetzwelt.webrtcclient.PeerConnectionParameters;
import com.vtnetzwelt.webrtcclient.WebRtcClient;

import org.json.JSONException;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.LinkedList;
import java.util.List;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    public static final String IS_EMERGENCY_CALLER_ADDED = "emergencyCallerAdded";
    public static final String EMERGENCY_CALLER = "emergencyCaller";
    public static final String START_CAMERA_BROADCAST = "startcamerabroadcast";
    public static final String USERNAME = "username";
    private static final String PREF_RTC = "RtcAndroid";
    private static final String PREF_LOC_LAT = "RtcAndroidLat";
    private static final String PREF_LOC_LNG = "RtcAndroidLng";
    private static RtcActivity instanceRtcActivity;
    public static boolean emergency_call_active=false;
    private RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;
    private String mSocketAddress;
    private String callerId;
    private RelativeLayout layoutCall;
    private RelativeLayout layoutCallerInfo;
    private EditText editTextNumber,editTextUsername;
    private Button buttonSubmit;
    private SharedPreferences sharedPreferences;

    /******/


    private LocationRequest locationRequest;
    //private FusedLocationProviderApi fusedLocationProviderApi;
    private GoogleApiClient mGoogleApiClient;

    private boolean skipResume = false;
    private boolean firstTime = false;
    public static RtcActivity getInstance() {
        if (instanceRtcActivity != null)
            return instanceRtcActivity;
        else
            return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);
        layoutCall = (RelativeLayout) findViewById(R.id.layoutCall);
        layoutCallerInfo = (RelativeLayout) findViewById(R.id.layoutCallerInfo);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        instanceRtcActivity=this;
      /*  if (sharedPreferences.getBoolean(IS_EMERGENCY_CALLER_ADDED, false)) {
            layoutCallerInfo.setVisibility(View.VISIBLE);
            layoutCall.setVisibility(View.GONE);

       } else {*/

            editTextNumber = (EditText) findViewById(R.id.editTextNumber);
            editTextUsername = (EditText) findViewById(R.id.editTextUsername);


            editTextUsername.setText(sharedPreferences.getString(USERNAME, null));
            editTextNumber.setText(sharedPreferences.getString(EMERGENCY_CALLER, null));

            buttonSubmit = (Button) findViewById(R.id.buttonSubmit);
            buttonSubmit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editTextNumber.getText().length() > 0 && isValidMobile(editTextNumber.getText().toString())) {
                        sharedPreferences.edit().putBoolean(IS_EMERGENCY_CALLER_ADDED, true).apply();
                        sharedPreferences.edit().putString(EMERGENCY_CALLER, editTextNumber.getText().toString()).apply();
                        sharedPreferences.edit().putString(USERNAME, editTextUsername.getText().toString()).apply();
                        hideKeyboard(RtcActivity.this);
                        Toast.makeText(RtcActivity.this, "Your information has been saved", Toast.LENGTH_SHORT).show();
                       /* layoutCall.setVisibility(View.VISIBLE);
                        layoutCallerInfo.setVisibility(View.GONE);
                        initCallView();*/
                    } else if(editTextUsername.getText().length() > 0){
                        editTextUsername.setError("Please enter the valid number");
                    }else {
                        editTextNumber.setError("Please enter the valid name");
                    }
                }
            });

        //}
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            getLocationOnPermissionCheck();
        }else {
            getLocationOnPermissionModelCheck();

        }

    }

    private void initCallView() {

        layoutCall = (RelativeLayout) findViewById(R.id.layoutCall);
        layoutCallerInfo = (RelativeLayout) findViewById(R.id.layoutCallerInfo);
        layoutCallerInfo.setVisibility(View.GONE);
        layoutCall.setVisibility(View.VISIBLE);


        mSocketAddress = "http://" + getResources().getString(R.string.host);
        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {

                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

            final Intent intent = getIntent();
            final String action = intent.getAction();

            if (Intent.ACTION_VIEW.equals(action)) {
                final List<String> segments = intent.getData().getPathSegments();
                callerId = segments.get(0);
            }
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        client = new WebRtcClient(this, mSocketAddress, params);
    }

    @Override
    public void onPause() {
        super.onPause();
       /* vsv.onPause();
        if(client != null) {
            client.onPause();
        }*/
    }

    @Override
    public void onResume() {
        super.onResume();
        /* vsv.onResume();
       if(client != null) {
            client.onResume();
        }*/
      /*  */
      if(sharedPreferences.getBoolean(START_CAMERA_BROADCAST,false)){
            initCallView();

      }
    }

    @Override
    public void onDestroy() {
        if (client != null) {
            client.onDestroy();
        }
        vsv=null;
        super.onDestroy();
    }

    @Override
    public void onCallReady(String callId) {
        if (callerId != null) {
            try {
                answer(callerId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            startCam();
           /* try {
                answer("EHqxQHBthsG3lyEIAABF");
            } catch (JSONException e) {
                e.printStackTrace();
            }*/
        }
    }

    public void answer(String callerId) throws JSONException {
        client.sendMessage(callerId, "init", null);
        startCam();
    }

    public void call(String callId) {
        Intent msg = new Intent(Intent.ACTION_SEND);
        msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
        msg.setType("text/plain");
        startActivityForResult(Intent.createChooser(msg, "Call someone :"), VIDEO_CALL_SENT);
    }


    public void startCam() {
        // Camera settings

        client.start(sharedPreferences.getString(RtcActivity.USERNAME, null),
                getFromPreferences(RtcActivity.this, PREF_LOC_LAT),
                getFromPreferences(RtcActivity.this, PREF_LOC_LNG));
        AudioManager audioManager= (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);

    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        LinkedList<AudioTrack> audioTracks= localStream.audioTracks;


        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
        if(emergency_call_active) {
            moveTaskToBack(true);
           // RtcActivity.emergency_call_active=false;
        }
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType, false);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    private boolean isValidMobile(String phone) {
        return android.util.Patterns.PHONE.matcher(phone).matches();
    }

    /**
     * Hide the input keyboard
     *
     * @param context the calling context
     */
    public static void hideKeyboard(Activity context) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        try {
            inputMethodManager.hideSoftInputFromWindow(context.getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



    /**
     * Methos to initilze the Setting API to get the location permission status
     * if status is reuired for permission then show setting permission Alert Dialog otherwise try to get the current location
     */

    private void getLocationOnPermissionCheck() {
        mGoogleApiClient=null;
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(RtcActivity.this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).build();
            mGoogleApiClient.connect();

            locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(30 * 1000);
            locationRequest.setFastestInterval(5 * 1000);
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest);

            //**************************
            builder.setAlwaysShow(true); //this is the key ingredient
            //**************************

            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    final Status status = result.getStatus();
                    final LocationSettingsStates state = result.getLocationSettingsStates();
                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            // All location settings are satisfied. The client can initialize location
                            // requests here.
                            receiveLocationWithUpdate();
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the user
                            // a dialog.
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                status.startResolutionForResult(
                                        RtcActivity.this, 192);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                }
            });
        }

    }

    private void receiveLocationWithUpdate() {
        skipResume = true;
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, new com.google.android.gms.location.LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                    firstTime = false;

                    saveToPreferences(RtcActivity.this, PREF_LOC_LAT, "" + location.getLatitude());
                    saveToPreferences(RtcActivity.this, PREF_LOC_LNG, "" + location.getLongitude());
                    if(client!=null)
                    client.update(sharedPreferences.getString(RtcActivity.USERNAME, null),
                            getFromPreferences(RtcActivity.this, PREF_LOC_LAT),
                            getFromPreferences(RtcActivity.this, PREF_LOC_LNG));

            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /*switch (requestCode) {*/
// Check for the integer request code originally supplied to startResolutionForResult().
            if (requestCode == VIDEO_CALL_SENT) {
                startCam();
            }
            
        
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        receiveLocationWithUpdate();
                        break;
                    case Activity.RESULT_CANCELED:
                        //settingsrequest();//keep asking if imp or do whatever

                        showGPSPermissionAlertAfterDeny(1);

                        break;
                }
                
        /*}*/
    }


    /**
     * Check for location permission if app runs on android M
     */


    private void getLocationOnPermissionModelCheck() {
        int hasFinePermission = ContextCompat.checkSelfPermission(RtcActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCaursePermission = ContextCompat.checkSelfPermission(RtcActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (hasFinePermission != PackageManager.PERMISSION_GRANTED && hasCaursePermission != PackageManager.PERMISSION_GRANTED) {
            skipResume = true;
            ActivityCompat.requestPermissions(RtcActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    100);
            return;
        }else{
            getLocationOnPermissionCheck();
        }

    }


    /**
     * Location reuest permission call back method that shows status of permission
     * @param requestCode reuested code while try to get the permission status
     * @param permissions list of permissions
     * @param grantResults result
     */


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) || permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        skipResume = true;
                        getLocationOnPermissionCheck();
                    } else {
                        skipResume = false;
                        showGPSPermissionAlertAfterDeny(0);

                    }
                }
            }
        }
    }
    
    
    /**
     * Show Permission deny Dialog if user Deny the permission request
     * @param type Action type perform according to the Alert dialog apper either from location permission reuest in Android M
     *             or deny the reuest from Setting API
     */


    private void showGPSPermissionAlertAfterDeny(final int type) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(RtcActivity.this);

        // set dialog message
        alertDialogBuilder
                .setMessage(RtcActivity.this.getResources().getString(R.string.access_location_msg_conf))
                .setCancelable(false)
                .setPositiveButton(RtcActivity.this.getResources().getString(R.string.txt_accept), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                       /* Intent intentLocationService = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intentLocationService.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intentLocationService);
                        dialog.cancel();*/

                            if (type == 0) {
                                getLocationOnPermissionModelCheck();


                            } else {
                                getLocationOnPermissionCheck();
                            }

                    }
                })
                .setNegativeButton(RtcActivity.this.getResources().getString(R.string.txt_decline), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        ActivityCompat.finishAffinity(RtcActivity.this);
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it

        if (!(RtcActivity.this.isFinishing())) {
            alertDialog.show();
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        
    }

     void saveToPreferences(Context context, String prefName, String prefValue) {

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(prefName, prefValue);
        editor.commit();
    }

    public String getFromPreferences(Context context, String prefName) {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getString(prefName, null) != null) {
           Log.e("prefs.get prefName",sharedPreferences.getString(prefName, ""));
            return sharedPreferences.getString(prefName, "");
        } else {
            return "";
        }

    }

    /**
     * Check if location already captured before and saved in preferences
     * @return
     */

    private boolean locationAvailableInPrefrences() {
        if (getFromPreferences(RtcActivity.this, PREF_LOC_LAT) != null && !(getFromPreferences(RtcActivity.this, PREF_LOC_LAT).equalsIgnoreCase(""))) {
            skipResume = true;
            return true;
        }

        return false;
    }
}