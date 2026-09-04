package com.example.eventus.ui.screens.EditProfile;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.eventus.R;
import com.example.eventus.data.BaseActivity;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.LoggedInUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EditProfileActivity extends BaseActivity {
    EditProfileFragment userProfileFragment;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        this.user = (LoggedInUser) args.getSerializable("user");

        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(this::backButtonClick);

        userProfileFragment = new EditProfileFragment(this);
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true).replace(R.id.EditProfileFrame, userProfileFragment).commit();

    }

    public LoggedInUser getUser() {
        return this.user;
    }

    public void updateParams(HashMap<String, Object> updatedUserParams) {
        try {
            Database.editUser(this.user.get_id(), updatedUserParams);
            if (updatedUserParams.containsKey("name")) {
                this.user.setName((String) updatedUserParams.get("name"));
            }
            if (updatedUserParams.containsKey("bio")) {
                this.user.setBio((String) updatedUserParams.get("bio"));
            }
            if (updatedUserParams.containsKey("profile_pic")) {
                this.user.setProfile_pic((String) updatedUserParams.get("profile_pic"));
            }


        } catch (Exception e) {
            Log.e("Err", Objects.requireNonNull(e.getMessage()));
        }
    }

    public void backButtonClick(View view) {
        // Navigate back
        success();
    }

    @Override
    public Set<String> getRequiredArgs() {
        return new HashSet<>(Collections.singletonList("user"));
    }
}
