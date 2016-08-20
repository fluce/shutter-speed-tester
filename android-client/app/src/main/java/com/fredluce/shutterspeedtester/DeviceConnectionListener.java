package com.fredluce.shutterspeedtester;

/**
 * Created by Frédéric on 19/08/2016.
 */
public interface DeviceConnectionListener {
    void onReceiveData(float value);
    void logMessage(String message);
}
