package com.example.floe.klangsalat;

import android.Manifest;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.io.PdAudio;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MapsActivity extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        OnMapReadyCallback,
        LocationListener,
        LocationSource,
        SensorEventListener {

    private GoogleMap mMap;
    private MarkerOptions mUserLocationMarker;
    private Marker mMarker;
    private GoogleApiClient mGoogleApiClient;
    public static final String TAG = MapsActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private LocationRequest mLocationRequest;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    float[] mGravity;
    float[] mGeomagnetic;
    private Location mLastLocation;
    private Location mCurrentLocation;
    private OnLocationChangedListener mListener;
    private float mLookingAt;

    private List<String[]> poiList;
    private List<Poi> poisToSend;

    private PdUiDispatcher dispatcher;

    private final static int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1234;
    private final static float RADIUS = 1000f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(AppIndex.API).build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        InputStream inputStream = getResources().openRawResource(R.raw.pois);
        CSVReader csv = new CSVReader(inputStream);
        poiList = csv.read();

        try {
            initPD();
            loadPDPatch();
        } catch (IOException e) {
            finish();
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

            return;
        }
        mCurrentLocation = mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setMyLocationEnabled(true);
        mMap.setLocationSource(this);

        // move camera to location
        double currentLatitude = mLastLocation.getLatitude();
        double currentLongitude = mLastLocation.getLongitude();
        LatLng latLng = new LatLng(currentLatitude, currentLongitude);

        mUserLocationMarker = new MarkerOptions()
                .position(latLng)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_arrow)
        );

        //mMarker = mMap.addMarker(mUserLocationMarker);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        if (mLastLocation != null)
            handleNewLocation(mLastLocation);
        else
            Log.i(TAG, "onConnected: Couldn't get location.");
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: Permission granted!");
                } else {
                    Log.i(TAG, "onRequestPermissionsResult: Permission denied!");
                }
            }
        }
    }

    private void handleNewLocation(Location location) {
        List<Poi> poisToSort = new ArrayList<>();

        // check the distance of every poi relative to the users current location
        for(int i = 0; i < poiList.size(); i++) {
            String[] currentPoiString = poiList.get(i);

            Location poiLocation = new Location("poiLocation");
            poiLocation.setLatitude(Double.parseDouble(currentPoiString[1]));
            poiLocation.setLongitude(Double.parseDouble(currentPoiString[2]));

            float distance = location.distanceTo(poiLocation);
            float bearing = getBearing(location, poiLocation);

            Poi currentPoi = new Poi(i, distance, bearing, poiLocation, currentPoiString[0], currentPoiString[3]);

            // check if distance is in range
            if(distance <= RADIUS) {
                poisToSort.add(currentPoi);
            }

            LatLng poiLatLng = new LatLng(poiLocation.getLatitude(), poiLocation.getLongitude());

            mMap.addMarker(new MarkerOptions()
                    .position(poiLatLng)
                    .title(currentPoi.getName())
                    .snippet(currentPoi.getDescription())
            );
        }

        Collections.sort(poisToSort);
        int size = poisToSort.size();
        int sub = size >= 4 ? 4 : size;

        poisToSend = poisToSend != null ? checkPoisListOrder(poisToSort.subList(0, sub)) : poisToSort.subList(0, sub);

        // mMap.clear(); // delete all markers
        // mMarker = mMap.addMarker(mUserLocationMarker); // add user location marker

        // send the 4 pois to pd
        for (int i = 0; i < poisToSend.size(); i++) {
            Poi currentPoi = poisToSend.get(i);

            if(currentPoi != null) {
                PdBase.sendFloat("distance" + i, currentPoi.getDistance());
                PdBase.sendFloat("angle" + i, currentPoi.getAngle());
                PdBase.sendFloat("id" + i, currentPoi.getId());

                Log.d(TAG, "poisToSort: " + currentPoi.getId() + ", distance: " + currentPoi.getDistance() + ", angle: " + currentPoi.getAngle());
            }
        }
    }

    private List<Poi> checkPoisListOrder(List<Poi> poisToSort) {
        List<Poi> ordered = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ordered.add(null);
        }

        for (int i = 0; i < poisToSort.size(); i++) {
            Poi currentPoi = poisToSort.get(i);
            for (int j = 0; j < poisToSend.size(); j++) {
                Poi currentPoiToSend = poisToSend.get(j);
                if (currentPoiToSend != null) {
                    if(currentPoi.getId() == currentPoiToSend.getId()) {
                        ordered.set(j, currentPoi);
                    }
                }
            }
        }

        return ordered;
    }

    private float getBearing(Location userLocation, Location poiLocation) {
        float diff = mLookingAt - userLocation.bearingTo(poiLocation);

        if(diff < 0f) {
            diff += 360f;
        }

        return diff;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        PdAudio.startAudio(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        mSensorManager.unregisterListener(this);
        PdAudio.stopAudio();
    }

    @Override
    public void onLocationChanged(Location location) {
        if( mListener != null ) {
            mListener.onLocationChanged(location);
            LatLngBounds bounds = this.mMap.getProjection().getVisibleRegion().latLngBounds;
            if(!bounds.contains(new LatLng(location.getLatitude(), location.getLongitude())))
            {
                //Move the camera to the user's location if they are off-screen!
                mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
            }
        }
        handleNewLocation(location);
        mCurrentLocation = location;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);

            if (success) {
                float orientation[] = new float[3];

                SensorManager.getOrientation(R, orientation);
                float rad = orientation[0]; // orientation contains: azimut, pitch and roll
                Double azimut = Math.toDegrees(rad);
                mLookingAt = azimut.floatValue();

                if(mMarker != null)
                    mMarker.setRotation(Math.round(mLookingAt));

                if(poisToSend != null) {
                    for(int i = 0; i < poisToSend.size(); i++) {
                        Poi currentPoi = poisToSend.get(i);

                        if(currentPoi != null) {
                            float angle360 = getBearing(mCurrentLocation, currentPoi.getLocation());
                            if(angle360 <= 360f) {
                                currentPoi.setAngle(angle360);
                                PdBase.sendFloat("angle" + i, angle360);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initPD() throws IOException {
        int sampleRate = AudioParameters.suggestSampleRate();
        PdAudio.initAudio(sampleRate, 0, 2, 8, true);

        dispatcher = new PdUiDispatcher();
        PdBase.setReceiver(dispatcher);
    }

    private void loadPDPatch() throws IOException {
        File dir = getFilesDir();
        Log.i(TAG, "loadPDPatch: " + dir);
        IoUtils.extractZipResource(getResources().openRawResource(R.raw.klangsalat_sounds), dir, true);
        File pdPatch = new File(dir, "klangsalat_ready4app_realsounds.pd");
        PdBase.openPatch(pdPatch.getAbsolutePath());
    }

    @Override
    public void activate(OnLocationChangedListener listener)
    {
        mListener = listener;
    }

    @Override
    public void deactivate()
    {
        mListener = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Maps Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.example.floe.klangsalat/http/host/path")
        );
        AppIndex.AppIndexApi.start(mGoogleApiClient, viewAction);
        PdAudio.startAudio(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Maps Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.example.floe.klangsalat/http/host/path")
        );
        AppIndex.AppIndexApi.end(mGoogleApiClient, viewAction);
        mGoogleApiClient.disconnect();
        PdAudio.stopAudio();
    }
}
