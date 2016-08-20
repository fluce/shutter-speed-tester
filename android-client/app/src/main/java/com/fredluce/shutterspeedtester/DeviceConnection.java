package com.fredluce.shutterspeedtester;

/**
 * Created by Frédéric on 19/08/2016.
 */
public interface DeviceConnection {

    void setListener(DeviceConnectionListener listener);

    void startup();

    void reset();

    void stop();
}
