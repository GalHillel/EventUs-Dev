package com.example.eventus.ui.recycleViews;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventus.R;
import com.example.eventus.data.model.UserEventDisplay;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    public interface OnShowMoreDetailsClickListener {
        void onShowMoreDetailsClick(UserEventDisplay eventClicked);
    }

    private final List<UserEventDisplay> eventList;
    private OnShowMoreDetailsClickListener listener;

    public EventAdapter(List<UserEventDisplay> eventList) {
        this.eventList = eventList;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        UserEventDisplay event = eventList.get(position);

        holder.eventNameTextView.setText(event.getEventName());
        holder.eventDateTextView.setText(event.getDate().toString());
        holder.eventLocationTextView.setText(event.getLocation());

        holder.showMoreDetailsButton.setOnClickListener(v -> {
            // Notify the listener that the button was clicked
            if (listener != null) {

                listener.onShowMoreDetailsClick(eventList.get(holder.getAdapterPosition()));
            }
        });
    }

    public void setOnShowMoreDetailsClickListener(OnShowMoreDetailsClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView eventNameTextView;
        TextView eventDateTextView;
        TextView eventLocationTextView;
        LinearLayout showMoreDetailsButton;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventNameTextView = itemView.findViewById(R.id.eventNameTextView);
            eventDateTextView = itemView.findViewById(R.id.eventDateTextView);
            eventLocationTextView = itemView.findViewById(R.id.eventLocationTextView);
            showMoreDetailsButton = itemView.findViewById(R.id.showMoreDetails);
        }
    }
}
