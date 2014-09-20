package me.geniusburger.android.test.wifidirect;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SendAsyncTask extends AsyncTask<String, Void, Boolean> {

    private static final String TAG = "sender";
    private static final int SOCKET_TIMEOUT = 5000;

    Context context;
    int port;
    String host;

    public SendAsyncTask(Context context, WifiP2pInfo info, WifiP2pDevice device) {
        this.context = context;
        this.port = info.isGroupOwner ? MainActivity.OWNER_SEND_PORT : MainActivity.OTHER_SEND_PORT;
        host = info.isGroupOwner ? device.deviceAddress : info.groupOwnerAddress.getHostAddress();
        Log.d(TAG, "send: port " + port);
        Toast.makeText(context, "send port " + port, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        Toast.makeText(context, "send " + (Boolean.TRUE.equals(result) ? "success" : "failed"), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected Boolean doInBackground(String... params) {
        Socket socket = new Socket();

        try {
            Log.d(TAG, "Opening send socket - ");
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

            Log.d(TAG, "send socket - " + socket.isConnected());
            OutputStream stream = socket.getOutputStream();
            final PrintStream printStream = new PrintStream(stream);
            if( params != null) {
                for( String param : params) {
                    printStream.print(param);
                }
            }
            printStream.close();
            Log.d(TAG, "send: Data written");
            return true;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
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
