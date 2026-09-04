package com.example.eventus.ui.screens.Messages;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.eventus.R;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.UserMessageDisplay;
import com.example.eventus.ui.recycleViews.MessageAdaptor;
import com.example.eventus.ui.screens.UserMainScreen.UserMainActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserMessagesFragment extends Fragment implements MessageAdaptor.onMessageClickListener {
    private UserMainActivity holder;
    private RecyclerView messagesRecyclerView;
    private MessageAdaptor messageAdaptor;

    public UserMessagesFragment(UserMainActivity holder) {
        this.holder = holder;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<UserMessageDisplay> messageList = holder.getMessageList();
        Map<String, Boolean> inboxRead = holder.getMessageInbox();

        if (messageList == null) {
            try {
                holder.loadMessages();
                messageList = holder.getMessageList();
                inboxRead = holder.getMessageInbox();
            } catch (ServerSideException e) {
                // Handle server side exception
                messageList = new ArrayList<>();
                inboxRead = new HashMap<>();
                e.printStackTrace();
            } catch (Exception e) {
                // Handle other exception
                messageList = new ArrayList<>();
                inboxRead = new HashMap<>();
                e.printStackTrace();
            }
        }

        // Sort messageList
        Map<String, Boolean> finalInboxRead = inboxRead;
        messageList.sort((message1, message2) -> {
            if (Boolean.TRUE.equals(finalInboxRead.getOrDefault(message1.get_id(), false)) && Boolean.FALSE.equals(finalInboxRead.getOrDefault(message2.get_id(), false))) {
                return 1;
            } else if (Boolean.FALSE.equals(finalInboxRead.getOrDefault(message1.get_id(), false)) && Boolean.TRUE.equals(finalInboxRead.getOrDefault(message2.get_id(), false))) {
                return -1;
            } else {
                return message2.getDate_sent().compareTo(message1.getDate_sent());
            }
        });

        messagesRecyclerView = view.findViewById(R.id.userMessagesList);
        messageAdaptor = new MessageAdaptor(messageList, inboxRead);
        messageAdaptor.setOnMessageClickListener(this);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        messagesRecyclerView.setAdapter(messageAdaptor);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == R.id.activity_user_message) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
                String msg_id = data.getExtras().getString("message_id", "");
                if (!msg_id.isEmpty()) {
                    holder.getMessageInbox().put(msg_id, true);
                    holder.update(R.id.messages);
                }
            } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
                // Handle failure
                String error = data.getStringExtra("error");
                if (error != null) {
                    // Show error message
                    showToast(error);
                }
            }
        }
    }

    @Override
    public void onMessageClick(UserMessageDisplay messageClicked) {
        Bundle args = new Bundle();
        args.putSerializable("user", holder.getUser());
        args.putString("message_id", messageClicked.get_id());

        Intent i = new Intent(requireContext(), MessageActivity.class);
        i.putExtras(args);
        startActivityForResult(i, R.id.activity_user_message);
    }

    private void showToast(String message) {
        // Show toast message
        // For example:
        // Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
