package com.example.musicchords;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.musicchords.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    // Deklarasi Fragment sebagai variabel global agar tidak dibuat ulang terus menerus
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment historyFragment = new HistoryFragment();

    // Variabel untuk melacak fragment mana yang sedang aktif
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            // Jika belum login, login dulu, BARU buka HomeFragment
            auth.signInAnonymously()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Log.d("Auth", "Login sukses");
                            setupFragments();// Buka Home setelah sukses
                        } else {
                            Toast.makeText(MainActivity.this, "Gagal Login Database.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            setupFragments();
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        binding.navMenu.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home){
                showHideFragment(homeFragment);
                return true;
            } else {
                showHideFragment(historyFragment);
                return true;
            }
        });
    }
    // Fungsi awal untuk menambahkan semua fragment ke container tapi sembunyikan yang tidak perlu
    private void setupFragments() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Tambahkan kedua fragment, tapi sembunyikan historyFragment
        // Gunakan tag agar mudah dicari jika perlu
        if (!historyFragment.isAdded()) {
            ft.add(R.id.main_frame, historyFragment, "HISTORY").hide(historyFragment);
        }
        if (!homeFragment.isAdded()) {
            ft.add(R.id.main_frame, homeFragment, "HOME");
        }

        ft.commit();
        activeFragment = homeFragment;
    }

    // Fungsi untuk menyembunyikan fragment aktif dan memunculkan target
    private void showHideFragment(Fragment targetFragment){
        if (targetFragment == activeFragment) return;

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        ft.hide(activeFragment); // Sembunyikan yang sekarang
        ft.show(targetFragment); // Tampilkan yang dituju
        ft.commit();

        activeFragment = targetFragment; // Update status aktif
    }
}