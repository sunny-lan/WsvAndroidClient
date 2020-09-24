package com.kust.websocketvpn;

import java.io.IOException;
import java.net.Socket;

interface VpnServerConnection {
    interface Listener {
        void read(byte[] data);
        void onOpen();
        void protect(Socket s);
    }
    void connect() throws IOException;
    void send(byte[] data);
    void setListener(Listener reader);
    void disconnect();
}
