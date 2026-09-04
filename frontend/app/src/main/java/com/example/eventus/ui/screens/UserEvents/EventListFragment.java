package com.example.eventus.ui.screens.UserEvents;

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
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.ui.recycleViews.EventAdapter;
import com.example.eventus.ui.screens.EventDetails.EventDetailsActivity;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class EventListFragment extends Fragment implements EventAdapter.OnShowMoreDetailsClickListener {
    private final List<UserEventDisplay> eventList;
    private LoggedInUser user;

    public EventListFragment(LoggedInUser user, List<UserEventDisplay> eventList) {
        this.eventList = eventList;
        this.user = user;
        Collections.sort(eventList, (o1, o2) -> {
            Date currentDate = new Date();
            long diff1 = Math.abs(currentDate.getTime() - o1.getDate().getTime());
            long diff2 = Math.abs(currentDate.getTime() - o2.getDate().getTime());
            return Long.compare(diff1, diff2);
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_list, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView eventRecyclerView = view.findViewById(R.id.eventListRecycleView);
        EventAdapter eventAdapter = new EventAdapter(eventList);
        eventAdapter.setOnShowMoreDetailsClickListener(this);
        eventRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        eventRecyclerView.setAdapter(eventAdapter);

        if (eventList.isEmpty()) {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == R.id.showMoreDetails) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
                LoggedInUser newUser = (LoggedInUser) data.getExtras().getSerializable("user");
                if (newUser != null) {
                    this.user = newUser;
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(getContext(), "An error has occurred", Toast.LENGTH_LONG).show();
            }
        }
        Fragment parentFragment = getParentFragment();
        if (parentFragment != null) {
            parentFragment.onActivityResult(requestCode, resultCode, data);
        } else {
            requireActivity().setResult(resultCode, data);
        }
    }

    @Override
    public void onShowMoreDetailsClick(UserEventDisplay clickedEvent) {
        Intent intent = new Intent(getContext(), EventDetailsActivity.class);
        Bundle args = new Bundle();
        args.putSerializable("user", this.user);
        args.putString("eventId", clickedEvent.getId());
        intent.putExtras(args);
        startActivityForResult(intent, R.id.showMoreDetails);
    }
}
