package com.fredluce.shutterspeedtester;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Frédéric on 19/08/2016.
 */
public class Shtr10DeviceConnection implements DeviceConnection {

    private static final String TAG = Shtr10DeviceConnection.class.getSimpleName();

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;

    private Context context;

    public Shtr10DeviceConnection(Context context) {
        this.context=context;
    }

    @Override
    public void setListener(DeviceConnectionListener listener) {
        this.listener = listener;
    }

    private DeviceConnectionListener listener;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    updateReceivedData(data);
                }
            };

    StringBuffer buffer = new StringBuffer(1024);

    private synchronized void updateReceivedData(byte[] data) {

        buffer.append(new String(data));
        while (true) {
            int i = buffer.indexOf("\r\n");
            if (i == -1)
                return;
            String line = buffer.substring(0, i);
            buffer.delete(0, i + 2);

            Log.d(TAG, "Bytes read : " + line);

            String[] words = line.split(" ");
            if (words.length == 2 && words[0].equals("M2")) {
                int d = Integer.parseInt(words[1]);
                float df = (float) d / 1000.0f;
                listener.onReceiveData(df);
            } else {

                listener.logMessage("Bytes read"+" : " + line);

            }
        }
    }

    private static class UsbDeviceNameAccess {

        public static String getDescriptiveString(UsbDevice device) {
            return device.getManufacturerName().trim() + " / " + device.getProductName().trim();
        }
    }

    public synchronized void stop()
    {
        if (port != null) {
            mSerialIoManager.stop();
            try {
                port.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing port", e);
            }
            port = null;
            stopExecutor(mExecutor);
            mExecutor = Executors.newSingleThreadExecutor();
        }

        if (connection != null) {
            connection.close();
            connection = null;
        }

    }

    @Override
    public synchronized void startup() {

        stop();

        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x1B4F, 0x9206, CdcAcmSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            listener.logMessage(context.getString(R.string.no_driver_found));
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            listener.logMessage(context.getString(R.string.connection_failed));
            return;
        }

        port = driver.getPorts().get(0);
        try {
            UsbDevice device = driver.getDevice();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                listener.logMessage(context.getString(R.string.device_found) + " : " + UsbDeviceNameAccess.getDescriptiveString(device));
            } else
                listener.logMessage(context.getString(R.string.device_found));
            port.open(connection);
            port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);

            mSerialIoManager = new SerialInputOutputManager(port, mListener);
            mExecutor.submit(mSerialIoManager);

        } catch (IOException e) {
            listener.logMessage("Exception : " + e.getMessage());
            Log.e(TAG, "Exception", e);
        }

        setMode(2);
    }

    private static void stopExecutor(ExecutorService mExecutor) {
        mExecutor.shutdown();
        try {
            // Wait a while for existing tasks to terminate
            if (!mExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                mExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!mExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS))
                    Log.e(TAG, "Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            mExecutor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void reset() {
        setMode(2);
    }

    public synchronized void setMode(int mode) {
        if (port != null) {
            byte wbuf[] = (Integer.toString(mode) + "\r\n").getBytes();
            try {
                port.write(wbuf, 500);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to port", e);
            }
        }
    }

}
