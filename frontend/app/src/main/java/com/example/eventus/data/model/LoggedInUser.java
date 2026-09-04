package com.example.eventus.data.model;

import java.util.Map;

public class LoggedInUser extends UserProfile {
    Map<String, Boolean> messages;

    public Map<String, Boolean> getMessages() {
        return messages;
    }


}
