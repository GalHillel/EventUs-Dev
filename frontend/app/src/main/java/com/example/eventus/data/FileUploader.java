package com.example.eventus.data;

import androidx.annotation.NonNull;

import com.example.eventus.data.model.ServerResponse;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileUploader {

    public static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private final OkHttpClient client = new OkHttpClient();
    private ServerResponse lastResponse;

    public FileUploader() {
    }

    public ServerResponse getLastResponse() {
        return lastResponse;
    }

    public interface UploadCallback {
        void onSuccess(ServerResponse response);

        void onFailure(ServerResponse errorMessage);
    }

    public void uploadFile(String url, byte[] file, final UploadCallback callback) {


        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("icon", "img.png", RequestBody.create(MEDIA_TYPE_PNG, file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Handle failure
                callback.onFailure(new ServerResponse(111, e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Handle success
                assert response.body() != null;
                callback.onSuccess(new ServerResponse(response.code(), response.body().string()));
            }
        });


    }


}