package com.example.eventus.ui.screens.UserMainScreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.ui.screens.UserEvents.EventListFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class UserDiscoverFragment extends Fragment {

    private EditText searchEditText;
    private List<UserEventDisplay> searchResults = new ArrayList<>();

    // TODO: Add an option for searching users by username
    private final UserMainActivity holder;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public UserDiscoverFragment() {
        holder = null;
    }

    public UserDiscoverFragment(UserMainActivity holder) {
        this.holder = holder;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_discover, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        searchEditText = view.findViewById(R.id.searchEditText);
        Button searchButton = view.findViewById(R.id.searchButton);
        searchButton.setOnClickListener(v -> {
            // Perform event search
            performEventSearch();
        });
    }

    private void performEventSearch() {
        String searchQuery = searchEditText.getText().toString();

        // Create a HashMap with the search query
        HashMap<String, Object> searchParams = new HashMap<>();
        searchParams.put("name", searchQuery);
        searchParams.put("_id", searchQuery);
        searchParams.put("date", searchQuery);
        searchParams.put("location", searchQuery);
        searchParams.put("creator_id", searchQuery);

        // Execute the search in the background using ExecutorService
        try {
            UserEventDisplay[] temp = Database.searchEvents(searchParams);
            this.searchResults = Arrays.asList(temp);
        } catch (Exception e) {
            // Handle the exception, e.g., show an error message
            e.printStackTrace();
        }
        //filter events
        this.searchResults = searchResults.stream().filter(e -> {
            assert this.holder != null;
            return !this.holder.getUser().getEvents().contains(e.getId());
        }).collect(Collectors.toList());

        assert this.holder != null;
        EventListFragment searchEventListFragment = new EventListFragment(this.holder.getUser(), this.searchResults);
        getChildFragmentManager().beginTransaction().setReorderingAllowed(true).replace(R.id.discoveredEventsList, searchEventListFragment).commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == R.id.showMoreDetails) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getExtras() != null) {
                    LoggedInUser newUser = (LoggedInUser) data.getExtras().getSerializable("user");
                    if (newUser != null) {
                        assert this.holder != null;
                        if (newUser.getEvents().size() != this.holder.getUser().getEvents().size() || !newUser.getEvents().containsAll(this.holder.getUser().getEvents())) {
                            this.holder.setUser(newUser);
                            this.holder.loadEvents();
                        }
                    }

                    assert this.holder != null;
                    this.holder.update(R.id.discover);
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(requireContext(), "an error has occurred", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Shutdown the ExecutorService when the fragment is destroyed
        executorService.shutdown();
    }


}
