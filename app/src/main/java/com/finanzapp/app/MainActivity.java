package com.finanzapp.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.finanzapp.app.data.monetization.AdsRepository;
import com.finanzapp.app.data.monetization.BillingRepository;
import com.finanzapp.app.data.model.Notification;
import com.finanzapp.app.databinding.ActivityMainBinding;
import com.finanzapp.app.viewmodel.NotificationViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private View currentNotificationView;
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private static final int NOTIFICATION_DURATION_MS = 5000;
    private int statusBarHeight = 0;
    private final Queue<Notification> notificationQueue = new LinkedList<>();
    private boolean isShowingNotification = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding =
                ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        setupNotificationViewModel();
        applyWindowInsets(binding);
        setupMonetization();

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

    private void setupNotificationViewModel() {
        FinanzAppApplication.AppContainer container = ((FinanzAppApplication) getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(container);
        NotificationViewModel notificationViewModel = new ViewModelProvider(this, factory).get(NotificationViewModel.class);

        notificationViewModel.getNotificationEvent().observe(this, notification -> {
            notificationQueue.add(notification);
            processNextNotification();
        });
    }

    private void processNextNotification() {
        if (isShowingNotification || notificationQueue.isEmpty()) {
            return;
        }

        Notification nextNotification = notificationQueue.poll();
        if (nextNotification != null) {
            showNotificationPopUp(nextNotification);
        }
    }

    private void showNotificationPopUp(Notification notification) {
        isShowingNotification = true;
        
        ViewGroup root = findViewById(android.R.id.content);
        View notificationView = getLayoutInflater().inflate(R.layout.layout_notification_popup, root, false);
        TextView tvTitle = notificationView.findViewById(R.id.tv_notif_title);
        TextView tvBody = notificationView.findViewById(R.id.tv_notif_body);
        LinearProgressIndicator progressBar = notificationView.findViewById(R.id.progress_bar);

        tvTitle.setText(notification.getTitle());
        tvBody.setText(notification.getBody());

        setupSwipeToDismiss(notificationView, root);
        
        root.addView(notificationView);

        // Position it just below the status bar
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        int topOffset = statusBarHeight + margin;

        notificationView.setTranslationY(-500); // Start off-screen
        notificationView.animate().translationY(topOffset).setDuration(300).start(); 

        currentNotificationView = notificationView;

        final long startTime = System.currentTimeMillis();
        // Check for more notifications
        Runnable notificationRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentNotificationView != notificationView) return;

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= NOTIFICATION_DURATION_MS) {
                    notificationView.animate().translationY(-500).setDuration(300).withEndAction(() -> {
                        if (currentNotificationView == notificationView) {
                            root.removeView(notificationView);
                            currentNotificationView = null;
                            isShowingNotification = false;
                            processNextNotification(); // Check for more notifications
                        }
                    }).start();
                } else {
                    int progress = (int) (100 - (elapsed * 100 / NOTIFICATION_DURATION_MS));
                    progressBar.setProgress(progress);
                    notificationHandler.postDelayed(this, 30);
                }
            }
        };
        notificationHandler.post(notificationRunnable);
    }

    private void setupSwipeToDismiss(View view, ViewGroup parent) {
        ViewConfiguration vc = ViewConfiguration.get(this);
        int swipeThreshold = vc.getScaledTouchSlop();
        int minVelocity = vc.getScaledMinimumFlingVelocity();
        int maxVelocity = vc.getScaledMaximumFlingVelocity();

        view.setOnTouchListener(new View.OnTouchListener() {
            private float initialX;
            private float initialTranslationX;
            private VelocityTracker velocityTracker;
            private boolean isSwiping = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getRawX();
                        initialTranslationX = v.getTranslationX();
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        } else {
                            velocityTracker.clear();
                        }
                        velocityTracker.addMovement(event);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (velocityTracker != null) {
                            velocityTracker.addMovement(event);
                        }
                        float deltaX = event.getRawX() - initialX;
                        if (!isSwiping && Math.abs(deltaX) > swipeThreshold) {
                            isSwiping = true;
                        }

                        if (isSwiping) {
                            v.setTranslationX(initialTranslationX + deltaX);
                            // Fade out as we swipe
                            float alpha = 1.0f - (Math.abs(deltaX) / (v.getWidth() / 1.5f));
                            v.setAlpha(Math.max(0.2f, alpha));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!isSwiping) {
                            if (velocityTracker != null) {
                                velocityTracker.recycle();
                                velocityTracker = null;
                            }
                            v.performClick();
                            return false;
                        }

                        float finalDeltaX = event.getRawX() - initialX;
                        boolean dismiss = false;

                        if (velocityTracker != null) {
                            velocityTracker.addMovement(event);
                            velocityTracker.computeCurrentVelocity(1000, maxVelocity);
                            float velocityX = velocityTracker.getXVelocity();
                            if (Math.abs(velocityX) > minVelocity) {
                                dismiss = true;
                            }
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }

                        if (Math.abs(finalDeltaX) > v.getWidth() / 3f) {
                            dismiss = true;
                        }

                        if (dismiss) {
                            float targetX = finalDeltaX > 0 ? v.getWidth() : -v.getWidth();
                            v.animate()
                                    .translationX(targetX)
                                    .alpha(0)
                                    .setDuration(200)
                                    .withEndAction(() -> dismissNotification(v, parent))
                                    .start();
                        } else {
                            v.animate()
                                    .translationX(initialTranslationX)
                                    .alpha(1.0f)
                                    .setDuration(200)
                                    .start();
                        }
                        isSwiping = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void dismissNotification(View view, ViewGroup parent) {
        if (currentNotificationView == view) {
            notificationHandler.removeCallbacksAndMessages(null);
            parent.removeView(view);
            currentNotificationView = null;
            isShowingNotification = false;
            processNextNotification();
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
            statusBarHeight = systemBars.top;

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

    private void setupMonetization() {
        FinanzAppApplication.AppContainer container = ((FinanzAppApplication) getApplication()).getAppContainer();
        com.finanzapp.app.data.repository.AuthRepository authRepository = container.getAuthRepository();
        BillingRepository billingRepository = container.getBillingRepository();
        AdsRepository adsRepository = container.getAdsRepository();
        
        billingRepository.getIsPremium().observe(this, isPremium -> {
            // isPremium will be null if UNKNOWN, false if not premium, true if premium.
            // BillingRepository now only resolves to NONE/PURCHASED/WHITELIST if logged in.
            if (Boolean.FALSE.equals(isPremium)) {
                if (authRepository.isLoggedIn()) {
                    // Initialize Ads only for non-premium logged-in users
                    adsRepository.init(this, canRequestAds -> 
                        android.util.Log.d("MainActivity", "Ads initialization complete. Can request ads: " + canRequestAds));
                } else {
                    android.util.Log.d("MainActivity", "User not logged in. Postponing ads initialization.");
                }
            } else if (Boolean.TRUE.equals(isPremium)) {
                android.util.Log.d("MainActivity", "Skipping ads initialization for premium user.");
            }
        });
    }
}