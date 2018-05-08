package com.example.t.myapplication;

        import android.Manifest;
        import android.annotation.TargetApi;
        import android.app.Activity;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothGatt;
        import android.bluetooth.BluetoothGattCallback;
        import android.bluetooth.BluetoothGattCharacteristic;
        import android.bluetooth.BluetoothGattDescriptor;
        import android.bluetooth.BluetoothGattService;
        import android.bluetooth.BluetoothManager;
        import android.bluetooth.BluetoothProfile;
        import android.bluetooth.le.BluetoothLeScanner;
        import android.bluetooth.le.ScanCallback;
        import android.bluetooth.le.ScanFilter;
        import android.bluetooth.le.ScanResult;
        import android.bluetooth.le.ScanSettings;
        import android.content.Context;
        import android.content.Intent;
        import android.content.pm.PackageManager;
        import android.graphics.Color;
        import android.hardware.Sensor;
        import android.hardware.SensorManager;
        import android.os.Build;
        import android.os.Bundle;
        import android.os.Handler;
        import android.support.v4.app.ActivityCompat;
        import android.support.v4.content.ContextCompat;
        import android.support.v7.app.AppCompatActivity;
        import android.text.method.ScrollingMovementMethod;
        import android.util.Log;
        import android.view.Menu;
        import android.view.MenuItem;
        import android.view.View;
        import android.widget.TextView;
        import android.widget.Toast;

        import com.github.mikephil.charting.charts.LineChart;
        import com.github.mikephil.charting.components.Legend;
        import com.github.mikephil.charting.components.XAxis;
        import com.github.mikephil.charting.components.YAxis;
        import com.github.mikephil.charting.data.Entry;
        import com.github.mikephil.charting.data.LineData;
        import com.github.mikephil.charting.data.LineDataSet;
        import com.github.mikephil.charting.highlight.Highlight;
        import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
        import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
        import com.github.mikephil.charting.utils.ColorTemplate;

        import java.util.ArrayList;
        import java.util.Arrays;
        import java.util.List;
        import java.util.UUID;

@TargetApi(21)
public class MainActivity extends AppCompatActivity implements OnChartValueSelectedListener {
    private static final String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private TextView mInfoTextView;
/* --------------------- Chart ---------------------- */
    private SensorManager mSensorManager;
    private Sensor mEcg;

    private LineChart mChart;
    private Thread thread;
    private boolean plotData = true;
    /* --------------------------------------------------*/

    //UI Actions
    public void onStartClick(View v){
        startScan();
    }

    public void onStopClick(View v){
        stopScan();
    }

    //LifeCycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mInfoTextView = (TextView) findViewById(R.id.textViewInfo);
        mInfoTextView.setMovementMethod(new ScrollingMovementMethod());
        setupBluetooth();
        requestLocationPermissions();

        /* ------------------------- Chart ----------------------------*/
        mChart = (LineChart) findViewById(R.id.chart);

        //mChart.setOnChartValueSelectedListener(this);

        // enable description text
        mChart.getDescription().setEnabled(true);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.LTGRAY);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        //xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        //leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(500f);
        leftAxis.setAxisMinimum(350f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.realtime, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.actionAdd: {
                addEntry(0);
                break;
            }
            case R.id.actionClear: {
                mChart.clearValues();
                Toast.makeText(this, "Chart cleared!", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.actionFeedMultiple: {
                feedMultiple();
                break;
            }
            case R.id.startScan: {
                startScan();
                break;
            }
            case R.id.stopScan: {
                stopScan();
                break;
            }
        }
        return true;
    }
    private void addEntry(int newEntry) {

        LineData data = mChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), newEntry + 30f), 0);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(1f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private void feedMultiple() {

        if (thread != null)
            thread.interrupt();

        final Runnable runnable = new Runnable() {

            @Override
            public void run() {
                addEntry(0);
            }
        };

        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < 1000; i++) {

                    // Don't generate garbage runnables inside the loop.
                    runOnUiThread(runnable);

                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (thread != null) {
            thread.interrupt();
        }
    }
    /* ------------------------------------------------------------*/
    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //Bluetooth
    void setupBluetooth(){

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    void requestLocationPermissions(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)  ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }



    void stopScan(){
        logToTextView("stopScan");
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }
    private void scanLeDevice(final boolean enable) {
        logToTextView("scanLeDevice");
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (SDKLower21()) {
                        logToTextView("stopLeScan SDK_INT < 21");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        logToTextView("stopScan SDK_INT >= 21");
                        mLEScanner.stopScan(mScanCallback);
                    }
                }
            }, SCAN_PERIOD);
            if (SDKLower21()) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
                //logToTextView("startLeScan SDK_INT < 21");
            } else {
                //logToTextView("startLeScan SDK_INT >= 21");
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (SDKLower21()) {
                //logToTextView("stopLeScan SDK_INT < 21");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                //logToTextView("stopLeScan SDK_INT >= 21");
                mLEScanner.stopScan(mScanCallback);
            }
        }
        Toast.makeText(getApplicationContext(), "scanLeDevice()", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "scanLeDevice");
    }
    void startScan(){
        logToTextView("starting scan");

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
                logToTextView("set settings");
            }
            scanLeDevice(true);
        }
    }
    //scan Callbacks
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            logToTextView(String.valueOf(callbackType));
            logToTextView(result.toString());
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            logToTextView("onBatchScanResults");
            for (ScanResult sr : results) {
                logToTextView(sr.toString());
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            logToTextView("Error Code");
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logToTextView(device.toString());
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };


    //connecting
    public void connectToDevice(BluetoothDevice device) {
        logToTextView("connectToDevice!");
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(TAG, "STATE_CONNECTED");
                    logToTextView("STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    logToTextView("STATE_DISCONNECTED");
                    Log.e(TAG, "STATE_DISCONNECTED");
                    break;
                default:
                    logToTextView("STATE_OTHER");
                    Log.e(TAG, "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();

            ArrayList<String> ids = new ArrayList<String>();
            for (BluetoothGattService service :services){
                ids.add(service.getUuid().toString());
            }
            Log.i("onServicesDiscovered",ids.toString());
            logToTextView(String.format("onServicesDiscovered: %s",ids.toString()));

            //BluetoothGattCharacteristic bodyTempCharacteristic = getBodyTemperatureCharacteristics(services);
            BluetoothGattCharacteristic ecgCharacteristic = getEcgCharacteristics(services);

            //BluetoothGattDescriptor descriptor = bodyTempCharacteristic.getDescriptor(
            //        UUID.fromString("362ba79d-b620-41d3-89ee-48f865559129"));
            //mGatt.setCharacteristicNotification(bodyTempCharacteristic, true);
            //gatt.readCharacteristic(bodyTempCharacteristic);
            gatt.readCharacteristic(ecgCharacteristic);
            //descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            //mGatt.writeDescriptor(descriptor);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            readDataFromCharacteristic(gatt,characteristic);
        }
        @Override
        // Characteristic notification
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.i("onCharacteristicChanged", characteristic.getUuid().toString());
            readDataFromCharacteristic(gatt, characteristic);
        }
    };


    //charcteristics
    String SENSOR_LIST_SERVICE_ID = "5b552788-5c7b-4ce8-8362-cf5dd093251d";
    String BODY_TEMPERATURE_SENSOR_ID = "362ba79d-b620-41d3-89ee-48f865559129";
    String ECG_SENSOR_ID = "ade7a273-89f9-49e1-b9d4-3cb36bce261b";


    BluetoothGattCharacteristic getBodyTemperatureCharacteristics(List<BluetoothGattService> services){


        for (BluetoothGattService service :services){
            if (service.getUuid().toString().equals(SENSOR_LIST_SERVICE_ID)){
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()){
                    if (characteristic.getUuid().toString().equals(BODY_TEMPERATURE_SENSOR_ID)){
                        return  characteristic;
                    }
                }
            }
        }
        throw new java.lang.Error("not temp sensor");
    };

    BluetoothGattCharacteristic getEcgCharacteristics(List<BluetoothGattService> services) {
        for (BluetoothGattService service :services){
            if (service.getUuid().toString().equals(SENSOR_LIST_SERVICE_ID)){
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()){
                    if (characteristic.getUuid().toString().equals(ECG_SENSOR_ID)){
                        return  characteristic;
                    }
                }
            }
        }
        throw new java.lang.Error("Not ECG Sensor");
    }

    int bytesToRead = 1000;
    byte[] byteArray;
    int[] intArray;
    int ecgValue = 0;
    private Handler readHandler = new Handler();
    long READ_DELAY = 10;
    void readDataFromCharacteristic(final BluetoothGatt gatt,final BluetoothGattCharacteristic characteristic){
        logToTextView(String.format("onCharacteristicRead: %s", characteristic.getUuid().toString()));
        Log.i("onCharacteristicRead", characteristic.getUuid().toString());

        //String biteLog = String.format("BYTES COUNT: %d BYTES FROM SENSOR: %s",characteristic.getValue().length, bytesToDec(characteristic.getValue()));
        byteArray = characteristic.getValue();
        intArray = bytearray2intarray(byteArray);
        if (intArray.length > 1) ecgValue = intArray[0] + (256 * intArray[1]); else ecgValue = 0;

        String biteLog = String.format("BYTES COUNT: %d BYTES F S: %d",characteristic.getValue().length, ecgValue);
        addEntry(ecgValue);


        logToTextView(biteLog);
        Log.i("onCharacteristicRead",biteLog);
        if  (bytesToRead > 0){
            readHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    gatt.readCharacteristic(characteristic);
                    bytesToRead--;
                }
            },READ_DELAY );
        } else {
            gatt.disconnect();
        }
    }

    //helpers
    String logs = "";
    void logToTextView(final String text){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logs = logs.concat("\n");
                logs = logs.concat(text);
                mInfoTextView.setText(logs);
            }
        });
    }
    boolean SDKLower21(){
        return Build.VERSION.SDK_INT < 21;
    }

    public String bytesToDec(byte[] bytes) {
        int[] numbers = bytearray2intarray(bytes);
        String result = "";
        for (int i : numbers){
            result = result.concat(String.valueOf(i));
            result = result.concat(" ");
        }
        return result;
    }


    public int[] bytearray2intarray(byte[] barray) {
        int[] iarray = new int[barray.length];
        int i = 0;
        for (byte b : barray)
            iarray[i++] = b & 0xff;
        return iarray;
    }

}



