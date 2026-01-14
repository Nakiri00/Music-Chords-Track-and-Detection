package com.example.musicchords;

import java.util.HashMap;
import java.util.Map;

public class ChordTemplates {
    // Array 12 nada
    public static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final Map<String, boolean[]> chordTemplates = new HashMap<>();

    // Inisialisasi templat akor dasar (Mayor dan Minor)
    static {
        for (int i = 0; i < NOTES.length; i++) {
            // Templat Akor Mayor (root, root+4, root+7)
            boolean[] major = new boolean[12];
            major[i % 12] = true;
            major[(i + 4) % 12] = true;
            major[(i + 7) % 12] = true;
            chordTemplates.put(NOTES[i] + " Major", major);

            // Templat Akor Minor (root, root+3, root+7)
            boolean[] minor = new boolean[12];
            minor[i % 12] = true;
            minor[(i + 3) % 12] = true;
            minor[(i + 7) % 12] = true;
            chordTemplates.put(NOTES[i] + " Minor", minor);

            // Contoh logika (konsep):
            // Major 7th = Root + 4 + 7 + 11 semitone
//            boolean[] maj7 = new boolean[12];
//            maj7[i % 12] = true;
//            maj7[(i + 4) % 12] = true;
//            maj7[(i + 7) % 12] = true;
//            maj7[(i + 11) % 12] = true;
//            chordTemplates.put(NOTES[i] + "maj7", maj7);
        }
    }

    public static String findBestMatchingChord(boolean[] chroma) {
        String bestChord = "N/A";
        double maxScore = -1.0;

        for (Map.Entry<String, boolean[]> entry : chordTemplates.entrySet()) {
            String chordName = entry.getKey();
            boolean[] template = entry.getValue();
            double score = 0;
            int matches = 0;

            for (int i = 0; i < 12; i++) {
                if (chroma[i] && template[i]) {
                    score += 1.0; // Beri skor jika not ada di chroma dan templat
                    matches++;
                } else if (!chroma[i] && template[i]) {
                    score -= 0.5; // Penalti jika not wajib tidak ada
                } else if (chroma[i] && !template[i]) {
                    score -= 0.2; // Penalti kecil jika ada not di luar akor
                }
            }

            // Hanya pertimbangkan jika minimal 2 not dari akor cocok
            if (matches >= 2 && score > maxScore) {
                maxScore = score;
                bestChord = chordName;
            }
        }
        return bestChord;
    }
}