package com.example.musicchords;

public interface ChordAnalyzerStrategy {
    void analyzeChords(String audioPath, int sampleRate, AudioAnalysisRepository.AnalysisCallback callback);
}