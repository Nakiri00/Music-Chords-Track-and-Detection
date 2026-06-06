package com.example.musicchords;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.NoteOff;
import com.leff.midi.event.NoteOn;
import com.leff.midi.event.meta.Tempo;
import com.leff.midi.event.meta.TimeSignature;

import java.io.File;
import java.util.List;

public class MidiExportHelper {

    private static final String TAG = "MidiExportHelper";

    // Method utama untuk memanggil export
    public static File exportChordsToMidi(Context context, List<ChordTimestamp> detectedChords, String fileName) {
        try {
            // 1. Inisialisasi Track MIDI
            MidiTrack tempoTrack = new MidiTrack();
            MidiTrack noteTrack = new MidiTrack();

            // Set Time Signature (4/4) dan Tempo (120 BPM)
            TimeSignature ts = new TimeSignature();
            ts.setTimeSignature(4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION);
            tempoTrack.insertEvent(ts);

            Tempo tempo = new Tempo();
            tempo.setBpm(120);
            tempoTrack.insertEvent(tempo);

            // Resolusi standar MIDI (Ticks per Quarter Note)
            final int RESOLUTION = MidiFile.DEFAULT_RESOLUTION; // Biasanya 480
            final int TICKS_PER_SECOND = 960;

            // Kasih delay super kecil (sekitar 0.1 detik) di awal lagu biar aman dari reset player
            final long TICK_OFFSET = 100;

            // 2. Masukkan nada dari list chord
            boolean isGuitarSet = false; // Flag biar alat musik diganti sekali aja

            for (int i = 0; i < detectedChords.size(); i++) {
                ChordTimestamp current = detectedChords.get(i);

                // Lewati jika tidak ada chord yang terdeteksi
                if (current.getChordName().equals("-") || current.getChordName().equals("N/A")) continue;

                // Hitung kapan chord dimulai dan berakhir (dalam Ticks) ditambah Offset
                long startTick = TICK_OFFSET + (long) (current.getTimeSeconds() * TICKS_PER_SECOND);
                long endTick;

                // Durasi chord adalah sampai chord berikutnya berbunyi
                if (i < detectedChords.size() - 1) {
                    endTick = TICK_OFFSET + (long) (detectedChords.get(i + 1).getTimeSeconds() * TICKS_PER_SECOND);
                } else {
                    // Untuk chord terakhir, beri durasi default 2 detik
                    endTick = startTick + (2 * TICKS_PER_SECOND);
                }


                if (!isGuitarSet) {
                    long pcTick = startTick - 10;
                    com.leff.midi.event.ProgramChange changeInstrument = new com.leff.midi.event.ProgramChange(pcTick, 0, 25);
                    noteTrack.insertEvent(changeInstrument);
                    isGuitarSet = true;
                }

                // Dapatkan angka MIDI (Root, 3rd, 5th)
                int[] midiNotes = getMidiNotesForChord(current.getChordName());

                for (int note : midiNotes) {
                    NoteOn noteOn = new NoteOn(startTick, 0, note, 100);
                    NoteOff noteOff = new NoteOff(endTick, 0, note, 0);

                    noteTrack.insertEvent(noteOn);
                    noteTrack.insertEvent(noteOff);
                }
            }

            // 3. Gabungkan Track dan Simpan ke File
            MidiFile midiFile = new MidiFile(RESOLUTION);
            midiFile.addTrack(tempoTrack);
            midiFile.addTrack(noteTrack);

            // Simpan ke direktori Music internal aplikasi
            File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (!outputDir.exists()) outputDir.mkdirs();

            File outputFile = new File(outputDir, fileName + ".mid");
            midiFile.writeToFile(outputFile);

            Log.d(TAG, "Berhasil mengekspor MIDI ke: " + outputFile.getAbsolutePath());
            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Gagal mengekspor MIDI", e);
            return null;
        }
    }

    // Method pemetaan Chord String -> MIDI Notes (C4 = 60)
    private static int[] getMidiNotesForChord(String chord) {
        String rootStr = chord.split(" ")[0]
                .replace("Ab", "G#").replace("Eb", "D#")
                .replace("Bb", "A#").replace("Db", "C#").replace("Gb", "F#");
        boolean isMinor = chord.contains("Minor");

        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int rootMidi = 60; // Mulai dari C4 (Oktav tengah)

        for (int i = 0; i < notes.length; i++) {
            if (notes[i].equals(rootStr)) {
                rootMidi = 60 + i;
                break;
            }
        }

        // Rumus Semitone: Major = +4, Minor = +3. Perfect 5th selalu +7
        int thirdMidi = rootMidi + (isMinor ? 3 : 4);
        int fifthMidi = rootMidi + 7;

        return new int[]{rootMidi, thirdMidi, fifthMidi};
    }
}