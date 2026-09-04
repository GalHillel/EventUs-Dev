package com.example.eventus.ui.screens.Messages;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.eventus.R;
import com.example.eventus.data.BaseActivity;
import com.example.eventus.data.Database;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserDisplay;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CreateMessageActivity extends BaseActivity {

    private UserDisplay[] otherUsers;
    private String defaultTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_message);

        this.user = (LoggedInUser) args.getSerializable("user");
        this.otherUsers = (UserDisplay[]) args.getSerializable("other_users");
        this.defaultTitle = args.getString("title", "");

        CreateMessageFragment createMessageFragment = new CreateMessageFragment(this);

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_create_message, createMessageFragment)
                .commit();

        findViewById(R.id.backButton).setOnClickListener(this::backButtonClick);
    }

    public UserDisplay getUser() {
        return user;
    }

    public UserDisplay[] getOtherUsers() {
        return otherUsers;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public Intent sendMessage(String title, String content) {
        Intent res = new Intent();
        try {
            Database.sendMessage(user.get_id(), Arrays.stream(otherUsers)
                    .map(UserDisplay::get_id)
                    .collect(Collectors.toList()), title, content);
            res.putExtra("code", Activity.RESULT_OK);
        } catch (ServerSideException e) {
            res.putExtra("code", e.getReturnCode());
            res.putExtra("message", e.getMessage());
        } catch (Exception e) {
            res.putExtra("code", Activity.RESULT_CANCELED);
            res.putExtra("message", e.getMessage());
        }
        return res;
    }

    @Override
    public Set<String> getRequiredArgs() {
        return new HashSet<>(Arrays.asList("user", "other_users"));
    }

    public void backButtonClick(View view) {
        success();
    }
}
