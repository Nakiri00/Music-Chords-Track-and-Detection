package com.example.musicchords;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private HistoryAdapter adapter;
    private List<ChordHistory> historyList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_history);
        emptyView = view.findViewById(R.id.tv_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        loadHistory();
    }

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    historyList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            historyList.add(doc.toObject(ChordHistory.class));
                        }
                        emptyView.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // === INNER CLASS ADAPTER ===
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<ChordHistory> list;
        private SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        public HistoryAdapter(List<ChordHistory> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Ubah ini untuk menggunakan layout kartu yang baru dibuat
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChordHistory item = list.get(position);

            // 1. Set Data ke Tampilan Kartu
            // Karena di database kita baru punya 'title', kita pakai itu dulu.
            // (Tips: Jika judul dari YouTube biasanya "Artis - Lagu", Anda bisa memisahnya dengan fungsi split("-") jika mau).
            holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Tanpa Judul");

            String dateStr = "-";
            if(item.getTimestamp() != null) {
                dateStr = sdf.format(item.getTimestamp().toDate());
            }
            holder.tvDate.setText(dateStr);

            // 2. Logika KLIK untuk Memunculkan Hasil (Pop-up Dialog)
            holder.itemView.setOnClickListener(v -> {
                showResultDialog(item.getTitle(), item.getResult());
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                // Binding ke ID yang ada di item_history_card.xml
                tvTitle = itemView.findViewById(R.id.tv_history_title);
                tvDate = itemView.findViewById(R.id.tv_history_date);
            }
        }
    }

    // Fungsi Helper untuk menampilkan Pop-up Hasil
    private void showResultDialog(String title, String result) {
        new AlertDialog.Builder(getContext())
                .setTitle("Hasil: " + title)
                .setMessage(result != null ? result : "Tidak ada data hasil.")
                .setPositiveButton("Tutup", (dialog, which) -> dialog.dismiss())
                .setNeutralButton("Salin", (dialog, which) -> {
                    // Opsional: Fitur copy ke clipboard
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Chord Result", result);
                    clipboard.setPrimaryClip(clip);
                })
                .show();
    }
}