package com.example.eventus.ui.screens.EditProfile;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.FileUploader;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.ServerResponse;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Objects;

public class EditProfileFragment extends Fragment {

    private EditText usernameEditText;
    private EditText emailEditText;
    private EditText bioEditText;
    private TextInputEditText oldPasswordEditText;
    private TextInputEditText newPasswordEditText;
    private ImageView profilePhotoImageView;
    Button saveUserDetailsButton;

    private boolean isValidProfilePic;
    HashMap<String, Object> updatedUserParams = new HashMap<>();
    EditProfileActivity holder;

    public EditProfileFragment(EditProfileActivity holder) {
        this.holder = holder;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        usernameEditText = view.findViewById(R.id.Username);
        emailEditText = view.findViewById(R.id.email);
        oldPasswordEditText = view.findViewById(R.id.oldPassword);
        newPasswordEditText = view.findViewById(R.id.newPassword);
        saveUserDetailsButton = view.findViewById(R.id.saveUserDetailsButton);
        bioEditText = view.findViewById(R.id.bio);
        profilePhotoImageView = view.findViewById(R.id.profilePhotoImageView);

        if (!this.holder.getUser().getProfile_pic().isEmpty()) {
            try {
                Bitmap profile_icon = Database.getProfilePic(this.holder.getUser().get_id());
                int width = getResources().getInteger(R.integer.profile_profilePicSize);
                int height = getResources().getInteger(R.integer.profile_profilePicSize);
                profile_icon = Bitmap.createScaledBitmap(profile_icon, width, height, false);
                profilePhotoImageView.setImageBitmap(profile_icon);

            } catch (ServerSideException e) {
                // Handle the exception (e.g., show an error message)
                e.printStackTrace();
            } catch (Exception e) {
                // Handle other exceptions
                e.printStackTrace();
            }
        }
        if (this.holder.getUser() != null) {
            usernameEditText.setText(this.holder.getUser().getName());
            bioEditText.setText(this.holder.getUser().getBio());
        }


        profilePhotoImageView.setOnClickListener(v -> choosePhotoFromGallery());

        saveUserDetailsButton.setOnClickListener(v -> {
            updatedUserParams = new HashMap<>();
            String newBio = Objects.requireNonNull(bioEditText.getText()).toString();
            String newUsername = Objects.requireNonNull(usernameEditText.getText()).toString();
            String newEmail = Objects.requireNonNull(emailEditText.getText()).toString();
            String oldPass = Objects.requireNonNull(oldPasswordEditText.getText()).toString();
            String newPass = Objects.requireNonNull(newPasswordEditText.getText()).toString();

            if (hasNewPassword()) {
                updatedUserParams.put("oldPassword", oldPass);
                updatedUserParams.put("password", newPass);
            }
            if (hasNewEmail()) {
                updatedUserParams.put("email", newEmail);
            }
            if (hasNewName()) {
                updatedUserParams.put("name", newUsername);
            }
            if (hasNewBio()) {
                updatedUserParams.put("bio", newBio);
            }
            if (isValidProfilePic) {
                saveProfilePicture();
                reset();
            } else if (!updatedUserParams.isEmpty()) {
                holder.updateParams(updatedUserParams);
                reset();
            }


        });


        setUpTextListeners();

    }

    public void reset() {
        oldPasswordEditText.setText("");
        newPasswordEditText.setText("");
        usernameEditText.setText(holder.getUser().getName());
        bioEditText.setText(holder.getUser().getBio());
        emailEditText.setText("");
        // Prints success message
        Toast.makeText(requireContext(), "Account details have changed successfully", Toast.LENGTH_SHORT).show();
        isValidProfilePic = false;
    }

    private void setUpTextListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButtonsState();
            }
        };

        TextWatcher newpPasswordListener = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!hasNewPassword()) {
                    newPasswordEditText.setError(null);
                    updateSaveButtonsState();
                    return;
                }

                String oldPass = Objects.requireNonNull(oldPasswordEditText.getText()).toString();
                String newPass = Objects.requireNonNull(newPasswordEditText.getText()).toString();

                if (oldPass.equals(newPass)) {
                    newPasswordEditText.setError("Passwords may not match");
                    updateSaveButtonsState();
                    return;
                }
                if (newPass.length() <= 5) {
                    newPasswordEditText.setError("The password length must be greater than 5");
                    updateSaveButtonsState();
                    return;
                }

                newPasswordEditText.setError(null);
                updateSaveButtonsState();


            }
        };
        TextWatcher oldPasswordListener = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!hasNewPassword()) {
                    oldPasswordEditText.setError(null);
                    updateSaveButtonsState();
                    return;
                }
                String oldPass = Objects.requireNonNull(oldPasswordEditText.getText()).toString();
                if (oldPass.length() <= 5) {
                    oldPasswordEditText.setError("The password length must be greater than 5");
                    updateSaveButtonsState();
                    return;
                }
                oldPasswordEditText.setError(null);
                updateSaveButtonsState();


            }
        };
        TextWatcher emailListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!hasNewEmail()) {
                    emailEditText.setError(null);
                    updateSaveButtonsState();
                    return;
                }
                String newEmail = Objects.requireNonNull(emailEditText.getText()).toString();
                if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    emailEditText.setError("Invalid Email address");
                    updateSaveButtonsState();
                    return;
                }
                emailEditText.setError(null);
                updateSaveButtonsState();


            }
        };
        TextWatcher usernameListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!hasNewName()) {
                    usernameEditText.setError(null);
                    updateSaveButtonsState();
                    return;
                }
                String newName = Objects.requireNonNull(usernameEditText.getText()).toString();
                if (newName.isEmpty()) {
                    usernameEditText.setError("Must have name");
                    updateSaveButtonsState();
                    return;
                }
                usernameEditText.setError(null);
                updateSaveButtonsState();
            }
        };


        usernameEditText.addTextChangedListener(usernameListener);
        emailEditText.addTextChangedListener(emailListener);
        oldPasswordEditText.addTextChangedListener(oldPasswordListener);
        newPasswordEditText.addTextChangedListener(newpPasswordListener);
        bioEditText.addTextChangedListener(textWatcher);
        // Initial state
        updateSaveButtonsState();
    }

    void updateSaveButtonsState() {
        boolean btnState = (hasNewName() || hasNewPassword() || hasNewEmail() || hasNewBio() || isValidProfilePic) && usernameEditText.getError() == null && emailEditText.getError() == null && oldPasswordEditText.getError() == null && newPasswordEditText.getError() == null && bioEditText.getError() == null;

        saveUserDetailsButton.setClickable(btnState);
        saveUserDetailsButton.setEnabled(btnState);


    }

    boolean hasNewName() {
        String newUsername = Objects.requireNonNull(usernameEditText.getText()).toString();
        return !holder.getUser().getName().equals(newUsername);
    }


    boolean hasNewPassword() {
        String oldPass = Objects.requireNonNull(oldPasswordEditText.getText()).toString();
        String newPass = Objects.requireNonNull(newPasswordEditText.getText()).toString();
        return !TextUtils.isEmpty(oldPass) && !TextUtils.isEmpty(newPass);
    }

    boolean hasNewEmail() {
        String newEmail = Objects.requireNonNull(emailEditText.getText()).toString();
        return !TextUtils.isEmpty(newEmail);
    }

    boolean hasNewBio() {
        String newBio = Objects.requireNonNull(bioEditText.getText()).toString();
        return !holder.getUser().getBio().equals(newBio);
    }

    // Method to choose a photo from the gallery
    private void choosePhotoFromGallery() {
        ImagePicker.with(this).cropSquare().compress(getResources().getInteger(R.integer.max_image_fileSize)).maxResultSize(getResources().getInteger(R.integer.profile_profilePicSize), getResources().getInteger(R.integer.profile_profilePicSize)).start();


    }

    // Method to handle the result of choosing a photo from the gallery
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            // Set the selected image to the profile photo ImageView
            profilePhotoImageView.setImageURI(uri);
            this.isValidProfilePic = true;
            updateSaveButtonsState();
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(requireContext(), ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Task Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveProfilePicture() {
        // Get the Drawable from the ImageView
        Drawable drawable = profilePhotoImageView.getDrawable();

        // Convert the Drawable into a Bitmap
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            // If the drawable is not a BitmapDrawable, create a new Bitmap
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        // Convert the Bitmap into a byte array
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] pic = stream.toByteArray();
        //TODO add removing profile pic
        Database.uploadProfilePic(pic, new FileUploader.UploadCallback() {
            @Override
            public void onSuccess(ServerResponse response) {
                String profile_id = response.getPayload();
                updatedUserParams.put("profile_pic", profile_id);
                holder.updateParams(updatedUserParams);

            }

            @Override
            public void onFailure(ServerResponse errorMessage) {
                //TODO handle exceptions
                Log.e("Err", errorMessage.toString());
            }
        });

    }
}
