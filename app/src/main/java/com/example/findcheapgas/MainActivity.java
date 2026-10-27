package com.example.findcheapgas;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.findcheapgas.ui.favorite.FavoriteFragment;
import com.example.findcheapgas.ui.map.MapFragment;
import com.example.findcheapgas.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_MAP = "tab_map";
    private static final String TAG_FAVORITE = "tab_favorite";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final String[] TABS = {TAG_MAP, TAG_FAVORITE, TAG_SETTINGS};

    private static final String KEY_CURRENT_TAB = "current_tab";

    private String currentTab = TAG_MAP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // super.onCreate보다 먼저 호출해야 로딩 화면이 보인다.
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // 아래쪽 시스템 바 여백은 BottomNavigationView가 직접 처리한다.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // 탭 Fragment 3개를 처음에 모두 붙여 두고 show/hide로 바꾼다.
        // 그래서 탭을 바꿔도 지도 화면이 새로 만들어지지 않는다.
        if (savedInstanceState == null) {
            Fragment favorite = new FavoriteFragment();
            Fragment settings = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container, new MapFragment(), TAG_MAP)
                    .add(R.id.fragment_container, favorite, TAG_FAVORITE)
                    .add(R.id.fragment_container, settings, TAG_SETTINGS)
                    .hide(favorite)
                    .hide(settings)
                    .commitNow();
        } else {
            // 화면 회전 등으로 다시 만들어지면 FragmentManager가 Fragment와 숨김 상태를 복원한다.
            currentTab = savedInstanceState.getString(KEY_CURRENT_TAB, TAG_MAP);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_favorite) {
                showTab(TAG_FAVORITE);
            } else if (id == R.id.nav_settings) {
                showTab(TAG_SETTINGS);
            } else {
                showTab(TAG_MAP);
            }
            return true;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_TAB, currentTab);
    }

    private void showTab(String tag) {
        if (tag.equals(currentTab)) {
            return;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction().setReorderingAllowed(true);
        for (String tab : TABS) {
            Fragment fragment = fragmentManager.findFragmentByTag(tab);
            if (fragment == null) {
                continue;
            }
            if (tab.equals(tag)) {
                transaction.show(fragment);
            } else {
                transaction.hide(fragment);
            }
        }
        transaction.commit();
        currentTab = tag;
    }
}
