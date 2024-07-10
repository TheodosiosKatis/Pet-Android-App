package com.ashencostha.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.util.UUID;

public class MqttHandler {

    private double longitude;
    private double latitude;
    private int battery;
    private String timestamp;

    private MqttClient client ;
    private static final String BROKER_URI = "tcp://XXX.XXX.XXX.XXX:1883";
    private static final String CLIENT_ID = "android_" + UUID.randomUUID().toString();

    /*
     * Function which connects to a MQTT broker
     * */
    public void connect(String username, String password) {
        try {
            // Set up the persistence layer
            MemoryPersistence persistence = new MemoryPersistence();

            // Initialize the MQTT client
            client = new MqttClient(BROKER_URI, CLIENT_ID, persistence);

            // Set up the connection options
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            // Set username and password
            if (username != null && !username.isEmpty()) {
                connectOptions.setUserName(username);
            }
            if (password != null && !password.isEmpty()) {
                connectOptions.setPassword(password.toCharArray());
            }

            // Set up a callback for incoming messages
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.e("MqttHandler", "Connection lost: " + cause.getMessage());
                    cause.printStackTrace(); // Add this line to print the stack trace
                    reconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // Handle the received message
                    handleIncomingMessage(topic, new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used for subscription
                }
            });

            // Connect to the broker
            client.connect(connectOptions);
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MqttHandler", "Exception while connecting to the MQTT broker. Reason: " + e.getReasonCode() + ", Message: " + e.getMessage());
        }
    }


    /*
     * Check if the client is connected to the broker
     * */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /*
     * Reconnect function in case that the client disconnects
     * */
    public void reconnect() {
        // Implement your reconnect logic here
        try {
            if (client != null && !client.isConnected()) {
                client.reconnect();
            }
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MqttHandler", "Exception during reconnect: " + e.getMessage());
        }
    }

    /*
     * Disconnect function for the client
     * */
    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /*
     * Extract IMEI from the topic assuming the format "IMEI/parameter"
     * */
    private String getImeiPrefix(String topic) {
        String[] parts = topic.split("/");
        if (parts.length > 1) {
            return parts[0];
        }
        return ""; // Default value if IMEI not found
    }

    /*
     * Saves the values of each topic to a var & update the listeners
     * */
    private void handleIncomingMessage(String topic, String message) {
        // Extract the IMEI from the topic
        String imei = getImeiPrefix(topic);
        String longTopic = imei + "/longitude";
        String latTopic = imei + "/latitude";
        String battTopic = imei + "/battery";
        String timeTopic = imei + "/timestamp";

        // Update variables based on the topic
        if (longTopic.equals(topic)) {
            longitude = Double.parseDouble(message);
            updateLongitude(longitude); // Notify the listener of the update
        } else if (latTopic.equals(topic)) {
            latitude = Double.parseDouble(message);
            updateLatitude(latitude); // Notify the listener of the update
        } else if (battTopic.equals(topic)) {
            battery = Integer.parseInt(message);
            updateBattery(battery); // Notify the listener of the update
        }
        else if (timeTopic.equals(topic)) {
            timestamp = message;
            updateTimestamp(timestamp); // Notify the listener of the update
        }
    }

    /*
     * Function for publish a message to a specific topic
     * */
    public void publish(String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /*
     * Function to subscribe to a specific topic
     * */
    public void subscribe(String topic) {
        try {
            if (client != null && client.isConnected()) {
                client.subscribe(topic);
            } else {
                Log.e("MqttHandler", "Cannot subscribe. Client is null or not connected.");
            }
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MqttHandler", "Exception while subscribing to topic: " + e.getMessage());
        }
    }

    /*
     * Define and initialize the listeners
     * */
    private LatitudeUpdateListener latitudeUpdateListener;
    private LongitudeUpdateListener longitudeUpdateListener;
    private BatteryUpdateListener batteryUpdateListener;
    private TimestampUpdateListener timestampUpdateListener;

    public interface LongitudeUpdateListener {
        void onLongitudeUpdate(double longitude);
    }
    public interface LatitudeUpdateListener {
        void onLatitudeUpdate(double latitude);
    }
    public interface BatteryUpdateListener {
        void onBatteryUpdate(int battery);
    }
    public interface TimestampUpdateListener {
        void onTimestampUpdate(String timestamp);
    }

    public void setLongitudeUpdateListener(LongitudeUpdateListener listener) {
        this.longitudeUpdateListener = listener;
    }
    public void setLatitudeUpdateListener(LatitudeUpdateListener listener) {
        this.latitudeUpdateListener = listener;
    }
    public void setBatteryUpdateListener(BatteryUpdateListener listener) {
        this.batteryUpdateListener = listener;
    }
    public void setTimestampUpdateListener(TimestampUpdateListener listener) {
        this.timestampUpdateListener = listener;
    }

    private void updateLongitude(double longitude) {
        if (longitudeUpdateListener != null) {
            longitudeUpdateListener.onLongitudeUpdate(longitude);
        }
    }

    private void updateLatitude(double latitude) {
        if (latitudeUpdateListener != null) {
            latitudeUpdateListener.onLatitudeUpdate(latitude);
        }
    }
    private void updateBattery(int battery) {
        if (batteryUpdateListener != null) {
            batteryUpdateListener.onBatteryUpdate(battery);
        }
    }
    private void updateTimestamp(String timestamp) {
        if (timestampUpdateListener != null) {
            timestampUpdateListener.onTimestampUpdate(timestamp);
        }
    }
}
