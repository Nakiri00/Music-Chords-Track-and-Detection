package com.example.musicchords;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChordAdapter extends RecyclerView.Adapter<ChordAdapter.ChordViewHolder> {

    private Context context;
    private List<Chord> chordList;
    private MediaPlayer mediaPlayer;

    public ChordAdapter(Context context, List<Chord> chordList) {
        this.context = context;
        this.chordList = chordList;
    }

    @NonNull
    @Override
    public ChordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chord, parent, false);
        return new ChordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChordViewHolder holder, int position) {
        Chord chord = chordList.get(position);

        // Pasang nama dan teks senar
        holder.tvChordName.setText(chord.getChordName());
        holder.tvChordStrings.setText("Senar: " + chord.getStrings());

        // MENGGAMBAR DIAGRAM AKOR SECARA OTOMATIS!
        holder.chordView.setChordPositions(chord.getStrings());

        holder.btnPlayAudio.setOnClickListener(v -> {
            try {
                if (mediaPlayer != null) mediaPlayer.release();
                if (chord.getAudioResId() != 0) {
                    mediaPlayer = MediaPlayer.create(context, chord.getAudioResId());
                    mediaPlayer.start();
                    Toast.makeText(context, "Memutar " + chord.getChordName(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Suara belum tersedia untuk akor ini", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public int getItemCount() {
        return chordList.size();
    }

    public static class ChordViewHolder extends RecyclerView.ViewHolder {
        ChordView chordView; // Berubah dari ImageView menjadi ChordView
        TextView tvChordName;
        TextView tvChordStrings;
        ImageButton btnPlayAudio;

        public ChordViewHolder(@NonNull View itemView) {
            super(itemView);
            chordView = itemView.findViewById(R.id.cv_chord_diagram);
            tvChordName = itemView.findViewById(R.id.tv_chord_name);
            tvChordStrings = itemView.findViewById(R.id.tv_chord_strings);
            btnPlayAudio = itemView.findViewById(R.id.btn_play_audio);
        }
    }
}