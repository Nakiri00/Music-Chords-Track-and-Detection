package com.example.musicchords;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
public class HomeViewModel extends ViewModel{
    private final MutableLiveData<String> apiResponse = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public MutableLiveData<String> getApiResponse() {
        return apiResponse;
    }

    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }


    public void convertYoutubeUrl(String url){
        String videoId = youtubeUrl(url);
        if(videoId == null || videoId.isEmpty()){
            errorMessage.postValue("URL Tidak Valid");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);
        apiResponse.postValue(null);

        OkHttpClient client = new OkHttpClient();
//        String apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=" + videoId;

        Request request = new Request.Builder()
                .url("https://youtube-mp36.p.rapidapi.com/dl?id=" + videoId)
                .get()
                .addHeader("x-rapidapi-key", "a2a9510207msheeca3f728ede5afp1b5184jsn7d296cce0748")
                .addHeader("x-rapidapi-host", "youtube-mp36.p.rapidapi.com")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("HomeViewModel", "API Call Failed", e);
                errorMessage.postValue("Gagal Terhubung ke Server : " + e.getMessage());
                isLoading.postValue(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.isSuccessful()){
                    String responseBody = response.body().string();
                    apiResponse.postValue(responseBody);
                }else{
                    Log.e("HomeViewModel", "API Error : " + response.code() + " " + response.message());
                    errorMessage.postValue("Error dari Server : " + response.code());
                }
                isLoading.postValue(false);
            }
        });
    }

    private String youtubeUrl(String url){
        if (url == null || url.trim().isEmpty()){
            return "";
        }

        String pattern = "((http|https)://)?(?:[0-9A-Z-]+\\.)?(?:youtu\\.be/|youtube(?:-nocookie)?\\.com\\S*[^\\w\\s-])([\\w-]{11})(?=[^\\w-]|$)(?![?=&+%\\w.-]*(?:['\"][^<>]*>|</a>))[?=&+%\\w.-]*";
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);

        if (m.find()) {
            return m.group(3);
        }

        return "";
    }

    public void clearErrorMessage() {
        errorMessage.setValue(null);
    }

//    public void fetchDataFromApi(){
//        isLoading.setValue(true);
//        OkHttpClient client = new OkHttpClient();
//        Request request = new Request.Builder()
//                .url("https://youtube-mp36.p.rapidapi.com/dl?id=UxxajLWwzqY")
//                .get()
//                .addHeader("x-rapidapi-key", "a2a9510207msheeca3f728ede5afp1b5184jsn7d296cce0748")
//                .addHeader("x-rapidapi-host", "youtube-mp36.p.rapidapi.com")
//                .build();
//
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                errorMessage.postValue("Gagal Terhubung ke Server : "+e.getMessage());
//                isLoading.postValue(false);
//            }
//
//            @Override
//            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
//                if(response.isSuccessful()){
//                    String responseBody = response.body().string();
//                    apiResponse.postValue(responseBody);
//                }else{
//                    errorMessage.postValue("Error : "+response.code()+ " "+ response.message());
//                }
//                isLoading.postValue(false);
//            }
//        });
//    }
}
