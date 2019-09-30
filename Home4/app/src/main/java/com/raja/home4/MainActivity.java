package com.raja.home4;
/*
https://github.com/firebase/quickstart-unity/issues/402
Problem: runtime exception :
java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/localbroadcastmanager/content/LocalBroadcastManager;
Solution: in the app/build.gradle, add:
implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0'
 */
import com.raja.helpers.*;
import androidx.appcompat.app.AppCompatActivity;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Rajaraman";
    private TextView mLabel1;
    protected MyqtService myqttService;
    protected MyqtService.LocalBinder mBinder;
    private ServiceConnection mServiceConnection;
    protected myBroadcastReceiver mReceiver;  // inner class defined below

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((Button)findViewById(R.id.button1)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button2)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button3)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button4)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button5)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button6)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button7)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button8)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button9)).setOnClickListener((View.OnClickListener) this);
        ((Button)findViewById(R.id.button10)).setOnClickListener((View.OnClickListener) this);
        mLabel1 = (TextView)findViewById(R.id.label1);
        // We need internet connection to proceed further
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network; starting WiFi..");
            changeWifiStatus(true);
        }
        if (mServiceConnection == null) {
            Log.d(TAG, "Initializing local service...");
            initService();
        }
    }

    public void initService() {
        IntentFilter intentFilter = new IntentFilter(MyqtService.MQTT_RECEIVED_ACTION);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        mReceiver = new myBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName(getApplicationContext(), MyqtService.MQTT_SERVICE_NAME);
        serviceIntent.setPackage("com.raja.helpers");
        mServiceConnection = new ServiceConnection() {
            @SuppressWarnings("unchecked")
            @Override
            public void onServiceConnected (ComponentName className, final IBinder service) {
                Log.d(TAG, "Local service connected: ");
                Log.d(TAG, className.toShortString());
                mBinder = (MyqtService.LocalBinder) service;
                myqttService = mBinder.getServerInstance();
                myqttService.initializeConnection();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "Local service disconnected: ");
                Log.d(TAG, name.toShortString());
            }
        };
        // bind to the object we just created
        Log.d(TAG, "Binding to local service.. ");
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    protected void onResume() {
        Log.d(TAG, "-- OnResume --");
        super.onResume();
    }

    public void onPause() {
        Log.d(TAG, "-- OnPause --");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "-- OnDestroy --");
        if (mServiceConnection != null) {
            Log.d(TAG, "Disconnecting from MQTT server.. ");
            myqttService.disconnect();
            Log.d(TAG, "Unbinding local service.. ");
            unbindService(mServiceConnection);
            mServiceConnection = null;
        }
        Log.d(TAG, "Unregistering receiver..");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        String payload = getPayload(view);
        try {
            if (myqttService != null)
                myqttService.sendCommand(payload);
            else {
                Log.d(TAG, "Unable to publlish: MQTT service is NULL");
                displayStatus ("-- Unable to publish");
            }
        } catch (Exception e) {
            Log.d(TAG, "* Unable to publish to MQTT:");
            e.printStackTrace();
        }
    }

    private String getPayload (View v) {
        String payload = "ERROR";
        int buttonid = v.getId();
        switch (buttonid) {
            case R.id.button1 :
                payload = "ON0";
                break;
            case R.id.button5 :
                payload = "OFF0";
                break;
            case R.id.button2 :
                payload = "ON1";
                break;
            case R.id.button6:
                payload = "OFF1";
                break;
            case R.id.button3 :
                payload = "ON2";
                break;
            case R.id.button7 :
                payload = "OFF2";
                break;
            case R.id.button4 :
                payload = "ON3";
                break;
            case R.id.button8 :
                payload = "OFF3";
                break;
            case R.id.button9 :
                payload = "STA";  // status
                break;
            case R.id.button10 :
                payload = "UPD";  // firmware update
                break;
        }
        return payload;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void changeWifiStatus(boolean status) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(status);
    }

    int lineCount = 0;
    public void displayStatus (String message) {
        lineCount++;
        if (lineCount >=15) {
            lineCount = 0;
            mLabel1.setText(message);
            mLabel1.append("\n");
        }
        else {
            mLabel1.append(message);
            mLabel1.append("\n");
        }
    }

    //----------- inner class --------------------------------------------
    public class myBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //String topic = intent.getStringExtra("topic");
            String message = intent.getStringExtra("message");
            Log.d(TAG, "myBroadcastReceiver -> OnReceive: "+ message);
            displayStatus (message);
        }
    }
}
