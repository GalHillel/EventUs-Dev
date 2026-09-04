package com.example.eventus.ui.screens.UserMainScreen;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.UserEvent;
import com.example.eventus.data.model.UserEventDisplay;

import java.util.Calendar;

public class CreateEventFragment extends Fragment {

    private EditText eventNameEditText;
    private EditText eventDateEditText;
    private EditText eventLocationEditText;
    private EditText eventDescriptionEditText;
    private CheckBox setPrivateEventCheckbox;
    private UserMainActivity holder;

    private Calendar calendar;

    public CreateEventFragment(UserMainActivity holder) {
        this.holder = holder;
    }

    public CreateEventFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_create_event, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        eventNameEditText = view.findViewById(R.id.eventNameEditText);
        eventDateEditText = view.findViewById(R.id.eventDateEditText);
        eventLocationEditText = view.findViewById(R.id.eventLocationEditText);
        eventDescriptionEditText = view.findViewById(R.id.eventDescriptionEditText);
        Button createEventButton = view.findViewById(R.id.createEventButton);
        setPrivateEventCheckbox = view.findViewById(R.id.setPrivateEventCheckbox);

        calendar = Calendar.getInstance();

        view.findViewById(R.id.pickDateButton).setOnClickListener(this::showDatePickerDialog);

        // Set up click listener for the create event button
        createEventButton.setOnClickListener(v -> createEvent());
    }

    private void createEvent() {
        // Retrieve data from UI fields
        String eventName = eventNameEditText.getText().toString();
        String eventDate = eventDateEditText.getText().toString();
        String eventLocation = eventLocationEditText.getText().toString();
        String eventDescription = eventDescriptionEditText.getText().toString();
        boolean isPrivate = setPrivateEventCheckbox.isChecked();
        try {
            // Call the Database method to add the event with the correct creator_id
            UserEvent userEvent = Database.addEvent(this.holder.getUser().get_id(), eventName, eventDate, eventLocation, eventDescription, isPrivate);

            // Display information about the created event using UserEventDisplay
            displayEventDetails(userEvent);
            Toast.makeText(requireContext(), "Event created successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Handle the exception, e.g., show an error message
            Toast.makeText(requireContext(), "Error creating event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        eventNameEditText.setText("");
        eventDateEditText.setText("");
        eventLocationEditText.setText("");
        eventDescriptionEditText.setText("");
    }

    private void displayEventDetails(UserEventDisplay userEventDisplay) {
        // Example: Displaying event details in a Toast message
        String message = "Event created:\n" + "Name: " + userEventDisplay.getEventName() + "\n" + "Date: " + userEventDisplay.getDate() + "\n" + "Location: " + userEventDisplay.getLocation();

        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();


        if (this.holder.getUserEvents() != null) {
            this.holder.getUser().getEvents().add(userEventDisplay.getId());
            this.holder.getUserEvents().add(userEventDisplay);
        }


        // You can also perform other actions, such as updating UI elements or navigating to another fragment
    }

    public void showDatePickerDialog(View view) {
        DatePickerDialog.OnDateSetListener dateSetListener = (datePicker, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            timePicker();
        };

        new DatePickerDialog(requireContext(), dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void timePicker() {
        // Launch Time Picker Dialog
        TimePickerDialog.OnTimeSetListener timePickerDialog = (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            updateDateEditText();
        };
        new TimePickerDialog(requireContext(), timePickerDialog, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();


    }

    private void updateDateEditText() {
        eventDateEditText.setText(calendar.getTime().toString());
    }


}
