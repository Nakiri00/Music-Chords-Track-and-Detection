package com.example.musicchords;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibraryFragment extends Fragment {

    private RecyclerView rvChords;
    private ChordAdapter chordAdapter;
    private List<Chord> chordList;

    public static class GuitarData {
        public Map<String, List<ChordInfo>> chords;
    }

    public static class ChordInfo {
        public String key;
        public String suffix;
        public List<Position> positions;
    }

    public static class Position {
        public List<Integer> frets; // Menggunakan List<Integer> karena datanya [-1, 3, 2, 0, 1, 0]
    }

    public LibraryFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvChords = view.findViewById(R.id.rv_chords);
        rvChords.setLayoutManager(new LinearLayoutManager(getContext()));

        chordList = new ArrayList<>();

        // Mulai proses baca JSON
        loadChordsFromJson();

        chordAdapter = new ChordAdapter(getContext(), chordList);
        rvChords.setAdapter(chordAdapter);
    }

    private void loadChordsFromJson() {
        try {
            // Membuka file dari folder assets
            InputStream is = getContext().getAssets().open("guitar.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonString = new String(buffer, "UTF-8");

            // Mengubah JSON menjadi Objek Java
            Gson gson = new Gson();
            GuitarData guitarData = gson.fromJson(jsonString, GuitarData.class);

            if (guitarData != null && guitarData.chords != null) {
                // Kita ambil urutan nada dasar yang umum (C sampai B)
                String[] targetKeys = {"C", "D", "E", "F", "G", "A", "B"};

                for (String key : targetKeys) {
                    List<ChordInfo> chordListInfo = guitarData.chords.get(key);
                    if (chordListInfo != null) {
                        for (ChordInfo info : chordListInfo) {

                            if (info.suffix.equals("major") || info.suffix.equals("minor")) {

                                // Gabungkan nama kunci (contoh: "C" + " " + "major" = "C major")
                                String namaAkor = info.key + " " + info.suffix;

                                // Ambil posisi senar pertama yang paling mudah dimainkan
                                List<Integer> frets = info.positions.get(0).frets;

                                // Ubah array [-1, 3, 2, 0, 1, 0] menjadi teks "X 3 2 0 1 0"
                                StringBuilder fretStr = new StringBuilder();
                                for (int fretAngka : frets) {
                                    if (fretAngka == -1) {
                                        fretStr.append("X "); // -1 artinya senar tidak dipetik
                                    } else {
                                        fretStr.append(fretAngka).append(" ");
                                    }
                                }

                                // Masukkan ke dalam daftar tampilan
                                chordList.add(new Chord(
                                        namaAkor,
                                        fretStr.toString().trim(),
                                        R.drawable.ic_launcher_background, // Ikon bawaan sementara
                                        0 // Audio kosong sementara
                                ));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e("JSON_ERROR", "Gagal membaca file JSON", e);
        }
    }
}