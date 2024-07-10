package com.ashencostha.mqtt;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class ApiRequestTask {
    private static final String TAG = "ApiRequestTask";

    private final String token;
    private final ApiResponseListener listener;
    private final ExecutorService executor;

    /*
     * Constructor definition
     * */
    public ApiRequestTask(String token, ApiResponseListener listener) {
        this.token = token;
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /*
     * Method for executing the API request
     * */
    public void executeTask(String... params) {
        executor.execute(() -> {
            String response = doInBackground(params);
            onPostExecute(response);
        });
    }

    /*
     * Method which formats the API request
     * Such as Headers, type of request (GET, POST), body etc
     * */
    private String doInBackground(String[] params) {
        String response = null;
        try {
            String apiUrl = params[0];
            URL url = new URL(apiUrl);

            // Check if the URL protocol is HTTPS
            if (!url.getProtocol().equalsIgnoreCase("https")) {
                Log.e(TAG, "URL is not HTTPS");
                return null;
            }

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("x-app-token", token);

            // Trust all certificates (for self-signed)
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            connection.setSSLSocketFactory(sslContext.getSocketFactory());

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    responseBuilder.append(line);
                }
                in.close();
                response = responseBuilder.toString();
            } else {
                Log.e(TAG, "Server returned HTTP error code: " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from API: " + e.getMessage(), e);
        }
        return response;
    }


    /*
     * Method for updating the listener on server's response
     * */
    private void onPostExecute(String response) {
        if (listener != null) {
            listener.onApiResponseReceived(response);
        }
    }

    public interface ApiResponseListener {
        void onApiResponseReceived(String response);
    }
}