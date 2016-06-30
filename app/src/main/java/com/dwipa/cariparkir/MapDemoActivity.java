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
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.dwipa.cariparkir.Geofence.GeofenceErrorMessages;
import com.dwipa.cariparkir.Geofence.GeofenceTransitionsIntentService;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
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
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strongloop.android.loopback.Model;
import com.strongloop.android.loopback.ModelRepository;
import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.VoidCallback;
import com.strongloop.android.remoting.adapters.Adapter;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
        GoogleMap.OnMapLongClickListener,
        AddParkingFragment.AddParkingListener,
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
    TextSwitcher availableSlot;
    TextView totalSlot;
    private Parking selectedParking, bookedParking;
    private Boolean hasBookedASlot = false;
    LatLng myPosition = new LatLng(-8.6757927, 115.2137193);
    Location prevLocation;
    Integer maxRetry = 5;
    LatLng newParkingPos;
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
    private Circle geofenceCircle;
    private int tryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        bookItContainer = (LinearLayout) findViewById(R.id.bookContainer);
        bookItBtn = (Button) findViewById(R.id.bookItBtn);
        bookItBtn.setOnClickListener(onBookItClickListener);

        availableSlot = (TextSwitcher) findViewById(R.id.availableSlot);
        totalSlot = (TextView) findViewById(R.id.totalSlot);

        // Set the ViewFactory of the TextSwitcher that will create TextView object when asked
        availableSlot.setFactory(new ViewSwitcher.ViewFactory() {

            public View makeView() {
                // TODO Auto-generated method stub
                // create new textView and set the properties like clolr, size etc
                TextView myText = new TextView(MapDemoActivity.this);
                myText.setTextColor(Color.parseColor("#ff669900"));
                myText.setTypeface(null, Typeface.BOLD);
                return myText;
            }
        });

        // Declare the in and out animations and initialize them
        Animation in = AnimationUtils.loadAnimation(this,android.R.anim.slide_in_left);
        Animation out = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right);

        // set the animation type of textSwitcher
        availableSlot.setInAnimation(in);
        availableSlot.setOutAnimation(out);

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

        //Socket io
        mSocket.on("/Parking/PUT", onParkingUpdate);
        mSocket.on("/Parking/POST", onParkingCreated);

        mSocket.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("/Parking/PUT", onParkingUpdate);
        mSocket.off("/Parking/POST", onParkingCreated);
    }

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("https://cariparkir.herokuapp.com");
        } catch (URISyntaxException e) {}
    }

    private Emitter.Listener onParkingUpdate = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        Parking parking = createParkingFromLocation(data);
                        Parking parkingOld = findParking(parking);
                        if (parkingOld != null) {
                            if (selectedParking != null && parking.getId().equals(selectedParking.getId())) updateSelectedParking(parking);
                            int index = getIndex(parkingOld);
                            parkingList.set(index, parking);
                        }
                    } catch (JSONException e) {
                        return;
                    }

                }
            });
        }
    };

    private Emitter.Listener onParkingCreated = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    JSONObject data = (JSONObject) args[0];
                    try {
                        Parking parking = createParkingFromLocation(data);
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MapDemoActivity.this);
                        String radius = settings.getString("radius_list", "500");
                        int radiusInt = Integer.parseInt(radius);
                        int currentRadius = (Integer) Math.round(getDistance(parking.getGeo()));
                        if (currentRadius < radiusInt) {
                            Parking parkingOld = findParking(parking);
                            if (parkingOld == null) {
                                createMarker(parking.getName(), parking.getGeo());
                            }
                        }
                    } catch (JSONException e) {
                        return;
                    }

                }
            });
        }

        private float getDistance(LatLng position) {
            final float[] distanceResult = new float[1];
            Location.distanceBetween(
                    myPosition.latitude, myPosition.longitude,
                    position.latitude, position.longitude,
                    distanceResult);
            return distanceResult[0];
        }
    };

    private void updateSelectedParking(Parking parking) {
        selectedParking = parking;
        if (bookItContainer.getVisibility() == View.VISIBLE) {
            availableSlot.setText("Available: " + selectedParking.getAvailable());
            totalSlot.setText("Total: " + selectedParking.getTotal());
            if (selectedParking.getAvailable() == 0) {
                bookItBtn.setOnClickListener(null);
                bookItBtn.setText("Full!");
                bookItBtn.setBackgroundColor(Color.RED);
            } else {
                bookItBtn.setOnClickListener(onBookItClickListener);
                bookItBtn.setText("Book it");
                bookItBtn.setBackgroundColor(Color.parseColor("#ff669900"));
            }
        }
        bounceTheMarker(selectedMarker);
    }

    protected void loadMap(GoogleMap googleMap) {
        mMap = googleMap;
        if (mMap != null) {
            // Map is ready
            Toast.makeText(this, "Map Fragment was loaded properly!", Toast.LENGTH_SHORT).show();
            MapDemoActivityPermissionsDispatcher.getMyLocationWithCheck(this);
            mMap.setOnMapClickListener(mapClickListener);
            mMap.setOnMapLongClickListener(this);
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
        if (location == null) return;
        myPosition = new LatLng(location.getLatitude(),location.getLongitude());

        Boolean isExited = mSharedPreferences.getBoolean(Constants.PARKING_SLOT_EXIT, false);
        Boolean isEntered = mSharedPreferences.getBoolean(Constants.PARKING_SLOT_ENTER, false);

        if (isExited && hasBookedASlot) {
            hasBookedASlot = false;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putBoolean(Constants.PARKING_SLOT_EXIT, false);
            editor.putBoolean(Constants.PARKING_SLOT_ENTER, false);
            editor.commit();
            removeGeofencesButtonHandler();
            if (polyline!= null) polyline.remove();
        }

        if (prevLocation == null) prevLocation = location;

        if (location.distanceTo(prevLocation) > 10 && hasBookedASlot && !isEntered) {
            prevLocation = location;
            new GetDirectionTask(new LatLng(location.getLatitude(), location.getLongitude()),
                    selectedMarker.getPosition(), GMapV2Direction.MODE_DRIVING).execute();
        }

        if (hasBookedASlot) updateCamera(location);
    }

    private void updateCamera(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
        mMap.animateCamera(cameraUpdate);
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

    public void showAddParkingDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new AddParkingFragment();
        dialog.show(getSupportFragmentManager(), "AddParkingFragment");
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        EditText name = (EditText) dialog.getDialog().findViewById(R.id.parkingname);
        EditText total = (EditText) dialog.getDialog().findViewById(R.id.parkingtotal);

        new AddParkingTask(name.getText().toString(), total.getText().toString()).execute();
    }

    private class AddParkingTask extends AsyncTask<Void, Void, JsonObject> {

        private String name, total;

        public AddParkingTask(String name, String total) {
            this.name = name;
            this.total = total;
        }

        @Override
        protected JsonObject doInBackground(Void... voids) {
            return postNewParking(name, total);
        }

        private JsonObject postNewParking(String name, String total) {
            try {
                URL url = new URL("https://cariparkir.herokuapp.com/api/Parkings");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();

                // Send POST output.
                String bodyRequest = setupBodyRequest(name, total);
                DataOutputStream printout = new DataOutputStream(conn.getOutputStream());
                printout.writeBytes(bodyRequest);
                printout.flush();
                printout.close();

                int HttpResult = conn.getResponseCode();
                if(HttpResult ==HttpURLConnection.HTTP_OK){
                    JsonParser jp = new JsonParser();
                    JsonElement root = jp.parse(new InputStreamReader((InputStream) conn.getContent()));
                    JsonObject rootobj = root.getAsJsonObject();
                    return rootobj;

                }else{
                    Log.e("MapDemo", "error 1" + conn.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("MapDemo", "error 2" + e.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(JsonObject parking) {
            super.onPostExecute(parking);
            createMarker(name,newParkingPos);
        }

        private String setupBodyRequest(String name, String total) {

            JSONObject jsonParam = new JSONObject();

            try {
                jsonParam.put("name", name);
                jsonParam.put("type", "Mall");
                jsonParam.put("rate", "2000");
                jsonParam.put("available", Integer.parseInt(total));
                jsonParam.put("total", Integer.parseInt(total));
                JSONObject geo = new JSONObject();
                geo.put("lat", newParkingPos.latitude);
                geo.put("lng", newParkingPos.longitude);
                jsonParam.put("geo",geo);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return jsonParam.toString();
        }
    }

    private void createMarker(String name, LatLng position) {
        MarkerOptions markerOptions =  new MarkerOptions()
                .position(position)
                .title(name)
                .snippet(getDistanceString(position));

        Marker marker = mMap.addMarker(markerOptions);
        markerList.add(marker);
        bounceTheMarker(marker);
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        newParkingPos = latLng;
        showAddParkingDialog();
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
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String radius = settings.getString("radius_list", "500");
        defaultMaxRadius = Integer.parseInt(radius);
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
                        tryCount = 0;
                    }

                    @Override
                    public void onError(final Throwable t) {
                        Log.e("MapsActivity", "Cannot list locations.", t);
                        showResult("Failed. Retry..");
                        while (tryCount < maxRetry) {
                            sendRequest();
                            tryCount++;
                        }
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
            updateSelectedParking(selectedParking);
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

    private Parking findParking(Parking parking) {
        for (Parking parkingLot : parkingList) {
            if (parking.getId().equalsIgnoreCase(parkingLot.getId())){
                return parking;
            }
        }
        return null;
    }

    public int getIndex(Parking parking)
    {
        for (int i = 0; i < parkingList.size(); i++)
        {
            Parking item = parkingList.get(i);
            if (item.getId().equals(parking.getId())) {
                return i;
            }
        }

        return -1;
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

        new GetDirectionTask(myPosition, marker.getPosition(), GMapV2Direction.MODE_DRIVING).execute();
        populateGeofenceList(marker);
        addGeofencesButtonHandler();
    }

    public class GetDirectionTask extends AsyncTask<Void, Void,Document> {
        private LatLng start, end;
        private String mode;

        public GetDirectionTask(LatLng start, LatLng end, String mode) {
            this.start = start;
            this.end = end;
            this.mode = mode;
        }

        @Override
        protected Document doInBackground(Void... voids) {
            String stringUrl = "http://maps.googleapis.com/maps/api/directions/xml?"
                    + "origin=" + start.latitude + "," + start.longitude
                    + "&destination=" + end.latitude + "," + end.longitude
                    + "&sensor=false&units=metric&mode=" + mode;
            Log.d("url", stringUrl);
            try {
                URL url = new URL(stringUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.connect();

                InputStream in = (InputStream) conn.getContent();
                DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder();
                Document doc = builder.parse(in);
                return doc;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Document document) {
            super.onPostExecute(document);
            try {
                GMapV2Direction md = new GMapV2Direction();
                ArrayList<LatLng> directionPoint = md.getDirection(document);
                PolylineOptions rectLine = new PolylineOptions().width(10).color(Color.RED);

                for (int i = 0; i < directionPoint.size(); i++) {
                    rectLine.add(directionPoint.get(i));
                }
                if (polyline!= null) polyline.remove();
                polyline = mMap.addPolyline(rectLine);
                md.getDurationText(document);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

            if (mGeofencesAdded) {
                geofenceCircle = mMap.addCircle(new CircleOptions()
                        .center(selectedMarker.getPosition())
                        .radius(Constants.GEOFENCE_RADIUS_IN_METERS)
                        .strokeColor(Color.BLUE));
            } else {
                if (geofenceCircle != null) geofenceCircle.remove();
            }
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

    private void bounceTheMarker(final Marker selectedMarker) {
        final Handler handler = new Handler();

        final long startTime = SystemClock.uptimeMillis();
        final long duration = 2000;

        Projection proj = mMap.getProjection();
        final LatLng markerLatLng = selectedMarker.getPosition();
        Point startPoint = proj.toScreenLocation(markerLatLng);
        startPoint.offset(0, -100);
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);

        final Interpolator interpolator = new BounceInterpolator();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - startTime;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lng = t * markerLatLng.longitude + (1 - t) * startLatLng.longitude;
                double lat = t * markerLatLng.latitude + (1 - t) * startLatLng.latitude;
                selectedMarker.setPosition(new LatLng(lat, lng));

                if (t < 1.0) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.setting, menu);

        // Calling super after populating the menu is necessary here to ensure that the
        // action bar helpers have a chance to handle this event.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // User chose the "Settings" item, show the app settings UI...
                startActivity(new Intent(MapDemoActivity.this,SettingsActivity.class));
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
}
