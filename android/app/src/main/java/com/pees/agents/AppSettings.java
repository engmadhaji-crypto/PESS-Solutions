package com.pees.agents;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_settings")
public class AppSettings {
    @PrimaryKey
    public int id = 1;
    public String serverUrl = "";
    public long lastSync = 0L;

    public AppSettings() {}
}
