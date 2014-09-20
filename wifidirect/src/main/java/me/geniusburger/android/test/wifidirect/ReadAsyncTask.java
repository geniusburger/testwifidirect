package me.geniusburger.android.test.wifidirect;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A simple server socket that accepts connection and writes some data on the stream.
 */
public class ReadAsyncTask extends AsyncTask<Void, String, Void> {

    private static final String TAG = "reader";

    private WifiDirectListener listener;
    private int port;
    private ServerSocket serverSocket = null;

    public ReadAsyncTask(Context context, WifiDirectListener listener, WifiP2pInfo info) {
        this.listener = listener;
        this.port = info.isGroupOwner ? MainActivity.OTHER_SEND_PORT : MainActivity.OWNER_SEND_PORT;
        Log.d(TAG, "read: port " + port);
        Toast.makeText(context, "read port " + port, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected Void doInBackground(Void... messages) {
        try {
            serverSocket = new ServerSocket(port);
            Log.d(TAG, "read: Socket opened");
            Socket client = serverSocket.accept();
            Log.d(TAG, "read: connection done");

            if( isCancelled()) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            while(!isCancelled()) {
                publishProgress(reader.readLine());
            }
            serverSocket.close();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if( values != null  && values.length >= 1) {
            listener.messageReceived(values[0]);
        }
    }

    @Override
    protected void onCancelled() {
        if( serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close read socket on cancel");
            }
        }
    }
}
