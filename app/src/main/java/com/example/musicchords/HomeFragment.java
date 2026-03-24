package com.example.musicchords;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.util.fft.FFT;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    private TextView tvResultChord;
    private EditText editText;
    private TextView resultTextView;
    private ProgressBar loadingIndicator;
    private Button buttonDownload;

    private String downloadLink = "";
    private String audioTitle = "";
    private String downloadedFilePath = null;
    private long downloadID;
    private TextView tvPlayingTitle;
    private MediaPlayer mediaPlayer;
    private ImageButton btnPlay, btnPause;
    private SeekBar seekBarAudio;
    private TextView tvDuration;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;
    private Button buttonPickFile;
    private LinearLayout layoutAudioPlayer;
    private List<ChordTimestamp> detectedChords = new ArrayList<>();

    private Handler chordHandler = new Handler(Looper.getMainLooper());
    private Runnable chordRunnable;

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

    private final ActivityResultLauncher<String> pickAudioLauncher =
        registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processSelectedFile(uri, null);
                }
            }
        );

    private void startChordSync() {
        chordRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    // 1. Dapatkan posisi lagu saat ini (ubah ke detik)
                    double currentSeconds =
                        mediaPlayer.getCurrentPosition() / 1000.0;

                    // 2. Cari chord yang cocok dengan waktu ini
                    String chordToShow = getCurrentChord(currentSeconds);

                    // 3. Update UI (Pastikan TextView berukuran besar/jelas)
                    tvResultChord.setText(chordToShow);

                    // 4. Ulangi pengecekan setiap 100ms
                    chordHandler.postDelayed(this, 100);
                }
            }
        };
        chordHandler.post(chordRunnable);
    }

    // Fungsi helper untuk mencari chord terdekat
    private String getCurrentChord(double currentTime) {
        String currentChord = "-";
        // Loop sederhana (bisa dioptimalkan dengan Binary Search jika data banyak)
        for (ChordTimestamp item : detectedChords) {
            if (currentTime >= item.timeSeconds) {
                currentChord = item.chordName;
            } else {
                // Karena list berurutan waktu, jika waktu item sudah lebih besar dari currentTime, stop.
                break;
            }
        }
        return currentChord;
    }

    // JANGAN LUPA: Hentikan handler saat pause atau stop
    private void stopChordSync() {
        chordHandler.removeCallbacks(chordRunnable);
    }

    private void playAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            btnPlay.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            updateSeekBar();
            startChordSync();
        }
    }

    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPause.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            handler.removeCallbacks(updateSeekBarRunnable);
            stopChordSync();
        }
    }

    private void updateSeekBar() {
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBarAudio.setProgress(mediaPlayer.getCurrentPosition());
                    updateDurationText();
                    handler.postDelayed(this, 1000); // Update setiap 1 detik
                }
            }
        };
        handler.post(updateSeekBarRunnable);
    }

    private void updateDurationText() {
        if (mediaPlayer != null) {
            int current = mediaPlayer.getCurrentPosition() / 1000;
            int total = mediaPlayer.getDuration() / 1000;

            String currentStr = String.format(
                "%02d:%02d",
                current / 60,
                current % 60
            );
            String totalStr = String.format(
                "%02d:%02d",
                total / 60,
                total % 60
            );

            tvDuration.setText(currentStr + " / " + totalStr);
        }
    }

    private void setAudioControlsEnabled(boolean enabled) {
        btnPlay.setEnabled(enabled);
        btnPause.setEnabled(enabled);
        seekBarAudio.setEnabled(enabled);
        if (!enabled) {
            btnPlay.setAlpha(0.5f);
            seekBarAudio.setProgress(0);
        } else {
            btnPlay.setAlpha(1.0f);
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            handler.removeCallbacks(updateSeekBarRunnable);
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void setupAudioPlayer(String path) {
        releaseMediaPlayer();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();

            if (tvPlayingTitle != null) {
                tvPlayingTitle.setText(audioTitle);
            }

            seekBarAudio.setMax(mediaPlayer.getDuration());
            updateDurationText();
            setAudioControlsEnabled(true);
            layoutAudioPlayer.setVisibility(View.VISIBLE);

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPause.setVisibility(View.GONE);
                btnPlay.setVisibility(View.VISIBLE);
                seekBarAudio.setProgress(0);
                handler.removeCallbacks(updateSeekBarRunnable);
            });
        } catch (Exception e) {
            Log.e("AudioPlayer", "Error preparing audio", e);
            Toast.makeText(
                getContext(),
                "Gagal memuat audio",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void processSelectedFile(Uri uri, String customTitle) {
        try {
            String fileName = getFileNameFromUri(uri);

            if (fileName == null) fileName =
                "audio_" + System.currentTimeMillis() + ".mp3";
            String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String finalFileName = "history_" + safeFileName;

            File uniqueFile = new File(
                requireContext().getFilesDir(),
                finalFileName
            );

            InputStream inputStream = requireContext()
                .getContentResolver()
                .openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(uniqueFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();
            this.downloadedFilePath = uniqueFile.getAbsolutePath();

            if (customTitle != null && !customTitle.isEmpty()) {
                this.audioTitle = customTitle;
            } else {
                this.audioTitle = fileName;
            }

            // Reset chord state from any previously loaded file
            stopChordSync();
            detectedChords.clear();
            tvResultChord.setText("-");

            resultTextView.setText("File Siap: " + this.audioTitle);

            buttonDetectPitch.setVisibility(View.VISIBLE);
            buttonDetectPitch.setEnabled(true);
            buttonDownload.setVisibility(View.GONE);

            setupAudioPlayer(downloadedFilePath);

            Toast.makeText(
                getContext(),
                "File dimuat: " + audioTitle,
                Toast.LENGTH_SHORT
            ).show();
        } catch (Exception e) {
            Log.e("PickFile", "Error processing file", e);
            Toast.makeText(
                getContext(),
                "Gagal memproses file: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    // Helper untuk mengambil nama file dari URI
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (
                Cursor cursor = requireContext()
                    .getContentResolver()
                    .query(uri, null, null, null, null)
            ) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    );
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private final BroadcastReceiver onDownloadComplete =
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1
                );

                // Pastikan ID download cocok
                if (downloadID == id) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(id);

                    DownloadManager dm =
                        (DownloadManager) context.getSystemService(
                            Context.DOWNLOAD_SERVICE
                        );
                    Cursor cursor = dm.query(query);

                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(
                            DownloadManager.COLUMN_STATUS
                        );
                        int status = cursor.getInt(statusIndex);
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // Ambil URI file langsung dari DownloadManager
                            int uriIndex = cursor.getColumnIndex(
                                DownloadManager.COLUMN_LOCAL_URI
                            );
                            String uriString = cursor.getString(uriIndex);

                            if (uriString != null) {
                                Uri fileUri = Uri.parse(uriString);
                                // untuk menyalinnya ke cache agar bisa diakses path-nya
                                processSelectedFile(fileUri, audioTitle);

                                // Update UI Text (Override teks yang diset processSelectedFile jika perlu)
                                resultTextView.setText(
                                    "Unduhan Selesai. Siap Analisis."
                                );
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Unduhan Gagal / Belum Selesai",
                                Toast.LENGTH_SHORT
                            ).show();
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
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
        if (receiverRegistered) {
            requireActivity().unregisterReceiver(onDownloadComplete);
            receiverRegistered = false;
        }
    }

    @Override
    public void onViewCreated(
        @NonNull View view,
        @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        buttonConvert = view.findViewById(R.id.button_convert);
        resultTextView = view.findViewById(R.id.textview_result);
        loadingIndicator = view.findViewById(R.id.progress_bar);
        editText = view.findViewById(R.id.url_input);
        buttonDownload = view.findViewById(R.id.button_download);
        buttonDetectPitch = view.findViewById(R.id.button_detect_pitch);
        tvResultChord = view.findViewById(R.id.tv_result_chord);
        btnPlay = view.findViewById(R.id.btn_play);
        btnPause = view.findViewById(R.id.btn_pause);
        seekBarAudio = view.findViewById(R.id.seekbar_audio);
        tvDuration = view.findViewById(R.id.tv_duration);
        buttonPickFile = view.findViewById(R.id.btn_pick_file);
        layoutAudioPlayer = view.findViewById(R.id.layout_audio_player);
        tvPlayingTitle = view.findViewById(R.id.tv_playing_title);

        buttonPickFile.setOnClickListener(v -> {
            pickAudioLauncher.launch("audio/*");
        });
        buttonDetectPitch.setEnabled(false);
        setAudioControlsEnabled(false);
        if (getArguments() != null) {
            loadHistoryData(getArguments());
        }
        buttonConvert.setOnClickListener(v -> {
            String youtubeUrl = editText.getText().toString().trim();
            if (youtubeUrl.isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "Silakan masukkan URL",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // Reset UI
            resultTextView.setText("Memproses...");
            buttonDownload.setVisibility(View.GONE);
            layoutAudioPlayer.setVisibility(View.GONE); // SEMBUNYIKAN PLAYER SAAT RESET
            releaseMediaPlayer(); // Matikan lagu sebelumnya

            downloadLink = "";
            audioTitle = "";
            downloadedFilePath = null;
            buttonDetectPitch.setEnabled(false);

            homeViewModel.convertYoutubeUrl(youtubeUrl);
        });
        // Listener Tombol
        btnPlay.setOnClickListener(v -> playAudio());
        btnPause.setOnClickListener(v -> pauseAudio());
        seekBarAudio.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
                ) {
                    if (fromUser && mediaPlayer != null) {
                        mediaPlayer.seekTo(progress);
                        updateDurationText();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        handler.removeCallbacks(updateSeekBarRunnable); // Stop update saat digeser user
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        handler.post(updateSeekBarRunnable); // Resume update setelah dilepas
                    }
                }
            }
        );
        // Setup listener untuk tombol
        buttonConvert.setOnClickListener(v -> {
            String youtubeUrl = editText.getText().toString().trim();
            if (youtubeUrl.isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "Silakan masukkan URL",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }
            // Reset UI sebelum request baru
            resultTextView.setText("Memproses...");
            buttonDownload.setVisibility(View.GONE);
            downloadLink = "";
            audioTitle = "";
            downloadedFilePath = null;
            buttonDetectPitch.setEnabled(false);

            homeViewModel.convertYoutubeUrl(youtubeUrl);
        });

        buttonDownload.setOnClickListener(v -> {
            if (!downloadLink.isEmpty() && !audioTitle.isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "Mulai mengunduh: " + audioTitle,
                    Toast.LENGTH_SHORT
                ).show();
                downloadAudio(downloadLink, audioTitle);
            } else {
                Toast.makeText(
                    getContext(),
                    "Link download tidak valid.",
                    Toast.LENGTH_SHORT
                ).show();
            }
        });

        buttonDetectPitch.setOnClickListener(v -> {
            if (downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
                tvResultChord.setText("Analyzing Chords");
                analyzeChords(downloadedFilePath);
            } else {
                Toast.makeText(
                    getContext(),
                    "Unduh file audio terlebih dahulu atau tunggu unduhan selesai.",
                    Toast.LENGTH_SHORT
                ).show();
            }
        });

        // Panggil fungsi untuk setup observer
        setupObservers();
    }

    private void parseAndLoadChords(String savedData) {
        detectedChords.clear();
        String[] lines = savedData.split("\n");

        int count = 0;
        for (String line : lines) {
            line = line.trim();
            // Cek apakah baris dimulai dengan kurung siku waktu, contoh: [00:12]
            if (line.startsWith("[") && line.contains("]")) {
                try {
                    // Ambil bagian waktu: "00:12"
                    int closeBracketIndex = line.indexOf("]");
                    String timeStr = line.substring(1, closeBracketIndex);

                    // Ambil nama chord: "Cmaj"
                    String chordName = line
                        .substring(closeBracketIndex + 1)
                        .trim();

                    // Parsing waktu menit:detik ke detik total (double)
                    String[] parts = timeStr.split(":");
                    if (parts.length == 2) {
                        int mm = Integer.parseInt(parts[0]);
                        int ss = Integer.parseInt(parts[1]);
                        double totalSeconds = (mm * 60) + ss;

                        detectedChords.add(
                            new ChordTimestamp(totalSeconds, chordName)
                        );
                        count++;
                    }
                } catch (Exception e) {
                    // Abaikan baris yang formatnya salah/header judul
                    Log.w("ParseHistory", "Skipping line: " + line);
                }
            }
        }

        if (count > 0) {
            tvResultChord.setText("Data Loaded (" + count + " Chords)");
        } else {
            tvResultChord.setText("No chord data found inside history text.");
        }
    }

    public void loadHistoryData(Bundle bundle) {
        if (bundle == null) return;

        String audioPath = bundle.getString("audioPath");
        String title = bundle.getString("songTitle");
        String savedChordData = bundle.getString("chordData");

        if (audioPath != null) {
            File audioFile = new File(audioPath);
            if (audioFile.exists()) {
                // Update variabel global
                this.downloadedFilePath = audioPath;
                this.audioTitle = title;

                // Update UI
                if (tvPlayingTitle != null) tvPlayingTitle.setText(title);

                // Siapkan Player
                setupAudioPlayer(audioPath);

                // Parsing Data Chord (agar sync jalan)
                if (savedChordData != null && !savedChordData.isEmpty()) {
                    parseAndLoadChords(savedChordData); // Fungsi parser yang sudah dibuat sebelumnya
                    resultTextView.setText("Data dimuat dari History.");
                } else {
                    resultTextView.setText("File siap. Silakan Analisis.");
                }

                // Tampilkan tombol yang sesuai
                buttonDetectPitch.setVisibility(View.VISIBLE);
                buttonDetectPitch.setEnabled(true);
                buttonDownload.setVisibility(View.GONE);
            } else {
                Toast.makeText(
                    getContext(),
                    "File audio tidak ditemukan (mungkin sudah dihapus)",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void setupObservers() {
        homeViewModel
            .getIsLoading()
            .observe(getViewLifecycleOwner(), isLoading -> {
                loadingIndicator.setVisibility(
                    isLoading ? View.VISIBLE : View.GONE
                );
                buttonConvert.setEnabled(!isLoading);
            });

        homeViewModel
            .getApiResponse()
            .observe(getViewLifecycleOwner(), response -> {
                if (response == null) return;
                Log.d("HomeFragment", "API Response : " + response);
                try {
                    Gson gson = new Gson();
                    JsonObject jsonObject = gson.fromJson(
                        response,
                        JsonObject.class
                    );
                    String status = jsonObject.has("status")
                        ? jsonObject.get("status").getAsString()
                        : "error";
                    if (
                        "ok".equalsIgnoreCase(status) && jsonObject.has("link")
                    ) {
                        this.downloadLink = jsonObject
                            .get("link")
                            .getAsString();
                        this.audioTitle = jsonObject.has("title")
                            ? jsonObject.get("title").getAsString()
                            : "audio";
                        resultTextView.setText(
                            "Audio Siap : " + this.audioTitle
                        );
                        buttonDownload.setVisibility(View.VISIBLE);
                    } else if ("processing".equalsIgnoreCase(status)) {
                        resultTextView.setText(
                            "server Sedang Memproses Video. Mohon Tunggu"
                        );
                        buttonDownload.setVisibility(View.GONE);
                    } else {
                        String message = jsonObject.has("mess")
                            ? jsonObject.get("mess").getAsString()
                            : "Format Respons Tidak Dikenal";
                        resultTextView.setText("Gagal : " + message);
                        buttonDownload.setVisibility(View.GONE);
                    }
                } catch (JsonSyntaxException e) {
                    Log.e("HomeFragment", "JSON Parsing Error ", e);
                    resultTextView.setText(
                        "Terjadi kesalahan saat memproses respons server."
                    );
                    buttonDownload.setVisibility(View.GONE);
                }
            });
        homeViewModel
            .getErrorMessage()
            .observe(getViewLifecycleOwner(), error -> {
                if (error != null && !error.isEmpty()) {
                    Toast.makeText(
                        getContext(),
                        error,
                        Toast.LENGTH_SHORT
                    ).show();
                    resultTextView.setText("Error : " + error);
                    Log.e("HomeFragment", "API Error : " + error);
                }
            });
    }

    /**
     * Menggunakan DownloadManager Android untuk mengunduh file audio.
     * @param url URL download file MP3.
     * @param title Judul file yang akan disimpan.
     */
    private void downloadAudio(String url, String title) {
        String sanitizedTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");

        String fileName = sanitizedTitle + ".mp3";

        DownloadManager.Request request = new DownloadManager.Request(
            Uri.parse(url)
        );
        request.setTitle(title);
        request.setDescription("Mengunduh Audio MP3");
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );

        // FIX untuk Android 10+
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        );

        DownloadManager dm =
            (DownloadManager) requireActivity().getSystemService(
                Context.DOWNLOAD_SERVICE
            );
        downloadID = dm.enqueue(request);

        Log.d("Download", "Download dimulai. ID = " + downloadID);
    }

    // Class helper sederhana untuk menampung data audio + formatnya
    private static class AudioData {

        byte[] bytes;
        int sampleRate;
        int channels;

        AudioData(byte[] bytes, int sampleRate, int channels) {
            this.bytes = bytes;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    private void analyzeChords(String audioPath) {
        buttonDetectPitch.setEnabled(false);
        detectedChords.clear();
        new Thread(() -> {
            try {
                File audioFile = new File(audioPath);
                if (!audioFile.exists()) return;

                // 1. Decode audio to raw PCM
                AudioData decoded = decodeAudio(audioPath);
                if (decoded == null) return;

                byte[] pcmData = (decoded.channels > 1)
                    ? convertToMono(decoded.bytes)
                    : decoded.bytes;
                int sampleRate = decoded.sampleRate;

                // 2. FFT setup
                // bufferSize=8192 → frequency resolution ~5.4 Hz/bin @ 44100 Hz
                // hop size = 8192 - 4096 = 4096 samples → ~93ms per frame
                int bufferSize = 8192;
                int bufferOverlap = 4096;

                TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(
                    sampleRate,
                    16,
                    1,
                    true,
                    false
                );
                UniversalAudioInputStream inputStream =
                    new UniversalAudioInputStream(
                        new ByteArrayInputStream(pcmData),
                        format
                    );
                AudioDispatcher dispatcher = new AudioDispatcher(
                    inputStream,
                    bufferSize,
                    bufferOverlap
                );

                final FFT fft = new FFT(bufferSize);
                final float[] spectrum = new float[bufferSize / 2];

                // 3. Pre-compute Hann window to reduce spectral leakage
                final float[] hannWindow = new float[bufferSize];
                for (int i = 0; i < bufferSize; i++) {
                    hannWindow[i] = (float) (0.5 *
                        (1.0 -
                            Math.cos((2.0 * Math.PI * i) / (bufferSize - 1))));
                }

                // 4. Sliding vote window for stable, onset-accurate chord recording
                //    5 frames × ~93ms = ~465ms window; majority = 3 out of 5
                final int VOTE_WINDOW_SIZE = 5;
                final ArrayDeque<String> chordWindow = new ArrayDeque<>();
                final ArrayDeque<Double> timeWindow = new ArrayDeque<>();
                final String[] lastSavedChord = { "" };

                AudioProcessor chordProcessor = new AudioProcessor() {
                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] audioBuffer = audioEvent.getFloatBuffer();
                        double timestamp = audioEvent.getTimeStamp();

                        // --- A. SILENCE DETECTION ---
                        double rms = 0;
                        for (float s : audioBuffer) rms += s * s;
                        rms = Math.sqrt(rms / audioBuffer.length);
                        if (rms < 0.008) {
                            updateVoteWindow(
                                "-",
                                timestamp,
                                chordWindow,
                                timeWindow,
                                lastSavedChord,
                                VOTE_WINDOW_SIZE
                            );
                            return true;
                        }

                        // --- B. APPLY HANN WINDOW (reduces spectral leakage) ---
                        float[] transformBuffer = new float[audioBuffer.length];
                        for (int i = 0; i < audioBuffer.length; i++) {
                            transformBuffer[i] = audioBuffer[i] * hannWindow[i];
                        }

                        // --- C. FFT ---
                        fft.forwardTransform(transformBuffer);
                        fft.modulus(transformBuffer, spectrum);

                        // --- D. NOISE FLOOR: 3% of peak amplitude in guitar range ---
                        float maxAmp = 0;
                        for (int i = 0; i < spectrum.length; i++) {
                            double f = fft.binToHz(i, sampleRate);
                            if (f >= 80.0 && f <= 4000.0) maxAmp = Math.max(
                                maxAmp,
                                spectrum[i]
                            );
                        }
                        float noiseFloor = maxAmp * 0.03f;

                        // --- E. ENERGY-WEIGHTED CHROMA (float[12]) ---
                        // Accumulate spectral amplitude into 12 pitch classes.
                        // Using 80–4000 Hz captures guitar fundamentals AND their harmonics.
                        // Harmonics of the same note land on the same pitch class (mod 12),
                        // so they naturally reinforce the correct chord tones.
                        float[] chroma = new float[12];
                        for (int i = 1; i < spectrum.length - 1; i++) {
                            if (spectrum[i] < noiseFloor) continue;
                            double freq = fft.binToHz(i, sampleRate);
                            if (freq < 80.0 || freq > 4000.0) continue;

                            double midiExact =
                                69.0 +
                                (12.0 * Math.log(freq / 440.0)) / Math.log(2);
                            int pitchClass = ((int) Math.round(midiExact)) % 12;
                            if (pitchClass < 0) pitchClass += 12;
                            chroma[pitchClass] += spectrum[i];
                        }

                        // --- F. NORMALIZE CHROMA to [0.0 – 1.0] ---
                        float maxChroma = 0;
                        for (float v : chroma)
                            maxChroma = Math.max(maxChroma, v);
                        if (maxChroma <= 0) {
                            updateVoteWindow(
                                "-",
                                timestamp,
                                chordWindow,
                                timeWindow,
                                lastSavedChord,
                                VOTE_WINDOW_SIZE
                            );
                            return true;
                        }
                        for (int i = 0; i < 12; i++) chroma[i] /= maxChroma;

                        // --- G. HARMONIC CONTENT FILTER ---

                        // Check 1: Need at least 2 dominant pitch classes
                        // (blocks silence and single notes)
                        int dominantClasses = 0;
                        for (float v : chroma) if (v > 0.30f) dominantClasses++;
                        if (dominantClasses < 2) {
                            updateVoteWindow(
                                "-",
                                timestamp,
                                chordWindow,
                                timeWindow,
                                lastSavedChord,
                                VOTE_WINDOW_SIZE
                            );
                            return true;
                        }

                        // Check 2: Reject noise/sound effects — too many active pitch classes.
                        // Real chords have 3-5 active notes.
                        // Noise/effects spread energy across 8-12 pitch classes.
                        int broadClasses = 0;
                        for (float v : chroma) if (v > 0.20f) broadClasses++;
                        if (broadClasses > 7) {
                            updateVoteWindow(
                                "-",
                                timestamp,
                                chordWindow,
                                timeWindow,
                                lastSavedChord,
                                VOTE_WINDOW_SIZE
                            );
                            return true;
                        }

                        // Check 3: Chroma energy must be concentrated in the top 3 pitch classes.
                        // A chord focuses energy on 2-3 notes (root, third, fifth).
                        // Sound effects spread energy across many bins, so top-3 share is low.
                        float top1 = 0,
                            top2 = 0,
                            top3 = 0;
                        for (float v : chroma) {
                            if (v > top1) {
                                top3 = top2;
                                top2 = top1;
                                top1 = v;
                            } else if (v > top2) {
                                top3 = top2;
                                top2 = v;
                            } else if (v > top3) {
                                top3 = v;
                            }
                        }
                        float totalChromaEnergy = 0;
                        for (float v : chroma) totalChromaEnergy += v;
                        float concentration =
                            (top1 + top2 + top3) / (totalChromaEnergy + 1e-10f);
                        if (concentration < 0.55f) {
                            updateVoteWindow(
                                "-",
                                timestamp,
                                chordWindow,
                                timeWindow,
                                lastSavedChord,
                                VOTE_WINDOW_SIZE
                            );
                            return true;
                        }

                        // --- H. CHORD MATCHING via cosine similarity ---
                        String currentChord =
                            ChordTemplates.findBestMatchingChord(chroma);
                        if ("N/A".equals(currentChord)) currentChord = "-";

                        // --- I. VOTE WINDOW STABILIZATION ---
                        updateVoteWindow(
                            currentChord,
                            timestamp,
                            chordWindow,
                            timeWindow,
                            lastSavedChord,
                            VOTE_WINDOW_SIZE
                        );
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        finalizeResults();
                    }
                };

                dispatcher.addAudioProcessor(chordProcessor);
                dispatcher.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        })
            .start();
    }

    /**
     * Adds a chord result to the sliding vote window and records a chord change
     * when a candidate achieves majority. The onset timestamp is taken from the
     * FIRST occurrence of the winning chord inside the current window, which
     * corrects the latency introduced by the stability requirement.
     */
    private void updateVoteWindow(
        String chord,
        double timestamp,
        ArrayDeque<String> chordWindow,
        ArrayDeque<Double> timeWindow,
        String[] lastSaved,
        int windowSize
    ) {
        chordWindow.addLast(chord);
        timeWindow.addLast(timestamp);
        if (chordWindow.size() > windowSize) {
            chordWindow.pollFirst();
            timeWindow.pollFirst();
        }

        // Wait until the window is fully populated before making decisions
        if (chordWindow.size() < windowSize) return;

        // Count votes for each chord candidate
        HashMap<String, Integer> votes = new HashMap<>();
        for (String c : chordWindow) votes.merge(c, 1, Integer::sum);

        // Find the chord with the most votes
        String winner = "-";
        int maxVotes = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > maxVotes) {
                maxVotes = e.getValue();
                winner = e.getKey();
            }
        }

        // Require a clear majority (more than half the window)
        int majority = windowSize / 2 + 1; // e.g., 3 out of 5
        if (maxVotes < majority) return;

        // Only record if this is a different chord from what was last saved
        if (winner.equals(lastSaved[0])) return;

        // Find the timestamp of the FIRST occurrence of the winner in the window.
        // This gives a better chord onset time instead of the (delayed) last stable frame.
        double onsetTime = timestamp;
        String[] windowChords = chordWindow.toArray(new String[0]);
        Double[] windowTimes = timeWindow.toArray(new Double[0]);
        for (int i = 0; i < windowChords.length; i++) {
            if (windowChords[i].equals(winner)) {
                onsetTime = windowTimes[i];
                break;
            }
        }

        synchronized (detectedChords) {
            detectedChords.add(new ChordTimestamp(onsetTime, winner));
        }
        lastSaved[0] = winner;
    }

    // // Fungsi Helper untuk logika stabilisasi (menghindari duplikasi kode)
    // private void processStableChord(double time, String currentChord, String[] potential, int[] count, int minFrames) {
    //     if (currentChord.equals(potential[0])) {
    //         count[0]++;
    //     } else {
    //         // Jika chord berubah, reset counter
    //         potential[0] = currentChord;
    //         count[0] = 1;
    //     }

    //     // Jika chord konsisten selama sekian frame, baru kita anggap valid
    //     if (count[0] >= minFrames) {
    //         synchronized (detectedChords) {
    //             // Opsional: Hanya simpan jika BEDA dengan chord yang baru saja disimpan
    //             // (Agar list tidak penuh dengan chord yang sama berulang-ulang)
    //             if (detectedChords.isEmpty() ||
    //                     !detectedChords.get(detectedChords.size() - 1).chordName.equals(currentChord)) {
    //                 detectedChords.add(new ChordTimestamp(time, currentChord));
    //             }
    //         }
    //     }
    // }

    // Fungsi Helper untuk update UI terakhir
    private void finalizeResults() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                StringBuilder sb = new StringBuilder();
                sb
                    .append(audioTitle != null ? audioTitle : "Audio")
                    .append("\n");

                for (ChordTimestamp item : detectedChords) {
                    int mm = (int) (item.timeSeconds / 60);
                    int ss = (int) (item.timeSeconds % 60);
                    // Format [01:23] NamaChord
                    sb.append(
                        String.format(
                            java.util.Locale.US,
                            "[%02d:%02d] %s\n",
                            mm,
                            ss,
                            item.chordName
                        )
                    );
                }

                if (detectedChords.isEmpty()) {
                    tvResultChord.setText("Tidak ada chord terdeteksi.");
                    buttonDetectPitch.setEnabled(true);
                } else {
                    tvResultChord.setText("Analisis Selesai");
                    buttonDetectPitch.setEnabled(false);
                }

                resultTextView.setText("Analisis selesai.");

                // Simpan log lengkap ke Firestore
                saveToFirestore(audioTitle, downloadedFilePath, sb.toString());
            });
        }
    }

    // KONVERTER STEREO KE MONO
    private byte[] convertToMono(byte[] stereoData) {
        if (stereoData == null || stereoData.length == 0) return stereoData;

        // setiap sample 2 byte (16-bit)
        int totalSamples = stereoData.length / 2; // jumlah 'short' samples (both channels)
        int totalFrames = totalSamples / 2; // setiap frame punya 2 channel
        ByteBuffer bb = ByteBuffer.wrap(stereoData).order(
            java.nio.ByteOrder.LITTLE_ENDIAN
        );
        java.nio.ShortBuffer sb = bb.asShortBuffer();

        short[] monoShorts = new short[totalFrames];

        for (int i = 0; i < totalFrames; i++) {
            short left = sb.get(i * 2);
            short right = sb.get(i * 2 + 1);
            int avg = (left + right) / 2;
            monoShorts[i] = (short) avg;
        }

        ByteBuffer outBb = ByteBuffer.allocate(monoShorts.length * 2).order(
            java.nio.ByteOrder.LITTLE_ENDIAN
        );
        outBb.asShortBuffer().put(monoShorts);
        return outBb.array();
    }

    // Method Decoder yang mengembalikan Data + Format
    private AudioData decodeAudio(String path) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(path);

            int audioTrack = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    format = f;
                    break;
                }
            }

            if (audioTrack < 0) {
                Log.e("DecodeAudio", "No audio track found");
                extractor.release();
                return null;
            }

            extractor.selectTrack(audioTrack);

            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            boolean inputDone = false;
            boolean outputDone = false;

            final int TIMEOUT_US = 10000;

            while (!outputDone) {
                // feed input
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        if (inputBuffer != null) {
                            int sampleSize = extractor.readSampleData(
                                inputBuffer,
                                0
                            );
                            if (sampleSize < 0) {
                                // End of stream -- send EOS to codec
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                );
                                inputDone = true;
                            } else {
                                long presentationTimeUs =
                                    extractor.getSampleTime();
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                );
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    TIMEOUT_US
                );

                if (outIndex >= 0) {
                    ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);

                    if (outBuffer != null && bufferInfo.size > 0) {
                        byte[] chunk = new byte[bufferInfo.size];
                        outBuffer.get(chunk);
                        pcmOutput.write(chunk);
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    // Check for end of stream from codec
                    if (
                        (bufferInfo.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) !=
                        0
                    ) {
                        outputDone = true;
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = codec.getOutputFormat();
                    Log.d("DecodeAudio", "Output format changed: " + newFormat);
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    // If input is done and no more output, we should continue waiting for EOS
                    if (inputDone) {
                        // small sleep to avoid busy loop
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }

            // cleanup
            codec.stop();
            codec.release();
            extractor.release();

            byte[] pcmBytes = pcmOutput.toByteArray();

            int channels = 1;
            int sampleRate = 44100;
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            }

            Log.d(
                "DecodeAudio",
                "Decoded PCM bytes: " +
                    pcmBytes.length +
                    " channels=" +
                    channels +
                    " sr=" +
                    sampleRate
            );

            return new AudioData(pcmBytes, sampleRate, channels);
        } catch (Exception e) {
            Log.e("DecodeAudio", "Decode error", e);
            return null;
        }
    }

    private void saveToFirestore(String title, String path, String resultText) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();

        if (uid != null) {
            com.google.firebase.firestore.CollectionReference historyRef = db
                .collection("users")
                .document(uid)
                .collection("history");

            // 1. Cek Apakah judul lagu ini sudah ada?
            historyRef
                .whereEqualTo("title", title)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            // KONDISI 1: SUDAH ADA (UPDATE) ===
                            // Ambil dokumen pertama yang ditemukan
                            DocumentSnapshot doc = task
                                .getResult()
                                .getDocuments()
                                .get(0);
                            String docId = doc.getId();

                            // Update Timestamp (biar jadi paling atas) dan Path file baru
                            historyRef
                                .document(docId)
                                .update(
                                    "timestamp",
                                    new Timestamp(new Date()),
                                    "result",
                                    resultText,
                                    "filePath",
                                    path
                                )
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(
                                        "Firestore",
                                        "History updated: " + title
                                    );
                                    if (getActivity() != null) {
                                        Toast.makeText(
                                            getContext(),
                                            "History diperbarui",
                                            Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                });
                        } else {
                            // === KONDISI 2: BELUM ADA (BUAT BARU) ===
                            ChordHistory history = new ChordHistory(
                                title,
                                path,
                                resultText,
                                new Timestamp(new Date())
                            );

                            historyRef
                                .add(history)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d("Firestore", "New history added");
                                });
                        }
                    } else {
                        Log.e(
                            "Firestore",
                            "Error checking history",
                            task.getException()
                        );
                    }
                });
        }
    }
}
