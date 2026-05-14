package com.example.musicchords;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

public class HistoryFragment extends Fragment {

    private HistoryViewModel viewModel;
    private HistoryAdapter adapter;

    // Views
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextInputEditText etSearch;
    private Chip chipSortDate;
    private Chip chipSortTitle;
    private LinearLayout layoutPagination;
    private TextView tvPageInfo;
    private Button btnPrev, btnNext;

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
        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

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

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        // Klik item → buka di HomeFragment via MainActivity
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

        // Klik hapus → konfirmasi dialog
        adapter.setOnDeleteListener((docId, filePath, item) ->
            DialogHelper.showDestructiveDialog(
                requireContext(),
                "Hapus Riwayat",
                "Hapus riwayat \"" +
                    item.getTitle() +
                    "\"?\nFile audio lokal juga akan dihapus.",
                "Hapus",
                () -> viewModel.deleteItem(docId, filePath)
            )
        );

        setupClickListeners();
        setupObservers();

        // Mulai dengarkan Firestore (idempotent — aman dipanggil berkali-kali)
        viewModel.startListening();
    }

    private void setupClickListeners() {
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
                    viewModel.setSearchQuery(s.toString());
                }
            }
        );

        chipSortDate.setOnClickListener(v -> viewModel.setSortDate());
        chipSortTitle.setOnClickListener(v -> viewModel.setSortTitle());
        btnPrev.setOnClickListener(v -> viewModel.goToPrevPage());
        btnNext.setOnClickListener(v -> viewModel.goToNextPage());
    }

    private void setupObservers() {
        viewModel
            .getUiState()
            .observe(getViewLifecycleOwner(), state -> {
                // Update list
                adapter.updateData(state.items, state.docIds);
                recyclerView.scrollToPosition(0);

                // Empty state
                emptyView.setVisibility(
                    state.isEmpty ? View.VISIBLE : View.GONE
                );
                if (state.isEmpty) emptyView.setText(state.emptyMessage);

                // Pagination
                layoutPagination.setVisibility(
                    state.showPagination ? View.VISIBLE : View.GONE
                );
                if (state.showPagination) {
                    tvPageInfo.setText(
                        (state.currentPage + 1) + " / " + state.totalPages
                    );
                    btnPrev.setEnabled(state.currentPage > 0);
                    btnNext.setEnabled(
                        state.currentPage < state.totalPages - 1
                    );
                }

                // Sort chips
                chipSortDate.setChecked(state.dateChipActive);
                chipSortTitle.setChecked(!state.dateChipActive);
                chipSortDate.setText(state.dateChipLabel);
                chipSortTitle.setText(state.titleChipLabel);
            });

        viewModel
            .getToastMessage()
            .observe(getViewLifecycleOwner(), msg -> {
                if (msg != null && !msg.isEmpty() && isAdded()) {
                    Toast.makeText(
                        requireContext(),
                        msg,
                        Toast.LENGTH_SHORT
                    ).show();
                    viewModel.clearToastMessage();
                }
            });
    }
}
