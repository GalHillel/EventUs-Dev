package com.example.eventus.data.model;

import java.util.ArrayList;

public class UserProfile extends UserDisplay {
    protected String bio;
    protected int rating;
    protected ArrayList<String> events;


    public String getBio() {
        return this.bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public ArrayList<String> getEvents() {
        return events;
    }
}
