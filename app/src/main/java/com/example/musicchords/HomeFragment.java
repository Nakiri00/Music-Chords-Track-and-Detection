package com.example.musicchords;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SpectralPeakProcessor;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


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
    private Button buttonConvert;
    private Button buttonDetectPitch;
    private TextView textViewPitchResult;
    private EditText editText;
    private TextView resultTextView;
    private ProgressBar loadingIndicator;
    private Button buttonDownload;

    private String downloadLink = "";
    private String audioTitle = "";
    private String downloadedFilePath = null;
    private long downloadID;

    private boolean receiverRegistered = false;

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

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            // Pastikan ID download cocok
            if (downloadID == id) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                Cursor cursor = dm.query(query);

                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        String fileName = audioTitle.replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3";
                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                        downloadedFilePath = file.getAbsolutePath();

                        Log.d("Download", "File path fixed: " + downloadedFilePath);
                        Toast.makeText(context, "Unduhan selesai: " + audioTitle, Toast.LENGTH_SHORT).show();

                        // Update UI
                        buttonDetectPitch.setVisibility(View.VISIBLE);
                        buttonDetectPitch.setEnabled(true);
                        resultTextView.setText("Unduhan Selesai. Siap Analisis.");

                    } else {
                        Toast.makeText(context, "Unduhan Gagal / Belum Selesai", Toast.LENGTH_SHORT).show();
                    }
                    cursor.close();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        if (!receiverRegistered) {
            int flags = ContextCompat.RECEIVER_EXPORTED;
            ContextCompat.registerReceiver(
                    requireContext(),
                    onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    flags
            );
            receiverRegistered = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            requireActivity().unregisterReceiver(onDownloadComplete);
            receiverRegistered = false;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        buttonConvert = view.findViewById(R.id.button_convert);
        resultTextView = view.findViewById(R.id.textview_result);
        loadingIndicator = view.findViewById(R.id.progress_bar);
        editText = view.findViewById(R.id.url_input);
        buttonDownload = view.findViewById(R.id.button_download);
        buttonDetectPitch = view.findViewById(R.id.button_detect_pitch);
        textViewPitchResult = view.findViewById(R.id.textview_pitch_result);

        buttonDetectPitch.setEnabled(false);

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
            downloadedFilePath = null;
            buttonDetectPitch.setEnabled(false);
            textViewPitchResult.setText("Hasil analisis akor akan muncul di sini.");

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

        buttonDetectPitch.setOnClickListener(v -> {
            if (downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
                textViewPitchResult.setText("Menganalisis progesi akor...");
                analyzeChords(downloadedFilePath);
            } else {
                Toast.makeText(getContext(), "Unduh file audio terlebih dahulu atau tunggu unduhan selesai.", Toast.LENGTH_SHORT).show();
            }
        });

        // Panggil fungsi untuk setup observer
        setupObservers();
    }

    private void setupObservers() {
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(),isLoading ->{
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            buttonConvert.setEnabled(!isLoading);
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

        String fileName = title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3";

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(title);
        request.setDescription("Mengunduh Audio MP3");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // FIX untuk Android 10+
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
        );

        DownloadManager dm = (DownloadManager) requireActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        downloadID = dm.enqueue(request);

        Log.d("Download", "Download dimulai. ID = " + downloadID);
    }

    private void analyzeChords(String audioPath) {
        // Jalankan proses berat di background thread agar UI tidak macet
        new Thread(() -> {
            try {
                File audioFile = new File(audioPath);

                // Validasi file
                if (!audioFile.exists()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "File tidak ditemukan: " + audioPath, Toast.LENGTH_LONG).show()
                        );
                    }
                    return;
                }

                int bufferSize = 4096 * 2;
                int bufferOverlap = bufferSize / 2;
                int sampleRate = 44100;

                final Map<Double, String> detectedChords = new TreeMap<>();
                final List<String> progression = new ArrayList<>();

                // --- PERBAIKAN DI SINI ---
                // Gunakan fromPipe, bukan fromFile.
                // Di Android, fromFile akan menyebabkan error javax.sound.sampled
                AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(
                        audioPath,
                        sampleRate,
                        bufferSize,
                        bufferOverlap
                );

                final FFT fft = new FFT(bufferSize);
                final float[] amplitudes = new float[bufferSize / 2];

                AudioProcessor chordProcessor = new AudioProcessor() {
                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] audioFloatBuffer = audioEvent.getFloatBuffer();
                        fft.forwardTransform(audioFloatBuffer);
                        fft.modulus(audioFloatBuffer, amplitudes);

                        boolean[] chroma = new boolean[12];
                        for (int i = 0; i < amplitudes.length; i++) {
                            double frequency = fft.binToHz(i, sampleRate);
                            // Filter frekuensi suara manusia/musik umum
                            if (frequency > 80 && frequency < 2000) {
                                int midiNote = (int) Math.round(69 + 12 * Math.log(frequency / 440.0) / Math.log(2.0));
                                if (midiNote > 0) {
                                    int noteIndex = midiNote % 12;
                                    // Ambang batas amplitudo (sesuaikan jika terlalu sensitif)
                                    if (amplitudes[i] > 0.5) {
                                        chroma[noteIndex] = true;
                                    }
                                }
                            }
                        }

                        String chord = ChordTemplates.findBestMatchingChord(chroma);
                        double timeStamp = audioEvent.getTimeStamp();

                        // Logika sederhana untuk mengurangi duplikat akor berurutan
                        if (!"N/A".equals(chord)) {
                            if (progression.isEmpty() || !chord.equals(progression.get(progression.size() - 1))) {
                                detectedChords.put(timeStamp, chord);
                                progression.add(chord);
                            }
                        }
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        final StringBuilder resultBuilder = new StringBuilder();
                        for (Map.Entry<Double, String> entry : detectedChords.entrySet()) {
                            // Format waktu menit:detik
                            int seconds = entry.getKey().intValue();
                            int p1 = seconds % 60;
                            int p2 = seconds / 60;
                            int p3 = p2 % 60;
                            String timeStr = String.format("%02d:%02d", p3, p1);

                            resultBuilder.append(String.format("[%s] %s\n", timeStr, entry.getValue()));
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (resultBuilder.length() > 0) {
                                    textViewPitchResult.setText(resultBuilder.toString());
                                } else {
                                    textViewPitchResult.setText("Tidak ada akor yang terdeteksi (Audio mungkin terlalu lemah atau format tidak didukung).");
                                }
                            });
                        }
                    }
                };

                dispatcher.addAudioProcessor(chordProcessor);
                dispatcher.run(); // Gunakan run() langsung karena kita sudah di dalam Thread baru

            } catch (Exception e) {
                // Tangkap Exception umum untuk menghindari crash
                Log.e("ChordAnalysis", "Error saat analisis akor", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            textViewPitchResult.setText("Error Analisis: " + e.getMessage())
                    );
                }
            }
        }).start();
    }

}