package com.example.musicchords;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    // ── Views ────────────────────────────────────────────────────────────────
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextInputEditText etSearch;
    private Chip chipSortDate; // replaces chipSort
    private Chip chipSortTitle; // new
    private LinearLayout layoutPagination;
    private TextView tvPageInfo;
    private Button btnPrev, btnNext;

    // ── Adapter data (current page only) ────────────────────────────────────
    private HistoryAdapter adapter;
    private final List<ChordHistory> historyList = new ArrayList<>();
    private final List<String> documentIds = new ArrayList<>();

    // ── Full dataset from Firestore ──────────────────────────────────────────
    private final List<ChordHistory> masterList = new ArrayList<>();
    private final List<String> masterDocIds = new ArrayList<>();

    // ── Filtered dataset (search + sort applied) ─────────────────────────────
    private final List<ChordHistory> filteredList = new ArrayList<>();
    private final List<String> filteredDocIds = new ArrayList<>();

    // ── State ────────────────────────────────────────────────────────────────
    private static final int PAGE_SIZE = 5;
    private static final int SORT_DATE = 0; // sort by analysis timestamp
    private static final int SORT_TITLE = 1; // sort by song title A-Z
    private int currentPage = 0;
    private int totalPages = 1;
    private String searchQuery = "";
    private int sortType = SORT_DATE; // active sort dimension
    private boolean sortAscending = false; // false = newest / Z-A  true = oldest / A-Z

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(
        @NonNull View view,
        @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        recyclerView = view.findViewById(R.id.recycler_history);
        emptyView = view.findViewById(R.id.tv_empty);
        etSearch = view.findViewById(R.id.et_search);
        chipSortDate = view.findViewById(R.id.chip_sort_date);
        chipSortTitle = view.findViewById(R.id.chip_sort_title);
        layoutPagination = view.findViewById(R.id.layout_pagination);
        tvPageInfo = view.findViewById(R.id.tv_page_info);
        btnPrev = view.findViewById(R.id.btn_prev_page);
        btnNext = view.findViewById(R.id.btn_next_page);

        // RecyclerView setup
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter(historyList, documentIds);
        recyclerView.setAdapter(adapter);

        // Tap card → open in HomeFragment
        adapter.setOnItemClickListener(item -> {
            if (item.getFilePath() == null || item.getFilePath().isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "File audio tidak ditemukan di data history.",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putString("audioPath", item.getFilePath());
            bundle.putString("songTitle", item.getTitle());
            bundle.putString("chordData", item.getResult());
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).playSongFromHistory(bundle);
            }
        });

        // Tap delete icon → confirm dialog
        adapter.setOnDeleteListener((docId, filePath, item) ->
            DialogHelper.showDestructiveDialog(
                requireContext(),
                "Hapus Riwayat",
                "Hapus riwayat \"" +
                    item.getTitle() +
                    "\"?\nFile audio lokal juga akan dihapus.",
                "Hapus",
                () -> deleteHistoryItem(docId, filePath)
            )
        );

        // Real-time search
        etSearch.addTextChangedListener(
            new TextWatcher() {
                @Override
                public void beforeTextChanged(
                    CharSequence s,
                    int st,
                    int c,
                    int a
                ) {}

                @Override
                public void onTextChanged(
                    CharSequence s,
                    int st,
                    int b,
                    int c
                ) {}

                @Override
                public void afterTextChanged(Editable s) {
                    searchQuery = s.toString().trim();
                    applyFiltersAndPaginate(true); // reset to page 1 on new query
                }
            }
        );

        // Sort toggle chip
        // Tap "Tanggal" chip:
        //   – if already active → toggle ↓ ↑ direction
        //   – if switching from Title → switch to Date sort (default: newest ↓)
        chipSortDate.setOnClickListener(v -> {
            if (sortType == SORT_DATE) {
                sortAscending = !sortAscending; // toggle direction
            } else {
                sortType = SORT_DATE;
                sortAscending = false; // default: newest first
            }
            chipSortDate.setChecked(true);
            chipSortTitle.setChecked(false);
            updateSortChipLabels();
            applyFiltersAndPaginate(true);
        });

        // Tap "Judul" chip:
        //   – if already active → toggle A-Z / Z-A direction
        //   – if switching from Date → switch to Title sort (default: A-Z)
        chipSortTitle.setOnClickListener(v -> {
            if (sortType == SORT_TITLE) {
                sortAscending = !sortAscending; // toggle direction
            } else {
                sortType = SORT_TITLE;
                sortAscending = true; // default: A-Z
            }
            chipSortDate.setChecked(false);
            chipSortTitle.setChecked(true);
            updateSortChipLabels();
            applyFiltersAndPaginate(true);
        });

        // Pagination
        btnPrev.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                applyPage();
                updatePaginationUI();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                applyPage();
                updatePaginationUI();
            }
        });

        loadHistory();
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener((value, error) -> {
                if (error != null) return;

                masterList.clear();
                masterDocIds.clear();

                if (value != null) {
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        ChordHistory h = doc.toObject(ChordHistory.class);
                        if (h != null) {
                            masterList.add(h);
                            masterDocIds.add(doc.getId());
                        }
                    }
                }
                // After Firestore reload, preserve page position if still valid
                applyFiltersAndPaginate(false);
            });
    }

    private void deleteHistoryItem(String docId, String filePath) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("history")
            .document(docId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                if (filePath != null && !filePath.isEmpty()) {
                    File f = new File(filePath);
                    if (f.exists()) f.delete();
                }
                if (getContext() != null) {
                    Toast.makeText(
                        getContext(),
                        "Riwayat berhasil dihapus",
                        Toast.LENGTH_SHORT
                    ).show();
                }
                // addSnapshotListener auto-refreshes the list
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(
                        getContext(),
                        "Gagal menghapus riwayat",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            });
    }

    // ── Filter + Paginate ─────────────────────────────────────────────────────

    /**
     * Filters masterList by searchQuery + sort order → filteredList,
     * then slices the correct page into historyList (adapter source).
     *
     * @param resetPage true  → always go to page 1 (on search/sort change)
     *                  false → preserve current page if still in range (on Firestore reload)
     */
    private void applyFiltersAndPaginate(boolean resetPage) {
        filteredList.clear();
        filteredDocIds.clear();

        // 1. Apply search filter
        String query = searchQuery.toLowerCase(Locale.getDefault());
        for (int i = 0; i < masterList.size(); i++) {
            ChordHistory item = masterList.get(i);
            String title =
                item.getTitle() != null
                    ? item.getTitle().toLowerCase(Locale.getDefault())
                    : "";
            if (query.isEmpty() || title.contains(query)) {
                filteredList.add(item);
                filteredDocIds.add(masterDocIds.get(i));
            }
        }

        // 2. Apply sort
        if (sortType == SORT_DATE) {
            // masterList from Firestore is already newest-first (DESCENDING).
            // sortAscending=false → keep as-is (newest first)
            // sortAscending=true  → reverse to get oldest first
            if (sortAscending) {
                Collections.reverse(filteredList);
                Collections.reverse(filteredDocIds);
            }
        } else {
            // SORT_TITLE
            // Sort both lists together using an index list to keep them in sync.
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < filteredList.size(); i++) indices.add(i);

            final boolean asc = sortAscending;
            indices.sort((a, b) -> {
                String tA =
                    filteredList.get(a).getTitle() != null
                        ? filteredList.get(a).getTitle()
                        : "";
                String tB =
                    filteredList.get(b).getTitle() != null
                        ? filteredList.get(b).getTitle()
                        : "";
                int cmp = tA.compareToIgnoreCase(tB);
                return asc ? cmp : -cmp; // asc=true → A-Z,  asc=false → Z-A
            });

            // Rebuild both lists in the sorted order
            List<ChordHistory> sortedHistory = new ArrayList<>();
            List<String> sortedIds = new ArrayList<>();
            for (int i : indices) {
                sortedHistory.add(filteredList.get(i));
                sortedIds.add(filteredDocIds.get(i));
            }
            filteredList.clear();
            filteredList.addAll(sortedHistory);
            filteredDocIds.clear();
            filteredDocIds.addAll(sortedIds);
        }

        // 3. Recalculate total pages
        totalPages = Math.max(
            1,
            (int) Math.ceil((double) filteredList.size() / PAGE_SIZE)
        );

        // 4. Clamp current page
        currentPage = resetPage
            ? 0
            : Math.max(0, Math.min(currentPage, totalPages - 1));

        // 5. Slice page → adapter
        applyPage();

        // 6. Refresh UI
        updatePaginationUI();
    }

    /**
     * Updates the text on both chips to reflect the current sort type and direction.
     * The active chip shows an arrow; the inactive chip shows its default label.
     */
    private void updateSortChipLabels() {
        if (sortType == SORT_DATE) {
            chipSortDate.setText(sortAscending ? "Oldest" : "Newest");
            chipSortTitle.setText("Title A-Z"); // reset to default
        } else {
            chipSortDate.setText("Newest"); // reset to default
            chipSortTitle.setText(sortAscending ? "Title A-Z" : "Title Z-A");
        }
    }

    /** Copies the current page slice from filteredList into historyList (adapter source). */
    private void applyPage() {
        historyList.clear();
        documentIds.clear();

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filteredList.size());

        for (int i = start; i < end; i++) {
            historyList.add(filteredList.get(i));
            documentIds.add(filteredDocIds.get(i));
        }

        adapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(0);
    }

    private void updatePaginationUI() {
        // Empty state message
        if (filteredList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(
                searchQuery.isEmpty()
                    ? "Belum ada riwayat"
                    : "Tidak ada hasil untuk \"" + searchQuery + "\""
            );
        } else {
            emptyView.setVisibility(View.GONE);
        }

        // Pagination bar: only visible when there is more than 1 page
        if (totalPages > 1) {
            layoutPagination.setVisibility(View.VISIBLE);
            tvPageInfo.setText((currentPage + 1) + " / " + totalPages);
            btnPrev.setEnabled(currentPage > 0);
            btnNext.setEnabled(currentPage < totalPages - 1);
        } else {
            layoutPagination.setVisibility(View.GONE);
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static class HistoryAdapter
        extends RecyclerView.Adapter<HistoryAdapter.ViewHolder>
    {

        private final List<ChordHistory> list;
        private final List<String> documentIds;
        private final SimpleDateFormat sdf = new SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        );

        private OnItemClickListener listener;
        private OnDeleteListener deleteListener;

        public interface OnItemClickListener {
            void onItemClick(ChordHistory item);
        }

        public interface OnDeleteListener {
            void onDeleteClick(
                String documentId,
                String filePath,
                ChordHistory item
            );
        }

        public void setOnItemClickListener(OnItemClickListener l) {
            this.listener = l;
        }

        public void setOnDeleteListener(OnDeleteListener l) {
            this.deleteListener = l;
        }

        public HistoryAdapter(
            List<ChordHistory> list,
            List<String> documentIds
        ) {
            this.list = list;
            this.documentIds = documentIds;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
        ) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_history_card,
                parent,
                false
            );
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChordHistory item = list.get(position);
            String docId = documentIds.get(position);

            holder.tvTitle.setText(
                item.getTitle() != null ? item.getTitle() : "Tanpa Judul"
            );
            holder.tvDate.setText(
                item.getTimestamp() != null
                    ? sdf.format(item.getTimestamp().toDate())
                    : "-"
            );

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDeleteClick(
                    docId,
                    item.getFilePath(),
                    item
                );
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            TextView tvTitle, tvDate;
            ImageButton btnDelete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_history_title);
                tvDate = itemView.findViewById(R.id.tv_history_date);
                btnDelete = itemView.findViewById(R.id.btn_delete_history);
            }
        }
    }
}
