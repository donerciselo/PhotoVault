package com.example.photovault;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;

// Biometric imports
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

// Google Backup imports
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private Python py;
    private PyObject mainModule;
    private LinearLayout calculatorLayout, pinLayout, crashLayout, browserLayout;
    private TextView calcDisplay;
    private WebView webView, browserWebView;
    private EditText edtBrowserUrl;
    private Button btnBrowserBack, btnBrowserClose;
    private TextureView cameraTextureView;
    private String currentCalc = "";
    private String confirmingPin = null;
    private int failedPinAttempts = 0;
    private boolean isFirstLaunch = true;
    private static final int CAMERA_SMS_PERMISSION_CODE = 101;
    private boolean isPickingImage = false;
    private boolean isUnlockedLocal = false;

    // Shake sensor variables
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    // Volume down count variables
    private int volumeDownCount = 0;
    private long firstVolumeDownTime = 0;

    // Fake crash top-left tap variables
    private int topLeftClickCount = 0;
    private long firstTopLeftClickTime = 0;
    private long onCreateTime = 0;

    // Secret video recording variables
    private boolean isRecordingVideo = false;
    private android.hardware.Camera videoCamera = null;
    private android.media.MediaRecorder mediaRecorder = null;
    private java.io.File tempVideoFile = null;
    private long volumeUpPressStartTime = 0;
    private boolean isVolumeUpLongPressed = false;
    private final Runnable volumeUpLongClickRunnable = new Runnable() {
        @Override
        public void run() {
            isVolumeUpLongPressed = true;
            toggleSecretVideoRecording();
        }
    };

    // Google Sign-In
    private static final int RC_SIGN_IN = 9001;
    private com.google.android.gms.auth.api.signin.GoogleSignInClient googleSignInClient;
    private android.net.Uri pendingDeleteUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        mainModule = py.getModule("main");

        String storagePath = getFilesDir().getAbsolutePath();
        mainModule.callAttr("init_vault", storagePath);

        failedPinAttempts = 0;
        isFirstLaunch = true;

        new Thread(() -> {
            try {
                mainModule.callAttr("run_flask");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Request camera, SMS and audio recording permissions at startup
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), CAMERA_SMS_PERMISSION_CODE);
        }

        // Initialize sensor manager and accelerometer
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        onCreateTime = System.currentTimeMillis();

        initUI();

        checkOTAUpdate();

        new Thread(() -> {
            try {
                boolean pinSet = mainModule.callAttr("is_pin_set").toBoolean();
                if (pinSet) {
                    runOnUiThread(() -> checkAndPromptBiometrics());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void initUI() {
        calculatorLayout = findViewById(R.id.calculator_layout);
        pinLayout = findViewById(R.id.pin_layout);
        calcDisplay = findViewById(R.id.calc_display);
        webView = findViewById(R.id.webview);
        cameraTextureView = findViewById(R.id.camera_texture_view);

        View displayContainer = findViewById(R.id.display_container);
        if (displayContainer != null) {
            displayContainer.setOnTouchListener((v, ev) -> {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = ev.getX();
                    float y = ev.getY();
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - onCreateTime <= 25000) { // Within 25 seconds of startup
                        if (x < 350) { // Top-left corner of display container
                            if (topLeftClickCount == 0 || currentTime - firstTopLeftClickTime <= 2000) {
                                if (topLeftClickCount == 0) {
                                    firstTopLeftClickTime = currentTime;
                                }
                                topLeftClickCount++;
                                if (topLeftClickCount >= 3) {
                                    showFakeANRDialog();
                                    topLeftClickCount = 0;
                                }
                            } else {
                                topLeftClickCount = 1;
                                firstTopLeftClickTime = currentTime;
                            }
                        }
                    }
                }
                return false; // Let touch propagate normally
            });

            // Tap on display screen to delete last digit (Backspace)
            displayContainer.setOnClickListener(v -> {
                if (currentCalc != null && !currentCalc.isEmpty()) {
                    currentCalc = currentCalc.substring(0, currentCalc.length() - 1);
                    if (currentCalc.isEmpty()) {
                        calcDisplay.setText("0");
                    } else {
                        calcDisplay.setText(currentCalc);
                    }
                }
            });

            // Long click on display screen to clear completely (Clear)
            displayContainer.setOnLongClickListener(v -> {
                currentCalc = "";
                calcDisplay.setText("0");
                Toast.makeText(MainActivity.this, "Cleared", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // Crash UI Init
        crashLayout = findViewById(R.id.crash_layout);
        findViewById(R.id.btn_crash_close).setOnClickListener(v -> finishAffinity());
        findViewById(R.id.btn_crash_feedback).setOnClickListener(v -> finishAffinity());
        findViewById(R.id.btn_crash_app_info).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finishAffinity();
            } catch (Exception e) {
                finishAffinity();
            }
        });
        crashLayout.setOnLongClickListener(v -> {
            crashLayout.setVisibility(View.GONE);
            currentCalc = "";
            calcDisplay.setText("0");
            return true;
        });

        // Browser UI Init
        browserLayout = findViewById(R.id.browser_layout);
        browserWebView = findViewById(R.id.browser_webview);
        edtBrowserUrl = findViewById(R.id.edt_browser_url);
        btnBrowserBack = findViewById(R.id.btn_browser_back);
        btnBrowserClose = findViewById(R.id.btn_browser_close);

        browserWebView.getSettings().setJavaScriptEnabled(true);
        browserWebView.getSettings().setDomStorageEnabled(true);
        browserWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                edtBrowserUrl.setText(url);
            }
        });

        edtBrowserUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) {
                String url = edtBrowserUrl.getText().toString().trim();
                if (!url.isEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        if (url.contains(".") && !url.contains(" ")) {
                            url = "https://" + url;
                        } else {
                            try {
                                url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(url, "UTF-8");
                            } catch (Exception e) {
                                url = "https://www.google.com";
                            }
                        }
                    }
                    browserWebView.loadUrl(url);
                }
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(edtBrowserUrl.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        btnBrowserBack.setOnClickListener(v -> {
            if (browserWebView.canGoBack()) {
                browserWebView.goBack();
            }
        });

        btnBrowserClose.setOnClickListener(v -> closeBrowser());

        browserWebView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = browserWebView.getHitTestResult();
            int type = result.getType();
            if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                String imageUrl = result.getExtra();
                if (imageUrl != null) {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Save to Vault")
                        .setMessage("Do you want to save this image directly to your encrypted vault?")
                        .setPositiveButton("Save", (dialog, which) -> downloadImageDirectly(imageUrl))
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                }
            }
            return false;
        });

        setCalcButtons();
    }

    private void openVault() {
        isUnlockedLocal = true;
        calculatorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new android.webkit.WebChromeClient());
        webView.addJavascriptInterface(new WebInterface(), "android");
        webView.loadUrl("http://127.0.0.1:5000/");
    }

    private void setCalcButtons() {
        int[] ids = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_plus, R.id.btn_minus, R.id.btn_mult, R.id.btn_div};
        View.OnClickListener listener = v -> {
            currentCalc += ((Button) v).getText().toString();
            calcDisplay.setText(currentCalc);
        };
        for (int id : ids) findViewById(id).setOnClickListener(listener);

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            currentCalc = "";
            calcDisplay.setText("0");
        });

        findViewById(R.id.btn_equals).setOnClickListener(v -> {
            final String input = currentCalc;
            if (input.matches("^\\d{4}$")) {
                new Thread(() -> {
                    boolean pinSet = mainModule.callAttr("is_pin_set").toBoolean();
                    runOnUiThread(() -> {
                        if (!pinSet) {
                            if (confirmingPin == null) {
                                confirmingPin = input;
                                currentCalc = "";
                                calcDisplay.setText("0");
                                Toast.makeText(this, "Enter PIN again to confirm", Toast.LENGTH_SHORT).show();
                            } else {
                                if (confirmingPin.equals(input)) {
                                    new Thread(() -> {
                                        mainModule.callAttr("register_pin", input);
                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "PIN configured successfully", Toast.LENGTH_SHORT).show();
                                            confirmingPin = null;

                                            // Save PIN for biometrics
                                            getSharedPreferences("pv_secure_prefs", MODE_PRIVATE)
                                                .edit()
                                                .putString("saved_pin", input)
                                                .apply();

                                            openVault();
                                        });
                                    }).start();
                                } else {
                                    confirmingPin = null;
                                    currentCalc = "";
                                    calcDisplay.setText("0");
                                    Toast.makeText(this, "PIN mismatch. Try again", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            new Thread(() -> {
                                boolean success = mainModule.callAttr("verify_pin", input).toBoolean();
                                runOnUiThread(() -> {
                                    if (success) {
                                        failedPinAttempts = 0;

                                        // Save PIN for biometrics
                                        getSharedPreferences("pv_secure_prefs", MODE_PRIVATE)
                                            .edit()
                                            .putString("saved_pin", input)
                                            .apply();

                                        openVault();
                                    } else {
                                        failedPinAttempts++;
                                        if (failedPinAttempts >= 3) {
                                            captureIntruder();
                                            failedPinAttempts = 0;
                                            showFakeCrashDialog();
                                        } else {
                                            Toast.makeText(MainActivity.this, "Incorrect PIN. Attempts: " + failedPinAttempts + "/3", Toast.LENGTH_SHORT).show();
                                        }
                                        currentCalc = "";
                                        calcDisplay.setText("0");
                                    }
                                });
                            }).start();
                        }
                    });
                }).start();
            } else {
                try {
                    String result = mainModule.callAttr("eval_math", input).toString();
                    calcDisplay.setText(result);
                    currentCalc = result.equals("Error") ? "" : result;
                } catch (Exception e) {
                    calcDisplay.setText("Error");
                    currentCalc = "";
                }
            }
        });
    }

    class WebInterface {
        @JavascriptInterface
        public void pickImage() {
            isPickingImage = true;
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 100);
        }

        @JavascriptInterface
        public void viewImage(String filename) {
            // Optional: Show full image or just toast
            Toast.makeText(MainActivity.this, "Opening " + filename, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void openBrowser() {
            runOnUiThread(() -> {
                webView.setVisibility(View.GONE);
                browserLayout.setVisibility(View.VISIBLE);
                browserWebView.loadUrl("https://www.google.com");
            });
        }

        @JavascriptInterface
        public void lockApp() {
            runOnUiThread(() -> {
                isUnlockedLocal = false;
                onResume();
            });
        }

        @JavascriptInterface
        public void connectGoogleDrive() {
            runOnUiThread(() -> startGoogleSignIn());
        }

        @JavascriptInterface
        public String getBackupStatus() {
            return getSharedPreferences("pv_secure_prefs", MODE_PRIVATE)
                    .getString("backup_status", "Disconnected");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isPickingImage = false;
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] bytes = getBytes(is);
                String filename = "img_" + System.currentTimeMillis() + ".jpg";
                mainModule.callAttr("add_image", filename, bytes);
                webView.post(() -> webView.reload());
                Toast.makeText(this, "Imported successfully!", Toast.LENGTH_SHORT).show();

                // Attempt to delete original from gallery after successful import
                try {
                    pendingDeleteUri = uri;
                    int deleted = getContentResolver().delete(uri, null, null);
                    if (deleted > 0) {
                        Toast.makeText(this, "Original image deleted from gallery", Toast.LENGTH_SHORT).show();
                        pendingDeleteUri = null;
                    }
                } catch (SecurityException securityException) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        if (securityException instanceof android.app.RecoverableSecurityException) {
                            android.app.RecoverableSecurityException recoverableSecurityException = 
                                (android.app.RecoverableSecurityException) securityException;
                            android.content.IntentSender intentSender = recoverableSecurityException.getUserAction().getActionIntent().getIntentSender();
                            try {
                                startIntentSenderForResult(intentSender, 101, null, 0, 0, 0);
                            } catch (android.content.IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to import photo", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 101 && resultCode == RESULT_OK) {
            if (pendingDeleteUri != null) {
                try {
                    getContentResolver().delete(pendingDeleteUri, null, null);
                    Toast.makeText(this, "Original image deleted from gallery", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pendingDeleteUri = null;
            }
        } else if (requestCode == RC_SIGN_IN) {
            com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> task = 
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInAccount account = task.getResult(com.google.android.gms.common.api.ApiException.class);
                if (account != null) {
                    Toast.makeText(this, "Connected: " + account.getEmail(), Toast.LENGTH_SHORT).show();
                    getSharedPreferences("pv_secure_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("backup_status", "Connected to " + account.getEmail())
                        .apply();
                    scheduleCloudBackup();
                    webView.post(() -> webView.reload());
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Google Connection Failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void captureIntruder() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }

        if (cameraId == -1) {
            return;
        }

        final int finalCameraId = cameraId;
        new Thread(() -> {
            Camera camera = null;
            try {
                camera = Camera.open(finalCameraId);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            try {
                SurfaceTexture surfaceTexture = null;
                if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                    surfaceTexture = cameraTextureView.getSurfaceTexture();
                }
                if (surfaceTexture == null) {
                    surfaceTexture = new SurfaceTexture(10);
                }
                camera.setPreviewTexture(surfaceTexture);
                camera.startPreview();
                final Camera finalCamera = camera;
                camera.takePicture(null, null, (data, cam) -> {
                    try {
                        String filename = "security_img_" + System.currentTimeMillis() + ".jpg";
                        mainModule.callAttr("save_intruder_image", filename, data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            cam.stopPreview();
                            cam.release();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                if (camera != null) {
                    try {
                        camera.release();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private byte[] getBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) buffer.write(data, 0, nRead);
        return buffer.toByteArray();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (isUnlockedLocal) {
            if (browserLayout.getVisibility() == View.VISIBLE) {
                calculatorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
            } else {
                calculatorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        } else {
            calculatorLayout.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            browserLayout.setVisibility(View.GONE);
            currentCalc = "";
            calcDisplay.setText("0");
        }
        pinLayout.setVisibility(View.GONE);
    }

    private void showFakeCrashDialog() {
        runOnUiThread(() -> {
            crashLayout.setVisibility(View.VISIBLE);
        });
    }

    private void closeBrowser() {
        runOnUiThread(() -> {
            browserLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        });
    }

    private void downloadImageDirectly(String urlString) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(urlString);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.connect();
                InputStream input = conn.getInputStream();
                byte[] bytes = getBytes(input);
                
                String filename = "img_" + System.currentTimeMillis() + ".jpg";
                mainModule.callAttr("add_image", filename, bytes);
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Saved directly to Vault!", Toast.LENGTH_SHORT).show();
                    webView.post(() -> webView.reload());
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to download image", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // Shake-to-Hide Sensor Listener
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
            if (acceleration > 15.0f) { // Shake detection threshold
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastShakeTime > 2000) {
                    lastShakeTime = currentTime;
                    triggerPanicLock();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // Volume Down 3-Click detection & Volume Up secret video recording
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            long currentTime = System.currentTimeMillis();
            if (volumeDownCount == 0 || currentTime - firstVolumeDownTime <= 2000) {
                if (volumeDownCount == 0) {
                    firstVolumeDownTime = currentTime;
                }
                volumeDownCount++;
                if (volumeDownCount >= 3) {
                    triggerPanicLock();
                    volumeDownCount = 0;
                }
            } else {
                volumeDownCount = 1;
                firstVolumeDownTime = currentTime;
            }
            return true; // Consume event
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getRepeatCount() == 0) {
                volumeUpPressStartTime = System.currentTimeMillis();
                isVolumeUpLongPressed = false;
                getWindow().getDecorView().postDelayed(volumeUpLongClickRunnable, 2000);
            }
            return true; // Consume event
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            getWindow().getDecorView().removeCallbacks(volumeUpLongClickRunnable);
            return true; // Consume event
        }
        return super.onKeyUp(keyCode, event);
    }



    private void triggerPanicLock() {
        runOnUiThread(() -> {
            isUnlockedLocal = false;
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL("http://127.0.0.1:5000/lock");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.getResponseCode();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            onResume();
            Toast.makeText(MainActivity.this, "Vault Locked!", Toast.LENGTH_SHORT).show();
        });
    }

    private void showFakeANRDialog() {
        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Uygulama Yanıt Vermiyor")
                .setMessage("PhotoVault uygulaması yanıt vermiyor. Kapatmak istiyor musunuz?")
                .setPositiveButton("Kapat", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setNegativeButton("Bekle", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
        });
    }

    private void checkAndPromptBiometrics() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(MainActivity.this, "Biometrics fallback to PIN", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        android.content.SharedPreferences prefs = getSharedPreferences("pv_secure_prefs", MODE_PRIVATE);
                        String savedPin = prefs.getString("saved_pin", null);
                        if (savedPin != null) {
                            new Thread(() -> {
                                boolean success = mainModule.callAttr("verify_pin", savedPin).toBoolean();
                                if (success) {
                                    runOnUiThread(() -> openVault());
                                }
                            }).start();
                        } else {
                            Toast.makeText(MainActivity.this, "Biometrics verified! Enter PIN", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Authenticate using your fingerprint or face")
                .setNegativeButtonText("Use PIN")
                .build();

            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void startGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    private void scheduleCloudBackup() {
        android.app.job.JobScheduler jobScheduler = (android.app.job.JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        android.content.ComponentName componentName = new android.content.ComponentName(this, BackupJobService.class);
        android.app.job.JobInfo jobInfo = new android.app.job.JobInfo.Builder(1002, componentName)
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_UNMETERED) // WiFi only
                .setPeriodic(24 * 60 * 60 * 1000) // Daily
                .setPersisted(true)
                .build();
        jobScheduler.schedule(jobInfo);
    }

    private void toggleSecretVideoRecording() {
        if (isRecordingVideo) {
            stopSecretVideoRecording();
        } else {
            startSecretVideoRecording();
        }
    }

    private void startSecretVideoRecording() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }
        try {
            videoCamera = android.hardware.Camera.open(0);
            videoCamera.unlock();

            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setCamera(videoCamera);
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(android.media.MediaRecorder.VideoSource.CAMERA);

            mediaRecorder.setProfile(android.media.CamcorderProfile.get(android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, android.media.CamcorderProfile.QUALITY_LOW));

            tempVideoFile = new java.io.File(getCacheDir(), "temp_secret_video.mp4");
            mediaRecorder.setOutputFile(tempVideoFile.getAbsolutePath());

            cameraTextureView = findViewById(R.id.camera_texture_view);
            if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                mediaRecorder.setPreviewDisplay(new android.view.Surface(cameraTextureView.getSurfaceTexture()));
            }

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecordingVideo = true;
            runOnUiThread(() -> Toast.makeText(this, "Secret video recording started...", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
            if (videoCamera != null) {
                videoCamera.release();
                videoCamera = null;
            }
            runOnUiThread(() -> Toast.makeText(this, "Failed to start secret recording", Toast.LENGTH_SHORT).show());
        }
    }

    private void stopSecretVideoRecording() {
        if (!isRecordingVideo || mediaRecorder == null) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;

            if (videoCamera != null) {
                videoCamera.lock();
                videoCamera.release();
                videoCamera = null;
            }

            isRecordingVideo = false;
            runOnUiThread(() -> Toast.makeText(this, "Secret video recording saved!", Toast.LENGTH_SHORT).show());

            if (tempVideoFile != null && tempVideoFile.exists()) {
                new Thread(() -> {
                    try {
                        java.io.FileInputStream fis = new java.io.FileInputStream(tempVideoFile);
                        byte[] bytes = getBytes(fis);
                        fis.close();
                        tempVideoFile.delete();

                        String filename = "vid_" + System.currentTimeMillis() + ".mp4";
                        mainModule.callAttr("add_image", filename, bytes);
                        runOnUiThread(() -> webView.post(() -> webView.reload()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkOTAUpdate() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://api.github.com/repos/mtugr/PhotoVault/releases/latest");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    byte[] bytes = getBytes(is);
                    String jsonStr = new String(bytes, "UTF-8");
                    org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                    String tagName = json.getString("tag_name");

                    double latestVersion = Double.parseDouble(tagName.replaceAll("[^0-9.]", ""));
                    double currentVersion = Double.parseDouble(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);

                    if (latestVersion > currentVersion) {
                        org.json.JSONArray assets = json.getJSONArray("assets");
                        if (assets.length() > 0) {
                            String downloadUrl = assets.getJSONObject(0).getString("browser_download_url");
                            runOnUiThread(() -> showUpdateDialog(downloadUrl, tagName));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showUpdateDialog(String downloadUrl, String tagName) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Update Available (" + tagName + ")")
                .setMessage("Do you want to download and install the latest PhotoVault release?")
                .setPositiveButton("Update", (dialog, which) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstallApk(String downloadUrl) {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Downloading Update");
        progressDialog.setMessage("Please wait...");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(downloadUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();
                InputStream input = new java.io.BufferedInputStream(url.openStream(), 8192);

                java.io.File apkFile = new java.io.File(getExternalFilesDir(null), "update.apk");
                java.io.OutputStream output = new java.io.FileOutputStream(apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> progressDialog.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Download Failed", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void installApk(java.io.File apkFile) {
        Uri apkUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
