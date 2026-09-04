package com.example.eventus.ui.screens.EventDetails;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import androidx.viewpager.widget.ViewPager;

import com.example.eventus.R;
import com.example.eventus.data.BaseActivity;
import com.example.eventus.data.Database;
import com.example.eventus.data.ServerSideException;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.data.model.UserEvent;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EventDetailsActivity extends BaseActivity {
    TabLayout tabLayout;
    ViewPager viewPager;

    private UserEvent userEvent;
    private final List<UserDisplay> users = new ArrayList<>();
    private boolean passed;
    BadgeDrawable badge;

    private final Map<String, Bitmap> profilePic_lookup = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(this::backButtonClick);

        tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        viewPager = (ViewPager) findViewById(R.id.viewPager);


        this.user = (LoggedInUser) args.getSerializable("user");
        String eventId = args.getString("eventId", "");
        try {
            this.userEvent = Database.loadEvent(eventId);
            UserDisplay[] tmp = Database.getUserList(eventId);
            this.users.clear();
            this.users.addAll(Arrays.asList(tmp));
        } catch (ServerSideException e) {
            Intent res = new Intent();
            res.putExtra("error", e.getMessage());
            setResult(e.getReturnCode(), res);
            this.finish();
            return;
        } catch (Exception e) {
            Intent res = new Intent();
            res.putExtra("error", e.getMessage());
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }

        Date d = new Date();
        this.passed = !userEvent.getDate().after(d);
        if (passed && !users.contains(user)) {
            tabLayout.removeTabAt(1);
        }

        this.users.sort(Comparator.comparing(UserDisplay::getUser_type));

        EventDetailsPagerAdaptor myAdaptor = new EventDetailsPagerAdaptor(this, getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(myAdaptor);

        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        this.badge = null;
        if (!passed && user.get_id().equals(userEvent.getCreator_id())) {
            this.badge = Objects.requireNonNull(tabLayout.getTabAt(1)).getOrCreateBadge();
            updateBadge();
        }
    }

    LoggedInUser getUser() {
        return this.user;
    }

    UserEvent getEvent() {
        return this.userEvent;
    }

    List<UserDisplay> getUsers() {
        return this.users;
    }

    public void backButtonClick(View view) {
        // Navigate back
        success();

    }

    @Override
    public Set<String> getRequiredArgs() {
        return new HashSet<>(Arrays.asList("user", "eventId"));
    }

    public void updateBadge() {
        if (this.badge != null) {
            int badgeNum = 0;
            if (this.userEvent.getIsPrivate()) {
                badgeNum = (int) this.userEvent.getAttendents().entrySet().stream().filter(e -> !e.getValue()).count();
            }

            if (badgeNum > 0) {
                badge.setVisible(true);
                badge.setNumber(badgeNum);
                return;
            }
            badge.setNumber(0);
            badge.setVisible(false);
        }

    }

    public Bitmap getProfilePicBitmap(String userId) {

        if (profilePic_lookup.containsKey(userId)) {

            return profilePic_lookup.get(userId);
        }
        Bitmap profile_icon = null;
        try {
            profile_icon = Database.getProfilePic(userId);
            int width = getResources().getInteger(R.integer.participant_list_profilePicSize);
            int height = getResources().getInteger(R.integer.participant_list_profilePicSize);
            profile_icon = Bitmap.createScaledBitmap(profile_icon, width, height, false);

        } catch (ServerSideException e) {
            // Handle the exception (e.g., show an error message)
            e.printStackTrace();
        } catch (Exception e) {
            // Handle other exceptions
            e.printStackTrace();
        }
        return profile_icon;
    }


    public boolean hasPassed() {
        return this.passed;
    }
}
