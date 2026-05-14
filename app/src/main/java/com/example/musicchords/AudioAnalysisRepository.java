package com.example.musicchords;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.util.fft.FFT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioAnalysisRepository {

    private static final String TAG = "AudioAnalysisRepo";

    public interface AnalysisCallback {
        void onComplete(List<ChordTimestamp> results);
        void onError(Exception e);
    }

    public void analyzeChords(String audioPath, AnalysisCallback callback) {
        new Thread(() -> {
            try {
                if (!new File(audioPath).exists()) {
                    callback.onError(new Exception("File not found: " + audioPath));
                    return;
                }

                AudioData decoded = decodeAudio(audioPath);
                if (decoded == null) {
                    callback.onError(new Exception("Failed to decode audio"));
                    return;
                }

                byte[] pcmData = (decoded.channels > 1) ? convertToMono(decoded.bytes) : decoded.bytes;
                int sampleRate = decoded.sampleRate;

                int bufferSize = 8192;
                int bufferOverlap = 4096;

                TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
                UniversalAudioInputStream inputStream = new UniversalAudioInputStream(
                        new ByteArrayInputStream(pcmData), format);
                AudioDispatcher dispatcher = new AudioDispatcher(inputStream, bufferSize, bufferOverlap);

                final FFT fft = new FFT(bufferSize);
                final float[] spectrum = new float[bufferSize / 2];

                final float[] hannWindow = new float[bufferSize];
                for (int i = 0; i < bufferSize; i++) {
                    hannWindow[i] = (float) (0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (bufferSize - 1))));
                }

                final int VOTE_WINDOW_SIZE = 5;
                final ArrayDeque<String> chordWindow = new ArrayDeque<>();
                final ArrayDeque<Double> timeWindow = new ArrayDeque<>();
                final String[] lastSavedChord = {""};
                final List<ChordTimestamp> detectedChords = new ArrayList<>();

                AudioProcessor chordProcessor = new AudioProcessor() {
                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] audioBuffer = audioEvent.getFloatBuffer();
                        double timestamp = audioEvent.getTimeStamp();

                        // A. SILENCE DETECTION
                        double rms = 0;
                        for (float s : audioBuffer) rms += s * s;
                        rms = Math.sqrt(rms / audioBuffer.length);
                        if (rms < 0.008) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        // B. APPLY HANN WINDOW
                        float[] transformBuffer = new float[audioBuffer.length];
                        for (int i = 0; i < audioBuffer.length; i++) {
                            transformBuffer[i] = audioBuffer[i] * hannWindow[i];
                        }

                        // C. FFT
                        fft.forwardTransform(transformBuffer);
                        fft.modulus(transformBuffer, spectrum);

                        // D. NOISE FLOOR
                        float maxAmp = 0;
                        for (int i = 0; i < spectrum.length; i++) {
                            double f = fft.binToHz(i, sampleRate);
                            if (f >= 80.0 && f <= 4000.0) maxAmp = Math.max(maxAmp, spectrum[i]);
                        }
                        float noiseFloor = maxAmp * 0.03f;

                        // E. ENERGY-WEIGHTED CHROMA
                        float[] chroma = new float[12];
                        for (int i = 1; i < spectrum.length - 1; i++) {
                            if (spectrum[i] < noiseFloor) continue;
                            double freq = fft.binToHz(i, sampleRate);
                            if (freq < 80.0 || freq > 4000.0) continue;
                            double midiExact = 69.0 + (12.0 * Math.log(freq / 440.0)) / Math.log(2);
                            int pitchClass = ((int) Math.round(midiExact)) % 12;
                            if (pitchClass < 0) pitchClass += 12;
                            chroma[pitchClass] += spectrum[i];
                        }

                        // F. NORMALIZE CHROMA
                        float maxChroma = 0;
                        for (float v : chroma) maxChroma = Math.max(maxChroma, v);
                        if (maxChroma <= 0) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }
                        for (int i = 0; i < 12; i++) chroma[i] /= maxChroma;

                        // G. HARMONIC CONTENT FILTER
                        int dominantClasses = 0;
                        for (float v : chroma) if (v > 0.30f) dominantClasses++;
                        if (dominantClasses < 2) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        int broadClasses = 0;
                        for (float v : chroma) if (v > 0.20f) broadClasses++;
                        if (broadClasses > 7) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        float top1 = 0, top2 = 0, top3 = 0;
                        for (float v : chroma) {
                            if (v > top1) { top3 = top2; top2 = top1; top1 = v; }
                            else if (v > top2) { top3 = top2; top2 = v; }
                            else if (v > top3) { top3 = v; }
                        }
                        float totalEnergy = 0;
                        for (float v : chroma) totalEnergy += v;
                        float concentration = (top1 + top2 + top3) / (totalEnergy + 1e-10f);
                        if (concentration < 0.55f) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        // H. CHORD MATCHING
                        String currentChord = ChordTemplates.findBestMatchingChord(chroma);
                        if ("N/A".equals(currentChord)) currentChord = "-";

                        // I. VOTE WINDOW STABILIZATION
                        updateVoteWindow(currentChord, timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        callback.onComplete(detectedChords);
                    }
                };

                dispatcher.addAudioProcessor(chordProcessor);
                dispatcher.run();

            } catch (Exception e) {
                Log.e(TAG, "Analysis error", e);
                callback.onError(e);
            }
        }).start();
    }

    private void updateVoteWindow(String chord, double timestamp,
                                  ArrayDeque<String> chordWindow, ArrayDeque<Double> timeWindow,
                                  String[] lastSaved, int windowSize, List<ChordTimestamp> detectedChords) {
        chordWindow.addLast(chord);
        timeWindow.addLast(timestamp);
        if (chordWindow.size() > windowSize) {
            chordWindow.pollFirst();
            timeWindow.pollFirst();
        }
        if (chordWindow.size() < windowSize) return;

        HashMap<String, Integer> votes = new HashMap<>();
        for (String c : chordWindow) votes.merge(c, 1, Integer::sum);

        String winner = "-";
        int maxVotes = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > maxVotes) {
                maxVotes = e.getValue();
                winner = e.getKey();
            }
        }

        int majority = windowSize / 2 + 1;
        if (maxVotes < majority || winner.equals(lastSaved[0])) return;

        double onsetTime = timestamp;
        String[] windowChords = chordWindow.toArray(new String[0]);
        Double[] windowTimes = timeWindow.toArray(new Double[0]);
        for (int i = 0; i < windowChords.length; i++) {
            if (windowChords[i].equals(winner)) { onsetTime = windowTimes[i]; break; }
        }

        synchronized (detectedChords) {
            detectedChords.add(new ChordTimestamp(onsetTime, winner));
        }
        lastSaved[0] = winner;
    }

        private AudioData decodeAudio(String path) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        try {
            extractor = new MediaExtractor();
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
            if (audioTrack < 0) return null;

            extractor.selectTrack(audioTrack);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) return null;

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            final int TIMEOUT_US = 10000;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        if (inputBuffer != null) {
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
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
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }

            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            return new AudioData(pcmOutput.toByteArray(), sampleRate, channels);

        } catch (Exception e) {
            Log.e(TAG, "Decode error", e);
            return null;
        } finally {
            // Selalu dibebaskan, bahkan jika exception terjadi di tengah jalan
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
            if (extractor != null) extractor.release();
        }
    }


    private byte[] convertToMono(byte[] stereoData) {
        if (stereoData == null || stereoData.length == 0) return stereoData;
        int totalFrames = stereoData.length / 4;
        ByteBuffer bb = ByteBuffer.wrap(stereoData).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        java.nio.ShortBuffer sb = bb.asShortBuffer();
        short[] monoShorts = new short[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            monoShorts[i] = (short) ((sb.get(i * 2) + sb.get(i * 2 + 1)) / 2);
        }
        ByteBuffer outBb = ByteBuffer.allocate(monoShorts.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        outBb.asShortBuffer().put(monoShorts);
        return outBb.array();
    }
}
