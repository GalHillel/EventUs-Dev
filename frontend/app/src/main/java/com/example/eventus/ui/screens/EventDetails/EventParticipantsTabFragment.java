package com.example.eventus.ui.screens.EventDetails;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventus.R;
import com.example.eventus.data.Database;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.ui.recycleViews.UserAdapter;
import com.example.eventus.ui.screens.Messages.CreateMessageActivity;
import com.example.eventus.ui.screens.Profile.ViewUserProfileActivity;

public class EventParticipantsTabFragment extends Fragment implements UserAdapter.ButtonListener, UserAdapter.Wrappers {

    private UserAdapter userAdapter;

    private Button exitEventButton, joinEventButton;

    private RecyclerView userListRecyclerView;

    private final EventDetailsActivity holder;

    public EventParticipantsTabFragment(EventDetailsActivity myContext) {
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
        return inflater.inflate(R.layout.fragment_participants_tab, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.joinEventButton = view.findViewById(R.id.joinEventButton);
        this.exitEventButton = view.findViewById(R.id.leaveEventButton);

        this.userListRecyclerView = view.findViewById(R.id.eventListRecycleView);


        this.joinEventButton.setOnClickListener(this::onJoinEventClick);
        this.exitEventButton.setOnClickListener(this::onLeaveEventClick);

        userAdapter = new UserAdapter(this.holder.getUsers(), this.holder.getEvent(), (this.holder.getUser().get_id().equals(this.holder.getEvent().getCreator_id())) ? "Organizer" : "Participant");
        userAdapter.setOnKickClickListener(this);
        userAdapter.setOnMessageClickListener(this);
        userAdapter.setOnUserItemClickListener(this);
        userAdapter.setProfileWrapper(this);
        userAdapter.setAcceptClickListener(this);


        if (this.holder.getEvent().getAttendents().containsKey(this.holder.getUser().get_id())) {
            this.joinEventButton.setVisibility(View.GONE);
            this.exitEventButton.setVisibility(View.VISIBLE);
            if (this.holder.getEvent().getIsPrivate()) {
                if (Boolean.FALSE.equals(this.holder.getEvent().getAttendents().get(this.holder.getUser().get_id()))) {
                    this.exitEventButton.setText("Cancel Request");
                    this.userListRecyclerView.setVisibility(View.INVISIBLE);
                } else {
                    this.exitEventButton.setText("Leave Event");
                }
            }


        } else {
            this.exitEventButton.setVisibility(View.GONE);
            this.joinEventButton.setVisibility(View.VISIBLE);
            if (this.holder.getEvent().getIsPrivate()) {
                this.userListRecyclerView.setVisibility(View.INVISIBLE);

            }
        }

        if (this.holder.hasPassed()) {
            exitEventButton.setVisibility(View.GONE);
            joinEventButton.setVisibility(View.GONE);
        }
        if (this.holder.getUser().get_id().equals(this.holder.getEvent().getCreator_id())) {
            this.exitEventButton.setText("Delete Event");
        }

        userListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        userListRecyclerView.setAdapter(userAdapter);

    }

    // This method will be called when the "Join Event" button is clicked
    public void onJoinEventClick(View view) {
        try {
            Database.joinEvent(this.holder.getUser().get_id(), this.holder.getEvent().getId());
            this.joinEventButton.setVisibility(View.GONE);
            this.exitEventButton.setVisibility(View.VISIBLE);
            this.holder.getUsers().add(this.holder.getUser());
            this.holder.getEvent().getAttendents().put(this.holder.getUser().get_id(), false);
            this.holder.getUser().getEvents().add(this.holder.getEvent().getId());
            if (this.holder.getEvent().getIsPrivate()) {
                if (Boolean.FALSE.equals(this.holder.getEvent().getAttendents().get(this.holder.getUser().get_id()))) {
                    this.exitEventButton.setText("Cancel Request");
                    this.userListRecyclerView.setVisibility(View.INVISIBLE);
                } else {
                    this.exitEventButton.setText("Leave Event");
                }
            }

            this.userAdapter.notifyItemInserted(this.holder.getUsers().size() - 1);
        } catch (Exception e) {
            //handle
        }
    }

    // This method will be called when the "Leave Event" button is clicked
    public void onLeaveEventClick(View view) {
        if (this.holder.getUser().get_id().equals(this.holder.getEvent().getCreator_id())) {
            try {
                Database.delEvent(this.holder.getEvent().getId());
                this.holder.getUser().getEvents().remove(this.holder.getEvent().getId());
                this.holder.success();
            } catch (Exception e) {
                //handle
            }
        } else {
            boolean removed = removeUser(holder.getUser());
            if (removed) {
                this.exitEventButton.setVisibility(View.GONE);
                this.joinEventButton.setVisibility(View.VISIBLE);
                this.holder.getUser().getEvents().remove(this.holder.getEvent().getId());
            }
        }
    }

    private boolean removeUser(UserDisplay user) {
        try {
            Database.exitEvent(user.get_id(), this.holder.getEvent().getId());
            int idx = this.holder.getUsers().indexOf(user);
            this.holder.getUsers().remove(idx);
            this.userAdapter.notifyItemRemoved(idx);
            return true;
        } catch (Exception e) {
            return false;
            //handle
        }
    }

    private void acceptUser(UserDisplay user) {
        try {
            Database.acceptUser(this.holder.getEvent().getId(), user.get_id());
            int idx = this.holder.getUsers().indexOf(user);
            this.holder.getEvent().getAttendents().put(user.get_id(), true);
            this.userAdapter.notifyItemChanged(idx);
        } catch (Exception e) {
            //handle
        }
    }

    @Override
    public void onKickClick(int position) {
        removeUser(this.holder.getUsers().get(position));
    }

    @Override
    public void onAcceptClick(int position) {
        acceptUser(this.holder.getUsers().get(position));
        holder.updateBadge();
    }

    @Override
    public void onMessageClick(int position) {
        Bundle args = new Bundle();
        args.putSerializable("user", this.holder.getUser());

        UserDisplay[] others = {this.holder.getUsers().get(position)};
        args.putSerializable("other_users", others);

        Intent i = new Intent(this.getContext(), CreateMessageActivity.class);
        i.putExtras(args);
        startActivity(i);

    }

    @Override
    public void onUserItemClick(int position) {
        Bundle args = new Bundle();
        args.putSerializable("user", this.holder.getUser());
        args.putSerializable("other_user_id", this.holder.getUsers().get(position).get_id());

        Intent i = new Intent(this.getContext(), ViewUserProfileActivity.class);
        i.putExtras(args);
        startActivity(i);
    }


    @Override
    public Bitmap getUserProfile(String profile_pic_id) {
        return this.holder.getProfilePicBitmap(profile_pic_id);
    }
}
