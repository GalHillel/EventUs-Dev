package com.example.eventus.ui.screens.UserMainScreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.ui.screens.UserEvents.EventListFragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class UserEventsFragment extends Fragment {

    private UserMainActivity holder;
    private final List<UserEventDisplay> upcomingEventsList = new ArrayList<>();
    private final List<UserEventDisplay> pastEventsList = new ArrayList<>();

    private EventListFragment pastEventsListFragment;
    private EventListFragment upcomingEventsFragment;

    public UserEventsFragment() {
    }

    public UserEventsFragment(UserMainActivity holder) {
        this.holder = holder;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (this.holder.getUserEvents() == null) {
            this.holder.loadEvents();
        }


        upcomingEventsList.clear();
        pastEventsList.clear();
        Date currentDate = new Date();
        for (UserEventDisplay event : this.holder.getUserEvents()) {
            if (event.getDate().after(currentDate)) {
                upcomingEventsList.add(event);
            } else {
                pastEventsList.add(event);
            }
        }
        upcomingEventsList.sort(Comparator.comparing(UserEventDisplay::getDate));
        pastEventsList.sort(Comparator.comparing(UserEventDisplay::getDate));

        if (pastEventsList.isEmpty()){
            // Remove past events and extend upcoming events
            getView().findViewById(R.id.pastEventsAppBar).setVisibility(View.GONE);
            ViewGroup.LayoutParams layoutParams = getView().findViewById(R.id.upcomingEventsList).getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getView().findViewById(R.id.upcomingEventsList).setLayoutParams(layoutParams);
        } else if (!this.holder.getUser().getUser_type().equals("Organizer")){
            // Change the past event title to "Waiting for rating"
            ((TextView) getView().findViewById(R.id.pastEventsAppBar).findViewById(R.id.title)).setText("Waiting for Rating");
        }

        upcomingEventsFragment = new EventListFragment(this.holder.getUser(), upcomingEventsList);
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.upcomingEventsList, upcomingEventsFragment)
                .commit();

        pastEventsListFragment = new EventListFragment(this.holder.getUser(), pastEventsList);
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.pastEventsList, pastEventsListFragment)
                .commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == R.id.showMoreDetails) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
                LoggedInUser newUser = (LoggedInUser) data.getExtras().getSerializable("user");
                if (newUser != null && !newUser.getEvents().equals(this.holder.getUser().getEvents())) {
                    this.holder.setUser(newUser);
                    this.holder.loadEvents();
                }
                this.holder.update(R.id.myevents);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(requireContext(), "An error has occurred", Toast.LENGTH_LONG).show();
            }
        }
    }
}
