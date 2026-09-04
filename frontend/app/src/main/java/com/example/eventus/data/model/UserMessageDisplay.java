package com.example.eventus.data.model;

import java.util.Date;
import java.util.Map;

public class UserMessageDisplay {
    protected String _id;
    protected Map<String, String>[] sender_id;
    protected String title;
    protected Date date_sent;


    public String get_id() {
        return _id;
    }

    public String getSender_id() {
        if (sender_id.length > 0 && sender_id[0].containsKey("name")) {
            return sender_id[0].get("_id");
        }
        return "";
    }

    public String getSenderName() {
        if (sender_id.length > 0 && sender_id[0].containsKey("name")) {
            return sender_id[0].get("name");
        }
        return "";
    }

    public String getTitle() {
        return title;
    }

    public Date getDate_sent() {
        return date_sent;
    }

    public String getDateString() {
        return this.date_sent.toString();
    }
}
