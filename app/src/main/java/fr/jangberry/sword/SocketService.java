package fr.jangberry.sword;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class SocketService extends Service {
    private final IBinder mBinder = new LocalBinder();
    List<MessageObject> lastMessages = new ArrayList<>();
    private ConnectionManager cm = new ConnectionManager();

    public SocketService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        new ConnectSocketThread().start();
        new RecvThread().start();
    }


    public boolean isConnectedToChannel() {
        return cm.isConnectedToChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void send(String message) {
        new SendThread("PRIVMSG #" + cm.getChannel() + " :" + message + "\r\n").start();
    }

    void onRecv(String message) {
        //Log.v("MSG recu", message);                   //Commented because of the spam created with
        MessageObject incomming = new MessageObject(message.substring(1, message.indexOf("!")), message.substring(message.indexOf(":", 1) + 1));
        /*                                                   _________/  _________________/                                            /
                                                            |           |                                                             |
        Typical twitch incoming message :                   :nrandolph99!nrandolph99@nrandolph99.tmi.twitch.tv PRIVMSG #katjawastaken :yeah
        where :                                                 /\          /\          /\                                  /\          /\
                                                            Both of them are the sender's name                              ||     And this is the message (can be any length)
                                                                                                                    This is channel name
         */
        lastMessages.add(incomming);
        if (BuildConfig.DEBUG) {
            Log.v("recv", "<" + message);
        }
    }

    public void socketConnect(String token) {
        cm.setToken(token);
        new connectThread().start();
    }

    public void onDestroy() {
        super.onDestroy();
        cm.disconnect();
    }

    public void setChannel(String channel) {
        if (channel != null) {
            new SetChannel(channel).start();
        }
    }

    public class SetChannel extends Thread {
        String channel;

        SetChannel(String channel) {
            this.channel = channel;
        }

        public void run() {
            cm.setChannel(channel);
            cm.joinChannel();
        }
    }

    class ConnectSocketThread extends Thread {
        @Override
        public void run() {
            super.run();
            cm.connectSocket();
        }
    }

    class connectThread extends Thread {
        @Override
        public void run() {
            super.run();
            cm.loginTwitch();
        }
    }


    public class LocalBinder extends Binder {
        SocketService getService() {
            return SocketService.this;
        }
    }

    class SendThread extends Thread {
        String message;

        SendThread(String message) {
            this.message = message;
        }

        public void run() {
            cm.rawSend(message);
        }
    }

    class RecvThread extends Thread {
        public void run() {
            try {
                while (cm.isActive()) {
                    while (!cm.isSocketConnected()) {
                        sleep(10);
                    }
                    while (cm.isSocketConnected()) {
                        String recv = null;
                        while (recv == null && cm.isSocketConnected()) {
                            try {
                                recv = cm.read();
                            } catch (SocketException e) {
                                Looper.prepare();
                                Toast.makeText(SocketService.this, R.string.reconnect, Toast.LENGTH_LONG).show();
                            }
                        }
                        if (cm.isSocketConnected()) {
                            if (!recv.equals("")
                                    && !recv.substring(0, 4).equals("PING")
                                    && !recv.substring(1, 14).equals("tmi.twitch.tv")
                                    && !(recv.contains(".tmi.twitch.tv JOIN #" + cm.getChannel()) &&
                                    recv.substring(1, recv.indexOf("!")).equals(cm.getUsername()))
                                    && !(recv.contains(".tmi.twitch.tv PART #") &&
                                    recv.substring(1, recv.indexOf("!")).equals(cm.getUsername()))
                                    && !recv.substring(1, cm.getUsername().length() + 15).equals(cm.getUsername() + ".tmi.twitch.tv")) {
                                onRecv(recv);
                            } else if (recv.substring(0, 4).equals("PING")) {
                                new SendThread("PONG").start();
                                if (BuildConfig.DEBUG) {
                                    Log.d("RecvThread", "PING-PONG");
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.i("MSG", recv);
                                }
                            }
                        }
                    }
                    sleep(50);
                }
            } catch (java.lang.InterruptedException e) {
                if (BuildConfig.DEBUG) {
                    Log.wtf("Receving", "Thread", e);
                }
            }
        }
    }
}
