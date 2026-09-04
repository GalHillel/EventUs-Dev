package com.example.eventus.ui.screens.EventDetails;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;


public class EventDetailsPagerAdaptor extends FragmentPagerAdapter {
    private final EventDetailsActivity myContext;
    int totalTabs;

    public EventDetailsPagerAdaptor(EventDetailsActivity context, FragmentManager fm, int totalTabs) {
        super(fm);
        myContext = context;
        this.totalTabs = totalTabs;
    }

    // this is for fragment tabs
    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new EventDetailsTabFragment(myContext);
            case 1:
                return new EventParticipantsTabFragment(myContext);
            default:
                return null;
        }
    }

    // this counts total number of tabs
    @Override
    public int getCount() {
        return totalTabs;
    }


}
