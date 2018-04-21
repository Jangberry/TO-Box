package fr.jangberry.sword;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.studioidan.httpagent.HttpAgent;
import com.studioidan.httpagent.JsonCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class SocketService extends Service {private final IBinder mBinder = new LocalBinder();
    public Boolean logged = false;
    Socket socket = new Socket();
    BufferedReader in;
    PrintWriter out;
    String username;
    String channel;

    public SocketService() {}

    protected void setChannel(String incoming) {
        channel = incoming;
    }

    protected void setUsername(String incoming) {
        username = incoming;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void send(String message) {
        new SendThread("PRIVMSG #" + channel + " :" + message + "\r\n").start();
    }

    List<MessageObject> lastMessages = new ArrayList<>();

    void onRecv(String message) {
        //Log.v("MSG recu", message);                   //Commented because of the spam created with
        MessageObject incomming = new MessageObject(message.substring(1, message.indexOf("!")), message.substring(message.indexOf(":", 1)+1));
        /*                                                   _________/  _________________/                                            /
                                                            |           |                                                             |
        Typical twitch incoming message :                   :nrandolph99!nrandolph99@nrandolph99.tmi.twitch.tv PRIVMSG #katjawastaken :yeah
        where :                                                 /\          /\          /\                                  /\          /\
                                                            Both of them are the sender's name                              ||     And this is the message (can be any length)
                                                                                                                    This is channel name
         */
        lastMessages.add(incomming);
    }

    public void socketConnect(String token) {
        new ConnectThread(token).start();
    }

    public void onDestroy() {
        super.onDestroy();
        try {
            socket.close();
            Log.i("SocketService", "Socket closed");
        } catch (IOException e) {
            Log.e("e", "IO", e);
        } catch (NullPointerException e) {
            Log.i("SocketService", "Socket not opened");
        }

    }

    public class LocalBinder extends Binder {
        SocketService getService() {
            return SocketService.this;
        }
    }

    class SendThread extends Thread {
        /*
         *   Usage
         *
         *   new SendThread(String).start();
         */
        String message;
        SendThread(String message) {
            this.message = message;
        }
        public void run() {
            out.println(message);
            Log.v("SendThread", "Sent>" + message);
        }
    }

    class RecvThread extends Thread {
        /*
         *  Usage:
         *
         *  Just launch the thread : there is an infinity loop that receive messages, and call
         *  the onRecv
         */
        public void run() {
            Boolean keepReceiving = true;
            while (keepReceiving) {
                try {
                    while (socket.isConnected()) {
                        String recv = null;
                        while (recv == null && socket.isConnected()) {
                            if (!keepReceiving) {
                                sleep(1000);
                            }
                            recv = in.readLine();
                        }
                        try {
                            if
                                    (!recv.equals("")
                                    && !recv.substring(0, 4).equals("PING")
                                    && !recv.substring(1, 14).equals("tmi.twitch.tv")
                                    && !(recv.contains(".tmi.twitch.tv JOIN #"+channel) &&
                                         recv.substring(1, recv.indexOf("!")).equals(username))
                                    && !(recv.contains(".tmi.twitch.tv PART #") &&
                                         recv.substring(1, recv.indexOf("!")).equals(username)))
                            {
                                onRecv(recv);
                            } else if (recv.substring(0, 4).equals("PING")) {
                                new SendThread("PONG").start();
                                Log.d("RecvThread", "PING-PONG");
                            } else {
                                Log.i("MSG", recv);
                            }
                        } catch (StringIndexOutOfBoundsException e) {
                        }
                    }
                } catch (java.net.SocketException e) {
                    keepReceiving = false;
                    Log.e("RecvThread", "Error", e);
                } catch (java.io.IOException e) {
                    Log.e("RecvThread", "Error", e);
                } catch (java.lang.InterruptedException e) {
                    Log.wtf("Receving", "Thread", e);
                }
            }
        }
    }

    public void newChannel(String channel) {
        logged = false;
        new NewChannel(channel).start();
    }

    public class NewChannel extends Thread {
        String newChannel;

        NewChannel(String newChannel) {
            this.newChannel = newChannel;
        }

        public void run() {
            if (!newChannel.equals(channel)) {
                out.println("PART #" + channel);
                channel = newChannel;
                out.println("JOIN #" + newChannel);
            }
            logged = true;
        }
    }

    class ConnectThread extends Thread {
        String token;

        ConnectThread(String token) {
            this.token = token;
        }

        @Override
        public void run() {
            if (!logged || !socket.isConnected()) {
                try {
                    if (!socket.isConnected()) {
                        SocketAddress socaddrs = new InetSocketAddress(
                                "irc.chat.twitch.tv", 6667);
                        Log.i("SocketService", "Trying to connect");
                        socket.connect(socaddrs, 5000);
                        if (socket.isConnected()) {
                            out = new PrintWriter(socket.getOutputStream(), true);
                            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            Log.i("SocketService", "Socket connected");
                            new RecvThread().start();
                            out.println("PASS oauth:" + token);

                            String url = "https://api.twitch.tv/kraken";
                            HttpAgent.get(url)
                                    .headers("client-id", MainActivity.clientID,
                                            "Authorization", "OAuth " + token,
                                            "Accept", "application/vnd.twitchtv.v5+json")
                                    .goJson(new JsonCallback() {
                                        @Override
                                        protected void onDone(boolean success, JSONObject jsonResults) {
                                            if (success) {
                                                //Log.v("HTTP", "Success :" + jsonResults.toString());
                                                // Commented because of security issues
                                                try {
                                                    setUsername(jsonResults.getJSONObject("token")
                                                            .get("user_name")
                                                            .toString());
                                                } catch (org.json.JSONException e) {}
                                            } else {
                                                Log.e("HTTP", "error");
                                            }
                                        }
                                    });
                            while (username == null) {
                                sleep(10);
                            }
                            out.println("NICK " + username);
                            Log.v("SocketService", "Username get ! It's " +
                                    username + " and you're now logged with");
                        }
                    }
                    while (channel == null) {
                        sleep(250);
                    }
                    out.println("JOIN #" + channel);
                    logged = true;
                    Log.i("SocketService",
                            "Now logged with username " + username +
                                    " to channel " + channel);
                } catch (IOException e) {
                    Log.e("e", "IOErreur", e);
                } catch (Exception e) {
                    Log.e("Socket service", "", e);
                }
            } else {
                Log.d("SocketService", "Already logged");
                logged = true;
            }
        }
    }
}
