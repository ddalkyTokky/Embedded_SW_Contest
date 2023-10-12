package com.example.googlemapmavsdk;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
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
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.googlemapmavsdk.databinding.ActivityMapsBinding;

import java.util.Objects;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {
    private GoogleMap mMap;
    private Marker mMarker;
    private ActivityMapsBinding binding;
    private double carLat = 37.601070088505644;
    private double carLong = 126.865068843289;
    private static final float ZOOM_SCALE = 17f;
    private static final float MISSION_MARKER_COLOR = 180f;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private double fus_long;
    private double fus_lat;

    CameraPosition cameraPosition;

    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        } else {
            lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 10, 1, gpsLocationListener);
        }
    }

    final LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {

            String provider = location.getProvider();
            if (Objects.equals(provider, "fused")) {
                fus_long = location.getLongitude();
                fus_lat = location.getLatitude();

                if (mMarker != null) {
                    mMarker.remove();
                }
                mMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(fus_lat, fus_long)).title("My Location"));

                // 위도, 경도를 라디안 단위로 변환
                double w1 = fus_lat * Math.PI / 180;
                double w2 = carLat * Math.PI / 180;
                double r1 = fus_long * Math.PI / 180;
                double r2 = carLong * Math.PI / 180;

                double y = Math.sin(r2 - r1) * Math.cos(w2);
                double x = Math.cos(w1) * Math.sin(w2) - Math.sin(w1) * Math.cos(w2) * Math.cos(r2 - r1);
                double seta = Math.atan2(y, x); // 방위각 (라디안)
                double bearing = (seta * 180 / Math.PI + 360) % 360; // 방위각 (디그리, 정규화 완료)

                cameraPosition = new CameraPosition.Builder().target(new LatLng(carLat, carLong))      // Sets the center of the map to Mountain View
                        .zoom(ZOOM_SCALE)                   // Sets the zoom
                        .bearing((float) bearing)      // Sets the orientation of the camera to east
                        .tilt(0)                   // Sets the tilt of the camera to 30 degrees
                        .build();                   // Creates a CameraPosition from the builder
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 100, null);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };

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

        // Add a marker in Sydney and move the camera
        LatLng initialPoint = new LatLng(carLat, carLong);
        mMap.addMarker(new MarkerOptions().position(initialPoint).icon(BitmapDescriptorFactory.defaultMarker(MISSION_MARKER_COLOR)).title("My Car"));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(ZOOM_SCALE));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(initialPoint));

        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_maps, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.button_photo:
                intent = new Intent(getApplicationContext(), PhotoActivity.class);

                startActivity(intent);
                break;
            case R.id.button_arrow:
                intent = new Intent(getApplicationContext(), ArrowActivity.class);

                double[] intentLatLong = {carLat, carLong};
                intent.putExtra("carLatLong", intentLatLong);

                startActivity(intent);
                break;
            case R.id.button_bluetooth:
                intent = new Intent(getApplicationContext(), BluetoothActivity.class);

                startActivity(intent);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mAccelerometer);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
    }
}