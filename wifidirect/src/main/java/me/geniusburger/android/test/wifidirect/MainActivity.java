package me.geniusburger.android.test.wifidirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity implements WifiP2pManager.PeerListListener, View.OnClickListener {

    private static final String TAG = "WifiDirect";
    public static final int OWNER_SEND_PORT = 8988;
    public static final int OTHER_SEND_PORT = 8989;
    private static final int SOCKET_TIMEOUT = 5000;

    private TextView thisDeviceTextView;
    private TextView statusTextView;
    private TextView peersTextView;
    private ProgressBar progressBar;
    private TextView progressNumber;
    private ListView devicesListView;
    private TextView rxLabel;
    private TextView rxText;
    private EditText txText;
    private ImageButton sendButton;

    private Context context;
    private MainActivity activity;
    private Progress currentStatus;
    private boolean disconnectOptionIsVisible = false;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    private List<WifiP2pDevice> devices;
    private WifiP2pDevice device;
    private WifiP2pInfo currentInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thisDeviceTextView = (TextView) findViewById(R.id.textViewThisDevice);
        statusTextView = (TextView) findViewById(R.id.textViewStatus);
        peersTextView = (TextView) findViewById(R.id.textViewPeers);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressNumber = (TextView) findViewById(R.id.textViewProgressNumber);
        devicesListView = (ListView) findViewById(R.id.listViewDevices);
        rxLabel = (TextView) findViewById(R.id.textViewRxLabel);
        rxText = (TextView) findViewById(R.id.textViewRx);
        txText = (EditText) findViewById(R.id.editText);
        sendButton = (ImageButton) findViewById(R.id.button);

        activity = this;
        context = getApplicationContext();
        progressBar.setMax(Progress.getMax());
        sendButton.setOnClickListener(this);
        showRxTx(false);
        showDevices(false);
        updateStatus(Progress.START);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WifiDirectBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    protected void updateThisDevice(WifiP2pDevice device) {
        thisDeviceTextView.setText(deviceToString(device));
    }

    protected void updateWifiState(boolean enabled) {
        if(enabled) {
            // wifi direct mode enabled
            discoverPeers();
        }
    }

    private void discoverPeers() {
        updateStatus(Progress.ENABLED);
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                updateStatus(Progress.DISCOVER);
                showDevices(true);
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(context, "Failed to discover peers: " + reasonCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatus(Progress progress) {
        currentStatus = progress;
        progressBar.setProgress(progress.getValue());
        statusTextView.setText(progress.getText());
        progressNumber.setText(String.format("%d/%d", progress.getValue(), Progress.getMax()));
    }

    public void receivedMessage(String message) {
        if( currentInfo != null) {
            updateStatus(Progress.TALKING);
            rxText.setText(message);
            listen();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_disconnect).setVisible(disconnectOptionIsVisible);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_disconnect) {
            disconnect();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void disconnect() {
        manager.removeGroup(channel, disconnectListener);
    }

    private WifiP2pManager.ActionListener disconnectListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onFailure(int reasonCode) {
            Log.e(TAG, "Disconnect failed. Reason :" + reasonCode);
        }
        @Override
        public void onSuccess() {
        }
    };

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);

    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        devices = new ArrayList<>(peers.getDeviceList());
        peersTextView.setText("Peers: " + devices.size());

        List<String> displayStrings = new ArrayList<>(devices.size());
        for (WifiP2pDevice device : devices) {
            displayStrings.add(deviceToString(device));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, displayStrings.toArray(new String[displayStrings.size()]));
        devicesListView.setAdapter(adapter);
        devicesListView.setOnItemClickListener(deviceClickListener);

        if (currentStatus.getValue() <= Progress.FOUND_PEERS.getValue()) {
            updateStatus(Progress.FOUND_PEERS);
        }
    }

    private String deviceToString(WifiP2pDevice device) {
        return String.format("%s - %s", device.deviceName, getStatus(device.status));
    }

    private AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            device = devices.get(position);
            switch(device.status) {
                case WifiP2pDevice.AVAILABLE:
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = device.deviceAddress;
                    manager.connect(channel, config, connectListener);
                    break;
                case WifiP2pDevice.CONNECTED:
                    handleConnected();
                    break;
                default:
                    Toast.makeText(context, "invalid status", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private WifiP2pManager.ActionListener connectListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason) {
            Toast.makeText(context, "Failed to connect: " + reason, Toast.LENGTH_SHORT).show();
        }
    };

    private void showDisconnectOption(boolean show) {
        disconnectOptionIsVisible = show;
        invalidateOptionsMenu();
    }

    protected void notifyOfConnectionChange(NetworkInfo info) {
        if( info.isConnected()) {
            handleConnected();
        } else {
            currentInfo = null;
            showDisconnectOption(false);
            showDevices(true);
            discoverPeers();
        }
    }

    private void handleConnected() {
        rxLabel.setText((device == null ? "null" : device.deviceName) + ": ");
        updateStatus(Progress.CONNECTED);
        showDisconnectOption(true);
        manager.requestConnectionInfo(channel, connectionInfoListener);
    }

    private WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            currentInfo = info;
            if( info.groupFormed) {
                if( device == null && devices != null) {
                    for(WifiP2pDevice oldDev : devices) {
                        if( oldDev.deviceAddress.equals(info.groupOwnerAddress.getHostAddress())) {
                            device = oldDev;
                            rxLabel.setText( device.deviceName + " ");
                            break;
                        }
                    }
                }
                Toast.makeText(context, "Group Formed: " + info.groupOwnerAddress.getHostAddress(), Toast.LENGTH_LONG).show();
                showRxTx(true);
                listen();
            } else {
                Toast.makeText(context, "Failed to make group", Toast.LENGTH_LONG).show();
                disconnect();
            }
        }
    };

    private void listen() {
        new ReadAsyncTask(activity, currentInfo).execute();
    }

    private void showRxTx(boolean show) {
        int visible = show ? View.VISIBLE : View.GONE;
        rxLabel.setVisibility(visible);
        rxText.setVisibility(visible);
        rxText.setText(null);
        txText.setVisibility(visible);
        txText.setText(null);
        sendButton.setVisibility(visible);
        if( show) {
            showDevices(false);
        }
    }

    private void showDevices(boolean show) {
        devicesListView.setVisibility(show ? View.VISIBLE : View.GONE);
        if( show) {
            showRxTx(false);
        }
    }

    private String getStatus(int status) {
        switch(status) {
            case WifiP2pDevice.AVAILABLE:
                return "available";
            case WifiP2pDevice.CONNECTED:
                return "connected";
            case WifiP2pDevice.FAILED:
                return "failed";
            case WifiP2pDevice.INVITED:
                return "invited";
            case WifiP2pDevice.UNAVAILABLE:
                return "unavailable";
            default:
                return "?";
        }
    }

    @Override
    public void onClick(View v) {
        new SendAsyncTask(activity, currentInfo, device).execute(txText.getText().toString());
    }

    public static class SendAsyncTask extends AsyncTask<String, Void, Boolean> {

        MainActivity activity;
        int port;
        String host;

        public SendAsyncTask(MainActivity activity, WifiP2pInfo info, WifiP2pDevice device) {
            this.activity = activity;
            this.port = info.isGroupOwner ? MainActivity.OWNER_SEND_PORT : MainActivity.OTHER_SEND_PORT;
            host = info.isGroupOwner ? device.deviceAddress : info.groupOwnerAddress.getHostAddress();
            Log.d(TAG, "send: port " + port);
            Toast.makeText(activity, "send port " + port, Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Toast.makeText(activity, "send " + (Boolean.TRUE.equals(result) ? "success" : "failed"), Toast.LENGTH_SHORT).show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            Socket socket = new Socket();

            try {
                Log.d(MainActivity.TAG, "Opening send socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(MainActivity.TAG, "send socket - " + socket.isConnected());
                OutputStream stream = socket.getOutputStream();
                final PrintStream printStream = new PrintStream(stream);
                if( params != null) {
                    for( String param : params) {
                        printStream.print(param);
                    }
                }
                printStream.close();
                Log.d(MainActivity.TAG, "send: Data written");
                return true;
            } catch (IOException e) {
                Log.e(MainActivity.TAG, e.getMessage());
                return false;
            } finally {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Give up
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * A simple server socket that accepts connection and writes some data on the stream.
     */
    public static class ReadAsyncTask extends AsyncTask<Void, Void, String> {

        MainActivity activity;
        int port;

        public ReadAsyncTask(MainActivity activity, WifiP2pInfo info) {
            this.activity = activity;
            this.port = info.isGroupOwner ? MainActivity.OTHER_SEND_PORT : MainActivity.OWNER_SEND_PORT;
            Log.d(TAG, "read: port " + port);
            Toast.makeText(activity, "read port " + port, Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(Void... messages) {
            String received = null;
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                Log.d(TAG, "read: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(TAG, "read: connection done");

                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                received = reader.readLine();
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return received;
        }

        @Override
        protected void onPostExecute(String result) {
            activity.receivedMessage(result);
        }
    }
}
