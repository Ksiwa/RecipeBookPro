package com.recipebookpro.presentation.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.recipebookpro.R;
import com.recipebookpro.data.repository.NotificationRepositoryImpl;
import com.recipebookpro.domain.repository.NotificationRepository;
import com.recipebookpro.util.NotificationHelper;
import com.recipebookpro.service.NotificationService;

public class MainActivity extends BaseActivity {

    private static final String EXTRA_START_DESTINATION =
            "com.recipebookpro.presentation.ui.MainActivity.EXTRA_START_DESTINATION";

    private NavController navController;
    private NotificationRepository notificationRepository;
    private BadgeDrawable badgeDrawable;

    public static Intent createIntent(Context context) {
        return new Intent(context, MainActivity.class);
    }

    public static Intent createProfileStartIntent(Context context) {
        return createIntent(context).putExtra(EXTRA_START_DESTINATION, R.id.profileFragment);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Clean up legacy health cache preferences to avoid stale data showing across profiles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteSharedPreferences("HealthWarningCachePrefs");
        } else {
            getSharedPreferences("HealthWarningCachePrefs", android.content.Context.MODE_PRIVATE).edit().clear().apply();
        }
        
        setContentView(R.layout.activity_main);

        // Apply top inset to AppBarLayout so toolbar doesn't go behind status bar
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, 0);
            return windowInsets; // Pass insets down to siblings
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        View navHost = findViewById(R.id.nav_host_fragment);

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            if (navHost != null && v.getHeight() > 0) {
                navHost.setPadding(0, 0, 0, v.getHeight() + insets.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        bottomNav.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (navHost != null) {
                // system bottom inset is already added via bottomNav's own paddingBottom
                navHost.setPadding(0, 0, 0, bottomNav.getHeight());
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);
            NavigationUI.setupActionBarWithNavController(this, navController);
            
            bottomNav.setOnItemSelectedListener(item -> {
                // If we are on NotificationFragment, pop it so it isn't saved in the tab's backstack
                if (navController.getCurrentDestination() != null && 
                    navController.getCurrentDestination().getId() == R.id.notificationFragment) {
                    navController.popBackStack();
                }
                return NavigationUI.onNavDestinationSelected(item, navController);
            });

            handleRequestedStartDestination(bottomNav, savedInstanceState);
        }

        notificationRepository = new NotificationRepositoryImpl();
        NotificationHelper.createNotificationChannel(this);
        checkNotificationPermission();
        observeNotifications();
        startNotificationService();
    }

    private void handleRequestedStartDestination(BottomNavigationView bottomNav, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        int requestedDestination = getIntent().getIntExtra(EXTRA_START_DESTINATION, 0);
        if (requestedDestination == R.id.profileFragment) {
            bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.profileFragment));
        }
    }

    private void startNotificationService() {
        Intent intent = new Intent(this, NotificationService.class);
        startService(intent);
    }

    private void observeNotifications() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        notificationRepository.getNotifications(uid).observe(this, notifications -> {
            if (notifications == null) return;
            int unreadCount = 0;
            for (com.recipebookpro.domain.model.Notification n : notifications) {
                if (!n.isRead()) unreadCount++;
            }
            updateNotificationBadge(unreadCount);
        });
    }

    private void updateNotificationBadge(int count) {
        if (badgeDrawable == null) return;
        if (count > 0) {
            badgeDrawable.setVisible(true);
            badgeDrawable.setNumber(count);
        } else {
            badgeDrawable.setVisible(false);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.permission_required)
                            .setMessage(R.string.notification_permission_rationale)
                            .setPositiveButton(R.string.accept, (dialog, which) -> {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }
        }
    }

    @com.google.android.material.badge.ExperimentalBadgeUtils
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        if (notificationItem != null) {
            badgeDrawable = BadgeDrawable.create(this);
            badgeDrawable.setVisible(false);
            
            MaterialToolbar toolbar = findViewById(R.id.topAppBar);
            toolbar.post(() -> {
                BadgeUtils.attachBadgeDrawable(badgeDrawable, toolbar, R.id.action_notifications);
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            navController.navigate(R.id.notificationFragment);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp()
                || super.onSupportNavigateUp();
    }
}
