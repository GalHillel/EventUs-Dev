package com.example.eventus.ui.screens.Messages;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.data.model.UserMessage;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

public class MessageFragment extends Fragment {

    private final MessageActivity holder;
    private final UserMessage message;
    private final UserDisplay sender;
    private final UserDisplay user;

    public MessageFragment(MessageActivity holder, UserMessage message, UserDisplay sender, UserDisplay user) {
        this.holder = holder;
        this.message = message;
        this.sender = sender;
        this.user = user;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView titleTextView = view.findViewById(R.id.messageTitleTextView);
        TextView senderTextView = view.findViewById(R.id.messageSenderTextView);
        MaterialAutoCompleteTextView contentTextView = view.findViewById(R.id.messageContentTextView);

        // Set message details
        titleTextView.setText(message.getTitle());
        senderTextView.setText(getString(R.string.from_sender2, sender.getName()));
        contentTextView.setText(message.getContent());

        Button replyButton = view.findViewById(R.id.replyButton);
        replyButton.setOnClickListener(this::onReplyButtonClick);
    }

    private void onReplyButtonClick(View view) {
        // Start CreateMessageActivity for replying
        Intent intent = new Intent(requireContext(), CreateMessageActivity.class);
        Bundle args = new Bundle();
        args.putSerializable("user", user);
        args.putSerializable("other_users", new UserDisplay[]{sender});
        args.putString("title", getString(R.string.reply_title, message.getTitle()));
        intent.putExtras(args);
        startActivityForResult(intent, R.id.activity_create_messages);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == R.id.activity_create_messages) {
            if (resultCode == Activity.RESULT_OK) {
                // Handle successful reply
                holder.success();
            } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
                // Handle reply cancellation
                String error = data.getStringExtra("error");
                if (error != null) {
                    // Show error message
                    showToast(error);
                }
            }
        }
    }

    private void showToast(String message) {
        // Display toast message
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
