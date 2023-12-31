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
    private Handler mHandler; // Our main handler that will receive callback notifications
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private TextView mReadBuffer;
    private ConnectThread mConnectThread; // bluetooth background worker thread to send and receive data
    private int alpha = -1;
    private int gamma = -1;
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
//            if (Objects.equals(location.getProvider(), "fused")) {
            now_long = location.getLongitude();
            now_lat = location.getLatitude();
//            }

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
                float arrowDegree;
                if (gamma == -1) {
                    arrowDegree = (float) bearing - azimuthinDegress;
                } else {
                    arrowDegree = gamma - alpha + azimuthinDegress;
                }
                RotateAnimation ra = new RotateAnimation(mCurrentDegress, arrowDegree, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                ra.setDuration(250);
                ra.setFillAfter(true);
                mArrow.startAnimation(ra);
                txtResult.setText(" " + (int) azimuthinDegress + "° \n " + (int) bearing + "° ");
                lastUpdate = System.currentTimeMillis();
                mCurrentDegress = arrowDegree;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void connectBluetooth() {

        mConnectThread = ((StoreDevice) getApplication()).globalConnectThread;

        if (mConnectThread == null) {
            return;
        }

        mReadBuffer = (TextView) findViewById(R.id.textview_readbuffer);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = null;
                    readMessage = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                    mReadBuffer.setText(readMessage);

                    boolean flag = false;
                    int num = 0;
                    for (int i = 0; i < readMessage.length(); i++) {
                        char temp_item = readMessage.charAt(i);
                        if ((temp_item >= '0') && (temp_item <= '9')) {
                            num *= 10;
                            num += (temp_item - 48);
                            flag = true;
                        }
                        if (temp_item == 'c') {
                            alpha = -1;
                            gamma = -1;
                            stack = 0;
                            break;
                        }
                    }
                    if (flag) {
                        switch (stack) {
                            case (0):
                                alpha = num;
                                stack++;
                                break;
                            case (1):
                                gamma = num;
                                stack = 0;
                                break;
                        }
                    }
//                    Toast.makeText(getApplication(), readMessage, Toast.LENGTH_LONG).show();
                    Toast.makeText(getApplication(), "alpha: " + alpha + "\ngamma: " + gamma, Toast.LENGTH_LONG).show();
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
//        try {
//            mConnectThread.wait();
//            Toast.makeText(this, "THREAD STOP", Toast.LENGTH_LONG);
//        } catch (InterruptedException e) {
//            Toast.makeText(this, "THREAD CANNOT STOP", Toast.LENGTH_LONG);
//        }
        mConnectThread.changeContextHandler(getApplicationContext(), mHandler);
    }

}