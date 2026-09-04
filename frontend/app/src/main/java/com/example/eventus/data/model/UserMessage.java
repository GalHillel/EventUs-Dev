package com.example.eventus.data.model;

import java.util.List;

public class UserMessage extends UserMessageDisplay {

    protected List<String> receiver_ids;
    protected String content;

    public List<String> getReceiver_ids() {
        return receiver_ids;
    }

    public String getContent() {
        return content;
    }
}
