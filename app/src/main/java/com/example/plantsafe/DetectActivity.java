package com.example.plantsafe;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.os.LocaleListCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.example.plantsafe.databinding.ActivityDetectBinding;
import com.example.plantsafe.network.WeatherApiService;
import com.example.plantsafe.network.gemini.GeminiApiService;
import com.example.plantsafe.network.gemini.GeminiRequest;
import com.example.plantsafe.network.gemini.GeminiResponse;
import com.example.plantsafe.network.model.AnalysisResponse;
import com.example.plantsafe.network.model.WeatherResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.material.navigation.NavigationBarView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.example.plantsafe.BuildConfig;


public class DetectActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.example.plantsafe.EXTRA_IMAGE_URI";
    private static final String TAG = "DetectActivity";
    private static final String GEMINI_MODEL_NAME = "gemini-1.5-flash";

    private ActivityDetectBinding binding;
    private Uri imageUri;

    private List<AnalysisResponse.LeafResult> lastLeafResults = null;
    private Bitmap annotatedBitmap = null;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private WeatherApiService plantAnalysisService;
    private GeminiApiService geminiService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbarDetect;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_detect);
        }

        setupRetrofit();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_IMAGE_URI)) {
            String uriString = intent.getStringExtra(EXTRA_IMAGE_URI);
            if (uriString != null) {
                imageUri = Uri.parse(uriString);
                Log.d(TAG, "Received image URI: " + imageUri.toString());
                binding.imageViewDetect.setImageURI(imageUri);
            } else { handleError("Failed to load image URI."); }
        } else { handleError("No image URI provided."); }

        setupButtonClickListeners();
    }

    private void setupButtonClickListeners() {
        binding.buttonAnalyzeDetect.setOnClickListener(v -> {
            if (imageUri != null) {
                Log.d(TAG, "Analyze button clicked.");
                startAnalysis(imageUri);
            } else {
                Log.e(TAG, "Analyze button clicked but imageUri is null.");
                Toast.makeText(this, "Image URI is missing", Toast.LENGTH_SHORT).show();
            }
        });

        binding.buttonAiExplanation.setOnClickListener(v -> {
            if (lastLeafResults != null && !lastLeafResults.isEmpty()) {
                Log.d(TAG, "AI Explanation button clicked.");
                AnalysisResponse.LeafResult firstLeaf = lastLeafResults.get(0);
                fetchAiExplanation("Leaf " + firstLeaf.getLeafIndex(), firstLeaf.getPercentage());
            } else {
                Log.w(TAG, "AI Explanation button clicked but analysis results missing.");
                Toast.makeText(this, "Analysis results not available", Toast.LENGTH_SHORT).show();
            }
        });

        binding.imageViewDetect.setOnClickListener(v -> {
            if (annotatedBitmap != null) {
                Log.d(TAG, "Image view clicked, showing fullscreen annotated dialog.");
                showFullscreenImageDialog(annotatedBitmap);
            } else if (imageUri != null) {
                Log.d(TAG, "Image view clicked, loading original image fullscreen.");
                loadOriginalBitmapAndShowDialog(imageUri);
            } else {
                Log.w(TAG, "Image view clicked, but no image available.");
                Toast.makeText(this, "Image not available yet.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void handleError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "handleError: " + message);
        if (binding != null) {
            binding.buttonAnalyzeDetect.setEnabled(false);
            binding.buttonAiExplanation.setVisibility(View.GONE);
            if (binding.loadingAnimationView != null) {
                binding.loadingAnimationView.cancelAnimation();
                binding.loadingAnimationView.setVisibility(View.GONE);
            }
        }
    }
    private void handleErrorOnUiThread(String message) {
        mainThreadHandler.post(() -> handleError(message));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRetrofit() {
        String backendBaseUrl = "https://abcomputer-plantsafe-backend.hf.space/analyze/";
        String geminiBaseUrl = "https://generativelanguage.googleapis.com/";

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.d("OkHttp", message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit plantSafeRetrofit = new Retrofit.Builder()
                .baseUrl(backendBaseUrl).client(client).addConverterFactory(GsonConverterFactory.create()).build();
        plantAnalysisService = plantSafeRetrofit.create(WeatherApiService.class);
        Log.i(TAG, "Retrofit setup complete for PlantSafe URL: " + backendBaseUrl);

        Retrofit geminiRetrofit = new Retrofit.Builder()
                .baseUrl(geminiBaseUrl).client(client).addConverterFactory(GsonConverterFactory.create()).build();
        geminiService = geminiRetrofit.create(GeminiApiService.class);
        Log.i(TAG, "Retrofit setup complete for Gemini URL: " + geminiBaseUrl);
    }


    private void startAnalysis(Uri imageUriToAnalyze) {
        Log.i(TAG, "Starting analysis for URI: " + imageUriToAnalyze.toString());
        lastLeafResults = null; annotatedBitmap = null;
        binding.resultsCardDetect.setVisibility(View.GONE);
        binding.textViewLeafResultsList.setVisibility(View.GONE);
        binding.buttonAiExplanation.setVisibility(View.GONE);
        binding.loadingAnimationView.setVisibility(View.VISIBLE);
        binding.loadingAnimationView.playAnimation();
        binding.buttonAnalyzeDetect.setEnabled(false);

        backgroundExecutor.execute(() -> {
            Log.d(TAG, "Analysis background task started.");
            MultipartBody.Part imagePart = createImagePart(this, imageUriToAnalyze);
            if (imagePart == null) { handleErrorOnUiThread("Error preparing image"); return; }
            if (plantAnalysisService == null) { handleErrorOnUiThread("Analysis service error"); return; }

            Log.d(TAG, "Making network call to analyzeLeafImage...");
            Call<AnalysisResponse> call = plantAnalysisService.analyzeLeafImage(imagePart);
            try {
                Response<AnalysisResponse> response = call.execute();
                Log.d(TAG, "Network call executed. Response code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    AnalysisResponse analysisResponse = response.body();
                    Log.i(TAG, "Analysis successful via API. Processing response data...");

                    Bitmap decodedAnnotatedBitmap = null;
                    String base64Image = analysisResponse.getAnnotatedImageBase64();
                    if (base64Image != null && !base64Image.isEmpty()) {
                        Log.d(TAG, "Attempting to decode Base64 image in background...");
                        try {
                            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                            decodedAnnotatedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            if (decodedAnnotatedBitmap == null) Log.e(TAG, "BitmapFactory failed to decode byte array.");
                            else Log.i(TAG, "Annotated image successfully decoded in background.");
                        } catch (IllegalArgumentException e) { Log.e(TAG, "Base64 decoding failed in background", e);
                        } catch (OutOfMemoryError oom) {
                            Log.e(TAG, "OutOfMemoryError decoding Base64 image", oom);
                            decodedAnnotatedBitmap = null;
                            mainThreadHandler.post(() -> Toast.makeText(DetectActivity.this, "Annotated image too large", Toast.LENGTH_LONG).show());
                        }
                    } else { Log.w(TAG, "Annotated image was null or empty in response."); }

                    final Bitmap finalBitmapToShow = decodedAnnotatedBitmap;
                    final List<AnalysisResponse.LeafResult> finalLeafResults = analysisResponse.getLeafResults();

                    mainThreadHandler.post(() -> {
                        Log.d(TAG, "Updating UI on main thread after background processing.");
                        resetUiAfterAnalysisAttempt();
                        displayProcessedResults(finalBitmapToShow, finalLeafResults);
                    });

                } else {
                    final Response<AnalysisResponse> finalResponse = response;
                    final String errorMsg = parseErrorResponse(finalResponse);
                    Log.e(TAG, "Analysis API Error: " + errorMsg);
                    mainThreadHandler.post(() -> {
                        resetUiAfterAnalysisAttempt();
                        Toast.makeText(DetectActivity.this, "Analysis failed (Code: " + finalResponse.code() + ")", Toast.LENGTH_LONG).show();
                        if(imageUri != null && binding != null) binding.imageViewDetect.setImageURI(imageUri);
                    });
                }

            } catch (IOException e) {
                Log.e(TAG, "Network IOException during analysis: " + e.getMessage(), e);
                handleErrorOnUiThread("Network Error: " + e.getMessage());
                mainThreadHandler.post(this::resetUiAfterAnalysisAttempt);
                mainThreadHandler.post(() -> {if(imageUri != null && binding != null) binding.imageViewDetect.setImageURI(imageUri);});
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during analysis execution: " + e.getMessage(), e);
                handleErrorOnUiThread("Unexpected Error: " + e.getMessage());
                mainThreadHandler.post(this::resetUiAfterAnalysisAttempt);
                mainThreadHandler.post(() -> {if(imageUri != null && binding != null) binding.imageViewDetect.setImageURI(imageUri);});
            }
        });
    }

    private void resetUiAfterAnalysisAttempt() {
        Log.d(TAG, "Resetting UI after analysis attempt.");
        if (binding == null) return;
        binding.loadingAnimationView.cancelAnimation();
        binding.loadingAnimationView.setVisibility(View.GONE);
        binding.buttonAnalyzeDetect.setEnabled(true);
    }

    private void displayProcessedResults(Bitmap bitmapToShow, List<AnalysisResponse.LeafResult> results) {
        Log.d(TAG, "Inside displayProcessedResults.");
        if (binding == null) { Log.e(TAG, "Binding is null in displayProcessedResults."); return; }

        if (bitmapToShow != null) {
            binding.imageViewDetect.setImageBitmap(bitmapToShow);
            annotatedBitmap = bitmapToShow;
            Log.d(TAG,"Setting decoded annotated bitmap.");
        } else {
            annotatedBitmap = null;
            Log.w(TAG, "Decoded bitmap was null. Showing original image.");
            Toast.makeText(this, "Could not display annotated image.", Toast.LENGTH_SHORT).show();
            if(imageUri != null) binding.imageViewDetect.setImageURI(imageUri);
        }

        lastLeafResults = results;
        if (lastLeafResults != null && !lastLeafResults.isEmpty()) {
            Log.d(TAG, "Processing " + lastLeafResults.size() + " leaf results for colored display.");
            SpannableStringBuilder spannableResults = new SpannableStringBuilder();

            for (AnalysisResponse.LeafResult leaf : lastLeafResults) {
                String line = String.format(Locale.US, "Leaf %d: %.1f%%\n",
                        leaf.getLeafIndex(), leaf.getPercentage());
                int start = spannableResults.length();
                spannableResults.append(line);
                int end = spannableResults.length();
                int severityColor = getSeverityColor(leaf.getPercentage());
                spannableResults.setSpan(new ForegroundColorSpan(severityColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            binding.textViewLeafResultsList.setText(spannableResults, TextView.BufferType.SPANNABLE);
            binding.textViewLeafResultsList.setVisibility(View.VISIBLE);
            binding.resultsCardDetect.setVisibility(View.VISIBLE);
            binding.buttonAiExplanation.setVisibility(View.VISIBLE);
            Log.i(TAG, "Colored leaf results displayed.");

        } else {
            Log.w(TAG, "No leaf results found in response.");
            binding.textViewLeafResultsList.setText("No leaves detected or analyzed.");
            binding.textViewLeafResultsList.setVisibility(View.VISIBLE);
            binding.resultsCardDetect.setVisibility(View.VISIBLE);
            binding.buttonAiExplanation.setVisibility(View.GONE);
        }
    }

    private MultipartBody.Part createImagePart(Context context, Uri imageUri) {
        InputStream inputStream = null;
        ByteArrayOutputStream byteStream = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            if (contentResolver == null) { Log.e(TAG, "ContentResolver null"); return null; }
            String mimeType = contentResolver.getType(imageUri);
            if (mimeType == null || mimeType.isEmpty()) { mimeType = "image/jpeg"; }
            MediaType mediaType = MediaType.parse(mimeType);
            if (mediaType == null) mediaType = MediaType.parse("image/jpeg");
            inputStream = contentResolver.openInputStream(imageUri);
            if (inputStream == null) throw new FileNotFoundException("Stream null for URI: " + imageUri);
            byteStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) { byteStream.write(buffer, 0, bytesRead); }
            byte[] fileBytes = byteStream.toByteArray();
            if (fileBytes == null || fileBytes.length == 0) { Log.e(TAG, "Read 0 bytes"); return null; }
            RequestBody requestFile = RequestBody.create(mediaType, fileBytes);
            String filename = "upload.jpg";
            Log.d(TAG, "Creating image part. Filename: " + filename + ", MIME Type: " + mimeType);
            return MultipartBody.Part.createFormData("image", filename, requestFile);
        } catch (Exception e) { Log.e(TAG, "Error in createImagePart: "+e.getMessage(), e); return null;
        } finally { try { if (inputStream != null) inputStream.close(); if (byteStream != null) byteStream.close(); } catch (IOException e) { Log.e(TAG,"Stream close error"); } }
    }


    private void fetchAiExplanation(String diseaseContext, float percentageContext) {
        Log.d(TAG, "Fetching GEMINI explanation for: " + diseaseContext + " (" + percentageContext + "%)");
        if (binding == null) return;
        binding.buttonAiExplanation.setEnabled(false);
        binding.buttonAiExplanation.setText(R.string.ai_explanation_loading);

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API key missing/empty in BuildConfig.");
            mainThreadHandler.post(() -> {
                Toast.makeText(this, "AI Key Configuration Error", Toast.LENGTH_LONG).show();
                if(binding != null) {
                    binding.buttonAiExplanation.setEnabled(true);
                    binding.buttonAiExplanation.setText(R.string.ai_explanation_button);
                }
            });
            return;
        }
        Log.d(TAG, "Using Gemini API Key: " + apiKey.substring(0, 4) + "...");


        String prompt = createLlmPrompt(diseaseContext, percentageContext);
        GeminiRequest.Part part = new GeminiRequest.Part(prompt);
        GeminiRequest.Content content = new GeminiRequest.Content(Collections.singletonList(part));
        GeminiRequest requestBody = new GeminiRequest(Collections.singletonList(content));

        if (geminiService == null) {
            Log.e(TAG, "Gemini Service is null!");
            handleErrorOnUiThread("AI service configuration error.");
            mainThreadHandler.post(() -> {
                if(binding != null) {
                    binding.buttonAiExplanation.setEnabled(true);
                    binding.buttonAiExplanation.setText(R.string.ai_explanation_button);
                }
            });
            return;
        }

        backgroundExecutor.execute(() -> {
            Log.d(TAG, "Making Gemini API call...");
            Call<GeminiResponse> call = geminiService.generateContent(GEMINI_MODEL_NAME, apiKey, requestBody);
            try {
                Response<GeminiResponse> response = call.execute();
                Log.d(TAG, "Gemini call executed. Response code: " + response.code());

                final Response<GeminiResponse> finalResponse = response;
                mainThreadHandler.post(() -> {
                    if (binding != null) {
                        binding.buttonAiExplanation.setEnabled(true);
                        binding.buttonAiExplanation.setText(R.string.ai_explanation_button);
                    }

                    if (finalResponse.isSuccessful() && finalResponse.body() != null) {
                        GeminiResponse geminiResponse = finalResponse.body();
                        String explanation = extractTextFromGeminiResponse(geminiResponse);

                        if (explanation != null && !explanation.trim().isEmpty()) {
                            Log.i(TAG, "Gemini explanation received successfully.");
                            displayExplanationDialog(explanation.trim());
                        } else {
                            Log.w(TAG, "Gemini response content/text was null or empty.");
                            handleErrorOnUiThread("AI failed to generate explanation.");
                        }
                    } else {
                        String errorMsg = parseErrorResponse(finalResponse);
                        Log.e(TAG, "Gemini API Error: " + errorMsg);
                        handleErrorOnUiThread("AI explanation failed (Code: " + finalResponse.code() + ")");
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Network IOException during Gemini call: " + e.getMessage(), e);
                handleErrorOnUiThread("Network Error fetching explanation.");
                mainThreadHandler.post(() -> {
                    if(binding != null) {
                        binding.buttonAiExplanation.setEnabled(true);
                        binding.buttonAiExplanation.setText(R.string.ai_explanation_button);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during Gemini call: " + e.getMessage(), e);
                handleErrorOnUiThread("Unexpected error fetching explanation.");
                mainThreadHandler.post(() -> {
                    if(binding != null) {
                        binding.buttonAiExplanation.setEnabled(true);
                        binding.buttonAiExplanation.setText(R.string.ai_explanation_button);
                    }
                });
            }
        });
    }

    private String extractTextFromGeminiResponse(GeminiResponse response) {
        try {
            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty() &&
                    response.getCandidates().get(0) != null && response.getCandidates().get(0).getContent() != null &&
                    response.getCandidates().get(0).getContent().getParts() != null && !response.getCandidates().get(0).getContent().getParts().isEmpty() &&
                    response.getCandidates().get(0).getContent().getParts().get(0) != null) {
                return response.getCandidates().get(0).getContent().getParts().get(0).getText();
            }
        } catch (IndexOutOfBoundsException | NullPointerException e) { Log.e(TAG, "Error parsing Gemini response structure", e); }
        Log.w(TAG, "Could not extract text from Gemini response.");
        return null;
    }

    private String createLlmPrompt(String disease, float percentage) {
        return String.format(Locale.US,
                "Explain the condition for blueberry %s with approximately %.1f%% affected area shown in an image. " +
                        "Provide a simple, encouraging explanation for a home gardener, including potential impact and general advice if applicable. " +
                        "Keep the response concise, maximum 3-4 sentences.",
                disease,
                percentage);
    }

    private void displayExplanationDialog(String explanation) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_explanation_title)
                .setMessage(explanation)
                .setPositiveButton(R.string.dialog_ok, null)
                .setIcon(R.drawable.ic_ai)
                .show();
    }


    private void showFullscreenImageDialog(Bitmap bitmapToShow) {
        if (bitmapToShow == null || isFinishing() || isDestroyed()) { Log.w(TAG,"Cannot show dialog"); return; }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fullscreen_image, null);
        dialog.setContentView(dialogView);
        ImageView fullscreenImageView = dialogView.findViewById(R.id.fullscreenImageView);
        fullscreenImageView.setImageBitmap(bitmapToShow);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        fullscreenImageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    private void loadOriginalBitmapAndShowDialog(Uri uri) {
        if (uri == null || isFinishing() || isDestroyed()) return;
        backgroundExecutor.execute(() -> {
            Bitmap originalBitmap = null; InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) originalBitmap = BitmapFactory.decodeStream(inputStream);
            } catch (Exception e) { Log.e(TAG, "Error loading original bitmap", e);
            } finally { if (inputStream != null) try { inputStream.close(); } catch (IOException ignored) {} }
            final Bitmap finalBitmap = originalBitmap;
            if (finalBitmap != null && !isFinishing() && !isDestroyed()) {
                mainThreadHandler.post(() -> showFullscreenImageDialog(finalBitmap));
            } else if (!isFinishing() && !isDestroyed()){
                mainThreadHandler.post(() -> Toast.makeText(DetectActivity.this, "Could not load original image", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private <T> String parseErrorResponse(Response<T> response) {
        String errorMsg = "API Request Failed";
        String errorBodyStr = "(No error body)";
        if (response.errorBody() != null) {
            try { errorBodyStr = response.errorBody().string(); }
            catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
        }
        errorMsg += " (Code: " + response.code() + ")" + (!errorBodyStr.isEmpty() ? ": " + errorBodyStr : "");
        return errorMsg;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Shutting down background executor.");
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        if (annotatedBitmap != null && !annotatedBitmap.isRecycled()) {
            annotatedBitmap = null;
        }
    }

    @ColorInt
    private int getSeverityColor(float percentage) {
        float p = Math.max(0f, Math.min(100f, percentage));

        int lowColor = ContextCompat.getColor(this, R.color.severity_low);
        int medColor = ContextCompat.getColor(this, R.color.severity_medium);
        int highColor = ContextCompat.getColor(this, R.color.severity_high);

        if (p <= 50f) {
            float fraction = p / 50f;
            return ColorUtils.blendARGB(lowColor, medColor, fraction);
        } else {
            float fraction = (p - 50f) / 50f;
            return ColorUtils.blendARGB(medColor, highColor, fraction);
        }
    }
}