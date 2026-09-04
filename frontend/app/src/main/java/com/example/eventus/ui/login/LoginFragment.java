package com.example.eventus.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.databinding.FragmentLoginBinding;
import com.example.eventus.ui.screens.UserMainScreen.UserMainActivity;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView loginBtn = binding.login;
        TextView registerLink = binding.registerLink;

        loginBtn.setOnClickListener(v -> loginUser());

        registerLink.setOnClickListener(v ->
                NavHostFragment.findNavController(LoginFragment.this)
                        .navigate(R.id.action_loginFragment_to_registrationFragment));
    }

    private void loginUser() {
        String email = binding.email.getText().toString().trim();
        String password = binding.password.getText().toString().trim();
        boolean isOrganizer = binding.checkOrganizer.isChecked();

        // Default users for testing
        if (email.isEmpty() && password.isEmpty()) {
            email = isOrganizer ? "galorganizer@gmail.com" : "galp@gmail.com";
            password = "galPass";
        }

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Attempt to login using provided credentials
            LoggedInUser loggedInUser = Database.userLogin(email, password, isOrganizer ? "Organizer" : "Participant");

            // Clear input fields
            binding.email.setText("");
            binding.password.setText("");
            binding.checkOrganizer.setChecked(false);

            // Display success message
            Toast.makeText(requireContext(), "Welcome " + loggedInUser.getName(), Toast.LENGTH_SHORT).show();

            // Start UserMainActivity
            Intent intent = new Intent(requireContext(), UserMainActivity.class);
            intent.putExtra("user", loggedInUser);
            startActivity(intent);
        } catch (Exception e) {
            // Display error message if login fails
            Toast.makeText(requireContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
