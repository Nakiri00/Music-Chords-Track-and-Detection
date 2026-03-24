package com.example.musicchords;

import java.util.HashMap;
import java.util.Map;

public class ChordTemplates {

    public static final String[] NOTES = {
        "C",
        "C#",
        "D",
        "Eb",
        "E",
        "F",
        "F#",
        "G",
        "Ab",
        "A",
        "Bb",
        "B",
    };

    // Chord tone weights: root is most important, third defines major/minor, fifth adds body
    private static final float ROOT_WEIGHT = 1.00f;
    private static final float THIRD_WEIGHT = 0.85f;
    private static final float FIFTH_WEIGHT = 0.70f;

    private static final Map<String, float[]> chordTemplates = new HashMap<>();

    static {
        for (int i = 0; i < NOTES.length; i++) {
            // Major: Root + Major Third (4 semitones) + Perfect Fifth (7 semitones)
            float[] major = new float[12];
            major[i % 12] = ROOT_WEIGHT;
            major[(i + 4) % 12] = THIRD_WEIGHT;
            major[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + " Major", major);

            // Minor: Root + Minor Third (3 semitones) + Perfect Fifth (7 semitones)
            float[] minor = new float[12];
            minor[i % 12] = ROOT_WEIGHT;
            minor[(i + 3) % 12] = THIRD_WEIGHT;
            minor[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + " Minor", minor);
        }
    }

    /**
     * Finds the best matching chord using cosine similarity between
     * the energy-weighted chroma vector and weighted chord templates.
     *
     * @param chroma float[12] normalized energy per pitch class (0.0 to 1.0)
     * @return best matching chord name, or "N/A" if no confident match found
     */
    public static String findBestMatchingChord(float[] chroma) {
        String bestChord = "N/A";
        double maxSimilarity = 0.45; // Minimum cosine similarity threshold

        for (Map.Entry<String, float[]> entry : chordTemplates.entrySet()) {
            String chordName = entry.getKey();
            float[] template = entry.getValue();

            double dotProduct = 0.0;
            double normChroma = 0.0;
            double normTemplate = 0.0;

            for (int i = 0; i < 12; i++) {
                dotProduct += chroma[i] * template[i];
                normChroma += chroma[i] * chroma[i];
                normTemplate += template[i] * template[i];
            }

            // Cosine similarity: 1.0 = perfect match, 0.0 = completely different
            double similarity =
                dotProduct / (Math.sqrt(normChroma * normTemplate) + 1e-10);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestChord = chordName;
            }
        }
        return bestChord;
    }
}
