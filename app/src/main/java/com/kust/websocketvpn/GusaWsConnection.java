package com.kust.websocketvpn;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

import tech.gusavila92.websocketclient.WebSocketClient;

public class GusaWsConnection implements VpnServerConnection {
    public GusaWsConnection(URI uri, int timeout){
        WebSocketClient ws = new WebSocketClient(uri) {
            @Override
            public void onOpen() {

            }

            @Override
            public void onTextReceived(String message) {

            }

            @Override
            public void onBinaryReceived(byte[] data) {

            }

            @Override
            public void onPingReceived(byte[] data) {

            }

            @Override
            public void onPongReceived(byte[] data) {

            }

            @Override
            public void onException(Exception e) {

            }

            @Override
            public void onCloseReceived() {

            }
        };

        ws.setConnectTimeout(timeout);
    }

    @Override
    public void connect() throws IOException {

    }

    @Override
    public void send(byte[] data) {

    }

    @Override
    public void setListener(Listener reader) {

    }

    @Override
    public void disconnect() {

    }
}
