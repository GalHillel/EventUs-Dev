package com.example.eventus.ui.screens.EventDetails;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.ui.screens.Messages.CreateMessageActivity;

import java.util.HashMap;
import java.util.Optional;

public class EventDetailsTabFragment extends Fragment {

    private RatingBar ratingBar;
    private Button editEventButton;
    private Button saveEventButton;
    private Button pickDateButton;
    private Button saveRatingButton;
    private Calendar calendar;
    private EditText eventNameView, eventDateView, eventLocationview, eventDescription;

    EventDetailsActivity holder;

    public EventDetailsTabFragment(EventDetailsActivity myContext) {
        this.holder = myContext;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Additional initialization or setup can be done here
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_event_details_tab, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventNameView = view.findViewById(R.id.eventNameTextView);
        eventDateView = view.findViewById(R.id.eventDateTextView);
        eventLocationview = view.findViewById(R.id.eventLocationTextView);
        eventDescription = view.findViewById(R.id.eventDescriptionTextView);
        this.calendar = Calendar.getInstance();
        this.editEventButton = view.findViewById(R.id.editEventButton);
        this.saveEventButton = view.findViewById(R.id.saveEventButton);
        this.pickDateButton = view.findViewById(R.id.pickDateButton);
        Button contactUserButton = view.findViewById(R.id.contanctUserButton);
        this.saveRatingButton = view.findViewById(R.id.saveRatingButton);
        this.ratingBar = view.findViewById(R.id.ratingBar);
        TextView ratingCount = view.findViewById(R.id.ratingCountTextView);
        ratingCount.setText(this.holder.getEvent().getNum_rating() + " Ratings");


        if ("Organizer".equals(this.holder.getUser().getUser_type())) {
            contactUserButton.setText("Contact All Participants");
            contactUserButton.setOnClickListener(this::onContactAllParticipantsClick);
        } else {
            contactUserButton.setOnClickListener(this::onContactOrganizerClick);
        }

        this.pickDateButton.setOnClickListener(v -> onPickDateClick());
        this.editEventButton.setOnClickListener(this::onEditEventClick);
        this.saveEventButton.setOnClickListener(this::onSaveEventClick);
        this.saveRatingButton.setOnClickListener(this::onSaveRatingClick);
        this.saveRatingButton.setEnabled(false);
        ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> saveRatingButton.setEnabled(true));

        updateFields();
        boolean user_in_event = holder.getEvent().getAttendents().containsKey(holder.getUser().get_id()) && (!holder.getEvent().getIsPrivate() || Boolean.TRUE.equals(holder.getEvent().getAttendents().get(holder.getUser().get_id())));

        toggleEditableMode(false);

        if (!holder.hasPassed()) {
            ratingBar.setVisibility(View.GONE);
            saveRatingButton.setVisibility(View.GONE);
            ratingCount.setVisibility(View.GONE);
        } else {
            ratingBar.setVisibility(View.VISIBLE);
            saveRatingButton.setVisibility(View.VISIBLE);
            ratingCount.setVisibility(View.VISIBLE);
            editEventButton.setVisibility(View.GONE);
            if (holder.getUser().getUser_type().equals("Organizer") || !user_in_event) {
                ratingBar.setEnabled(false);
                ratingBar.setRating(this.holder.getEvent().getRating());
                saveRatingButton.setEnabled(false);
                saveRatingButton.setVisibility(View.GONE);
            }
        }
        if (!user_in_event || holder.hasPassed()) {
            contactUserButton.setVisibility(View.GONE);
        }
    }

    private void onSaveRatingClick(View view) {
        // Get the eventId and userId
        String eventId = holder.getEvent().getId();
        String userId = holder.getUser().get_id();

        // Get the rating value from the RatingBar
        float rating = ratingBar.getRating();
        try {
            // Call the rateEvent method with eventId, userId, and rating
            Database.rateEvent(userId, eventId, rating);
            holder.getUser().getEvents().remove(eventId);
            holder.success();
        } catch (Exception e) {
            // Handle exceptions
            Log.e("EventDetailsTabFragment", "Error saving rating", e);
        }

    }


    private void updateFields() {
        eventNameView.setText(this.holder.getEvent().getName());
        eventDateView.setText(this.holder.getEvent().getDate().toString());
        eventLocationview.setText(this.holder.getEvent().getLocation());
        eventDescription.setText(this.holder.getEvent().getDescription());
    }


    // This method will be called when the "Edit Event" button is clicked
    public void onEditEventClick(View view) {
        toggleEditableMode(true);
        pickDateButton.setVisibility(View.VISIBLE);
    }

    public void onContactAllParticipantsClick(View view) {
        Bundle args = new Bundle();
        args.putSerializable("user", this.holder.getUser());

        UserDisplay[] others = this.holder.getUsers().toArray(new UserDisplay[0]);
        args.putSerializable("other_users", others);

        Intent i = new Intent(this.getContext(), CreateMessageActivity.class);
        i.putExtras(args);
        startActivity(i);

    }

    public void onContactOrganizerClick(View view) {
        Bundle args = new Bundle();
        args.putSerializable("user", this.holder.getUser());

        Optional<UserDisplay> org;
        org = this.holder.getUsers().stream().filter(e -> e.get_id().equals(this.holder.getEvent().getCreator_id())).findFirst();
        if (org.isPresent()) {
            UserDisplay[] others = new UserDisplay[]{org.get()};
            args.putSerializable("other_users", others);

            Intent i = new Intent(this.getContext(), CreateMessageActivity.class);
            i.putExtras(args);
            startActivity(i);
        }


    }

    // This method will be called when the "Save Event" button is clicked
    public void onSaveEventClick(View view) {
        // Get the updated event details and call the editEvent function
        HashMap<String, Object> updatedEventParams = new HashMap<>();
        updatedEventParams.put("name", eventNameView.getText().toString());
        updatedEventParams.put("date", eventDateView.getText().toString());
        updatedEventParams.put("location", eventLocationview.getText().toString());
        updatedEventParams.put("description", eventDescription.getText().toString());

        try {
            Database.editEvent(this.holder.getEvent().getId(), updatedEventParams);
            toggleEditableMode(false);

            this.holder.getEvent().setName(eventNameView.getText().toString());
            this.holder.getEvent().setDate(calendar.getTime());
            this.holder.getEvent().setLocation(eventLocationview.getText().toString());
            this.holder.getEvent().setDescription(eventDescription.getText().toString());
            updateFields();

        } catch (Exception e) {
            // Handle exception
        }
    }

    private void toggleEditableMode(boolean isEdit) {

        // Enable or disable editing of TextViews based on the editable flag
        setAsTextView(eventNameView, isEdit);
        setAsTextView(eventDateView, isEdit);
        setAsTextView(eventLocationview, isEdit);
        setAsTextView(eventDescription, isEdit);
        // Show or hide the buttons based on the editable flag
        if (isEdit) {
            editEventButton.setVisibility(View.GONE);
            saveEventButton.setVisibility(View.VISIBLE);
            pickDateButton.setVisibility(View.VISIBLE);
        } else {
            //enable the edit button only for creator
            if (this.holder.getUser().get_id().equals(this.holder.getEvent().getCreator_id())) {
                editEventButton.setVisibility(View.VISIBLE);
            } else {
                editEventButton.setVisibility(View.GONE);
            }
            saveEventButton.setVisibility(View.GONE);
            pickDateButton.setVisibility(View.GONE);
        }


    }

    private void setAsTextView(EditText tv, boolean flg) {
        if (flg) {

            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setCursorVisible(true);
            tv.setBackground(new AppCompatEditText(tv.getContext()).getBackground());
        } else {
            tv.setClickable(false);
            tv.setFocusable(false);
            tv.setCursorVisible(false);
            tv.setBackground(null);
        }

    }

    public void onPickDateClick() {
        showDatePickerDialog();
    }

    public void showDatePickerDialog() {
        DatePickerDialog.OnDateSetListener dateSetListener = (datePicker, year, month, day) -> {
            calendar.set(java.util.Calendar.YEAR, year);
            calendar.set(java.util.Calendar.MONTH, month);
            calendar.set(java.util.Calendar.DAY_OF_MONTH, day);
            timePicker();
        };

        new DatePickerDialog(requireContext(), dateSetListener, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void timePicker() {
        // Launch Time Picker Dialog
        TimePickerDialog.OnTimeSetListener timePickerDialog = (view, hourOfDay, minute) -> {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, 0);
            updateDateTextView();
        };
        new TimePickerDialog(requireContext(), timePickerDialog, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show();


    }

    private void updateDateTextView() {

        eventDateView.setText(calendar.getTime().toString());
    }
}
