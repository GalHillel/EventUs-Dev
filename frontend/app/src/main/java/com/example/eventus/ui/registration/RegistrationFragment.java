package com.example.eventus.ui.registration;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventus.R;

public class RegistrationFragment extends Fragment {

    private RegistrationViewModel registrationViewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        registrationViewModel = new ViewModelProvider(this).get(RegistrationViewModel.class);

        EditText emailEditText = view.findViewById(R.id.email);
        EditText passwordEditText = view.findViewById(R.id.password);
        EditText passwordValidationEditText = view.findViewById(R.id.passwordValidation);
        EditText usernameEditText = view.findViewById(R.id.username);
        RadioButton radioOrganizer = view.findViewById(R.id.radioOrganizer);
        Button registerButton = view.findViewById(R.id.register);

        registerButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String passwordValidation = passwordValidationEditText.getText().toString().trim();
            String username = usernameEditText.getText().toString().trim();
            String userType = radioOrganizer.isChecked() ? "Organizer" : "Participant";

            registrationViewModel.register(email, password, passwordValidation, username, userType);
        });

        registrationViewModel.getRegistrationSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(requireContext(), "Registration successful!", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(RegistrationFragment.this).navigate(R.id.action_registrationFragment_to_loginFragment);
            } else {
                Toast.makeText(requireContext(), "Registration failed. Please try again later.", Toast.LENGTH_SHORT).show();
            }
        });

        registrationViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        TextView loginLink = view.findViewById(R.id.loginLink);
        loginLink.setOnClickListener(v -> NavHostFragment.findNavController(RegistrationFragment.this).navigate(R.id.action_registrationFragment_to_loginFragment));
    }
}
