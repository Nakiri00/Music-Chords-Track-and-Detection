package com.example.musicchords;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.musicchords.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;

    private final Fragment homeFragment = new HomeFragment();
    private final Fragment historyFragment = new HistoryFragment();
    private final Fragment libraryFragment = new LibraryFragment();
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Nonaktifkan nav sampai auth & fragment siap
        binding.navMenu.setEnabled(false);

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main),
            (v, insets) -> {
                Insets sb = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                );
                v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
                return insets;
            }
        );

        // Observe auth state dari ViewModel
        viewModel
            .getAuthState()
            .observe(this, state -> {
                switch (state) {
                    case SUCCESS:
                        setupFragments();
                        break;
                    case FAILED:
                        Toast.makeText(
                            this,
                            "Gagal Login Database.",
                            Toast.LENGTH_SHORT
                        ).show();
                        break;
                    case LOADING:
                        // Tampilkan loading indicator jika perlu
                        break;
                }
            });

        viewModel.ensureAuthenticated();
    }

    private void setupFragments() {
        var fm = getSupportFragmentManager();
        var ft = fm.beginTransaction();
        if (!historyFragment.isAdded()) ft
            .add(R.id.main_frame, historyFragment, "HISTORY")
            .hide(historyFragment);
        if (!homeFragment.isAdded()) ft.add(
            R.id.main_frame,
            homeFragment,
            "HOME"
        );
        if (!libraryFragment.isAdded()) ft
            .add(R.id.main_frame, libraryFragment, "LIBRARY")
            .hide(libraryFragment);
        ft.commit();
        activeFragment = homeFragment;

        binding.navMenu.setEnabled(true);
        binding.navMenu.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                showHideFragment(homeFragment);
                return true;
            } else if (id == R.id.library) {
                showHideFragment(libraryFragment);
                return true;
            } else if (id == R.id.history) {
                showHideFragment(historyFragment);
                return true;
            }
            return false;
        });
    }

    private void showHideFragment(Fragment target) {
        if (target == activeFragment) return;
        getSupportFragmentManager()
            .beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit();
        activeFragment = target;
    }

    public void playSongFromHistory(Bundle bundle) {
        binding.navMenu.setSelectedItemId(R.id.home);
        ((HomeFragment) homeFragment).loadHistoryData(bundle);
    }
}
