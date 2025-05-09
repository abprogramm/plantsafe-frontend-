package com.example.plantsafe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import com.example.plantsafe.databinding.ActivityMainBinding;
import com.example.plantsafe.databinding.WeatherWidgetBinding;

import com.example.plantsafe.network.WeatherApiService;
import com.example.plantsafe.network.model.WeatherResponse;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PlantSafePrefs";
    private static final String PREF_LANGUAGE = "selected_language";
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private WeatherWidgetBinding weatherBinding;
    private Uri latestTmpUri = null;

    private ActivityResultLauncher<String> selectImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private FusedLocationProviderClient fusedLocationClient;

    private WeatherApiService weatherApiService;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();


    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(updateBaseContextLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeActivityResultLaunchers();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            weatherBinding = WeatherWidgetBinding.bind(binding.weatherWidgetLayout.getRoot());
        } catch (Exception e) {
            Log.e(TAG, "Error binding weather widget. Check include tag ID in activity_main.xml", e);
            Toast.makeText(this, "Weather widget could not be loaded", Toast.LENGTH_LONG).show();
        }


        Toolbar toolbar = binding.toolbarMain;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        setupButtonClickListeners();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupRetrofit();

        checkAndFetchWeather();
    }

    private Context updateBaseContextLocale(Context context) {
        String language = loadLanguagePreference(context);
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.os.LocaleList localeList = new android.os.LocaleList(locale);
            android.os.LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            config.locale = locale;
        }
        return context.createConfigurationContext(config);
    }
    private void setLocale(String languageCode) {
        String currentLanguage = loadLanguagePreference(this);
        if (!currentLanguage.equals(languageCode)) {
            saveLanguagePreference(languageCode);
            recreate();
        }
    }
    private void saveLanguagePreference(String languageCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_LANGUAGE, languageCode);
        editor.apply();
    }
    private String loadLanguagePreference(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_LANGUAGE, "en");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_language) {
            View anchor = findViewById(R.id.action_language);
            if (anchor == null) {
                anchor = binding.toolbarMain;
            }
            showLanguagePopup(anchor);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void showLanguagePopup(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(Menu.NONE, 1, 1, "English");
        popup.getMenu().add(Menu.NONE, 2, 2, "Español");

        popup.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == 1) {
                setLocale("en");
                return true;
            } else if (itemId == 2) {
                setLocale("es");
                return true;
            }
            return false;
        });
        popup.show();
    }


    private void initializeActivityResultLaunchers() {
        selectImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) launchDetectActivity(uri); else Toast.makeText(this, "Selection cancelled", Toast.LENGTH_SHORT).show(); });
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                isSuccess -> {
                    if (isSuccess && latestTmpUri != null) {
                        launchDetectActivity(latestTmpUri);
                    } else {
                        Toast.makeText(MainActivity.this, "Camera capture failed", Toast.LENGTH_SHORT).show();
                        if (latestTmpUri != null) {
                            getContentResolver().delete(latestTmpUri, null, null);
                            latestTmpUri = null;
                        }
                    }
                });
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> { if (isGranted) launchCamera(); else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show(); });

        locationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
            Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

            if (fineLocationGranted != null && fineLocationGranted) {
                fetchLastLocation();
            } else if (coarseLocationGranted != null && coarseLocationGranted) {
                fetchLastLocation();
            } else {
                Log.d(TAG, "Location permission denied");
                showWeatherError("Location permission needed for weather.");
            }
        });
    }

    private void setupButtonClickListeners() {
        binding.uploadImageButton.setOnClickListener(v -> selectImageLauncher.launch("image/*"));
        binding.takePictureButton.setOnClickListener(v -> checkAndRequestCameraPermission());
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_detect || itemId == R.id.nav_home) {
                if (binding.nestedScrollView != null) binding.nestedScrollView.smoothScrollTo(0, 0);
                return true;
            }
            return false;
        });
    }

    private void launchDetectActivity(Uri imageUri) {
        Intent intent = new Intent(MainActivity.this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, imageUri.toString());
        startActivity(intent);
    }

    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    private void launchCamera() {
        Uri imageUri = getTmpFileUri();
        if (imageUri != null) {
            latestTmpUri = imageUri;
            takePictureLauncher.launch(imageUri);
        } else {
            Toast.makeText(this, "Error creating file for camera", Toast.LENGTH_SHORT).show();
        }
    }
    private Uri getTmpFileUri() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File cacheDir = new File(getApplicationContext().getCacheDir(), "images");
            cacheDir.mkdirs();
            File tempFile = File.createTempFile(imageFileName, ".jpg", cacheDir);
            String authority = getApplicationContext().getPackageName() + ".provider";
            return FileProvider.getUriForFile(getApplicationContext(), authority, tempFile);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error creating temp file for camera", e);
            Toast.makeText(this, "Error creating temporary file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    private void setupRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        weatherApiService = retrofit.create(WeatherApiService.class);
    }

    private void checkAndFetchWeather() {
        showWeatherLoading();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.d(TAG, "Requesting location permissions...");
            locationPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            Log.d(TAG, "Location permissions granted, fetching location...");
            fetchLastLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation() {
        Log.d(TAG, "Attempting to get current location...");
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        Log.i(TAG, "Location fetched: Lat=" + location.getLatitude() + ", Lon=" + location.getLongitude());
                        fetchWeatherData(location.getLatitude(), location.getLongitude());
                    } else {
                        Log.w(TAG, "FusedLocationProviderClient returned null location.");
                        showWeatherError("Couldn't get location. Is location enabled?");
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "FusedLocationProviderClient failed.", e);
                    showWeatherError("Location unavailable.");
                });
    }

    private void fetchWeatherData(double lat, double lon) {
        if (weatherApiService == null) {
            Log.e(TAG, "WeatherApiService is not initialized!");
            showWeatherError("Service error.");
            return;
        }
        Log.d(TAG, "Fetching weather for Lat: " + lat + ", Lon: " + lon);

        String apiKey = "937861cc904928c80f4c5db84de6083c";
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("null") || apiKey.equals("")) {
            Log.e(TAG, "OpenWeatherMap API key is missing or empty in BuildConfig. Check local.properties and build.gradle.");
            showWeatherError("API Key Missing");
            return;
        }

        Call<WeatherResponse> call = weatherApiService.getCurrentWeather(lat, lon, apiKey, "metric");

        networkExecutor.execute(() -> {
            try {
                Response<WeatherResponse> response = call.execute();
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d(TAG, "Weather API call successful.");
                        updateWeatherUI(response.body());
                    } else {
                        Log.e(TAG, "Weather API call failed - Code: " + response.code() + " Msg: " + response.message());
                        try {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        } catch (Exception ignored) {}
                        showWeatherError("Weather data unavailable (Code: " + response.code() + ")");
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Network I/O error fetching weather data", e);
                runOnUiThread(() -> showWeatherError("Network error."));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during weather fetch", e);
                runOnUiThread(() -> showWeatherError("Unexpected error."));
            }
        });
    }

    private void updateWeatherUI(WeatherResponse weatherData) {
        if (weatherBinding == null) return;

        weatherBinding.progressBarWeather.setVisibility(View.GONE);
        weatherBinding.textViewWeatherError.setVisibility(View.GONE);

        weatherBinding.textViewLocation.setText(weatherData.name);
        weatherBinding.textViewLocation.setVisibility(View.VISIBLE);

        weatherBinding.textViewTemperature.setText(String.format(Locale.getDefault(), "%.0f°C", weatherData.main.temp));
        weatherBinding.textViewTemperature.setVisibility(View.VISIBLE);


        if (weatherData.weather != null && !weatherData.weather.isEmpty()) {
            String description = weatherData.weather.get(0).description;
            description = description.substring(0, 1).toUpperCase() + description.substring(1);
            weatherBinding.textViewWeatherDescription.setText(description);
            weatherBinding.textViewWeatherDescription.setVisibility(View.VISIBLE);

            setWeatherIcon(weatherData.weather.get(0).icon);
            weatherBinding.imageViewWeatherIcon.setVisibility(View.VISIBLE);
        } else {
            weatherBinding.textViewWeatherDescription.setText("");
            weatherBinding.imageViewWeatherIcon.setVisibility(View.INVISIBLE);
            weatherBinding.textViewWeatherDescription.setVisibility(View.INVISIBLE);
        }
    }

    private void setWeatherIcon(String iconCode) {
        if (weatherBinding == null) return;
        int iconResId;
        switch (iconCode) {
            case "01d": iconResId = R.drawable.ic_weather_clear_day; break;
            case "01n": iconResId = R.drawable.ic_weather_clear_night; break;
            case "02d": iconResId = R.drawable.ic_weather_few_clouds_day; break;
            case "02n": iconResId = R.drawable.ic_weather_few_clouds_night; break;
            case "03d":
            case "03n": iconResId = R.drawable.ic_weather_scattered_clouds; break;
            case "04d":
            case "04n": iconResId = R.drawable.ic_weather_broken_clouds; break;
            case "09d":
            case "09n": iconResId = R.drawable.ic_weather_shower_rain; break;
            case "10d": iconResId = R.drawable.ic_weather_rain_day; break;
            case "10n": iconResId = R.drawable.ic_weather_rain_night; break;
            case "11d":
            case "11n": iconResId = R.drawable.ic_weather_thunderstorm; break;
            case "13d":
            case "13n": iconResId = R.drawable.ic_weather_snow; break;
            case "50d":
            case "50n": iconResId = R.drawable.ic_weather_mist; break;
            default: iconResId = R.drawable.ic_weather_few_clouds_day;
        }
        weatherBinding.imageViewWeatherIcon.setImageResource(iconResId);
    }


    private void showWeatherLoading() {
        if (weatherBinding == null) {
            Log.w(TAG, "showWeatherLoading called but weatherBinding is null");
            return;
        }
        weatherBinding.progressBarWeather.setVisibility(View.VISIBLE);
        weatherBinding.textViewWeatherError.setVisibility(View.GONE);
        weatherBinding.imageViewWeatherIcon.setVisibility(View.INVISIBLE);
        weatherBinding.textViewLocation.setVisibility(View.INVISIBLE);
        weatherBinding.textViewTemperature.setVisibility(View.INVISIBLE);
        weatherBinding.textViewWeatherDescription.setVisibility(View.INVISIBLE);
    }

    private void showWeatherError(String message) {
        if (weatherBinding == null) {
            Log.w(TAG, "showWeatherError called but weatherBinding is null");
            return;
        }
        weatherBinding.progressBarWeather.setVisibility(View.GONE);
        weatherBinding.textViewWeatherError.setText(message);
        weatherBinding.textViewWeatherError.setVisibility(View.VISIBLE);
        weatherBinding.imageViewWeatherIcon.setVisibility(View.INVISIBLE);
        weatherBinding.textViewLocation.setVisibility(View.INVISIBLE);
        weatherBinding.textViewTemperature.setVisibility(View.INVISIBLE);
        weatherBinding.textViewWeatherDescription.setVisibility(View.INVISIBLE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdown();
    }
}