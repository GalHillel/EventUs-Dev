package com.example.eventus.data.model;

public class ServerResponse {
    final int returnCode;
    final String payload;

    public ServerResponse(int returnCode, String payload) {
        this.returnCode = returnCode;
        this.payload = payload;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public String getPayload() {
        return payload;
    }
}
