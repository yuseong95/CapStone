package com.quicinc.chatapp;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenieManager {

    private static GenieWrapper genieWrapper = null;
    private static boolean initialized = false;
    private static final String TAG = "GenieManager";

    public static void initialize(Context context, String modelName, String htpExtensionsDir) {
        try {
            if (initialized) return;

            String nativeLibPath = context.getApplicationInfo().nativeLibraryDir;
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true);
            Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true);


            File externalCache = context.getExternalCacheDir();
            if (externalCache == null) {
                Log.e(TAG, "External cache dir is null");
                return;
            }
            String externalCacheDir = context.getExternalCacheDir().getAbsolutePath().toString();
            String modelDir = Paths.get(externalCacheDir, "models", modelName).toString();
            genieWrapper = new GenieWrapper(modelDir, htpExtensionsDir);
            initialized = true;

            Log.d(TAG, "✅ GenieWrapper initialized with " + modelDir);
        } catch (Exception e) {
            Log.e(TAG, "❌ Initialization failed: " + e.getMessage());
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void processPrompt(String prompt, StringCallback callback) {
        if (!initialized || genieWrapper == null) {
            Log.e(TAG, "❌ GenieWrapper not initialized!");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            genieWrapper.getResponseForPrompt(prompt, new StringCallback() {
                @Override
                public void onNewString(String partial) {
                    fullResponse.append(partial);
                }
            });

            // 응답이 끝났다고 가정하고 한 번만 콜백 호출
            // 만약 GenieWrapper가 blocking 호출이라면 이 위치로 이동 가능
            callback.onNewString(fullResponse.toString());
        });
    }

    /*public static void processPrompt(String prompt, StringCallback callback) {
        if (!initialized || genieWrapper == null) {
            Log.e(TAG, "❌ GenieWrapper not initialized!");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> genieWrapper.getResponseForPrompt(prompt, callback));
    }*/
}