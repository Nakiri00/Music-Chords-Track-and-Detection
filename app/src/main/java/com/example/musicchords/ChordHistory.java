package com.example.musicchords;

import com.google.firebase.Timestamp;

public class ChordHistory {
    private String title;
    private String filePath;
    private String result;
    private Timestamp timestamp;

    // Diperlukan constructor kosong untuk Firestore
    public ChordHistory() {}

    public ChordHistory(String title, String filePath, String result, Timestamp timestamp) {
        this.title = title;
        this.filePath = filePath;
        this.result = result;
        this.timestamp = timestamp;
    }

    // Getter methods
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public String getResult() { return result; }
    public Timestamp getTimestamp() { return timestamp; }
}