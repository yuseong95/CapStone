package com.quicinc.chatapp;

import android.util.Log;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.Translation;

public class TranslatorHelper {

    private static final String TAG = "TranslatorHelper";

    private final Translator translator;

    public TranslatorHelper() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build();

        translator = Translation.getClient(options);
    }

    public void downloadModel(OnDownloadListener listener) {
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(TranslateLanguage.KOREAN).build();
        RemoteModelManager modelManager = RemoteModelManager.getInstance();

        modelManager.isModelDownloaded(model)
                .addOnSuccessListener(isDownloaded -> {
                    if (isDownloaded) {
                        Log.d(TAG, "모델이 이미 존재함 → 스킵");
                        listener.onSuccess();
                    } else {
                        Log.d(TAG, "모델이 없음 → 다운로드 시작");

                        DownloadConditions conditions = new DownloadConditions.Builder()
                                .requireWifi()
                                .build();

                        translator.downloadModelIfNeeded(conditions)
                                .addOnSuccessListener(unused -> {
                                    Log.d(TAG, "모델 다운로드 성공");
                                    listener.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "모델 다운로드 실패: " + e.getMessage());
                                    listener.onFailure(e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "모델 존재 여부 확인 실패: " + e.getMessage());
                    listener.onFailure(e);
                });
    }

    public void translate(String inputText, OnTranslationListener listener) {
        translator.translate(inputText)
                .addOnSuccessListener(listener::onSuccess)
                .addOnFailureListener(listener::onFailure);
    }

    public interface OnDownloadListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface OnTranslationListener {
        void onSuccess(String translatedText);
        void onFailure(Exception e);
    }
}
