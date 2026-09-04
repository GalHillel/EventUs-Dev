package com.example.eventus.ui.screens.UserMainScreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.ui.screens.EditProfile.EditProfileActivity;
import com.example.eventus.ui.screens.Profile.BaseUserProfileFragment;

public class UserProfileFragment extends Fragment {
    private final UserMainActivity holder;
    private static final int EDIT_PROFILE_REQUEST_CODE = 100;

    public UserProfileFragment() {
        this.holder = null;
    }

    public UserProfileFragment(UserMainActivity holder) {
        this.holder = holder;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_profile, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LoggedInUser user = null;
        if (holder.getUser() == null && getArguments() != null) {
            user = (LoggedInUser) getArguments().getSerializable("user");
        }

        view.findViewById(R.id.logout).setOnClickListener(this::onLogoutButtonClicked);
        view.findViewById(R.id.editProfileButton).setOnClickListener(this::onEditProfileButtonClicked);

        BaseUserProfileFragment userProfileFragment = new BaseUserProfileFragment(holder.getUser());
        getChildFragmentManager().beginTransaction().setReorderingAllowed(true).replace(R.id.user_profile, userProfileFragment).commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_PROFILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                handleProfileUpdate(data);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(requireContext(), "An error occurred", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleProfileUpdate(Intent data) {
        if (data != null && data.getExtras() != null) {
            LoggedInUser newUser = (LoggedInUser) data.getExtras().getSerializable("user");
            if (newUser != null) {
                holder.setUser(newUser);
                holder.update(R.id.profile);
            }
        }
    }

    public void onEditProfileButtonClicked(View view) {
        Bundle args = new Bundle();
        args.putSerializable("user", holder.getUser());
        Intent intent = new Intent(requireContext(), EditProfileActivity.class);
        intent.putExtras(args);
        startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE);
    }

    public void onLogoutButtonClicked(View view) {
        holder.success();
        Toast.makeText(requireContext(), "Logging out", Toast.LENGTH_SHORT).show();
    }
}
