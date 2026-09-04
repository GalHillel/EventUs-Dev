package com.example.eventus.data.model;

import java.io.Serializable;
import java.util.Objects;

public class UserDisplay implements Serializable {
    protected String _id;
    protected String name, user_type;
    protected String profile_pic;

    public String get_id() {
        return _id;
    }

    public String getName() {
        return name;
    }

    public String getUser_type() {
        return user_type;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !o.getClass().isAssignableFrom(getClass())) return false;
        UserDisplay that = (UserDisplay) o;
        return Objects.equals(_id, that._id);
    }

    public String getProfile_pic() {
        return profile_pic;
    }

    public void setProfile_pic(String s) {
        this.profile_pic = s;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id);
    }

}
