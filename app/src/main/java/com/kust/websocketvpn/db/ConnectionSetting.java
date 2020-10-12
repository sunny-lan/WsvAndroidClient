package com.kust.websocketvpn.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "connection_settings")
public class ConnectionSetting {
    @PrimaryKey
    @NonNull
    public String name;

    public String serverURL;

    public boolean enforceWss;

    public boolean allowIPV6 = false;

    public String tunAddr = "26.26.26.1";
    public String tunAddrIPV6 = "fdfe:dcba:9876::1";

    public String dnsAddr = "8.8.8.8";

    public long bufferSize = 32 * 1024;
}
