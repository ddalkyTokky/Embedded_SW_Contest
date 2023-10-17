package com.example.googlemapmavsdk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class ArrowActivity extends AppCompatActivity implements SensorEventListener {
    private TextView txtResult;
    private double now_long;
    private double now_lat;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;
    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];

    private float azimuthinDegress = 0f;
    private float mCurrentDegress = 0f;

    private double bearing = 0f;

    private ImageView mArrow;

    private Double carLat;
    private Double carLong;
    private long lastUpdate = 0;
    private BluetoothAdapter mBTAdapter;
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier
    private final String TAG = ArrowActivity.class.getSimpleName();
    private Handler mHandler; // Our main handler that will receive callback notifications
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private TextView mReadBuffer;
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private int alpha;
    private int beta;
    private int gamma;
    private int stack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrow);

        connectBluetooth();

        //Intent=============================

        Intent intent = getIntent(); /*데이터 수신*/

        double intentLatLong[] = intent.getExtras().getDoubleArray("carLatLong"); /*배열*/

        //Toast.makeText(getApplication(), "done", Toast.LENGTH_SHORT).show();

        carLat = intentLatLong[0];
        carLong = intentLatLong[1];

        //View=============================

        txtResult = (TextView) findViewById(R.id.txtResult);

        //Sensor===========================

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mArrow = (ImageView) findViewById(R.id.arrowImage);

        //GPS================================

        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ArrowActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        } else {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 1, gpsLocationListener);
            lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 10, 1, gpsLocationListener);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10, 1, gpsLocationListener);
        }
    }

    final LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            now_long = location.getLongitude();
            now_lat = location.getLatitude();

            // 위도, 경도를 라디안 단위로 변환
            double w1 = now_lat * Math.PI / 180;
            double w2 = carLat * Math.PI / 180;
            double r1 = now_long * Math.PI / 180;
            double r2 = carLong * Math.PI / 180;

            double y = Math.sin(r2 - r1) * Math.cos(w2);
            double x = (Math.cos(w1) * Math.sin(w2)) - (Math.sin(w1) * Math.cos(w2) * Math.cos(r2 - r1));
            double seta = Math.atan2(y, x); // 방위각 (라디안)
            bearing = (seta * 180 / Math.PI + 360) % 360; // 방위각 (디그리, 정규화 완료)
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mAccelerometer);
        mSensorManager.unregisterListener(this, mMagnetometer);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor == mAccelerometer) {
            System.arraycopy(sensorEvent.values, 0, mLastAccelerometer, 0, sensorEvent.values.length);
            mLastAccelerometerSet = true;
        } else if (sensorEvent.sensor == mMagnetometer) {
            System.arraycopy(sensorEvent.values, 0, mLastMagnetometer, 0, sensorEvent.values.length);
            mLastMagnetometerSet = true;
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
            azimuthinDegress = (int) (Math.toDegrees(SensorManager.getOrientation(mR, mOrientation)[0]) + 360) % 360;

            if ((System.currentTimeMillis() - lastUpdate) > 500) {
                RotateAnimation ra = new RotateAnimation(mCurrentDegress, (float) bearing - azimuthinDegress, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                ra.setDuration(250);
                ra.setFillAfter(true);
                mArrow.startAnimation(ra);

                txtResult.setText(" " + (int) azimuthinDegress + "° \n " + (int) bearing + "° ");

                lastUpdate = System.currentTimeMillis();

                mCurrentDegress = (float) bearing - azimuthinDegress;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void connectBluetooth() {
        mReadBuffer = (TextView) findViewById(R.id.textview_readbuffer);

        final String name = ((StoreDevice) getApplication()).global_name;
        final String address = ((StoreDevice) getApplication()).global_address;

        if ((name != null) && (address != null)) {
            Toast.makeText(getApplication(), name + " " + address, Toast.LENGTH_SHORT).show();
            mHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == MESSAGE_READ) {
                        String readMessage = null;
                        readMessage = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                        mReadBuffer.setText(readMessage);

                        int num = 0;
                        for(int i = 0; i < readMessage.length(); i++){
                            char temp_item = readMessage.charAt(i);
                            if((temp_item >= '0') && (temp_item <= '9')){
                                num *= 10;
                                num += (temp_item - 48);
                            }
                            else{
                                num = -1;
                                break;
                            }
                        }
                        if(num != -1){
                            switch (stack){
                                case(0):
                                    alpha = num;
                                    stack++;
                                    break;
                                case(1):
                                    beta = num;
                                    stack++;
                                    break;
                                case(2):
                                    gamma = num;
                                    stack = 0;
                                    break;
                            }
                        }
                        Toast.makeText(getApplication(), "alpha: " + alpha + " ", Toast.LENGTH_SHORT).show();
                    }

                    if (msg.what == CONNECTING_STATUS) {
                        char[] sConnected;
                        if (msg.arg1 == 1) {
                            Toast.makeText(getApplication(), getString(R.string.BTConnected) + msg.obj, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplication(), getString(R.string.BTconnFail), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            };

            mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
            } else {
                // Spawn a new thread to avoid blocking the GUI one
                Thread new_thread = new Thread() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void run() {
                        boolean fail = false;

                        BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                        try {
                            mBTSocket = createBluetoothSocket(device);
                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            mBTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                mBTSocket.close();
                                mHandler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                            } catch (IOException e2) {
                                //insert code to deal with this
                                Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                            }
                        }
                        if (!fail) {
                            mConnectedThread = new ConnectedThread(mBTSocket, mHandler);
                            mConnectedThread.start();

                            mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name).sendToTarget();
                        }
                    }
                };

                new_thread.start();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }
}