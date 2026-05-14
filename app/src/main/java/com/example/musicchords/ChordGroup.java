package com.example.musicchords;

import java.util.List;

/**
 * Satu kelompok chord: nama chord + semua variasi posisi jari.
 * Contoh: "C major" dengan 4 posisi fingering yang berbeda.
 */
public class ChordGroup {

    private final String chordName; // "C major"
    private final List<String> positions; // fret string tiap posisi, sudah dikonversi ke absolut
    private final int audioResId; // resource audio untuk chord ini

    public ChordGroup(
        String chordName,
        List<String> positions,
        int audioResId
    ) {
        this.chordName = chordName;
        this.positions = positions;
        this.audioResId = audioResId;
    }

    public String getChordName() {
        return chordName;
    }

    public List<String> getPositions() {
        return positions;
    }

    public int getAudioResId() {
        return audioResId;
    }
}
