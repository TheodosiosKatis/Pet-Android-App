package com.ashencostha.mqtt;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LoginActivity extends AppCompatActivity implements ApiRequestTask.ApiResponseListener{
    private boolean userLoggedInSuccessfully = false;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private TextView errorTextView;
    private Button loginButton;
    private Animation buttonPressedAnim;
    private String TAG = "thisislogin";

    private static final String PREF_NAME = "login_credentials";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private static final String CONFIG_FILE_NAME = "config.json";
    private String tokenAPI;

    // Variable to store the server's response after API call
    private String message;
    private String imeiNo1;
    private String imeiNo2;
    private String username;
    private String password;
    private String mqttUsername;
    private String mqttPassword;

    private Button closeButton;
    private RelativeLayout.LayoutParams layoutParams;
    private boolean isKeyboardOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Load the token from config.json
        loadTokenFromConfig();

        // Hide the ActionBar
        getSupportActionBar().hide();

        // Initialize views
        usernameEditText = findViewById(R.id.editText);
        passwordEditText = findViewById(R.id.editText2);
        loginButton = findViewById(R.id.button);
        errorTextView = findViewById(R.id.errorTextView);
        buttonPressedAnim = AnimationUtils.loadAnimation(this, R.anim.button_pressed_anim);
        ImageButton passwordVisibilityToggle = findViewById(R.id.passwordVisibilityToggle);
        EditText passwordEditText = findViewById(R.id.editText2);

        boolean fromMainActivity = getIntent().getBooleanExtra(MainActivity.EXTRA_FROM_MAIN_ACTIVITY, false);
        if (fromMainActivity) {
            // It clears all the values, because the user has been logged out
            clearSavedCredentials();
            clearImeiNumbers();
            clearMQTTCredentials();
        }

        // Check if credentials are saved &
        // Load saved username and password
        Pair<String, String> savedCredentials = loadSavedCredentials();
        if (savedCredentials != null) {
            String savedUsername = savedCredentials.first;
            String savedPassword = savedCredentials.second;
            navigateToLoadingActivity(savedUsername, savedPassword);
        }

        // Set onClickListener for loginButton
        loginButton.setOnClickListener(v -> {
            // Apply button pressed animation
            loginButton.startAnimation(buttonPressedAnim);

            // Save the username and password when the button is clicked
            username = usernameEditText.getText().toString();
            password = passwordEditText.getText().toString();

            if(username.isEmpty() || password.isEmpty()){
                showEmptyCredentialsMessage(false);
                return;
            }else{
                saveCredentials(username, password);
                makeApiRequest(username, password);
            }
        });

        /*------------------------------------------------------*/
        //closeButton = findViewById(R.id.closeButton);
        ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setVisibility(View.GONE); // Initially hide the button

        // Set up layout parameters for the button
        layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        //layoutParams.setMargins(0, 0, 0, 0); // Adjust margins as needed

        /*------------------------------------------------------*/
        // Set OnClickListener for password visibility toggle button
        passwordVisibilityToggle.setOnClickListener(view -> {
            // Toggle password visibility
            if (passwordEditText.getTransformationMethod() == PasswordTransformationMethod.getInstance()) {
                // Password is currently hidden, show it
                passwordEditText.setTransformationMethod(null);
                //passwordVisibilityToggle.setImageResource(R.drawable.ic_visibility_on); // Set the image for visibility on
            } else {
                // Password is currently visible, hide it
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                //passwordVisibilityToggle.setImageResource(R.drawable.ic_visibility_off); // Set the image for visibility off
            }
        });

        // Add a global layout listener to detect keyboard changes
        View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                contentView.getWindowVisibleDisplayFrame(r);
                int screenHeight = contentView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;
                if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                    if (!isKeyboardOpen) {
                        // Show the button
                        closeButton.setVisibility(View.VISIBLE);
                        // Adjust the position of the button higher above the keyboard
                        layoutParams.setMargins(0, 0, 20, keypadHeight); // Change the margin to move the button higher
                        closeButton.setLayoutParams(layoutParams);
                        isKeyboardOpen = true;
                    }
                } else { // Keyboard is closed
                    if (isKeyboardOpen) {
                        // Hide the button
                        closeButton.setVisibility(View.GONE);
                        isKeyboardOpen = false;
                    }
                }
            }
        });


        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Hide the keyboard
                hideKeyboard();
            }
        });
    }

    /*
     * It loads the token from a file in order to make the API request
     * */
    private void loadTokenFromConfig() {
        try {
            // Read the JSON from config.json
            InputStream inputStream = getAssets().open(CONFIG_FILE_NAME);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String json = new String(buffer, "UTF-8");

            // Parse the JSON to get the token value
            JSONObject jsonObject = new JSONObject(json);
            tokenAPI = jsonObject.getString("tokenAPI");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            // Handle error
        }
    }

    /*
     * Is being called when the 'closeButton' is being pressed
     * Closes the open keyboard
     * */
    private void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /*
     * Makes the API request
     * */
    private void makeApiRequest(String param1, String param2) {
        ApiRequestTask apiRequestTask = new ApiRequestTask(tokenAPI, this);

        String secondApiEndpoint = "https://XXX.XXX.XXX.XXX:1312/api/authenticate/";
        String ApiUrl = secondApiEndpoint + param1 + "/" + param2;

        apiRequestTask.executeTask(ApiUrl);
    }

    /*
     * Method for parsing API's response
     * */
    @Override
    public void onApiResponseReceived(String response) {
        if (response != null) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                // Extract the values
                message = jsonObject.optString("message", "");
                mqttUsername = jsonObject.optString("mqtt_username", "");
                mqttPassword = jsonObject.optString("mqtt_password", "");
                imeiNo1 = jsonObject.optString("imei_no1", "");
                imeiNo2 = jsonObject.optString("imei_no2", "");

                if(message.equals("Authentication successful.")) {
                    userLoggedInSuccessfully = true;
                    // After successful login, navigate to MainActivity
                    if (userLoggedInSuccessfully) {
                        saveImeiNumbers(imeiNo1, imeiNo2);
                        saveMQTTCredentials(mqttUsername, mqttPassword);

                        navigateToLoadingActivity(username, password);
                    }
                } else {
                    showWrongCredentialsMessage(false);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                // Handle JSON parsing error
            }
        } else {
            Log.e(TAG, "Response is null");
        }
    }

    /*
     * Phone vibrates in wrong credentials
     * */
    private void vibrateOnWrongCredentials() {
        // Get instance of Vibrator from current Context
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Check if device supports vibration
        if (vibrator != null && vibrator.hasVibrator()) {
            // Vibrate for 1000 milliseconds (1 second)
            vibrator.vibrate(100);
        }
    }

    /*
     * Checks if the 'username' or 'password' input fields is empty.
     * If it's true, it shows a corresponding message
      * */
    private void showEmptyCredentialsMessage(boolean credentialsCorrect) {
        if (!credentialsCorrect) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Perform UI operations on the main UI thread
                    // Start animations, show error messages, etc.
                    ObjectAnimator shakeUsername = ObjectAnimator.ofFloat(usernameEditText, "translationX", -10, 10);
                    shakeUsername.setDuration(100);
                    shakeUsername.setRepeatCount(5);
                    shakeUsername.start();

                    ObjectAnimator shakePassword = ObjectAnimator.ofFloat(passwordEditText, "translationX", -10, 10);
                    shakePassword.setDuration(100);
                    shakePassword.setRepeatCount(5);
                    shakePassword.start();

                    vibrateOnWrongCredentials();

                    // Show error message
                    errorTextView.setVisibility(View.VISIBLE);
                    errorTextView.setText("Please fill in Username & Password");
                }
            });
        }
    }

    /*
     * Checks if the 'username' or 'password' values is correct after an API request
     * */
    private void showWrongCredentialsMessage(boolean credentialsCorrect) {
        if (!credentialsCorrect) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Perform UI operations on the main UI thread
                    // Start animations, show error messages, etc.
                    ObjectAnimator shakeUsername = ObjectAnimator.ofFloat(usernameEditText, "translationX", -10, 10);
                    shakeUsername.setDuration(100);
                    shakeUsername.setRepeatCount(5);
                    shakeUsername.start();

                    ObjectAnimator shakePassword = ObjectAnimator.ofFloat(passwordEditText, "translationX", -10, 10);
                    shakePassword.setDuration(100);
                    shakePassword.setRepeatCount(5);
                    shakePassword.start();

                    vibrateOnWrongCredentials();

                    // Show error message
                    errorTextView.setVisibility(View.VISIBLE);
                    errorTextView.setText("Username or password is wrong");
                }
            });
        }
    }

    /*
     * Send data to LoadingActivity
     * Such as Username, Password etc
     * */
    private void navigateToLoadingActivity(String username, String password) {
        // Load IMEI numbers
        Pair<String, String> imeiNumbers = loadImeiNumbers();
        Pair<String, String> mqttCredentials = loadMQTTCredentials();

        Intent intent = new Intent(LoginActivity.this, LoadingActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("password", password);

        // Check if IMEI numbers are available and pass them as extras if so
        if (imeiNumbers != null) {
            String imeiNo1 = imeiNumbers.first;
            String imeiNo2 = imeiNumbers.second;
            intent.putExtra("imeiNo1", imeiNo1);
            intent.putExtra("imeiNo2", imeiNo2);
        }

        // Check if IMEI numbers are available and pass them as extras if so
        if (mqttCredentials != null) {
            String mqtt_username = mqttCredentials.first;
            String mqtt_password = mqttCredentials.second;
            intent.putExtra("mqtt_username", mqtt_username);
            intent.putExtra("mqtt_password", mqtt_password);
        }

        startActivity(intent);
        finish(); // This will close the LoginActivity so the user won't be able to navigate back to it without logging out
    }

    /*
     * It saves user's credentials for later use
     * */
    private void saveCredentials(String username, String password) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    /*
     * It loads user's credentials, when it needed
     * */
    private Pair<String, String> loadSavedCredentials() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString(KEY_USERNAME, "");
        String savedPassword = sharedPreferences.getString(KEY_PASSWORD, "");
        // Set the saved values to the EditTexts
        usernameEditText.setText(savedUsername);
        passwordEditText.setText(savedPassword);

        // Check if both username and password are non-empty
        if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
            return new Pair<>(savedUsername, savedPassword);
        } else {
            return null; // Return null if no credentials are saved
        }
    }

    /*
     * Clears the credentials from buffer when the user logs out
     * */
    private void clearSavedCredentials() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_PASSWORD);
        editor.apply();
    }

    /***************************************************************/
    /*
     * Same routine cycle with the 3 above methods (save, load, clear)
     * */
    private void saveImeiNumbers(String imeiNo1, String imeiNo2) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("imeiNo1", imeiNo1);
        editor.putString("imeiNo2", imeiNo2);
        editor.apply();
    }

    private Pair<String, String> loadImeiNumbers() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String imeiNo1 = sharedPreferences.getString("imeiNo1", "");
        String imeiNo2 = sharedPreferences.getString("imeiNo2", "");

        // Check if both IMEI numbers are non-empty
        if (!imeiNo1.isEmpty() || !imeiNo2.isEmpty()) {
            return new Pair<>(imeiNo1, imeiNo2);
        } else {
            return null; // Return null if no IMEI numbers are saved
        }
    }
    private void clearImeiNumbers() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("imeiNo1");
        editor.remove("imeiNo2");
        editor.apply();
    }

    private void saveMQTTCredentials(String mqtt_username, String mqtt_password) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("mqtt_username", mqtt_username);
        editor.putString("mqtt_password", mqtt_password);
        editor.apply();
    }

    private Pair<String, String> loadMQTTCredentials() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String mqtt_username = sharedPreferences.getString("mqtt_username", "");
        String mqtt_password = sharedPreferences.getString("mqtt_password", "");

        // Check if both IMEI numbers are non-empty
        if (!mqtt_username.isEmpty() || !mqtt_password.isEmpty()) {
            return new Pair<>(mqtt_username, mqtt_password);
        } else {
            return null; // Return null if no IMEI numbers are saved
        }
    }
    private void clearMQTTCredentials() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("mqtt_username");
        editor.remove("mqtt_password");
        editor.apply();
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Load saved username and password when the activity resumes
        loadSavedCredentials();
        loadImeiNumbers();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
