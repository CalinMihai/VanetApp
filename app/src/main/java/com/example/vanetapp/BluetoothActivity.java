package com.example.vanetapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.GeoPoint;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

import static com.example.vanetapp.Constants.ERROR_DIALOG_REQUEST;
import static com.example.vanetapp.Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION;
import static com.example.vanetapp.Constants.PERMISSIONS_REQUEST_ENABLE_GPS;

/*both devices need to be paired
* both devices need to start the connection in order to send data*/
public class BluetoothActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{

    private static final String TAG = "BluetoothActivity";
    TextView bluetoothView;
    TextView discoverableView;
    TextView inputTextView;
    TextView speedBtTextView;
    EditText messageEditText;
    TextView autoTextView;
    StringBuilder messages;
    double speed;

    boolean isRunnableRunning;
    boolean isBluetoothOn;
    boolean isDeviceDiscoverable;
    boolean isDeviceConnected;
    boolean foundAnotherDevice;
    boolean isDevicePaired;
    boolean pairingAttempt;
    boolean connectionAttempt;
    boolean nowSend;
    boolean autoConnect;

    boolean isBroadcastReceiverRegistered1;
    boolean isBroadcastReceiverRegistered2;
    boolean isBroadcastReceiverRegistered3;
    boolean isBroadcastReceiverRegistered4;


    private boolean mLocationPermissionGranted = false;
    private FusedLocationProviderClient mFusedLocationClient;

    Button onOffBtn;
    Button discoverable_btn;
    Button discoverDevices_btn;
    Button send_btn;
    Button startConnection_btn;
    Button autoConnect_btn;

    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final int LOCATION_UPDATE_INTERVAL = 5000;

    private Handler mHandler = new Handler();
    private Runnable mRunnable;

    BluetoothDevice bluetoothDevice;
    BluetoothDevice btDevice;
    BluetoothConnectionService bluetoothConnectionService;
    BluetoothAdapter mBluetoothAdapter;
    public DeviceListAdapter mDeviceListAdapter;
    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    ListView newDevicesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        onOffBtn = findViewById(R.id.onOffBtn);
        discoverable_btn = findViewById(R.id.discoverableBtn);
        discoverDevices_btn = findViewById(R.id.discoverBtn);
        send_btn = findViewById(R.id.sendBtn);
        startConnection_btn = findViewById(R.id.startConnectionBtn);
        autoConnect_btn = findViewById(R.id.autoConnectBtn);


        bluetoothView = findViewById(R.id.bluetoothView);
        discoverableView = findViewById(R.id.discoverableView);
        newDevicesListView = findViewById(R.id.newDevicesListView);
        inputTextView = findViewById(R.id.outputTextView);
        speedBtTextView = findViewById(R.id.speedBtTextView);
        messageEditText = findViewById(R.id.editText);
        autoTextView = findViewById(R.id.autoConnectTextView);

        isRunnableRunning = false;
        isBluetoothOn = false;
        isDeviceDiscoverable = false;
        isDeviceConnected = false;
        isDevicePaired = false;
        foundAnotherDevice = false;
        pairingAttempt = false;
        connectionAttempt = false;
        nowSend = false;
        autoConnect = false;

        isBroadcastReceiverRegistered1 = false;
        isBroadcastReceiverRegistered2 = false;
        isBroadcastReceiverRegistered3 = false;
        isBroadcastReceiverRegistered4 = false;

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        messages = new StringBuilder();
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver, new IntentFilter("incomingMessage"));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver2, new IntentFilter("isDevicePaired"));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver3, new IntentFilter("isDeviceConnected"));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver4, new IntentFilter("nowSend"));
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mReceiver5, new IntentFilter("connectionAttempt"));

        //Broadcasts when bond state changes (ie:pairing)
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver4, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        newDevicesListView.setOnItemClickListener(BluetoothActivity.this);


        verifyBluetoothState();
        autoTextView.setText("Auto is OFF");

        onOffBtn.setOnClickListener(view -> {
            Log.d(TAG, "onClick: enabling/disabling bluetooth.");
            enableDisableBT();
        });

        discoverable_btn.setOnClickListener(view -> {
            Log.d(TAG, "onClick: enabling/disabling discoverability.");
            enableDisableDiscoverability();
        });


        discoverDevices_btn.setOnClickListener(view -> {
            Log.d(TAG, "onClick: Looking for new devices.");
            discoverDevices();
        });

        startConnection_btn.setOnClickListener(view -> {
            Log.d(TAG, "onClick: Starting the connection ");
            startConnection();
        });

        send_btn.setOnClickListener(view -> {
            Log.d(TAG, "onClick: Sending the message ");
            byte[] bytes = messageEditText.getText().toString().getBytes(Charset.defaultCharset());
            bluetoothConnectionService.write(bytes);
            //setting the editText blank after sending a message
            messageEditText.setText("");
        });

        autoConnect_btn.setOnClickListener(view -> {
            if(autoConnect){
                Log.d(TAG, "onClick: Switching OFF the auto connect ");
                autoConnect = false;
                autoTextView.setText(" Auto is OFF");
            }else{
                Log.d(TAG, "onClick: Switching ON the auto connect ");
                autoConnect = true;
                autoTextView.setText(" Auto is ON");
            }
        });

    }

    //create method for starting connection
//***remember the connection will fail and app will crash if you haven't paired first
    public void startConnection(){
        startBTConnection(bluetoothDevice,MY_UUID_INSECURE);
    }

    /**
     * starting chat service method
     */
    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.");

        bluetoothConnectionService.startClient(device,uuid);
    }


    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("theMessage");
            messages.setLength(0);
            messages.append(text + "\n");

            inputTextView.setText(messages);
        }
    };

    BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bool = intent.getBooleanExtra("isDevicePaired", false);
           isDevicePaired = bool;
        }
    };

    BroadcastReceiver mReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bool = intent.getBooleanExtra("isDeviceConnected", false);
            isDeviceConnected = bool;
        }
    };

    BroadcastReceiver mReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bool = intent.getBooleanExtra("nowSend", false);
            nowSend = bool;
        }
    };

    BroadcastReceiver mReceiver5 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bool = intent.getBooleanExtra("connectionAttempt", false);
            connectionAttempt = bool;
        }
    };

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            isBroadcastReceiverRegistered1 = true;
            String action = intent.getAction();
            // When discovery finds a device
            if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra
                        (BluetoothAdapter.EXTRA_STATE, mBluetoothAdapter.ERROR);

                switch(state){
                    case BluetoothAdapter.STATE_OFF:
                        isBluetoothOn = false;
                        Log.d(TAG, "onReceive: STATE OFF");
                        bluetoothView.setText("BT is OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        isBluetoothOn = true;
                        Log.d(TAG, "mBroadcastReceiver1: STATE ON");
                        bluetoothView.setText("BT is ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON");
                        break;
                }
            }
        }
    };

    /**
     * Broadcast Receiver for changes made to bluetooth states such as:
     * 1) Discoverability mode on/off or expire.
     */
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isBroadcastReceiverRegistered2 = true;
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {

                int mode = intent.getIntExtra
                        (BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    //Device is in Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        discoverableView.setText("Visible");
                        isDeviceDiscoverable = true;
                        break;
                    //Device not in discoverable mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled." +
                                " Able to receive connections.");
                        discoverableView.setText("Connectable");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled." +
                                " Not able to receive connections.");
                        discoverableView.setText("Invisible");
                        isDeviceDiscoverable = false;
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting....");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        discoverableView.setText("Connected");
                        break;
                }

            }
        }
    };

    /**
     * Broadcast Receiver for listing devices that are not yet paired
     * -Executed by discoverDevices() method.
     */
    private BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isBroadcastReceiverRegistered3 = true;
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            if (action.equals(BluetoothDevice.ACTION_FOUND)){
                foundAnotherDevice = true;
                Log.d(TAG, "onReceive: found another device");

                BluetoothDevice device = intent.getParcelableExtra (BluetoothDevice.EXTRA_DEVICE);
                mBTDevices.add(device);

                btDevice = device;// for runnable

                Log.d(TAG, "onReceive: " + device.getName() + ": " + device.getAddress());
                mDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices);
                newDevicesListView.setAdapter(mDeviceListAdapter);
            }
        }
    };

    /**
     * Broadcast Receiver that detects bond state changes (Pairing status changes)
     */
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isBroadcastReceiverRegistered4 = true;
            final String action = intent.getAction();

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //3 cases:
                //case1: bonded already
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                    Toast.makeText(BluetoothActivity.this, "Connected ",
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.");
                    discoverableView.setText("Connected");

                    pairingAttempt = true;
                    bluetoothDevice = mDevice;
                }
                //case2: creating a bone
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                    Toast.makeText(BluetoothActivity.this, "Pairing ",
                            Toast.LENGTH_LONG).show();
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.");
                    pairingAttempt = true;
                }
                //case3: breaking a bond
                if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                    Toast.makeText(BluetoothActivity.this, "Pairing failed ",
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.");
                }
            }
        }
    };

    public void enableDisableBT(){
        if(mBluetoothAdapter == null){
            Log.d(TAG, "enableDisableBT: Does not have BT capabilities.");
        }
        if(!mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: enabling BT.");
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBTIntent);

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }
        if(mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: disabling BT.");
            Toast.makeText(this, "Turning Bluetooth OFF", Toast.LENGTH_SHORT).show();
            mBluetoothAdapter.disable();

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }

    }

    public void verifyBluetoothState(){
        if(mBluetoothAdapter == null){
            bluetoothView.setText("Does not have BT capabilities.");
            discoverableView.setText("Does not have BT capabilities.");
            isBluetoothOn = false;
        }
        if(!mBluetoothAdapter.isEnabled()){
            bluetoothView.setText("BT is OFF");
            discoverableView.setText("invisible");
            isBluetoothOn = false;
        }
        if(mBluetoothAdapter.isEnabled()){
            bluetoothView.setText("BT is ON");
            discoverableView.setText("Connectable");
            isBluetoothOn = true;
        }
    }

    public void enableDisableDiscoverability() {
        Log.d(TAG, "enableDisableDiscoverability: Making device discoverable for 300 seconds.");

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        IntentFilter intentFilter = new IntentFilter(mBluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver2,intentFilter);

    }

    private void discoverDevices() {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "btnDiscover: Canceling discovery.");

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
        if(!mBluetoothAdapter.isDiscovering()){

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter.cancelDiscovery();

        Log.d(TAG, "onItemClick: You Clicked on a device.");
        String deviceName = mBTDevices.get(i).getName();
        String deviceAddress = mBTDevices.get(i).getAddress();

        Log.d(TAG, "onItemClick: deviceName = " + deviceName);
        Log.d(TAG, "onItemClick: deviceAddress = " + deviceAddress);

        //create the bond.
        //NOTE: Requires API 17+? I think this is JellyBean
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2){
            Toast.makeText(this, "Trying to pair with " + deviceName,
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Trying to pair with " + deviceName);
            mBTDevices.get(i).createBond();

            bluetoothDevice = mBTDevices.get(i);
            bluetoothConnectionService = new BluetoothConnectionService(BluetoothActivity.this);
        }
    }

    public void pairing(){
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "btnDiscover: Canceling discovery.");

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
        if(!mBluetoothAdapter.isDiscovering()){

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }

    public void connecting() {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter.cancelDiscovery();

        String deviceName = btDevice.getName();
        String deviceAddress = btDevice.getAddress();


        Log.d(TAG, "runnable: deviceName = " + deviceName);
        Log.d(TAG, "runnable: deviceAddress = " + deviceAddress);

        //create the bond.
        //NOTE: Requires API 17+? I think this is JellyBean
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Toast.makeText(this, "Trying to pair with " + deviceName,
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Trying to pair with " + deviceName);
            btDevice.createBond();

            bluetoothDevice = btDevice;
            bluetoothConnectionService = new BluetoothConnectionService(BluetoothActivity.this);
        }

    }

    public void sendingTheSpeed(){
        String speedString = String.valueOf(speed);
        byte[] bytes = speedString.getBytes(Charset.defaultCharset());
        bluetoothConnectionService.write(bytes);
    }

    //finding out what was the the last known location of the user
    private void getLastKnownLocation() {
        //Log.d(TAG, "getLastKnownLocation: called.");
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Location location = task.getResult();

                speed = (int) (Math.round(3.6 * (location.getSpeed())));
                speedBtTextView.setText("My speed: " + speed + " km/h");

            }
        });
    }

    private void startUserLocationsRunnable(){
        isRunnableRunning = true;
        Log.d(TAG, "startAutoConnectingRunnable: starting runnable for automatic BT connections.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {

                //getting the location and speed
                getLastKnownLocation();

                if(isDeviceConnected) {
                    if(nowSend) {
                        sendingTheSpeed();
                    }else{
                        nowSend = true;
                    }

                }else {
                    if (autoConnect) {
                        if (isBluetoothOn) {
                            if (isDeviceDiscoverable) {
                                if (foundAnotherDevice) {
                                    if (isDevicePaired) {
                                        if (!connectionAttempt) {
                                            startConnection();
                                        }
                                    } else {
                                        connecting();
                                    }
                                } else {
                                    if (!pairingAttempt) {
                                        pairing();
                                    }

                                }
                            } else {
                                enableDisableDiscoverability();
                            }
                        }
                    }
                }
                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates(){
        mHandler.removeCallbacks(mRunnable);
        isRunnableRunning = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (checkGps()) {
            if (mLocationPermissionGranted) {
                //do what you intend with the app if the permission is granted

                verifyBluetoothState();
                getLastKnownLocation();

                if(!isRunnableRunning){
                    startUserLocationsRunnable();
                }

            } else {
                getLocationPermission();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called.");
        super.onDestroy();
        if(isBroadcastReceiverRegistered1){
            unregisterReceiver(mBroadcastReceiver1);
        }

        if(isBroadcastReceiverRegistered2){
            unregisterReceiver(mBroadcastReceiver2);
        }

        if(isBroadcastReceiverRegistered3){
            unregisterReceiver(mBroadcastReceiver3);
        }

        if(isBroadcastReceiverRegistered4){
            unregisterReceiver(mBroadcastReceiver4);
        }

        stopLocationUpdates();
    }



    //bt permission check
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < M(API 23).");
        }
    }

    //beginning of the gps permission checks
    private boolean checkGps() {
        if (isGpsEnabled()) {
            return true;
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

    public boolean isGpsEnabled() {
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
            verifyBluetoothState();
            getLastKnownLocation();
            if(!isRunnableRunning){
                startUserLocationsRunnable();
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
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

                    verifyBluetoothState();
                    getLastKnownLocation();
                    if(!isRunnableRunning){
                        startUserLocationsRunnable();
                    }
                } else {
                    getLocationPermission();
                }
            }
        }

    }
    //end of the permission check
}