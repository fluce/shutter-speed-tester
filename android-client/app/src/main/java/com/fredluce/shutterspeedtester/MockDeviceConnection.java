package com.fredluce.shutterspeedtester;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Frédéric on 19/08/2016.
 */
public class MockDeviceConnection implements DeviceConnection {

    private final String TAG = MockDeviceConnection.class.getSimpleName();

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private DeviceConnectionListener listener;

    @Override
    public void setListener(DeviceConnectionListener listener) {
        this.listener = listener;
    }

    private static class UsbDeviceNameAccess {

        public static String getDescriptiveString(UsbDevice device) {
            return device.getManufacturerName().trim() + " / " + device.getProductName().trim();
        }
    }

    private boolean run=false;

    @Override
    public synchronized void startup() {
        if (!run) {

            run = true;
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {

                    AbstractRealDistribution rnd = new NormalDistribution(RandomGeneratorFactory.createRandomGenerator(new Random()), 100, 30);
                    AbstractRealDistribution rnd2 = new LogNormalDistribution(RandomGeneratorFactory.createRandomGenerator(new Random()), 0.1, 1);
                    while (run) {
                        listener.onReceiveData((float) rnd.sample());
                        try {
                            Thread.sleep((long) (rnd2.sample() * 1000));
                        } catch (InterruptedException e) {
                        }
                    }
                }
            });
        }
    }

    @Override
    public void reset() {
    }

    public void stop() {
        run=false;
        mExecutor.shutdownNow();
        mExecutor=Executors.newSingleThreadExecutor();
    }
}
