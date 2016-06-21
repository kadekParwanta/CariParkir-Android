package com.dwipa.cariparkir;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.common.collect.ImmutableMap;
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

import fr.quentinklein.slt.LocationTracker;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private GoogleMap mMap;
    LatLng myPosition = new LatLng(-8.6757927, 115.2137193);
    JSONArray parkingResponse;
    SupportMapFragment mapFragment;
    Polyline polyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // You need to ask the user to enable the permissions
            Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
        } else {
            LocationTracker tracker = new LocationTracker(this) {
                @Override
                public void onLocationFound(Location location) {
                    // Do some stuff
                    myPosition = new LatLng(location.getLatitude(),location.getLongitude());
                    sendRequest();
                }

                @Override
                public void onTimeout() {

                }
            };
            tracker.startListening();
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
        mMap.setOnMarkerClickListener(MapsActivity.this);

        Toast.makeText(this,"MapReady",Toast.LENGTH_SHORT).show();
        final List<MarkerOptions> locations = convertLocationsToMarkerOptions(parkingResponse);
        // remove previous markers
        mMap.clear();

        // add markers for locations returned by the server
        for (final MarkerOptions loc: locations) {
            mMap.addMarker(loc);
        }

        CameraUpdate currentLocation = CameraUpdateFactory.newLatLngZoom(myPosition, 15);
        mMap.addMarker(new MarkerOptions().position(myPosition).icon(BitmapDescriptorFactory.fromResource(R.drawable.here)).title("You are here"));
        mMap.animateCamera(currentLocation);

        mMap.addCircle(new CircleOptions()
                .center(myPosition)
                .radius(5000)
                .strokeColor(Color.GREEN));
    }

    private void sendRequest() {
        // 1. Grab the shared RestAdapter instance.
        final CariParkirApplication app = (CariParkirApplication)getApplication();
        final RestAdapter adapter = app.getLoopBackAdapter();

        // 2. Instantiate our ModelRepository. Rather than create a subclass
        // this time, we'll use the base classes to show off their off-the-shelf
        // super-powers.
        final ModelRepository<Model> repository = adapter.createRepository("parking");

        // 3. The meat of the lesson: custom behaviour. Here, we're invoking
        // a custom, static method on the Location model type called "nearby".
        // As you might expect, it does a geo-based query ordered by the closeness
        // to the provided latitude and longitude. Rather than go through
        // LocationClient, we've plugged in the coordinates of our favorite noodle
        // shop here in San Mateo, California.
        //
        // Once we've successfully loaded the models, we pass them to our
        // `displayLocations` method to be converted to MarkerOptions and
        // added to the map as clickable pins!
        final Map<String,?> parameters = ImmutableMap.of(
                "here[lat]", myPosition.latitude,
                "here[lng]", myPosition.longitude,
                "max",3.10686);

        repository.invokeStaticMethod(
                "nearby",
                parameters,
                new Adapter.JsonArrayCallback() {
                    @Override
                    public void onSuccess(final JSONArray response) {
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
        parkingResponse = response;
        mapFragment.getMapAsync(this);
    }

    private List<MarkerOptions> convertLocationsToMarkerOptions(
            final JSONArray locations) {

        final List<MarkerOptions> result =
                new ArrayList<MarkerOptions>(locations.length());

        for (int ix = 0; ix < locations.length(); ix++) {
            try {
                final JSONObject loc = locations.getJSONObject(ix);
                result.add(createMarkerOptionsForLocation(loc));
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
        route(myPosition, marker.getPosition(), "");
        return false;
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

        new GMapV2DirectionAsyncTask(handler, sourcePosition, destPosition, GMapV2Direction.MODE_DRIVING).execute();
    }
}
