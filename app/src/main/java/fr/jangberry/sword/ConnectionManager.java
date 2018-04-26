package fr.jangberry.sword;

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
import java.net.SocketException;

import static java.lang.Thread.sleep;

class ConnectionManager {
    boolean active = true;
    private String token;
    private String username;
    private String channel;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connectedToChannel = false;
    private boolean connectedToTwitch = false;

    public boolean isActive() {
        return active;
    }

    public boolean isSocketConnected() {
        if (socket == null) {
            return false;
        } else {
            return socket.isConnected();
        }
    }

    public boolean isConnectedToChannel() {
        return connectedToChannel;
    }

    public void setToken(String token) {
        this.token = token;
        String url = "https://api.twitch.tv/helix/users";
        HttpAgent.get(url)
                .headers("client-id", fr.jangberry.sword.Resources.clientID,
                        "Authorization", "Bearer " + token)
                .goJson(new JsonCallback() {
                    @Override
                    protected void onDone(boolean success, JSONObject jsonResults) {
                        if (success) {
                            if (BuildConfig.DEBUG) {
                                Log.v("HTTP", "Success :" + jsonResults.toString());
                            }
                            try {
                                setUsername(jsonResults.getJSONArray("data")
                                        .getJSONObject(0)
                                        .get("login")
                                        .toString());
                            } catch (org.json.JSONException e) {
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.e("HTTP", "error");
                            }
                        }
                    }
                });
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        if (this.channel != null) {
            if (!channel.equals(this.channel)) {
                connectedToChannel = false;
                out.println("PART #" + this.channel);
            }
        } else {
            connectedToChannel = false;
        }
        this.channel = channel;

    }

    public String getUsername() {
        return username;
    }

    private void setUsername(String username) {
        this.username = username;
    }

    public void connectSocket() {
        try {
            socket = new Socket();
            SocketAddress socaddrs = new InetSocketAddress(
                    "irc.chat.twitch.tv", 6667);
            if (BuildConfig.DEBUG) {
                Log.i("Connection manager", "Trying to connect");
            }
            socket.connect(socaddrs, 1000);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            if (BuildConfig.DEBUG) {
                Log.i("Connection manager", "Socket connected");
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e("Connection manager", "error", e);
            }
        }
    }

    public void loginTwitch() {
        if (!socket.isConnected()) {
            connectSocket();
        }
        out.println("PASS oauth:" + token);
        if (username == null) {
            do {
                try {
                    sleep(10);
                } catch (Exception e) {
                }
            } while (username == null);
        }
        out.println("NICK " + username);
        connectedToTwitch = true;
        connectedToChannel = false;
    }

    public void joinChannel() {
        if (!connectedToTwitch) {
            loginTwitch();
        }
        if (!connectedToChannel) {
            out.println("JOIN #" + channel);
        }
        connectedToChannel = true;
    }

    public void rawSend(String message) {
        if (!connectedToChannel) {
            joinChannel();
        } else {
            out.println(message);
            if (BuildConfig.DEBUG) {
                Log.v("Send", ">" + message);
            }
        }
    }

    public String read() throws SocketException {
        try {
            if (socket.isConnected()) {
                while (in == null) {
                }
                return in.readLine();
            } else {
                return null;
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.wtf("read", "error", e);
            }
            if (e.getMessage().contains("Connection timed out")) {
                connectedToChannel = false;
                connectedToTwitch = false;
                try {
                    socket.close();
                } catch (IOException i) {
                }
                socket = null;
                connectSocket();
                new ReconnecterThread().start();
            } else {
                if (BuildConfig.DEBUG) {
                    Log.wtf("read", "error, unsolvable");
                }
                throw new SocketException();
            }
            return null;
        }
    }

    public void disconnect() {
        active = false;
        try {
            out.println("PART #" + channel);
            socket.close();
        } catch (IOException e) {
        }
    }

    class ReconnecterThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!socket.isConnected()) {
            }
            loginTwitch();
            if (channel != null) {
                joinChannel();
            }
        }
    }
}
