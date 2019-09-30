package com.raja.helpers;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.Nullable;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MyqtService extends Service implements MqttCallbackExtended, IMqttActionListener, IMqttMessageListener {

    private static final String TAG = "Rajaraman";
    protected boolean statusNotificationsEnabled = Config.enableStatusNotifications; // notify back to main activity

    public static final String MQTT_SERVICE_NAME = "com.raja.helpers.MyqtService";
    public final static String MQTT_RECEIVED_ACTION = "com.raja.helpers.MyqtService.MQTT_RECEIVED";

    protected MqttAndroidClient mClient;
    protected IBinder mBinder = new LocalBinder();

    public MyqtService() {
        super();
        Log.d(TAG, "MyqtService -> constructor");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "MyqtService -> returning binder");
        return mBinder;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect)   // If Clean Session is true, we need to re-subscribe now
            Log.d(TAG, "Reconnected to : " + serverURI);
        else
            Log.d(TAG, "Connected to: " + serverURI);
        if (statusNotificationsEnabled)
            sendBroadcast ("-- Connected to MQTT broker");
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d(TAG, "MQTT Connection was lost.");
        if (statusNotificationsEnabled)
            sendBroadcast ("-- MQTT connection lost");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Subscribed!");
        if (statusNotificationsEnabled)
            sendBroadcast ("-- Subscribed");
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.d(TAG, "-- Failed to subscribe --");
        if (statusNotificationsEnabled)
            sendBroadcast ("-- Failed to subscribe");
    }

    @Override
    public void messageArrived(String topic, MqttMessage payload) throws Exception {
        String message = new String(payload.getPayload());
        Log.d(TAG, "Service -> Message arrived: " + message);
        sendBroadcast(message); // send it to main activity
    }

    // ---- custom methods -----------------------------
    // send notification back to the main activity
    public void sendBroadcast(String message) {
        Intent intent = new Intent();
        intent.setAction(MQTT_RECEIVED_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        // intent.putExtra("topic", topic);
        intent.putExtra("message", message);
        sendBroadcast(intent); // base class method (Context.sendBroadcast)
    }

    // send MQTT command to IoT device
    public void sendCommand(String command) { // this is called from main activity
        Log.d(TAG, "Service -> Publishing: " +command);
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = command.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            mClient.publish(Config.publishTopic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
            if (statusNotificationsEnabled)
                sendBroadcast ("-- Failed to send MQTT message");
        }
    }

    public void subscribeToTopic(String topic, int qos){
        Log.d(TAG, "Subscribing to: "+ topic);
        try {
            mClient.subscribe(topic, qos, null, this);
        } catch (MqttException ex){
            System.err.println("Exception while subscribing: ");
            ex.printStackTrace();
            if (statusNotificationsEnabled)
                sendBroadcast ("-- Failed to subscribe");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "-- Service -> OnDestroy --");
        super.onDestroy();
    }

    private String getClientId() {
        String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d(TAG, "DeviceID : " +deviceId);
        String deviceString;
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            digest.update(deviceId.getBytes());
            byte messageDigest[] = digest.digest();
            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            deviceString = hexString.toString().substring(0,6);
        } catch (NoSuchAlgorithmException e) {
            deviceString = "Unknown";
        }
        String clientID =  "Raja-" + android.os.Build.MODEL + "-" + deviceString;
        Log.d(TAG, "Client ID : " +clientID);
        return (clientID);
    }

    public void initializeConnection() { // this is called from main activity
        if (mClient != null) {
            Log.d(TAG, "Service -> Client is already present");
            return;
        }
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        //mqttConnectOptions.setUserName(Config.userName);
        //mqttConnectOptions.setPassword(Config.password.toCharArray());
        mClient = new MqttAndroidClient(this, Config.serverUri, getClientId());
        mClient.setCallback(this);
        Log.d(TAG, "Connecting to MQTT server..");
        try {
            mClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic(Config.subscriptionTopic, Config.QOS);
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "-- Failed to connect to MQTT broker --");
                    if (statusNotificationsEnabled)
                        sendBroadcast ("-- Failed to connect to MQTT broker");
                }
            });
        } catch (MqttException ex) {
            Log.d(TAG, "-- MQTT Eception : --");
            ex.printStackTrace();
        }
    }

    public void disconnect() {
        Log.d(TAG, "Service -> unregistering resources..");
        mClient.unregisterResources();
    }

    public int onStartCommand(final Intent intent, int flags, final int startId) {
        Log.d(TAG, "Service -> starting..");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    // ---------------- inner class  ----------------------------------
    public class LocalBinder extends Binder {
        public MyqtService getServerInstance() {
            return MyqtService.this;  // return the containing class object
        }
    }
}
