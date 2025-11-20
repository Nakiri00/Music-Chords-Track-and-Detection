package com.example.musicchords;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private HomeViewModel homeViewModel;
    private Button convertButton;
    private EditText editText;
    private TextView resultTextView;
    private ProgressBar loadingIndicator;
    private Button buttonDownload;

    private String downloadLink = "";
    private String audioTitle = "";


    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        convertButton = view.findViewById(R.id.button_convert);
        resultTextView = view.findViewById(R.id.textview_result);
        loadingIndicator = view.findViewById(R.id.progress_bar);
        editText = view.findViewById(R.id.url_input);
        buttonDownload = view.findViewById(R.id.button_download);

        // Setup listener untuk tombol
        buttonConvert.setOnClickListener(v -> {
            String youtubeUrl = editText.getText().toString().trim();
            if (youtubeUrl.isEmpty()) {
                Toast.makeText(getContext(), "Silakan masukkan URL", Toast.LENGTH_SHORT).show();
                return;
            }
            // Reset UI sebelum request baru
            resultTextView.setText("Memproses...");
            buttonDownload.setVisibility(View.GONE);
            downloadLink = "";
            audioTitle = "";

            homeViewModel.convertYoutubeUrl(youtubeUrl);
        });

        buttonDownload.setOnClickListener(v -> {
            if (!downloadLink.isEmpty() && !audioTitle.isEmpty()) {
                Toast.makeText(getContext(), "Mulai mengunduh: " + audioTitle, Toast.LENGTH_SHORT).show();
                downloadAudio(downloadLink, audioTitle);
            } else {
                Toast.makeText(getContext(), "Link download tidak valid.", Toast.LENGTH_SHORT).show();
            }
        });

        // Panggil fungsi untuk setup observer
        setupObservers();
    }

    private void setupObservers() {
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(),isLoading ->{
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            fetchButton.setEnabled(!isLoading);
        });

        homeViewModel.getApiResponse().observe(getViewLifecycleOwner(),response -> {
            if (response == null) return;
            Log.d("HomeFragment", "API Response : "+ response);
            try{
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(response, JsonObject.class);
                String status = jsonObject.has("status")? jsonObject.get("status").getAsString() : "error";
                if("ok".equalsIgnoreCase(status) && jsonObject.has("link")){

                    this.downloadLink = jsonObject.get("link").getAsString();
                    this.audioTitle = jsonObject.has("title") ? jsonObject.get("title").getAsString() : "audio";
                    resultTextView.setText("Audio Siap : "+ this.audioTitle);
                    buttonDownload.setVisibility(View.VISIBLE);
                }else if ("processing".equalsIgnoreCase(status)){
                    resultTextView.setText("server Sedang Memproses Video. Mohon Tunggu");
                    buttonDownload.setVisibility(View.GONE);
                }else{
                    String message = jsonObject.has("mess") ? jsonObject.get("mess").getAsString() : "Format Respons Tidak Dikenal";
                    resultTextView.setText("Gagal : "+ message);
                    buttonDownload.setVisibility(View.GONE);
                }
            }catch (JsonSyntaxException e){
                Log.e("HomeFragment", "JSON Parsing Error ",e);
                resultTextView.setText("Terjadi kesalahan saat memproses respons server.");
                buttonDownload.setVisibility(View.GONE);
            }
        });
        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(),error ->{
            if(error != null && !error.isEmpty()){
                Toast.makeText(getContext(),error,Toast.LENGTH_SHORT).show();
                resultTextView.setText("Error : " + error);
                Log.e("HomeFragment","API Error : " + error);
            }
        });
    }

    /**
     * Menggunakan DownloadManager Android untuk mengunduh file audio.
     * @param url URL download file MP3.
     * @param title Judul file yang akan disimpan.
     */
    private void downloadAudio(String url, String title) {
        // Buat nama file yang valid
        String fileName = title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3";

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(title);
        request.setDescription("Mengunduh Audio MP3");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        // Simpan file di folder Download publik
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = (DownloadManager) requireActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
        } else {
            Toast.makeText(getContext(), "Download manager tidak tersedia.", Toast.LENGTH_SHORT).show();
        }
    }
}