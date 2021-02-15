package com.aurora.store.events;

import lombok.Data;


@Data
public class Event {

    private SubType subType;
    private String stringExtra;
    private int intExtra;
    private int status;

    public Event(SubType subType, String stringExtra, int status) {
        this.subType = subType;
        this.stringExtra = stringExtra;
        this.status = status;
    }

    public Event(SubType subType, String stringExtra) {
        this.subType = subType;
        this.stringExtra = stringExtra;
        this.status = StatusType.NOTSET.ordinal();
    }

    public Event(SubType subType, int intExtra) {
        this.subType = subType;
        this.intExtra = intExtra;
        this.status = StatusType.NOTSET.ordinal();
    }

    public Event(SubType subType) {
        this.subType = subType;
    }

    public enum SubType {
        API_SUCCESS,
        API_FAILED,
        API_ERROR,
        BLACKLIST,
        WHITELIST,
        INSTALLED,
        UNINSTALLED,
        NETWORK_UNAVAILABLE,
        NETWORK_AVAILABLE,
        BULK_UPDATE_NOTIFY
        BULK_UPDATE_STARTED,
        BULK_UPDATE_STOPPED,
        DOWNLOAD
    }

    // evermind: even so we have status types. The SubType may not even consider a StatusType
    // so make sure you know what the sender is doing
    public enum StatusType {
        FAILURE,
        SUCCESS,
        CANCELED,
        CANCEL,
        API_FAILURE,
        NOTSET
    }
}
