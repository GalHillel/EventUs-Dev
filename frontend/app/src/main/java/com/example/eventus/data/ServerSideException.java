package com.example.eventus.data;

public class ServerSideException extends Exception {
    private final int returnCode;

    public ServerSideException(String message) {
        super(message);
        this.returnCode = -1;
    }

    public ServerSideException(int returnCode, String message) {
        super(message);
        this.returnCode = returnCode;
    }

    public int getReturnCode() {
        return returnCode;
    }
}
