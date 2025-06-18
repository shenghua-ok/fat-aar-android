package com.kezong.demo.libaar;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;

import com.kezong.demo.libaar.databinding.DatabindingBinding;

public class TestActivity extends FragmentActivity {

    private DatabindingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.databinding);
        User user = new User();
        user.setName("Hello World");
        user.setSex("[success][dataBinding] male");
        binding.setUser(user);
        // 手动添加 NavHostFragment
//        NavHostFragment navHostFragment = NavHostFragment.create(R.navigation.nav_graph);
//        getSupportFragmentManager()
//                .beginTransaction()
//                .replace(R.id.host_fragment, navHostFragment)
//                .setPrimaryNavigationFragment(navHostFragment)
//                .commit();
    }
}
