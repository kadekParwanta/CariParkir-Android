package com.dwipa.cariparkir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dwipa.cariparkir.Geofence.GeofenceErrorMessages;
import com.dwipa.cariparkir.Geofence.GeofenceTransitionsIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.strongloop.android.loopback.Model;
import com.strongloop.android.loopback.ModelRepository;
import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.remoting.adapters.Adapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

/**
 * Created by Kadek_P on 6/22/2016.
 */
@RuntimePermissions
public class MapDemoActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status>,
        LocationListener {

    private SupportMapFragment mapFragment;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 60000;  /* 60 secs */
    private long FASTEST_INTERVAL = 5000; /* 5 secs */

    Polyline polyline;
    private float KMtoMile = 0.000621371f;
    private float defaultMaxRadius = 500; //in m
    LinearLayout bookItContainer;
    Button bookItBtn;
    ArrayList<Parking> parkingList = new ArrayList<>();
    ArrayList<Marker> markerList = new ArrayList<>();
    Marker selectedMarker;
    TextView availableSlot, totalSlot;
    private Parking selectedParking, bookedParking;
    private Boolean hasBookedASlot = false;
    LatLng myPosition = new LatLng(-8.6757927, 115.2137193);
    Location prevLocation;
    /**
     * Used to keep track of whether geofences were added.
     */
    private boolean mGeofencesAdded;

    protected ArrayList<Geofence> mGeofenceList;
    /**
     * Used when requesting to add or remove geofences.
     */
    private PendingIntent mGeofencePendingIntent;

    /**
     * Used to persist application state about whether geofences were added.
     */
    private SharedPreferences mSharedPreferences;

    /*
     * Define a request code to send to Google Play services This code is
     * returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        bookItContainer = (LinearLayout) findViewById(R.id.bookContainer);
        bookItBtn = (Button) findViewById(R.id.bookItBtn);
        bookItBtn.setOnClickListener(onBookItClickListener);

        availableSlot = (TextView) findViewById(R.id.availableSlot);
        totalSlot = (TextView) findViewById(R.id.totalSlot);

        mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap map) {
                    loadMap(map);
                }
            });
        } else {
            Toast.makeText(this, "Error - Map Fragment was null!!", Toast.LENGTH_SHORT).show();
        }

        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<Geofence>();

        // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null.
        mGeofencePendingIntent = null;

        // Retrieve an instance of the SharedPreferences object.
        mSharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME,
                MODE_PRIVATE);

        // Get the value of mGeofencesAdded from SharedPreferences. Set to false as a default.
        mGeofencesAdded = mSharedPreferences.getBoolean(Constants.GEOFENCES_ADDED_KEY, false);

        Boolean isExited = mSharedPreferences.getBoolean(Constants.PARKING_SLOT_EXIT, false);
        if (isExited) {
            removeGeofencesButtonHandler();
            hasBookedASlot = false;
        }
    }

    protected void loadMap(GoogleMap googleMap) {
        mMap = googleMap;
        if (mMap != null) {
            // Map is ready
            Toast.makeText(this, "Map Fragment was loaded properly!", Toast.LENGTH_SHORT).show();
            MapDemoActivityPermissionsDispatcher.getMyLocationWithCheck(this);
            mMap.setOnMapClickListener(mapClickListener);
        } else {
            Toast.makeText(this, "Error - Map was null!!", Toast.LENGTH_SHORT).show();
        }
    }

    private GoogleMap.OnMapClickListener mapClickListener = new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            if (bookItContainer.getVisibility() == View.VISIBLE) bookItContainer.setVisibility(View.GONE);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MapDemoActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @SuppressWarnings("all")
    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void getMyLocation() {
        if (mMap != null) {
            // Now that mMap has loaded, let's get our location!
            mMap.setMyLocationEnabled(true);
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).build();
            connectClient();
        }
    }

    protected void connectClient() {
        // Connect the client.
        if (isGooglePlayServicesAvailable() && mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    /*
     * Called when the Activity becomes visible.
    */
    @Override
    protected void onStart() {
        super.onStart();
        connectClient();
    }

    /*
	 * Called when the Activity is no longer visible.
	 */
    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    /*
     * Handle results returned to the FragmentActivity by Google Play services
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {

            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			/*
			 * If the result code is Activity.RESULT_OK, try to connect again
			 */
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mGoogleApiClient.connect();
                        break;
                }

        }
    }

    private boolean isGooglePlayServicesAvailable() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates", "Google Play services is available.");
            return true;
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(errorDialog);
                errorFragment.show(getSupportFragmentManager(), "Location Updates");
            }

            return false;
        }
    }

    /*
     * Called by Location Services when the request to connect the client
     * finishes successfully. At this point, you can request the current
     * location or start periodic updates
     */
    @Override
    public void onConnected(Bundle dataBundle) {
        // Display the connection status
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location != null) {
            prevLocation = location;
            Toast.makeText(this, "GPS location was found!", Toast.LENGTH_SHORT).show();
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            mMap.animateCamera(cameraUpdate);
            myPosition = new LatLng(location.getLatitude(),location.getLongitude());
            sendRequest();
        } else {
            Toast.makeText(this, "Current location was null, enable GPS on emulator!", Toast.LENGTH_SHORT).show();
        }
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    public void onLocationChanged(Location location) {
        // Report to the UI that the location was updated
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        Boolean isExited = mSharedPreferences.getBoolean(Constants.PARKING_SLOT_EXIT, false);
        if (isExited && hasBookedASlot) {
            removeGeofencesButtonHandler();
            hasBookedASlot = false;
            mSharedPreferences.edit().putBoolean(Constants.PARKING_SLOT_EXIT, false);
            mSharedPreferences.edit().commit();
        }

        if (location.distanceTo(prevLocation) > 10 && hasBookedASlot) {
            showResult("update route");
            prevLocation = location;
            route(new LatLng(location.getLatitude(), location.getLongitude()),
                    selectedMarker.getPosition(), GMapV2Direction.MODE_DRIVING);
        }

    }

    /*
     * Called by Location Services if the connection to the location client
     * drops because of an error.
     */
    @Override
    public void onConnectionSuspended(int i) {
        if (i == CAUSE_SERVICE_DISCONNECTED) {
            Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        } else if (i == CAUSE_NETWORK_LOST) {
            Toast.makeText(this, "Network lost. Please re-connect.", Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * Called by Location Services if the attempt to Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
		 * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            Toast.makeText(getApplicationContext(),
                    "Sorry. Location services not available to you", Toast.LENGTH_LONG).show();
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {

        // Global field to contain the error dialog
        private Dialog mDialog;

        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    private void sendRequest() {
        final CariParkirApplication app = (CariParkirApplication)getApplication();
        final RestAdapter adapter = app.getLoopBackAdapter();
        final ModelRepository<Model> repository = adapter.createRepository("parking");
        final Map<String,?> parameters = ImmutableMap.of(
                "here[lat]", myPosition.latitude,
                "here[lng]", myPosition.longitude,
                "max", defaultMaxRadius * KMtoMile);

        repository.invokeStaticMethod(
                "nearby",
                parameters,
                new Adapter.JsonArrayCallback() {
                    @Override
                    public void onSuccess(final JSONArray response) {
                        parkingList.clear();
                        displayLocations(response);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        Log.e("MapsActivity", "Cannot list locations.", t);
                        showResult("Failed.");
                    }
                });
    }

    private void showResult(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Display a list of locations in the GUI.
     */
    private void displayLocations(final JSONArray response) {
        // Convert JSON response to MarkerOptions
        mMap.setOnMarkerClickListener(MapDemoActivity.this);

        Toast.makeText(this,"MapReady",Toast.LENGTH_SHORT).show();
        final List<MarkerOptions> locations = convertLocationsToMarkerOptions(response);
        // remove previous markers
        mMap.clear();

        // add markers for locations returned by the server
        for (final MarkerOptions loc: locations) {
            Marker marker = mMap.addMarker(loc);
            markerList.add(marker);
        }

        mMap.addCircle(new CircleOptions()
                .center(myPosition)
                .radius(defaultMaxRadius)
                .strokeColor(Color.GREEN));
    }

    private List<MarkerOptions> convertLocationsToMarkerOptions(
            final JSONArray locations) {

        final List<MarkerOptions> result =
                new ArrayList<MarkerOptions>(locations.length());

        for (int ix = 0; ix < locations.length(); ix++) {
            try {
                final JSONObject loc = locations.getJSONObject(ix);
                result.add(createMarkerOptionsForLocation(loc));
                parkingList.add(createParkingFromLocation(loc));
            } catch (final JSONException e) {
                Log.w("LessonThreeFragment", "Skipping invalid location object.", e);
            }
        }

        return result;
    }

    private MarkerOptions createMarkerOptionsForLocation(final JSONObject location)
            throws JSONException {
        final JSONObject posJson = location.getJSONObject("geo");
        final LatLng geoPos = new LatLng(
                posJson.getDouble("lat"),
                posJson.getDouble("lng"));

        return new MarkerOptions()
                .position(geoPos)
                .title(location.getString("name"))
                .snippet(getDistanceString(geoPos));
    }

    private Parking createParkingFromLocation(final JSONObject location)
            throws JSONException {

        Parking parking = new Parking();
        parking.setId(location.getString("id"));
        parking.setName(location.getString("name"));
        parking.setType(location.getString("type"));
        parking.setAvailable(location.getInt("available"));
        parking.setTotal(location.getInt("total"));

        final JSONObject posJson = location.getJSONObject("geo");
        final LatLng geoPos = new LatLng(
                posJson.getDouble("lat"),
                posJson.getDouble("lng"));
        parking.setGeo(geoPos);

        return parking;
    }

    @SuppressLint("DefaultLocale")
    private String getDistanceString(final LatLng position) {
        final float[] distanceResult = new float[1];
        Location.distanceBetween(
                myPosition.latitude, myPosition.longitude,
                position.latitude, position.longitude,
                distanceResult);
        final float distance = distanceResult[0];

        return distance < 1000
                ? String.format("%dm", Math.round(distance))
                : String.format("%.1fkm", distance/1000);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        bookItContainer.setVisibility(View.VISIBLE);

        selectedMarker = marker;
        selectedParking = findByMarker(marker);

        if (selectedParking != null) {
            availableSlot.setText("Available: " + selectedParking.getAvailable());
            totalSlot.setText("Total: " + selectedParking.getTotal());
        } else {
            availableSlot.setText("Available: N/A" );
            totalSlot.setText("Total: N/A");
        }

        return false;
    }

    private Parking findByMarker(Marker marker) {
        for (Parking parking : parkingList) {
            if (parking.getName().equalsIgnoreCase(marker.getTitle())){
                return parking;
            }
        }
        return null;
    }

    private View.OnClickListener onBookItClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (hasBookedASlot) {
                Toast.makeText(MapDemoActivity.this,"You have already booked a slot: " + bookedParking.getName(),Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MapDemoActivity.this,selectedMarker.getTitle()+" has been Booked",Toast.LENGTH_SHORT).show();
                bookParkingLot(selectedParking, selectedMarker);
            }

            bookItContainer.setVisibility(View.GONE);
        }
    };

    private void bookParkingLot(Parking parking, Marker marker) {
        hasBookedASlot = true;
        bookedParking = parking;


        SharedPreferences.Editor prefsEditor = mSharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(parking);
        prefsEditor.putString(Constants.PARKING_SLOT, json);
        prefsEditor.commit();

        route(myPosition, marker.getPosition(), GMapV2Direction.MODE_DRIVING);
        populateGeofenceList(marker);
        addGeofencesButtonHandler();
    }



    protected void route(LatLng sourcePosition, LatLng destPosition, String mode) {
        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                try {
                    Document doc = (Document) msg.obj;
                    GMapV2Direction md = new GMapV2Direction();
                    ArrayList<LatLng> directionPoint = md.getDirection(doc);
                    PolylineOptions rectLine = new PolylineOptions().width(3).color(Color.RED);

                    for (int i = 0; i < directionPoint.size(); i++) {
                        rectLine.add(directionPoint.get(i));
                    }
                    if (polyline!= null) polyline.remove();
                    polyline = mMap.addPolyline(rectLine);
                    md.getDurationText(doc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ;
        };

        new GMapV2DirectionAsyncTask(handler, sourcePosition, destPosition, mode).execute();
    }


    /**
     * Builds and returns a GeofencingRequest. Specifies the list of geofences to be monitored.
     * Also specifies how the geofence notifications are initially triggered.
     */
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);

        // Add the geofences to be monitored by geofencing service.
        builder.addGeofences(mGeofenceList);

        // Return a GeofencingRequest.
        return builder.build();
    }

    /**
     * Adds geofences, which sets alerts to be notified when the device enters or exits one of the
     * specified geofences. Handles the success or failure results returned by addGeofences().
     */
    public void addGeofencesButtonHandler() {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    // The GeofenceRequest object.
                    getGeofencingRequest(),
                    // A pending intent that that is reused when calling removeGeofences(). This
                    // pending intent is used to generate an intent when a matched geofence
                    // transition is observed.
                    getGeofencePendingIntent()
            ).setResultCallback(MapDemoActivity.this); // Result processed in onResult().
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            logSecurityException(securityException);
        }
    }

    /**
     * Removes geofences, which stops further notifications when the device enters or exits
     * previously registered geofences.
     */
    public void removeGeofencesButtonHandler() {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Remove geofences.
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    // This is the same pending intent that was used in addGeofences().
                    getGeofencePendingIntent()
            ).setResultCallback(this); // Result processed in onResult().
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            logSecurityException(securityException);
        }
    }

    private void logSecurityException(SecurityException securityException) {
        Log.e("MapDemoActivity", "Invalid location permission. " +
                "You need to use ACCESS_FINE_LOCATION with geofences", securityException);
    }

    /**
     * Runs when the result of calling addGeofences() and removeGeofences() becomes available.
     * Either method can complete successfully or with an error.
     *
     * Since this activity implements the {@link ResultCallback} interface, we are required to
     * define this method.
     *
     * @param status The Status returned through a PendingIntent when addGeofences() or
     *               removeGeofences() get called.
     */


    public void onResult(Status status) {
        if (status.isSuccess()) {
            // Update state and save in shared preferences.
            mGeofencesAdded = !mGeofencesAdded;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putBoolean(Constants.GEOFENCES_ADDED_KEY, mGeofencesAdded);
            editor.apply();

            Toast.makeText(
                    this,
                    getString(mGeofencesAdded ? R.string.geofences_added :
                            R.string.geofences_removed),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            // Get the status code for the error and log it using a user-friendly message.
            String errorMessage = GeofenceErrorMessages.getErrorString(this,
                    status.getStatusCode());
            Log.e("MapDemoActivity", errorMessage);
        }
    }

    /**
     * Gets a PendingIntent to send with the request to add or remove Geofences. Location Services
     * issues the Intent inside this PendingIntent whenever a geofence transition occurs for the
     * current list of geofences.
     *
     * @return A PendingIntent for the IntentService that handles geofence transitions.
     */
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void populateGeofenceList(Marker marker) {
        mGeofenceList.add(new Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.

                .setRequestId(marker.getTitle())

                        // Set the circular region of this geofence.
                .setCircularRegion(
                        marker.getPosition().latitude,
                        marker.getPosition().longitude,
                        Constants.GEOFENCE_RADIUS_IN_METERS
                )

                        // Set the expiration duration of the geofence. This geofence gets automatically
                        // removed after this period of time.
                .setExpirationDuration(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)

                        // Set the transition types of interest. Alerts are only generated for these
                        // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)

                        // Create the geofence.
                .build());
    }

}
