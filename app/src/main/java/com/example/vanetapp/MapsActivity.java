package com.example.vanetapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;

import static com.example.vanetapp.Constants.ERROR_DIALOG_REQUEST;
import static com.example.vanetapp.Constants.MAPVIEW_BUNDLE_KEY;
import static com.example.vanetapp.Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION;
import static com.example.vanetapp.Constants.PERMISSIONS_REQUEST_ENABLE_GPS;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static TextView speedView;

    private boolean mLocationPermissionGranted = false;
    private static final String TAG = "MapsActivity";
    private static final int LOCATION_UPDATE_INTERVAL = 3000;
    private Button profile_button;
    private Button traffic_button;
    double distanceToUser;
    boolean isDangerAlertDisplayed;
    boolean isRunnableRunning;
    boolean addedMapMarkers;
    boolean traffic;

    private FirebaseFirestore firebaseFirestore;
    private MapView mMapView;
    private GoogleMap mMap;
    private LatLngBounds mMapBoundary;

    private FusedLocationProviderClient mFusedLocationClient;
    private UserLocation mUserLocation;
    private UserLocation mUserPosition;

    private ListenerRegistration mUserListEventListener;
    private ListenerRegistration mUserLocationEventListener;
    private ClusterManager<ClusterMarker> mClusterManager;
    private MyClusterManagerRenderer mClusterManagerRenderer;
    private ArrayList<ClusterMarker> mClusterMarkers = new ArrayList<>();
    private ArrayList<User> mUserList = new ArrayList<>();
    private ArrayList<UserLocation> mUserLocations = new ArrayList<>();

    private Handler mHandler = new Handler();
    private Runnable mRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        speedView = findViewById(R.id.speedView);
        mMapView = (MapView) findViewById(R.id.map);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firebaseFirestore = FirebaseFirestore.getInstance();
        initGoogleMap(savedInstanceState);

        isDangerAlertDisplayed = false;
        isRunnableRunning = false;
        addedMapMarkers = false;
        traffic = false;

        profile_button  = findViewById(R.id.profileBtn);
        profile_button.setOnClickListener(view -> openProfileActivity());

        traffic_button  = findViewById(R.id.trafficBtn);
        traffic_button.setOnClickListener(view -> {
            if(traffic){
                mMap.setTrafficEnabled(false);
                Log.d(TAG, "onCreate: traffic is off");
                traffic = false;
            }else{
                mMap.setTrafficEnabled(true);
                Log.d(TAG, "onCreate: traffic is on");
                traffic = true;
            }
        });



    }

    private void initGoogleMap(Bundle savedInstanceState){
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);
    }

    private void getUserDetails(){
        if(addedMapMarkers) {
            addedMapMarkers = false;
        }
        if(mUserLocation == null){
            mUserLocation = new UserLocation();

            DocumentReference userRef = firebaseFirestore
                    .collection("users")
                    .document(FirebaseAuth.getInstance().getUid());

            userRef.get().addOnCompleteListener(task -> {
                if(task.isSuccessful()){
                    Log.d(TAG, "onComplete: successfully get the user details.");
                    User user = task.getResult().toObject(User.class);
                    mUserLocation.setUser(user);

                    //setting the User singleton
                    ((UserClient)getApplicationContext()).setUser(user);

                    getLastKnownLocation();
                }
            });
        }
        else{
            getLastKnownLocation();
        }
    }


    //finding out what was the the last known location of the user
    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation: called.");
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Location location = task.getResult();
                GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                Log.d(TAG, "onComplete: latitude " + geoPoint.getLatitude());
                Log.d(TAG, "onComplete: longitude " + geoPoint.getLongitude());

                mUserLocation.setGeo_point(geoPoint);
                mUserLocation.setTimestamp(null);
                mUserLocation.setSpeed(0);

                saveUserLocation();

            }
        });

    }

    //setting the new information into the database
    private void saveUserLocation(){
        if(mUserLocation != null){
            DocumentReference locationRef = firebaseFirestore
                    .collection("User locations")
                    .document(FirebaseAuth.getInstance().getUid());
            locationRef.set(mUserLocation).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        Log.d(TAG, "saveUserLocation: \ninserted user location into database." +
                                "\n latitude: " + mUserLocation.getGeo_point().getLatitude() +
                                "\n longitude: " + mUserLocation.getGeo_point().getLongitude() +
                                "\n speed: " + mUserLocation.getSpeed());

                        getUsers();
                    }
                }
            });
        }
    }


    //getting all the users from the database
    private void getUsers() {
        CollectionReference usersRef = firebaseFirestore
                .collection("users");

        mUserListEventListener = usersRef
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot queryDocumentSnapshots,
                                        @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.e(TAG, "onEvent: Listen failed.", e);
                            return;
                        }

                        if (queryDocumentSnapshots != null) {

                            // Clear the list and add all the users again
                            mUserList.clear();
                            mUserList = new ArrayList<>();

                            //adding then users to a list
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                User user = doc.toObject(User.class);
                                mUserList.add(user);
                                //for each user we'll also get the location from the database
                                getUserLocation(user);
                            }

                            Log.d(TAG, "onEvent: user list size: " + mUserList.size());
                        }
                    }
                });
    }

    private void getUserLocation(User user){
        DocumentReference locationsRef = firebaseFirestore
                .collection("User locations")
                .document(user.getUser_id());

        locationsRef.get().addOnCompleteListener(task -> {

            if(task.isSuccessful()){
                if(task.getResult().toObject(UserLocation.class) != null){
                    //making a list containing all the user locations
                    mUserLocations.add(task.getResult().toObject(UserLocation.class));
                    Log.d(TAG, "onComplete: Added user location to the list: "
                            + mUserLocation.getUser().getUsername());
                    setUserPosition();
                }
            }
        });


    }
    //verifying if the current user's location is the authenticated user' location
    private void setUserPosition() {
        for (UserLocation userLocation: mUserLocations){
            if(userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())){
                mUserPosition = userLocation;

            }
        }

        //if the current user's location has been successfully found we can add the markers
        if(mUserPosition != null){
            addMapMarkers();
        }

    }

    //adding map markers
    private void addMapMarkers(){

        if(mMap != null){

            if(mClusterManager == null){
                mClusterManager = new ClusterManager<ClusterMarker>(this, mMap);
            }
            if(mClusterManagerRenderer == null){
                mClusterManagerRenderer = new MyClusterManagerRenderer(
                        this,
                        mMap,
                        mClusterManager
                );
                mClusterManager.setRenderer(mClusterManagerRenderer);
            }

            //for each user location we'll make customize marker on the map
            for(UserLocation userLocation: mUserLocations){

                Log.d(TAG, "addMapMarkers: location: " + userLocation.getGeo_point().toString());
                try{
                    String snippet = "";
                    if(userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())){
                        snippet = "This is you";
                    }
                    else{
                        snippet = "This is:  " + userLocation.getUser().getUsername();
                    }

                    int avatar = R.drawable.default_avatar; // set the default avatar
                    try{
                        avatar = Integer.parseInt(userLocation.getUser().getAvatar());
                    }catch (NumberFormatException e){
                        Log.d(TAG, "addMapMarkers: no avatar for " +
                                userLocation.getUser().getUsername() + ", setting default.");
                    }
                    ClusterMarker newClusterMarker = new ClusterMarker(
                            new LatLng(userLocation.getGeo_point().getLatitude(),
                                    userLocation.getGeo_point().getLongitude()),
                                    userLocation.getUser().getUsername(),
                                    snippet,
                                    avatar,
                                    userLocation.getUser()
                    );
                    //adding the marker to the manager
                    mClusterManager.addItem(newClusterMarker);
                    //and also adding them to a list because it makes it easier to be retrieved
                    mClusterMarkers.add(newClusterMarker);

                }catch (NullPointerException e){
                    Log.e(TAG, "addMapMarkers: NullPointerException: " + e.getMessage() );
                }

            }
            mClusterManager.cluster();
            addedMapMarkers = true;
            //focusing the camera view on the current user location
            setCameraView();

        }
    }

    //Overall map view window : 0.04 * 0.04 = 0.0016
    private void setCameraView(){

        double bottomBoundary = mUserPosition.getGeo_point().getLatitude() - .04;
        double topBoundary = mUserPosition.getGeo_point().getLatitude() + .04;
        double leftBoundary = mUserPosition.getGeo_point().getLongitude() - .04;
        double rightBoundary = mUserPosition.getGeo_point().getLongitude()  + .04;

        mMapBoundary = new LatLngBounds(
                new LatLng(bottomBoundary, leftBoundary),
                new LatLng(topBoundary, rightBoundary)
        );

        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMapBoundary, 0));
        //starting the location service
        startLocationService();
        // starting the location updates
        if(!isRunnableRunning){
            startUserLocationsRunnable();
        }else{
            Log.d(TAG, "UserLocationRunnable is already running.");
        }

    }

    private void startUserLocationsRunnable(){
        isRunnableRunning = true;
        Log.d(TAG, "startUserLocationsRunnable: starting runnable for retrieving updated locations.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                //constantly getting the geo point information to update the markers location
                retrieveUserLocations();
                //constantly getting  the user locations from the database to verify their speed,
                //and the distance between them
                if(isLocationServiceRunning()){
                    verifyDistance();
                }


                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates(){
        mHandler.removeCallbacks(mRunnable);
        isRunnableRunning = false;
    }

    private void retrieveUserLocations(){
        Log.d(TAG, "retrieveUserLocations: retrieving location of all users");

        try{
            for(final ClusterMarker clusterMarker: mClusterMarkers){

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection("User locations")
                        .document(clusterMarker.getUser().getUser_id());

                userLocationRef.get().addOnCompleteListener(task -> {
                    if(task.isSuccessful()){

                        final UserLocation updatedUserLocation = task.getResult().toObject(UserLocation.class);

                        // update the marker location on the map
                        for (int i = 0; i < mClusterMarkers.size(); i++) {
                            try {
                                if (mClusterMarkers.get(i).getUser().getUser_id().
                                        equals(updatedUserLocation.getUser().getUser_id())) {

                                    LatLng updatedLatLng = new LatLng(
                                            updatedUserLocation.getGeo_point().getLatitude(),
                                            updatedUserLocation.getGeo_point().getLongitude()
                                    );

                                    mClusterMarkers.get(i).setPosition(updatedLatLng);
                                    mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));

                                }


                            } catch (NullPointerException e) {
                                Log.e(TAG, "retrieveUserLocations: NullPointerException: " + e.getMessage());
                            }

                        }

                    }

                });
            }
        }catch (IllegalStateException e){
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query." +
                    " Ending query." + e.getMessage() );
        }

    }

    private void startLocationService(){
        if(!isLocationServiceRunning()){
            Intent serviceIntent = new Intent(this, LocationService.class);
//        this.startService(serviceIntent);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){

                MapsActivity.this.startForegroundService(serviceIntent);
            }else{
                startService(serviceIntent);
            }
        }
    }

    private void stopLocationService(){
        if(isLocationServiceRunning()){
            Intent serviceIntent = new Intent(this, LocationService.class);
            stopService(serviceIntent);
            Log.d(TAG, "stopLocationService: Tried to stop location service");
        }
    }

    private boolean isLocationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if("com.example.vanetapp.LocationService".equals(service.service.getClassName())) {
                Log.d(TAG, "isLocationServiceRunning: location service is already running.");
                return true;
            }
        }
        Log.d(TAG, "isLocationServiceRunning: location service is not running.");
        return false;
    }

    //beginning of the permission checks
    private boolean checkMapServices() {
        if (isServicesOK()) {
            return isMapsEnabled();
        }
        return false;
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This application requires GPS to work properly, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> {
                    Intent enableGpsIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(enableGpsIntent, PERMISSIONS_REQUEST_ENABLE_GPS);
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public boolean isMapsEnabled() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
            return false;
        }
        return true;
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
            //do what you intend with the app if the permission is granted
            getUserDetails();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    public boolean isServicesOK() {
        Log.d(TAG, "isServicesOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MapsActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            //everything is fine and the user can make map requests
            Log.d(TAG, "isServicesOK: Google Play Services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            //an error occurred but we can resolve it
            Log.d(TAG, "isServicesOK: an error occurred but we can fix it");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MapsActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
        } else {
            Toast.makeText(this, "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: called.");
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ENABLE_GPS: {
                if (mLocationPermissionGranted) {
                    //do what you intend with the app if the permission is granted

                    getUserDetails();
                } else {
                    getLocationPermission();
                }
            }
        }

    }
    //end of the permission check

    //message to be showed when a dangerous situation might occur
    private void buildAlertMessageDanger1( UserLocation userLocation) {
        //boolean so that only one pop-up appears at a time
        isDangerAlertDisplayed = true;

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Attention!!!\n\n " + userLocation.getUser().getUsername() +
                 " is approaching with " + userLocation.getSpeed() + " km/h")
                .setCancelable(false)
                .setNeutralButton("OK", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    isDangerAlertDisplayed = false;
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void buildAlertMessageDanger2( UserLocation userLocation) {
        //boolean so that only one pop-up appears at a time
        isDangerAlertDisplayed = true;

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Attention!!!\n\n " + userLocation.getUser().getUsername() +
                " is nearby and your speed is over 80 km/h")
                .setCancelable(false)
                .setNeutralButton("OK", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    isDangerAlertDisplayed = false;
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }
    private void verifyDistance() {

        //getting the user location from the database before every verification for accurate readings
        CollectionReference locationsRef = firebaseFirestore
                .collection("User locations");
        mUserLocationEventListener = locationsRef
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable @org.jetbrains.annotations
                            .Nullable QuerySnapshot queryDocumentSnapshots
                            , @Nullable @org.jetbrains.annotations
                            .Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.e(TAG, "onEvent: Listen failed.", error);
                            return;
                        }
                        if (queryDocumentSnapshots != null) {

                            // Clear the list and add all the user locations again
                            mUserLocations.clear();
                            mUserLocations = new ArrayList<>();

                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                if(doc != null){
                                    UserLocation userLocation = doc.toObject(UserLocation.class);
                                    mUserLocations.add(userLocation);

                                    if(userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())){
                                        mUserPosition = userLocation;
                                    }
                                }

                            }

                            Log.d(TAG, "onEvent: userLocations size: " + mUserLocations.size());
                        }
                    }
                });

        for (UserLocation userLocation: mUserLocations){
            if(userLocation.getUser().getUser_id() != mUserPosition.getUser().getUser_id()){
                distanceToUser = distance(userLocation.getGeo_point().getLatitude(),
                        userLocation.getGeo_point().getLongitude(),
                        mUserPosition.getGeo_point().getLatitude(),
                        mUserPosition.getGeo_point().getLongitude());
                /*Log.d(TAG, "distance between " + mUserPosition.getUser().getUsername()
                        + " and " + userLocation.getUser().getUsername() +
                        " is :" + distanceToUser);
                Log.d(TAG, "My speed is: " + mUserPosition.getSpeed() +
                        " and his speed is :" + userLocation.getSpeed());*/

                if(userLocation.getSpeed() >= 80  && distanceToUser <= 0.1){
                    if(isDangerAlertDisplayed != true){
                        buildAlertMessageDanger1(userLocation);
                        Log.d(TAG, "Danger1!!");
                    }
                }else if(mUserPosition.getSpeed() >= 80  && distanceToUser <= 0.1){
                    if(isDangerAlertDisplayed != true){
                        buildAlertMessageDanger2(userLocation);
                        Log.d(TAG, "Danger2!!");
                    }
                }

            }
        }
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
       /* if(!traffic){
            mMap.setTrafficEnabled(true);
        }*/


    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
        if (checkMapServices()) {
            if (mLocationPermissionGranted) {
                //do what you intend with the app if the permission is granted

                getUserDetails();

            } else {
                getLocationPermission();
            }
        }
    }

    private void openProfileActivity() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
    }


    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();

    }


    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();

        stopLocationService();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();

        stopLocationService();

        if(mUserListEventListener != null){
            mUserListEventListener.remove();
        }
        if(mUserLocationEventListener != null){
            mUserLocationEventListener.remove();
        }
        stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

}