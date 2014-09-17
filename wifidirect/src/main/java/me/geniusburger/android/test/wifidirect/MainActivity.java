package me.geniusburger.android.test.wifidirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
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
    public static final int PORT = 8988;
    private static final int SOCKET_TIMEOUT = 5000;

    private TextView statusTextView;
    private TextView peersTextView;
    private TextView nameTextView;
    private ProgressBar progressBar;
    private TextView progressNumber;
    private ListView devicesListView;
    private TextView rxLabel;
    private TextView txLabel;
    private TextView rxText;
    private EditText txText;
    private Button button;

    private Context context;
    private MainActivity activity;
    private Progress currentStatus;

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

        statusTextView = (TextView) findViewById(R.id.textViewStatus);
        peersTextView = (TextView) findViewById(R.id.textViewPeers);
        nameTextView = (TextView) findViewById(R.id.textViewDeviceName);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressNumber = (TextView) findViewById(R.id.textViewProgressNumber);
        devicesListView = (ListView) findViewById(R.id.listViewDevices);
        rxLabel = (TextView) findViewById(R.id.textViewRxLabel);
        txLabel = (TextView) findViewById(R.id.textViewTxLabel);
        rxText = (TextView) findViewById(R.id.textViewRx);
        txText = (EditText) findViewById(R.id.editText);
        button = (Button) findViewById(R.id.button);

        activity = this;
        context = getApplicationContext();
        progressBar.setMax(Progress.getMax());
        peersTextView.setVisibility(View.INVISIBLE);
        nameTextView.setVisibility(View.INVISIBLE);
        devicesListView.setVisibility(View.GONE);
        button.setOnClickListener(this);
        showRxTx(false);
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

    protected void updateWifiState(boolean enabled) {

        if(enabled) {
            updateStatus(Progress.ENABLED);
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    updateStatus(Progress.DISCOVER);
                }

                @Override
                public void onFailure(int reasonCode) {
                    Toast.makeText(context, "Failed to discover peers: " + reasonCode, Toast.LENGTH_SHORT).show();
                }
            });
        }
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
            connectionInfoListener.onConnectionInfoAvailable(currentInfo);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

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
            displayStrings.add(String.format("%s - %s", device.deviceName, getStatus(device.status)));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, displayStrings.toArray(new String[displayStrings.size()]));
        devicesListView.setAdapter(adapter);
        devicesListView.setOnItemClickListener(deviceClickListener);

        if (currentStatus.getValue() <= Progress.FOUND_PEERS.getValue()) {
            updateStatus(Progress.FOUND_PEERS);
            peersTextView.setVisibility(View.VISIBLE);
            nameTextView.setVisibility(View.INVISIBLE);
            devicesListView.setVisibility(View.VISIBLE);
        }
    }

    private AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            peersTextView.setVisibility(View.INVISIBLE);
            device = devices.get(position);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            manager.connect(channel, config, connectListener);
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

    protected void notifyOfConnectionChange(NetworkInfo info) {
        if( info.isConnected()) {
            updateStatus(Progress.CONNECTED);

            peersTextView.setVisibility(View.INVISIBLE);
            nameTextView.setVisibility(View.VISIBLE);
            nameTextView.setText(device == null ? "?" : device.deviceName);
            devicesListView.setVisibility(View.GONE);

            manager.requestConnectionInfo(channel, connectionInfoListener);
        } else {
            currentInfo = null;
            updateStatus(Progress.FOUND_PEERS);

            peersTextView.setVisibility(View.VISIBLE);
            nameTextView.setVisibility(View.INVISIBLE);
            devicesListView.setVisibility(View.VISIBLE);
        }
    }

    private WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            currentInfo = info;
            Toast.makeText(context, "Group: " + info.groupOwnerAddress.getHostAddress(), Toast.LENGTH_LONG).show();
            showRxTx(true);
            if (info.groupFormed && info.isGroupOwner) {
                new ServerAsyncTask(activity).execute();
            } else if (info.groupFormed) {
            }
        }
    };

    private void showRxTx(boolean show) {
        int v = show ? View.VISIBLE : View.INVISIBLE;
        rxLabel.setVisibility(v);
        txLabel.setVisibility(v);
        rxText.setVisibility(v);
        txText.setVisibility(v);
        button.setVisibility(v);
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
        new ClientAsyncTask(activity, currentInfo).execute(txText.getText().toString());
    }

    public static class ClientAsyncTask extends AsyncTask<String, Void, Boolean> {

        MainActivity activity;
        WifiP2pInfo info;

        public ClientAsyncTask(MainActivity activity, WifiP2pInfo info) {
            this.activity = activity;
            this.info = info;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Toast.makeText(activity, "send " + (Boolean.TRUE.equals(result) ? "success" : "failed"), Toast.LENGTH_SHORT).show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String host = info.groupOwnerAddress.getHostAddress();
            Socket socket = new Socket();
            int port = MainActivity.PORT;

            try {
                Log.d(MainActivity.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected());
                OutputStream stream = socket.getOutputStream();
                final PrintStream printStream = new PrintStream(stream);
                if( params != null) {
                    for( String param : params) {
                        printStream.print(param);
                    }
                }
                printStream.close();
                Log.d(MainActivity.TAG, "Client: Data written");
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
    public static class ServerAsyncTask extends AsyncTask<Void, Void, String> {

        MainActivity activity;

        public ServerAsyncTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        protected String doInBackground(Void... messages) {
            String received = null;
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(TAG, "Server: connection done");

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
            if (result == null) {
                result = "";
            }
            activity.receivedMessage(result);
        }
    }
}
