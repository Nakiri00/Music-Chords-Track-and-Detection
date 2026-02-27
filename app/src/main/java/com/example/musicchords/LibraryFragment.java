package com.example.musicchords;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

public class LibraryFragment extends Fragment {

    private RecyclerView rvChords;

    public LibraryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Menghubungkan fragment ini dengan layout fragment_library.xml
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inisialisasi RecyclerView dari layout
        rvChords = view.findViewById(R.id.rv_chords);

        // Nanti kita akan tambahkan pengaturan RecyclerView (Adapter, LayoutManager) di sini
    }
}