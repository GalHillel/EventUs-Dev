package com.example.eventus.ui.mainScreen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import com.example.eventus.R;

public class MainMenuUser extends Fragment {

    public MainMenuUser() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners(view);
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.loginRegisterButton).setOnClickListener(v -> navigateToLoginFragment());
    }

    private void navigateToLoginFragment() {
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.navigate(R.id.action_mainMenuUserFragment_to_loginFragment);
    }
}
