package fr.jangberry.sword;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    protected static final String clientID = "1usq907ae5z2wpjr6648fydgsjwqh1";
    private static final String savedData_Location = "fr.jangberry.twitchsword.prefs";
    private static final String apiScopes = "chat_login";
    String channel;
    String token;
    Boolean changinChannel = false;
    int currentView;    /*
                        0 = login (webview)
                        1 = choosing channel
                        2 = MainLayout
                        */
    private SocketService socketservice;
    protected ServiceConnection serviceconnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            SocketService.LocalBinder binder = (SocketService.LocalBinder) service;
            socketservice = binder.getService();
            //mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            //mBound = false;
        }

    };
    private RecyclerView usersRecycler;
    private List<MessageObject> usersObject = new ArrayList<>();
    private List<String> selected = new ArrayList<>();
    private List<String> usersString = new ArrayList<>();
    private SeekBar durationTO;
    private int intTOduration;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_aboutButton:
                Intent aboutActivityCall = new Intent(this, AboutTab.class);
                startActivity(aboutActivityCall);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    Boolean checkLogged() {
        return socketservice.logged;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentView == 0) {                                      //0 = login (webview)
                finish();
            } else if (currentView == 1) {                               //1 = choosing channel
                finish();
            } else if (currentView == 2) {                               //2 = MainView
                setContentView(R.layout.activity_main_chosechannel);
                currentView = 1;
                changinChannel = true;
                Toolbar toolbar = findViewById(R.id.toolbar_channel);
                setSupportActionBar(toolbar);
                setTitle(R.string.app_name);
                TextView channelField = findViewById(R.id.channelField);
                channelField.setText(channel);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SocketService.class));
        bindService(new Intent(this, SocketService.class),
                serviceconnection,
                Context.BIND_AUTO_CREATE);
        setContentView(R.layout.activity_main_login);
        currentView = 0;
        setTitle(getString(R.string.app_name));
        String uri = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + clientID +
                "&scope=" + apiScopes +
                "&redirect_uri=http://localhost" +
                "&response_type=token";
        final WebView webview = findViewById(R.id.Login);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient());
        webview.loadUrl(uri);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.v("URL", url);                         // Commented because of security issues
                if (url.contains("localhost/#")) {
                    Log.i("TwitchLogin",
                            "Logged, recovering Token, connecting to chat and setting up button view...");
                    token = url.substring(url.indexOf("=") + 1, url.indexOf("&"));
                    Log.v("Token", token);                 // Commented because of security issues
                    webview.destroy();
                    setContentView(R.layout.activity_main_chosechannel);
                    currentView = 1;
                    Toolbar toolbar = findViewById(R.id.toolbar_channel);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.app_name);
                    socketservice.socketConnect(token);
                    new ChangeViewChecker().start();
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!failingUrl.contains("localhost/#")) {
                    Log.v("failing url", failingUrl);
                    String htmlData =
                            "<html>" +
                                    "<body>" +
                                    "<h1>" +
                                    getString(R.string.connectionErrorHTML) +
                                    "</h1>" +
                                    getString(R.string.twitchUnjoinableHTML) +
                                    "</body>" +
                                    "</html>";
                    view.loadUrl("about:blank");
                    view.loadDataWithBaseURL(
                            null, htmlData, "message/html", "UTF-8", null);
                    view.invalidate();
                }
            }
        });
    }

    public void onChannelChosen(View view) {
        /*if(channel.equals("custom")){*/
        EditText channelField = findViewById(R.id.channelField);
        channel = channelField.getText().toString().toLowerCase().replace(" ", "");
        //}
        if (!channel.equals("")) {
            if (!changinChannel) {
                socketservice.setChannel(channel);
                findViewById(R.id.channelloading).setVisibility(View.VISIBLE);
                setTitle("...");
            } else {
                socketservice.newChannel(channel);
                findViewById(R.id.channelloading).setVisibility(View.VISIBLE);
                setTitle("...");
                new ChangeViewChecker().start();
                changinChannel = false;
            }
        } else {
            findViewById(R.id.textviewchannelnamerror).setVisibility(View.VISIBLE);
        }
    }


    public void onButtonClick(View view) {
    }


    /*
    public Boolean onButtonLongClick(View view) {

    }
    */

    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceconnection);
    }

    private void setButton() {
        TextView buttonTO = findViewById(R.id.buttonTO);
        if (intTOduration < 60) {
            buttonTO.setText(intTOduration + "s");
        } else if (intTOduration < 3600) {
            buttonTO.setText(intTOduration / 60 + "m");
        } else if (intTOduration < 86400) {
            buttonTO.setText(intTOduration / 60 / 60 + "h");
        } else if (intTOduration < 604800) {
            buttonTO.setText(intTOduration / 60 / 60 / 24 + getString(R.string.dayin1letter));
        } else {
            buttonTO.setText(intTOduration / 60 / 60 / 24 / 7 + getString(R.string.weekin1letter));
        }
    }

    public void onRefresh(@Nullable View view) {
        List<MessageObject> list = socketservice.lastMessages;
        int i;
        for (i = 0; i < list.size(); i++) {
            MessageObject message = list.get(i);
            if (usersString.contains(message.getUsername())) {
                usersObject.remove(usersString.indexOf(message.getUsername()));
                usersString.remove(usersString.indexOf(message.getUsername()));
            }
            usersObject.add(0, message);
            usersString.add(0, message.getUsername());
        }
        selected.clear();
        socketservice.lastMessages.clear();
        updateSelectedUsers();
        usersRecycler.setAdapter(new MyAdapter(usersObject));
    }

    public void onUserSelected(View view) {
        if (selected.contains(view.getContentDescription().toString())) {
            view.setBackground(getDrawable(R.color.cardUnselected));
            selected.remove(view.getContentDescription().toString());
        } else {
            view.setBackground(getDrawable(R.color.cardSelected));
            selected.add(view.getContentDescription().toString());
        }
        updateSelectedUsers();
    }

    private void updateSelectedUsers() {
        TextView view = findViewById(R.id.SelectedUser);
        String string = "";
        int i;
        for (i = 0; i < selected.size(); i++) {
            string += selected.get(i);
            if (selected.size() > 1 && i != selected.size() - 1) {
                string += "; ";
            }
        }
        view.setText(string);
    }

    public void onTO(View view) {
        int i;
        TextView reason = findViewById(R.id.Reason);
        for (i = 0; i < selected.size(); i++) {
            String string = "/timeout ";
            string += selected.get(i) + " ";
            string += intTOduration + " ";
            string += reason.getText();
            socketservice.send(string);
            usersObject.remove(usersString.indexOf(selected.get(i)));
            usersString.remove(selected.get(i));
        }
        onRefresh(null);
    }

    public void onBan(View view) {
        int i;
        TextView reason = findViewById(R.id.Reason);
        for (i = 0; i < selected.size(); i++) {
            String string = "/ban ";
            string += selected.get(i) + " ";
            string += reason.getText();
            socketservice.send(string);
            usersObject.remove(usersString.indexOf(selected.get(i)));
            usersString.remove(selected.get(i));
        }
        onRefresh(null);
    }

    public void onPremit(View view) {
        int i;
        for (i = 0; i < selected.size(); i++) {
            String string = "!permit ";
            string += selected.get(i) + " ";
            socketservice.send(string);
            usersObject.remove(usersString.indexOf(selected.get(i)));
            usersString.remove(selected.get(i));
        }
        onRefresh(null);
    }

    class ChangeViewChecker extends Thread {
        @Override
        public void run() {
            while (!checkLogged()) {
                try {
                    sleep(100);
                } catch (Exception e) {
                    Log.v("Waiting process", "interrupted", e);
                }
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    setContentView(R.layout.activity_main);
                    currentView = 2;
                    Toolbar toolbar = findViewById(R.id.toolbar_main);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.app_name);
                    usersRecycler = findViewById(R.id.users);
                    usersRecycler.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    TextView buttonTO = findViewById(R.id.buttonTO);
                    intTOduration = 1;
                    buttonTO.setText("1s");
                    final TextView customTO = findViewById(R.id.customTO);
                    durationTO = findViewById(R.id.seekBarTO);
                    durationTO.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                            if (customTO.getVisibility() == View.VISIBLE) {
                                customTO.setVisibility(View.GONE);
                            }
                            switch (i) {
                                case 0:
                                    intTOduration = 1;
                                    break;
                                case 1:
                                    intTOduration = 10;
                                    break;
                                case 2:
                                    intTOduration = 60;
                                    break;
                                case 3:
                                    intTOduration = 300;
                                    break;
                                case 4:
                                    intTOduration = 600;
                                    break;
                                case 5:
                                    intTOduration = 1800;
                                    break;
                                case 6:
                                    intTOduration = 3600;
                                    break;
                                case 7:
                                    intTOduration = 7200;
                                    break;
                                case 8:
                                    intTOduration = 21600;
                                    break;
                                case 9:
                                    intTOduration = 43200;
                                    break;
                                case 10:
                                    intTOduration = 86400;
                                    break;
                                case 11:
                                    intTOduration = 172800;
                                    break;
                                case 12:
                                    intTOduration = 259200;
                                    break;
                                case 13:
                                    intTOduration = 604800;
                                    break;
                                case 14:
                                    intTOduration = 1209600;
                                    break;
                                case 15:
                                    customTO.setVisibility(View.VISIBLE);
                                    customTO.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                                        @Override
                                        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                                            intTOduration = Integer.parseInt(textView.getText().toString());
                                            setButton();
                                            return false;
                                        }
                                    });

                                    break;
                            }
                            setButton();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                }
            });
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {
        List<MessageObject> list;

        public MyAdapter(List<MessageObject> list) {
            this.list = list;
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup viewGroup, int itemType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cell_cards, viewGroup, false);
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MyViewHolder myViewHolder, int position) {
            MessageObject messageObject = list.get(position);
            myViewHolder.bind(messageObject);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }


    public class MyViewHolder extends RecyclerView.ViewHolder {
        private TextView username;
        private View container;
        private TextView message;

        public MyViewHolder(View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.username);
            container = itemView.findViewById(R.id.cardcontainer);
            message = itemView.findViewById(R.id.usersmessage);
        }

        public void bind(MessageObject messageObject) {
            username.setText(messageObject.getUsername());
            container.setContentDescription(messageObject.getUsername());
            message.setText(messageObject.getMessage());
        }
    }


}