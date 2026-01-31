package com.rssai.model;

import java.util.Date;

public class LoginDevice {
    private String series;
    private String username;
    private Date lastUsed;
    private String deviceName;
    private boolean currentDevice;

    public LoginDevice() {
    }

    public LoginDevice(String series, String username, Date lastUsed) {
        this.series = series;
        this.username = username;
        this.lastUsed = lastUsed;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isCurrentDevice() {
        return currentDevice;
    }

    public void setCurrentDevice(boolean currentDevice) {
        this.currentDevice = currentDevice;
    }
}
