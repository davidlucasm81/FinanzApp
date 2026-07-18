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

            // "Cuando este en la lista de movimientos, si doy al boton de dashboard que vuelva al dashboard"
            // Ensure that clicking on an already selected tab (or one that is parent in the stack)
            // returns to its start destination.
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.dashboardFragment) {
                    // If we are already on the dashboard or any of its children (like transaction list),
                    // popping back stack to dashboard ensures we return there.
                    navController.popBackStack(R.id.dashboardFragment, false);
                }
                return NavigationUI.onNavDestinationSelected(item, navController);
            });

            binding.bottomNavigation.setOnItemReselectedListener(item -> {
                if (item.getItemId() == R.id.dashboardFragment) {
                    navController.popBackStack(R.id.dashboardFragment, false);
                }
            });
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