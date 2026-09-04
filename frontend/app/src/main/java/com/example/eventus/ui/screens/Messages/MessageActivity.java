package com.example.eventus.ui.screens.Messages;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import com.example.eventus.R;
import com.example.eventus.data.BaseActivity;
import com.example.eventus.data.Database;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.data.model.UserMessage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MessageActivity extends BaseActivity {
    private UserDisplay user, sender;
    private UserMessage message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_message);

        Bundle args = getIntent().getExtras();
        if (args != null) {
            user = (UserDisplay) args.getSerializable("user");
            String message_id = args.getString("message_id", "");

            try {
                message = Database.loadMessage(message_id, user.get_id());
                sender = Database.userDisplay(message.getSender_id());
            } catch (ServerSideException e) {
                handleServerSideException(e);
            } catch (Exception e) {
                handleOtherException(e);
            }
        }

        MessageFragment messageFragment = new MessageFragment(this, message, sender, user);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_message, messageFragment).commit();

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(this::backButtonClick);
    }

    private void handleServerSideException(ServerSideException e) {
        Intent res = new Intent();
        res.putExtra("error", e.getMessage());
        setResult(e.getReturnCode(), res);
        finish();
    }

    private void handleOtherException(Exception e) {
        Intent res = new Intent();
        res.putExtra("error", e.getMessage());
        setResult(Activity.RESULT_CANCELED, res);
        finish();
    }

    public void backButtonClick(View view) {
        success();
    }

    public UserDisplay getUser() {
        return user;
    }

    public UserDisplay getSender() {
        return sender;
    }

    public UserMessage getMessage() {
        return message;
    }

    public void success() {
        Intent i = new Intent();
        Bundle args = new Bundle();
        args.putString("message_id", message.get_id());
        args.putSerializable("user", user);
        i.putExtras(args);
        setResult(Activity.RESULT_OK, i);
        finish();
    }

    @Override
    public Set<String> getRequiredArgs() {
        return new HashSet<>(Arrays.asList("user", "message_id"));
    }
}
