package com.example.eventus.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.HashMap;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import com.example.eventus.BuildConfig;
import com.example.eventus.data.model.LoggedInUser;
import com.example.eventus.data.model.NewUserMessage;
import com.example.eventus.data.model.UserMessageDisplay;
import com.example.eventus.data.model.ServerResponse;
import com.example.eventus.data.model.User;
import com.example.eventus.data.model.UserDisplay;
import com.example.eventus.data.model.UserEvent;
import com.example.eventus.data.model.UserEventDisplay;
import com.example.eventus.data.model.UserMessage;
import com.example.eventus.data.model.UserProfile;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;


public class Database {
    static Gson gson = new Gson();

    //POST requests

    /**
     * TODO error handling
     * Registers a new user
     *
     * @param email     user email
     * @param name      username
     * @param password  password
     * @param user_type creator/user
     */
    public static User addUser(String email, String name, String password, String user_type) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("name", name);
        payloadData.put("email", email);
        payloadData.put("password", password);
        payloadData.put("user_type", user_type);
        AsyncHttpRequest task = new AsyncHttpRequest("users/register", payloadData, null, "POST");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_CREATED) {
            return gson.fromJson(response.getPayload(), User.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }


    }

    /**
     * TODO Test and add error handling
     * creates a new event
     *
     * @param creator_id  id of event creator
     * @param name        name of the event
     * @param date        date of the event
     * @param location    location of the event
     * @param description description of the event
     */
    public static UserEvent addEvent(String creator_id, String name, String date, String location, String description, Boolean isPrivate) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("name", name);
        payloadData.put("creator_id", creator_id);
        payloadData.put("date", date);
        payloadData.put("location", location);
        payloadData.put("description", description);
        payloadData.put("isPrivate", isPrivate);

        AsyncHttpRequest task = new AsyncHttpRequest("events/create", payloadData, null, "POST");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();

        if (response.getReturnCode() == HttpURLConnection.HTTP_CREATED) {
            return gson.fromJson(response.getPayload(), UserEvent.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    public static void sendMessage(String sender_id, List<String> receiver_ids, String title, String content) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("sender_id", sender_id);
        payloadData.put("receiver_ids", receiver_ids);
        payloadData.put("title", title);
        payloadData.put("content", content);
        AsyncHttpRequest task = new AsyncHttpRequest("messages", payloadData, null, "POST");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();

        //TODO when getting a message back from the server, must populate the message, or revert sender_id field
        if (response.getReturnCode() == HttpURLConnection.HTTP_CREATED) {
            gson.fromJson(response.getPayload(), NewUserMessage.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    public static void uploadProfilePic(byte[] pic, final FileUploader.UploadCallback callback) {
        FileUploader uploader = new FileUploader();
        uploader.uploadFile(BuildConfig.API_BASE_URL + "profilepics", pic, callback);


    }


    //GET requests

    /**
     * Checks if the user exists in the database and returns it if found
     *
     * @param email     user email
     * @param password  user password
     * @param user_type type of user Organizer or Participant
     * @return User entry in the database
     * @throws Exception response error
     */
    public static LoggedInUser userLogin(String email, String password, String user_type) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("email", email);
        payloadData.put("password", password);
        payloadData.put("user_type", user_type);

        AsyncHttpRequest task = new AsyncHttpRequest("users/login", payloadData, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), LoggedInUser.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }

    }

    /**
     * TODO Test and add error handling
     * Get user events for some user
     *
     * @param user_id the user we want to get the events from
     * @return array of eventDisplay
     */
    public static UserEventDisplay[] getEventList(String user_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/events", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserEventDisplay[].class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * get list of user displays from the database for some event
     *
     * @param event_id _id of the event we want to get the users from
     * @return array of userDisplay
     * @throws Exception ServerSideException or other exception
     */
    public static UserDisplay[] getUserList(String event_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id + "/users", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserDisplay[].class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }


    /**
     * TODO Test and error handling
     * get list of user displays from the database for some event
     *
     * @param user_id _id of the user we want to get the message inbox from
     * @return array of UserMessageDisplays
     * @throws Exception ServerSideException or other exception
     */
    public static UserMessageDisplay[] getMessageInbox(String user_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/messages", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {

            return gson.fromJson(response.getPayload(), UserMessageDisplay[].class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    public static Map<String, Boolean> getMessageInboxStatus(String user_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/messageField", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {

            return gson.fromJson(response.getPayload(), LoggedInUser.class).getMessages();
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * loads a certain event
     *
     * @param event_id id of the event
     * @return UserEvent object
     * @throws Exception ServerSideException or other exception
     */
    public static UserEvent loadEvent(String event_id) throws Exception {

        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id + "/info", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserEvent.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * gets a user's profile
     *
     * @param user_id id of the event
     * @return UserProfile object
     * @throws Exception ServerSideException or other exception
     */
    public static UserProfile userProfile(String user_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/profile", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserProfile.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * loads a single user display
     *
     * @param user_id id of the event
     * @return UserDisplay object
     * @throws Exception ServerSideException or other exception
     */
    public static UserDisplay userDisplay(String user_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/display", null, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserProfile.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * loads a certain event
     *
     * @param message_id id of the message
     * @return message object
     * @throws Exception ServerSideException or other exception
     */
    public static UserMessage loadMessage(String message_id, String user_id) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", user_id);
        AsyncHttpRequest task = new AsyncHttpRequest("messages/" + message_id + "/info", payloadData, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserMessage.class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO Test and error handling
     * loads a profile pic
     *
     * @param user_id id of the user we want the profile pic from
     * @return message object
     * @throws Exception ServerSideException or other exception
     */
    public static Bitmap getProfilePic(String user_id) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", user_id);
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/profilepic", payloadData, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            JSONObject jsonObject = new JSONObject(response.getPayload());
            JSONArray dataArray = jsonObject.getJSONArray("data");
            byte[] out = new byte[dataArray.length()];
            for (int i = 0; i < dataArray.length(); i++) {
                out[i] = (byte) dataArray.getInt(i);
            }
            return BitmapFactory.decodeByteArray(out, 0, out.length);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }


    /**
     * TODO error handling and testing
     *
     * @param search paramaters to search for an event, a key is a field in UserEvent
     * @return all valid EventDisplays given the search terms
     * @throws Exception ServerSideException or other exception
     */
    public static UserEventDisplay[] searchEvents(HashMap<String, Object> search) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("events/search", search, null, "GET");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.getPayload(), UserEventDisplay[].class);
        } else {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO error handling and testing
     * joins a user to an event, only use for participants
     *
     * @param user_id  _id of user
     * @param event_id _id of event
     * @throws Exception ServerSideException or other exception
     */
    public static void joinEvent(String user_id, String event_id) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", user_id);
        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id + "/joinEvent", payloadData, null, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }

    }

    /**
     * TODO error handling and testing
     * accepts a user to an event, only use for participants
     *
     * @param user_id  _id of user
     * @param event_id _id of event
     * @throws Exception ServerSideException or other exception
     */
    public static void acceptUser(String event_id, String user_id) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", user_id);
        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id + "/acceptuser", payloadData, null, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO error handling and testing
     * removes a user to an event, only use for participants
     *
     * @param user_id  _id of user
     * @param event_id _id of event
     * @throws Exception ServerSideException or other exception
     */
    public static void exitEvent(String user_id, String event_id) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", event_id);
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/exitEvent", payloadData, null, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO error handling and testing
     * removes a user to an event, only use for participants
     *
     * @param event_id       _id of event
     * @param newEventParams hashmap keys are the field UserEvent we want to update, values are the new field
     * @throws Exception ServerSideException or other exception
     */
    public static void editEvent(String event_id, HashMap<String, Object> newEventParams) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id + "/edit", newEventParams, null, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO error handling and testing
     * rates an event
     *
     * @param user_id  _id of user rating the event
     * @param event_id _id of event
     * @param rating   rating given by user
     * @throws Exception ServerSideException or other exception
     */
    public static void rateEvent(String user_id, String event_id, float rating) throws Exception {
        HashMap<String, Object> payloadData = new HashMap<>();
        payloadData.put("_id", event_id);
        payloadData.put("rating", rating);
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/rate", payloadData, null, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }

    /**
     * TODO error handling and testing
     * removes a user to an event, only use for participants
     *
     * @param user_id        _id of event
     * @param newEventParams hashmap keys are the field UserEvent we want to update, values are the new field
     * @throws Exception ServerSideException or other exception
     */
    public static void editUser(String user_id, HashMap<String, Object> newEventParams) throws Exception {
        HashMap<String, Object> query = new HashMap<>();
        AsyncHttpRequest task = new AsyncHttpRequest("users/" + user_id + "/edit", newEventParams, query, "PATCH");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }


    /**
     * TODO error handling and testing
     * Deletes an event from the database
     *
     * @param event_id _id of event
     * @throws Exception ServerSideException or other exception
     */
    public static void delEvent(String event_id) throws Exception {
        AsyncHttpRequest task = new AsyncHttpRequest("events/" + event_id, null, null, "DELETE");
        task.execute();
        task.get();
        ServerResponse response = task.getServerResponse();
        if (response.getReturnCode() != HttpURLConnection.HTTP_NO_CONTENT) {
            throw new ServerSideException(response.getReturnCode(), response.getPayload());
        }
    }


}
