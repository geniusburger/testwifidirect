package me.geniusburger.android.test.wifidirect;

import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

public interface WifiDirectListener extends WifiP2pManager.PeerListListener {
    void wifiStateChanged(boolean wifiEnabled);
    void connectionChanged(NetworkInfo info);
    void deviceChanged(WifiP2pDevice device);
    void messageReceived(String message);
}
