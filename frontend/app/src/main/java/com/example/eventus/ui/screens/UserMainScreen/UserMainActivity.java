package com.example.eventus.ui.screens.UserMainScreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.data.model.UserMessageDisplay;
import com.example.eventus.ui.screens.Messages.UserMessagesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserMainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    LoggedInUser user;
    List<UserMessageDisplay> messageList = null;
    Map<String, Boolean> inboxRead = null;

    List<UserEventDisplay> userEvents = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() == null) {
            Intent res = new Intent();
            res.putExtra("error", "null intent given");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }

        Intent intent = getIntent();

        if (intent.getExtras() == null) {
            Intent res = new Intent();
            res.putExtra("error", "intent missing bundle");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }

        Bundle args = intent.getExtras();

        if (!args.containsKey("user")) {
            Intent res = new Intent();
            res.putExtra("error", "args missing 'user' field");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }
        this.user = (LoggedInUser) args.getSerializable("user");

        setContentView(R.layout.activity_user_main_menu);
        BottomNavigationView navigationView = findViewById(R.id.mainMenuUserNavigation);


        if (user != null && user.getUser_type().equals("Organizer")) {
            navigationView.getMenu().findItem(R.id.discover).setVisible(false);
        } else {
            navigationView.getMenu().findItem(R.id.newEvent).setVisible(false);
        }

        navigationView.setOnItemSelectedListener(this);
        navigationView.setSelectedItemId(R.id.myevents);

    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        return update(itemId);

    }


    public void loadMessages() throws Exception {
        UserMessageDisplay[] newList = Database.getMessageInbox(this.user.get_id());
        inboxRead = Database.getMessageInboxStatus(this.user.get_id());
        messageList = new ArrayList<>();
        Collections.addAll(messageList, newList);
    }

    public void loadEvents() {
        try {
            UserEventDisplay[] tmp = Database.getEventList(user.get_id());
            // Clear the existing list and add the fetched events
            userEvents = new ArrayList<>();
            Collections.addAll(userEvents, tmp);

        } catch (ServerSideException e) {
            // Handle the exception
            e.printStackTrace();
        } catch (Exception e) {
            // Handle other exceptions
            e.printStackTrace();
        }
    }


    public LoggedInUser getUser() {
        return this.user;
    }

    public List<UserMessageDisplay> getMessageList() {
        return messageList;
    }

    public Map<String, Boolean> getMessageInbox() {
        return this.inboxRead;
    }

    public List<UserEventDisplay> getUserEvents() {
        return this.userEvents;
    }

    public void success() {
        this.setResult(Activity.RESULT_OK);
        this.finish();
    }

    public boolean update(int itemId) {
        if (itemId == R.id.messages) {
            getSupportFragmentManager().beginTransaction().replace(R.id.userMainMenuFrame, new UserMessagesFragment(this)).commit();
            return true;
        }

        //update messages
        try {
            loadMessages();
        } catch (Exception e) {

        }

        if (itemId == R.id.newEvent) {
            getSupportFragmentManager().beginTransaction().replace(R.id.userMainMenuFrame, new CreateEventFragment(this)).commit();
            return true;
        }
        if (itemId == R.id.discover) {
            getSupportFragmentManager().beginTransaction().replace(R.id.userMainMenuFrame, new UserDiscoverFragment(this)).commit();
            return true;
        }

        if (itemId == R.id.myevents) {
            getSupportFragmentManager().beginTransaction().replace(R.id.userMainMenuFrame, new UserEventsFragment(this)).commit();
            return true;
        }
        if (itemId == R.id.profile) {
            getSupportFragmentManager().beginTransaction().replace(R.id.userMainMenuFrame, new UserProfileFragment(this)).commit();
            return true;
        }
        return false;
    }


    public void setUser(LoggedInUser newUser) {
        this.user = newUser;
    }
}
