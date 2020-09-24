package com.kust.websocketvpn;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NvWsConnection extends WebSocketAdapter implements VpnServerConnection {
    private static final WebSocketFactory factory = new WebSocketFactory();

    private WebSocket ws;
    private Listener reader;

    public NvWsConnection(URI endpoint, int timeout) throws IOException {
        ws = factory.createSocket(endpoint, timeout);
        ws.addListener(this);
//        ws.setFrameQueueSize(1);
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        reader.onOpen();
    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        if(reader!=null){
            reader.read(binary);
        }
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        throw cause;
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        throw new RuntimeException("Fatal error",cause);
    }

    @Override
    public void connect() throws IOException {
        try {
            ws.connect();
            reader.protect(ws.getConnectedSocket());
        } catch (WebSocketException e) {
            throw new IOException("Failed to connect to WebSocket", e);
        }
    }

    @Override
    public void send(byte[] data){
        ws.sendBinary(data);
    }

    @Override
    public void setListener(Listener reader) {
        this.reader=reader;
    }

    @Override
    public void disconnect() {
        ws.disconnect();
    }

    private final static String TAG="NvWsConnection";
}
