package com.example.eventus.ui.registration;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.eventus.data.Database;
import com.example.eventus.data.model.User;

public class RegistrationViewModel extends ViewModel {

    private final MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public LiveData<Boolean> getRegistrationSuccess() {
        return registrationSuccess;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void register(String email, String password, String passwordValidation, String username, String userType) {
        if (email.isEmpty() || password.isEmpty() || passwordValidation.isEmpty() || username.isEmpty()) {
            errorMessage.setValue("Please fill in all fields.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue("Invalid email format. Please enter a valid email address.");
            return;
        }

        if (!passwordValidation.equals(password)) {
            errorMessage.setValue("Passwords do not match. Please try again.");
            return;
        }

        try {
            User user = Database.addUser(email, username, password, userType);

            registrationSuccess.setValue(user != null);
            errorMessage.setValue(user == null ? "Registration failed. Please try again." : null);
        } catch (Exception e) {
            errorMessage.setValue("An error occurred during registration.");
        }
    }
}
