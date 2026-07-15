package com.finanzapp.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.finanzapp.app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding =
                ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        applyWindowInsets(binding);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_main);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(
                    binding.bottomNavigation,
                    navController
            );
        }
    }

    /**
     * Con edge-to-edge activo por defecto (targetSdk reciente), el contenido se
     * dibuja detrás de la status bar y de la barra de navegación del sistema.
     * Empujamos el contenedor de fragments hacia abajo (status bar) y añadimos
     * padding inferior al bottom nav (barra de gestos), sin cambiar su altura visual.
     */
    private void applyWindowInsets(ActivityMainBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.navHostFragmentMain.setPadding(0, systemBars.top, 0, 0);

            binding.bottomNavigation.setPadding(
                    binding.bottomNavigation.getPaddingLeft(),
                    binding.bottomNavigation.getPaddingTop(),
                    binding.bottomNavigation.getPaddingRight(),
                    systemBars.bottom
            );

            return windowInsets;
        });
    }
}