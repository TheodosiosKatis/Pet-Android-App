package com.ashencostha.mqtt;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.util.Log;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import androidx.annotation.NonNull;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.location.LocationListener;
import android.location.LocationManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity
        implements  MqttHandler.LatitudeUpdateListener,
        MqttHandler.LongitudeUpdateListener,
        MqttHandler.BatteryUpdateListener,
        MqttHandler.TimestampUpdateListener,
        OnMapReadyCallback,
        ApiRequestTask.ApiResponseListener
{
    private ConnectivityManager.NetworkCallback networkCallback;
    private static final String CONFIG_FILE_NAME = "config.json";

    private String tokenAPI;
    private double trackerLongitude = 0.0;
    private double trackerLatitude = 0.0;
    private int trackerBattery = 0;
    private String trackerTimestamp;

    private static final String[] MAP_TYPES = {"Normal", "Hybrid", "Satellite", "Terrain"};
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Marker currentLocationMarker;
    private MqttHandler mqttHandler;
    private GoogleMap myMap;
    private MapView mapView;
    private LatLng trackerLocation;

    private String userName;
    private String userPass;
    private String userImeiNo1;
    private String mqttUsername;
    private String mqttPassword;

    private String timestampAPI;
    private double latitudeAPI = 0.0;
    private double longitudeAPI = 0.0;
    private int  batteryAPI;
    private static final String TAG = "thisismain";
    private TextView emptyTextView;

    public static final String EXTRA_FROM_MAIN_ACTIVITY = "from_main_activity";

    /*
     * Initialize all necessary parameters
     * This method is being called just one time, in the beginning of the app
     * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the emptyTextView
        emptyTextView = findViewById(R.id.empty_text_view);

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * It loads the necessary token for the API call
         * */
        loadTokenFromConfig();

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Setting up the toolbar with the 'Welcome' textview
         * Along with the 'Logout' Button
         * */
        Toolbar toolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Initialize Logout Button's functionality
         * */
        ImageButton imageButton = findViewById(R.id.logoutButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutDialog();
            }
        });

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Retrieve sensitive data from LoadingActivity
         * Such as MQTT_Username, MQTT_Password etc
         * */
        Intent intent = getIntent();
        if (intent != null) {
            userName = intent.getStringExtra("username");
            userPass = intent.getStringExtra("password");
            userImeiNo1 = intent.getStringExtra("imeiNo1");
            mqttUsername = intent.getStringExtra("mqtt_username");
            mqttPassword = intent.getStringExtra("mqtt_password");

            makeIMEIApiRequest(userImeiNo1);
            emptyTextView.setText(userName);
        }

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Initialize the MapView
         * Object that shows the Google Maps
         * */
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        checkLocationPermission();

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Connect to MQTT Broker
         * Listen for updates in Longitude, Latitude, Battery, Timestamp topics
         * */
        mqttHandler = new MqttHandler();
        mqttHandler.connect(mqttUsername, mqttPassword);
        mqttHandler.setLatitudeUpdateListener(this);
        mqttHandler.setLongitudeUpdateListener(this);
        mqttHandler.setBatteryUpdateListener(this);
        mqttHandler.setTimestampUpdateListener(this);

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Create the spinner
         * Spinner = Dropdown menu for the type of the map
         * Such as Normal, Satellite, Terrain, Hybrid
         * */
        Spinner mapTypeSpinner = findViewById(R.id.mapTypeSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MAP_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapTypeSpinner.setAdapter(adapter);
        mapTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // Get the selected map type
                String selectedMapType = MAP_TYPES[position];

                // Set the map type based on the user's selection
                setMapType(selectedMapType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing here
            }
        });

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        /*
         * Register the network callback
         * Create and register the network callback
         * Handle MQTT connection
         * */
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {

                // Network is available, handle it accordingly
                if (mqttHandler.isConnected()) {
                    mqttHandler.subscribe(userImeiNo1 + "/longitude");
                    mqttHandler.subscribe(userImeiNo1 + "/latitude");
                    mqttHandler.subscribe(userImeiNo1 + "/battery");
                    mqttHandler.subscribe(userImeiNo1 + "/timestamp");
                } else {
                    mqttHandler.reconnect();
                    Log.e(TAG, "Cannot subscribe. Client is not connected.");
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Network is lost, handle it accordingly
                Log.e(TAG, "Network lost. Handling accordingly...");
            }
        };
        registerNetworkCallback();

    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * Loads the necessary token for the API call
     * */
    private void loadTokenFromConfig() {
        try {
            // Read the JSON from config.json
            InputStream inputStream = getAssets().open(CONFIG_FILE_NAME);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String json = new String(buffer, "UTF-8");

            // Parse the JSON to get the token value
            JSONObject jsonObject = new JSONObject(json);
            tokenAPI = jsonObject.getString("tokenAPI");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            // Handle error
        }
    }

    /*
     * This method is responsible for making the API request about device's latest data
     * */
    private void makeIMEIApiRequest(String imei) {
        ApiRequestTask apiRequestTask = new ApiRequestTask(tokenAPI, this);

        String ApiEndpoint = "https://XXX.XXX.XXX.XXX:1312/api/device/";
        String ApiUrl = ApiEndpoint + imei;

        apiRequestTask.executeTask(ApiUrl);
    }

    /*
     * Method for parsing server's response after an API request
     * */
    @Override
    public void onApiResponseReceived(String response) {
        if (response != null) {
            String stringlatitudeAPI = "";
            String stringlongitudeAPI = "";
            String stringbatteryAPI = "";
            try {
                JSONObject jsonObject = new JSONObject(response);
                boolean success = jsonObject.optBoolean("success", false);

                if (success) {
                    JSONObject dataObject = jsonObject.optJSONObject("data");
                    if (dataObject != null) {
                        // Extract values for timestamp, latitude, longitude, and battery
                        stringlatitudeAPI = dataObject.optString("latitude", "");
                        stringlongitudeAPI = dataObject.optString("longitude", "");
                        stringbatteryAPI = dataObject.optString("battery", "");
                        timestampAPI = dataObject.optString("timestamp", "");
                        timestampAPI = formatTimestamp(timestampAPI);

                        latitudeAPI = convertStringToDouble(stringlatitudeAPI);
                        longitudeAPI = convertStringToDouble(stringlongitudeAPI);
                        batteryAPI = convertStringToInt(stringbatteryAPI);

                        if (latitudeAPI != 0 && longitudeAPI != 0) {
                            trackerLatitude = latitudeAPI;
                            trackerLongitude = longitudeAPI;
                            trackerBattery = batteryAPI;
                            trackerTimestamp = timestampAPI;
                        }
                    } else {
                        // Handle case where "data" object is missing or null
                        Log.e(TAG, "No data object found in the response");
                    }
                } else {
                    // Handle case where "success" is false
                    Log.e(TAG, "API request unsuccessful");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                // Handle JSON parsing error
            }
        } else {
            // Handle case where response is null
            Log.e(TAG, "Response is null");
        }
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * Formats the timestamp to a readable version
     * */
    private static String formatTimestamp(String timestamp) {
        DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
        DateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

        try {
            Date date = inputFormat.parse(timestamp);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return ""; // Return empty string if parsing fails
        }
    }

    /*
     * Converts String -> Double
     * */
    public static double convertStringToDouble(String str) {
        double result = 0.0; // Default value if parsing fails
        try {
            result = Double.parseDouble(str);
        } catch (NumberFormatException e) {
            // Handle parsing errors
            System.err.println("Error parsing string to double: " + e.getMessage());
        }
        return result;
    }

    /*
     * Converts String -> Integer
     * */
    public int convertStringToInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * Method to set the map type based on the user's selection
     * */
    private void setMapType(String selectedMapType) {
        if (myMap != null) {
            switch (selectedMapType) {
                case "Normal":
                    myMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    break;
                case "Hybrid":
                    myMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    break;
                case "Satellite":
                    myMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    break;
                case "Terrain":
                    myMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                    break;
                default:
                    Log.e(TAG, "Unknown map type: " + selectedMapType);
                    break;
            }
        } else {
            Log.e(TAG, "myMap is null");
        }
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * It displays to user a logout warning window
     * */
    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do you want to log out?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        navigateToLoginActivity();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss(); // Close the dialog
                    }
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Method in case of user's Logout
     * */
    private void navigateToLoginActivity() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.putExtra(EXTRA_FROM_MAIN_ACTIVITY, true);
        startActivity(intent);
        finish();
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * Check for Location permissions
     * Access location data with both Network or GPS provider
     * */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, setup map
                if (isLocationEnabled()) {
                    setupMap();
                    updateTrackerLocation();
                } else {
                    promptEnableLocation();
                    setupMap();
                    updateTrackerLocation();
                }
            } else {
                // Permission denied, show a message or handle accordingly
                Toast.makeText(this, "Location permission denied. Map functionality may be limited.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*
     * Method for checking if the user has gave the necessary Location Permissions
     * */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted, setup map
            if (isLocationEnabled()) {
                setupMap();
                updateTrackerLocation();
            } else {
                promptEnableLocation();
                setupMap();
                updateTrackerLocation();
            }
        }
    }

    /*
     * Method for checking if the Location in enabled
     * */
    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return isGpsEnabled || isNetworkEnabled;
    }

    /*
     * Method for setting up the Google Map
     * */
    private void setupMap() {
        // Check if the app has the necessary location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, enable the "My Location" layer
            try {
                if (myMap != null) {
                    myMap.setMyLocationEnabled(true);
                    myMap.getUiSettings().setMyLocationButtonEnabled(true); // Optionally enable the My Location button
                    myMap.getUiSettings().setZoomControlsEnabled(true); // Optionally enable the Zoom button
                    myMap.getUiSettings().setMapToolbarEnabled(false);
                }
            } catch (SecurityException e) {
                e.printStackTrace();
                Log.e(TAG, "SecurityException: " + e.getMessage());
            }
        } else {
            // Permission is not granted, request it
            Log.d(TAG, "Location permission not granted, requesting...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    /*
     * Routine method for constructing the Google Map
     * */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        myMap = googleMap;
        setupMap();
        updateTrackerLocation();
    }

    /*
     * It prompts the user to enable their Location
     * */
    private void promptEnableLocation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Location is not enabled. Do you want to enable it?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked Yes, open location settings
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked No, do nothing or handle as needed
                    }
                });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * The below 4 methods is for listening for updates on each topic
     * */
    @Override
    public void onLatitudeUpdate(double latitude) {
        runOnUiThread(() -> {
            trackerLatitude = latitude;
            updateTrackerLocation();
        });
    }

    @Override
    public void onLongitudeUpdate(double longitude) {
        runOnUiThread(() -> {
            trackerLongitude = longitude;
            updateTrackerLocation();
        });
    }

    @Override
    public void onBatteryUpdate(int battery) {
        runOnUiThread(() -> {
            trackerBattery = battery;
            updateTrackerLocation();
        });
    }
    @Override
    public void onTimestampUpdate(String timestamp) {
        runOnUiThread(() -> {
            trackerTimestamp = timestamp;
            updateTrackerLocation();
        });
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * This method is responsible for updating the Dog marker
     * It loads device's latest values from the database on Start
     * It displays the values of the incoming MQTT topics
     * */
    private void updateTrackerLocation() {
        runOnUiThread(() -> {
            // Update the trackerLocation variable
            trackerLocation = new LatLng(trackerLatitude, trackerLongitude);

            // Add a marker on the map at the updated location with a custom marker icon
            if (myMap != null) {
                myMap.clear();

                // Load the custom marker icon from resources and resize it
                BitmapDescriptor customMarker = getResizedBitmapDescriptor(R.drawable.custom_marker, 200, 200);

                // Check for location permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

                    // Retrieve the last known location using Fused Location Provider API
                    FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
                    fusedLocationClient.getLastLocation()
                            .addOnSuccessListener(this, location -> {
                                if (location != null) {
                                    // Got last known location
                                    double userLatitude = location.getLatitude();
                                    double userLongitude = location.getLongitude();

                                    // Calculate distance between user and tracker
                                    Location userLocation = new Location("user");
                                    userLocation.setLatitude(userLatitude);
                                    userLocation.setLongitude(userLongitude);

                                    Location trackerLocation = new Location("tracker");
                                    trackerLocation.setLatitude(trackerLatitude);
                                    trackerLocation.setLongitude(trackerLongitude);

                                    float distance = userLocation.distanceTo(trackerLocation);
                                    int roundedDistance = Math.round(distance);

                                    // Convert Location objects to LatLng
                                    LatLng userLatLng = new LatLng(userLatitude, userLongitude);
                                    LatLng trackerLatLng = new LatLng(trackerLatitude, trackerLongitude);

                                    // Set marker title with HTML line break for multiline text
                                    String batteryText = "Tracker's Battery: " + trackerBattery;
                                    String distanceText = "Distance: " + roundedDistance + " meters";
                                    String timeText = "Timestamp: " + trackerTimestamp;

                                    // Get icons from resources
                                    Drawable batteryIcon;
                                    Drawable distanceIcon = ContextCompat.getDrawable(this, R.drawable.distance_icon);
                                    Drawable timeIcon = ContextCompat.getDrawable(this, R.drawable.time_icon);
                                    if (trackerBattery <= 100 && trackerBattery > 70)
                                        batteryIcon = ContextCompat.getDrawable(this, R.drawable.ic_battery_status_5_5);
                                    else if (trackerBattery <= 70 && trackerBattery > 50)
                                        batteryIcon = ContextCompat.getDrawable(this, R.drawable.ic_battery_status_4_5);
                                    else if (trackerBattery <= 50 && trackerBattery > 30)
                                        batteryIcon = ContextCompat.getDrawable(this, R.drawable.ic_battery_status_3_5);
                                    else if (trackerBattery <= 30 && trackerBattery > 10)
                                        batteryIcon = ContextCompat.getDrawable(this, R.drawable.ic_battery_status_2_5);
                                    else
                                        batteryIcon = ContextCompat.getDrawable(this, R.drawable.ic_battery_status_1_5);

                                    // Set icon size (adjust as needed)
                                    int iconSize = 40; // in pixels

                                    // Resize the icons
                                    batteryIcon.setBounds(0, 0, iconSize, iconSize);
                                    distanceIcon.setBounds(0, 0, iconSize, iconSize);
                                    timeIcon.setBounds(0, 0, iconSize, iconSize);

                                    // Create ImageSpan with icons
                                    ImageSpan batteryImageSpan = new ImageSpan(batteryIcon, ImageSpan.ALIGN_BOTTOM);
                                    ImageSpan distanceImageSpan = new ImageSpan(distanceIcon, ImageSpan.ALIGN_BOTTOM);
                                    ImageSpan timeImageSpan = new ImageSpan(timeIcon, ImageSpan.ALIGN_BOTTOM);

                                    // Create SpannableString with icons
                                    SpannableString spannableBattery = new SpannableString("  " + batteryText);
                                    spannableBattery.setSpan(batteryImageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                                    SpannableString spannableDistance = new SpannableString("  " + distanceText);
                                    spannableDistance.setSpan(distanceImageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                                    SpannableString spannableTime = new SpannableString("  " + timeText);
                                    spannableTime.setSpan(timeImageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                                    // Combine the text with line breaks
                                    String markerTitle = TextUtils.concat(spannableBattery, "\n", spannableDistance, "\n", spannableTime).toString();

                                    // Add marker with custom icon
                                    Marker marker = myMap.addMarker(new MarkerOptions()
                                            .position(trackerLatLng)
                                            .title(markerTitle)
                                            .icon(customMarker));

                                    myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(trackerLatLng, 15.0f));

                                    // Set the custom InfoWindow
                                    myMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                                        @Override
                                        public View getInfoWindow(Marker marker) {
                                            return null;  // Use the default InfoWindow layout
                                        }

                                        @Override
                                        public View getInfoContents(Marker marker) {
                                            View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);
                                            TextView titleTextView = view.findViewById(R.id.titleTextView);
                                            TextView distanceTextView = view.findViewById(R.id.distanceTextView);
                                            TextView timeTextView = view.findViewById(R.id.timeTextView);

                                            titleTextView.setText(spannableBattery);
                                            distanceTextView.setText(spannableDistance);
                                            timeTextView.setText(spannableTime);

                                            return view;
                                        }
                                    });

                                    // Show the InfoWindow when the marker is clicked
                                    myMap.setOnMarkerClickListener(clickedMarker -> {
                                        clickedMarker.showInfoWindow();
                                        return true;
                                    });
                                }
                            });

                } else {
                    // Request location permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
                }
            }
        });
    }

    private BitmapDescriptor getResizedBitmapDescriptor(int resourceId, int width, int height) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */
    /*
     * Register a Network Callback
     * Listen for changes in Network (connection, lost connection etc)
     * */
    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            }
        }
    }
    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *  */

    /*
     * MainActivity Lifecycle methods
     * */
    @Override
    // Called when the activity becomes visible to the user but is not yet in the foreground
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "On Start");
    }
    // Called when the activity starts interacting with the user
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        Log.d(TAG, "On Resume");
    }

    // Called when the system is about to start resuming another activity
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        Log.d(TAG, "On Pause");
    }

    // Called when the activity is being destroyed
    @Override
    protected void onDestroy() {
        // MUST release pointers/listeners/resources
        super.onDestroy();

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        mqttHandler.disconnect();
        mapView.onDestroy();
        Log.d(TAG, "On Destroy");
    }

    // Called when the activity is in low memory
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
        Log.d(TAG, "On Low Memory");
    }
}
