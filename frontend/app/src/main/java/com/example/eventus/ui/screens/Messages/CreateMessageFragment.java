package com.example.eventus.ui.screens.Messages;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.model.UserDisplay;

import java.util.Arrays;
import java.util.stream.Collectors;

public class CreateMessageFragment extends Fragment {

    private EditText subjectEditText;
    private EditText messageEditText;
    private final CreateMessageActivity holder;

    public CreateMessageFragment(CreateMessageActivity holder) {
        this.holder = holder;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView recipientsText = view.findViewById(R.id.recipients);
        subjectEditText = view.findViewById(R.id.subjectEditText);
        messageEditText = view.findViewById(R.id.messageEditText);
        Button sendButton = view.findViewById(R.id.sendButton);
        sendButton.setOnClickListener(this::onSendButtonClick);

        if (!holder.getDefaultTitle().isEmpty()) {
            subjectEditText.setText(holder.getDefaultTitle());
            subjectEditText.setFocusable(false);
        }
        String usersNames = Arrays.stream(holder.getOtherUsers())
                .map(UserDisplay::getName)
                .collect(Collectors.joining(", "));

        recipientsText.setText(usersNames);
    }

    void onSendButtonClick(View view) {
        int subjectLen = subjectEditText.getText().toString().length();
        int contentLen = messageEditText.getText().toString().length();

        if (subjectLen == 0 && contentLen == 0) {
            showToast("Message missing subject and content");
            return;
        }
        if (subjectLen == 0) {
            showToast("Message missing subject");
            return;
        }
        if (contentLen == 0) {
            showToast("Message missing content");
            return;
        }

        Intent res = holder.sendMessage(subjectEditText.getText().toString(), messageEditText.getText().toString());

        if (res.getIntExtra("code", Activity.RESULT_CANCELED) == Activity.RESULT_OK) {
            subjectEditText.setText("");
            messageEditText.setText("");
            showToast("Message sent successfully");
            holder.success();
        }
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
