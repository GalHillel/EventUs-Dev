package com.example.eventus.ui.screens.UserEvents;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.eventus.R;
import com.example.eventus.data.BaseActivity;
import com.example.eventus.data.Database;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.data.model.UserProfile;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ViewEventsActivity extends BaseActivity {

    List<UserEventDisplay> userEvents;
    UserProfile userProfile;

    @SuppressLint("SetTextI18n")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_user_events);

        UserEventDisplay[] tmp = null;
        if (args.containsKey("eventArr")) {
            tmp = (UserEventDisplay[]) args.getSerializable("eventArr");
        }

        this.user = (LoggedInUser) args.getSerializable("user");
        this.userProfile = (UserProfile) args.getSerializable("other_user_profile");
        String mode = args.getString("mode", "");

        if (tmp == null) {
            try {
                tmp = Database.getEventList(userProfile.get_id());

            } catch (ServerSideException e) {
                Intent res = new Intent();
                res.putExtra("message", e.getMessage());
                setResult(e.getReturnCode(), res);
                this.finish();
                return;
            } catch (Exception e) {
                Intent res = new Intent();
                res.putExtra("message", e.getMessage());
                setResult(Activity.RESULT_CANCELED, res);
                this.finish();
                return;
            }
        }
        userEvents = Arrays.asList(tmp);

        Date d = new Date();
        if (mode.equals("upcoming")) {
            userEvents = userEvents.stream().filter(u -> u.getDate().after(d)).collect(Collectors.toList());
        } else {
            userEvents = userEvents.stream().filter(u -> !u.getDate().after(d)).collect(Collectors.toList());
        }
        if (!mode.isEmpty()) {
            mode = mode + " ";
        }

        backButton = findViewById(R.id.backButton);
        TextView title = findViewById(R.id.user_events_title);
        title.setText(this.userProfile.getName() + "'s " + mode + "events");
        backButton.setOnClickListener(this::backButtonClick);

        EventListFragment userEventListFragment = new EventListFragment(this.user, this.userEvents);

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_event_list, userEventListFragment)
                .commit();

    }

    public void backButtonClick(View view) {
        // Navigate back
        this.success();
    }

    @Override
    public Set<String> getRequiredArgs() {
        return new HashSet<>(Arrays.asList("user", "other_user_profile", "mode"));
    }

}