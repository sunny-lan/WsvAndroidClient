package com.kust.websocketvpn;


import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class AndroidAsyncWsConnection  implements VpnServerConnection {
    private Listener listener;
    private URI host;
    private WebSocket websocket;

    public AndroidAsyncWsConnection(URI host){
        this.host=host;
    }

    @Override
    public void connect() throws IOException {
        AsyncHttpClient.getDefaultInstance().websocket(host.toString(), (String)null, new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, WebSocket webSocket) {
                AndroidAsyncWsConnection.this.websocket=webSocket;
                listener.onOpen();
            }
        });
    }

    @Override
    public void send(byte[] data) {

    }

    @Override
    public void setListener(Listener reader) {
        this.listener=reader;
    }


    @Override
    public void disconnect() {

    }
}
