package com.nilhcem.usbfun;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int USB_VENDOR_ID  = 0x067B; //0x067B 是 usb->RS232 VENDOR_ID
    private static final int USB_PRODUCT_ID = 0x2303; //0x6001是 usb->RS232  PRODUCT_ID
    private static byte ETX=0x03;
    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private String buffer = "";
    //================================================================================================================================================以下是寫入 Cmd 到 PLC
   /*1.PLC & PI 透過 RS232 通訊
         a.讀取 Bit Data[use M Register]
            Send Cmd : 0x5 + "00FFBRAM000010" + 0xA + 0xD
            測試讀取範圍 : M0000 ~ M000F (16點)
        b.讀取 Word Data[use D Register]
            Send Cmd : 0x5 + "00FFWRAD000008" + 0xA + 0xD
            測試讀取範圍 : D0000 ~ M0007 (8點)    */
    String Msg_Word_Rd_Cmd = "\u0005" + "00FFWRAD000010";//  //讀取Word D0000-D0007 Cmd
    String Msg_Bit_Rd_Cmd  = "\u0005" + "00FFBRAM000010"; //  //讀取Bit  M0000-M0015 Cmd

    //--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    private UsbSerialInterface.UsbReadCallback callback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            try {
                String dataUtf8 = new String(data, "UTF-8");
                buffer += dataUtf8;
                int index;
                //Log.i(TAG, "Test....Serial data received: "+ buffer);  //Test
                while ((index = buffer.indexOf(ETX)) != -1) {    /////// etx   '\n'
                    final String dataStr = buffer.substring(0, index + 1).trim();
                    buffer = buffer.length() == index ? "" : buffer.substring(index + 1);
                    Log.i(TAG, "Test....收到結尾 0x3 "+ buffer);  //Test
                    //
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onSerialDataReceived(dataStr); //收到資料後的處理
                        }
                    });
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Error receiving USB data", e);
            }
        }
    };

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "USB device detached");
                    stopUsbConnection();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUsbConnection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbDetachedReceiver);
        stopUsbConnection();
    }

    private void startUsbConnection() {
        Map<String, UsbDevice> connectedDevices = usbManager.getDeviceList();

        if (!connectedDevices.isEmpty()) {
            for (UsbDevice device : connectedDevices.values()) {
                if (device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "Device found: " + device.getDeviceName());
                    startSerialConnection(device);
                    return;
                }
            }
        }
        Log.w(TAG, "Could not start USB connection - No devices found");
    }

    private void startSerialConnection(UsbDevice device) {
        Log.i(TAG, "Ready to open USB device connection");
        connection = usbManager.openDevice(device);
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialDevice != null) {
            if (serialDevice.open()) {
                serialDevice.setBaudRate(9600);
                serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialDevice.read(callback);
                Log.i(TAG, "Serial connection opened");
            } else {
                Log.w(TAG, "Cannot open serial connection");
            }
        } else {
            Log.w(TAG, "Could not create Usb Serial Device");
        }
    }

    private void onSerialDataReceived(String data) {
        // Add whatever you want here
        String Send_Out= "";
        Log.i(TAG, "Serial data received: " + data);  //接收後記錄資料
        //Send_Out = "1234567890" + "\u0003";
        Send_Out = Msg_Word_Rd_Cmd+ '\n'+'\r';
        serialDevice.write(Send_Out.getBytes());

        //serialDevice.write("Android Things\n".getBytes()); // Async-like operation now! :)  //這邊是寫入 Serial 範例
    }

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
    }
}
