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
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;

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
        new Thread(() -> {
            try {
                File audioFile = new File(audioPath);

                if (!audioFile.exists()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "File tidak ditemukan: " + audioPath, Toast.LENGTH_LONG).show()
                        );
                    }
                    return;
                }

                // 1. DECODE AUDIO → PCM
                AudioData decoded = decodeAudio(audioPath);
                if (decoded == null) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                textViewPitchResult.setText("Gagal decode audio menggunakan MediaExtractor")
                        );
                    }
                    return;
                }

                byte[] pcmData;
                if (decoded.channels > 1) {
                    pcmData = convertToMono(decoded.bytes);
                } else {
                    pcmData = decoded.bytes;
                }

                int sampleRate = decoded.sampleRate;
                int totalBytes = pcmData.length;
                int bytesPerSample = 2; // 16-bit
                int totalSamples = totalBytes / bytesPerSample;
                int durationSeconds = totalSamples / sampleRate;

                Log.d("ChordAnalysis", "PCM size=" + totalBytes + " bytes, samples=" + totalSamples + ", sr=" + sampleRate + ", duration(s)=" + durationSeconds);

                // jika durasi terlalu pendek beri tahu user
                if (durationSeconds < 3) {
                    final String warn = "Decoded audio sangat pendek: " + durationSeconds + " detik. Periksa file atau decoder.";
                    Log.w("ChordAnalysis", warn);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), warn, Toast.LENGTH_LONG).show());
                    }
                }
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
                        new UniversalAudioInputStream(new ByteArrayInputStream(pcmData), format);

                AudioDispatcher dispatcher =
                        new AudioDispatcher(inputStream, bufferSize, bufferOverlap);

                final FFT fft = new FFT(bufferSize);
                final float[] spectrum = new float[bufferSize / 2];

                Map<Double, String> detectedChords = new TreeMap<>();
                List<String> progression = new ArrayList<>();
                final String[] lastStableChord = {""}; // Chord terakhir yang valid ditampilkan
                final String[] potentialChord = {""};   // Kandidat chord baru
                final int[] stableCount = {0};          // Counter seberapa lama kandidat bertahan
                final int MIN_STABLE_FRAMES = 3;        // Minimal frame berturut-turut agar dianggap valid

                AudioProcessor chordProcessor = new AudioProcessor() {
                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] audioBuffer = audioEvent.getFloatBuffer();
                        float[] transformBuffer = new float[audioBuffer.length];
                        System.arraycopy(audioBuffer, 0, transformBuffer, 0, audioBuffer.length);
                        fft.forwardTransform(transformBuffer);
                        fft.modulus(transformBuffer, spectrum);

                        float maxAmp = 0;
                        for (float f : spectrum) {
                            if (f > maxAmp) maxAmp = f;
                        }

                        // --- PERBAIKAN 1: Turunkan threshold ---
                        if (maxAmp > 0.01f) {
                            boolean[] chroma = new boolean[12];
                            for (int i = 0; i < spectrum.length; i++) {
                                double freq = fft.binToHz(i, sampleRate);
                                // Rentang frekuensi untuk akor biasanya di mid-range
                                if (freq > 60 && freq < 2000) {
                                    // Threshold lokal juga bisa disesuaikan
                                    if (spectrum[i] > maxAmp * 0.1f) {
                                        int midi = (int) Math.round(
                                                69 + 12 * Math.log(freq / 440.0) / Math.log(2)
                                        );
                                        if (midi > 0) {
                                            chroma[midi % 12] = true;
                                        }
                                    }
                                }
                            }

                            String currentChord = ChordTemplates.findBestMatchingChord(chroma);
                            double t = audioEvent.getTimeStamp();

                            if (!"N/A".equals(currentChord)) {
                                // LOGIKA SMOOTHING
                                if (currentChord.equals(potentialChord[0])) {
                                    stableCount[0]++;
                                } else {
                                    // Reset jika chord berubah lagi (tidak stabil)
                                    potentialChord[0] = currentChord;
                                    stableCount[0] = 1;
                                }

                                // Jika chord sudah stabil selama X frame, dan BEDA dari yang terakhir ditampilkan
                                if (stableCount[0] >= MIN_STABLE_FRAMES) {
                                    if (!currentChord.equals(lastStableChord[0])) {
                                        // CATAT PERUBAHAN
                                        detectedChords.put(t, currentChord);
                                        progression.add(currentChord); // List untuk UI

                                        lastStableChord[0] = currentChord; // Update chord aktif

                                        // Log agar Anda bisa memantau hasilnya
                                        Log.d("ChordFinal", "Ganti ke: " + currentChord + " di detik " + t);
                                    }
                                }
                            }
                            return true;
                        }
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        StringBuilder sb = new StringBuilder();
                        String title = (audioTitle != null && !audioTitle.isEmpty())
                                ? audioTitle : "Audio";

                        sb.append(title).append("\n");

                        if (detectedChords.isEmpty()) {
                            sb.append("Tidak ada chord yang terdeteksi.\n");
                        } else {
                            for (Map.Entry<Double, String> entry : detectedChords.entrySet()) {
                                int seconds = entry.getKey().intValue();
                                String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
                                sb.append("[").append(time).append("] ")
                                        .append(entry.getValue()).append("\n");
                            }
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                textViewPitchResult.setText(sb.toString());
                                buttonDetectPitch.setEnabled(true);
                                resultTextView.setText("Analisis selesai.");
                            });
                        }
                    }
                };

                dispatcher.addAudioProcessor(chordProcessor);
                dispatcher.run();

            } catch (Exception e) {
                Log.e("ChordAnalysis", "Error analisis", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            textViewPitchResult.setText("Error analisis: " + e.getMessage())
                    );
                }
            }
        }).start();
    }


    // KONVERTER STEREO KE MONO
    private byte[] convertToMono(byte[] stereoData) {
        if (stereoData == null || stereoData.length == 0) return stereoData;

        // setiap sample 2 byte (16-bit)
        int totalSamples = stereoData.length / 2; // jumlah 'short' samples (both channels)
        int totalFrames = totalSamples / 2; // setiap frame punya 2 channel
        ByteBuffer bb = ByteBuffer.wrap(stereoData).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        java.nio.ShortBuffer sb = bb.asShortBuffer();

        short[] monoShorts = new short[totalFrames];

        for (int i = 0; i < totalFrames; i++) {
            short left = sb.get(i * 2);
            short right = sb.get(i * 2 + 1);
            int avg = (left + right) / 2;
            monoShorts[i] = (short) avg;
        }

        ByteBuffer outBb = ByteBuffer.allocate(monoShorts.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
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
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                // End of stream -- send EOS to codec
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                if (outIndex >= 0) {
                    ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);

                    if (outBuffer != null && bufferInfo.size > 0) {
                        byte[] chunk = new byte[bufferInfo.size];
                        outBuffer.get(chunk);
                        pcmOutput.write(chunk);
                    }

                    codec.releaseOutputBuffer(outIndex, false);

                    // Check for end of stream from codec
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
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
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // ignored for API >= 21 (we use getOutputBuffer)
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

            Log.d("DecodeAudio", "Decoded PCM bytes: " + pcmBytes.length + " channels=" + channels + " sr=" + sampleRate);

            return new AudioData(pcmBytes, sampleRate, channels);

        } catch (Exception e) {
            Log.e("DecodeAudio", "Decode error", e);
            return null;
        }
    }



}