package com.system.itl.ssp_bnv;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;


import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import device.itl.sspcoms.BarCodeReader;
import device.itl.sspcoms.DeviceEvent;
import device.itl.sspcoms.ItlCurrency;
import device.itl.sspcoms.SSPDevice;
import device.itl.sspcoms.SSPDeviceType;
import device.itl.sspcoms.SSPSystem;
import device.itl.sspcoms.SSPUpdate;


public class MainActivity extends AppCompatActivity {

    private static ITLDeviceCom deviceCom;
    private static D2xxManager ftD2xx = null;
    private static FT_Device ftDev = null;


    static FloatingActionButton fab;
    static LinearLayout bvDisplay;
    static MainActivity mainActivity;
    static ListView listChannels;
    static ListView listEvents;
    static Button bttnAccept;
    static Button bttnReject;
    static Switch swEscrow;
    static TextView txtFirmware;
    static TextView txtDevice;
    static TextView txtDataset;
    static TextView txtSerial;


    static ProgressDialog progress;
    static List<String> channelValues;
    static String[] eventValues;
    static ArrayAdapter<String> adapterChannels;
    static ArrayAdapter<String> adapterEvents;

    private static SSPDevice sspDevice = null;
    private SSPUpdate sspUpdate = null;
    private static MainActivity instance = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bvDisplay = (LinearLayout) findViewById(R.id.content_bill_validator);
        bvDisplay.setVisibility(View.INVISIBLE);
        mainActivity = this;
        this.instance = this;

        progress = new ProgressDialog(MainActivity.this);


        setTitle("Bill Validator");

        listEvents = (ListView) findViewById(R.id.listEvents);
        listChannels = (ListView) findViewById(R.id.listChannels);

        eventValues = new String[]{"", ""};
        channelValues = new ArrayList<String>();

        adapterEvents = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, eventValues);
        listEvents.setAdapter(adapterEvents);


        adapterChannels = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, channelValues);
        listChannels.setAdapter(adapterChannels);


        bttnAccept = (Button) findViewById(R.id.bttnAccept);
        bttnReject = (Button) findViewById(R.id.bttnReject);
        txtFirmware = (TextView) findViewById(R.id.txtFirmware);
        txtFirmware.setText(getResources().getString(R.string.firmware_title));
        txtDevice = (TextView) findViewById(R.id.txtDevice);
        txtDevice.setText(getResources().getString(R.string.device_title));
        txtDataset = (TextView) findViewById(R.id.txtDataset);
        txtDataset.setText(getResources().getString(R.string.dataset_title));
        txtSerial = (TextView) findViewById(R.id.txtSerialNumber);
        txtSerial.setText(getResources().getString(R.string.serial_number_title));


        try {
            ftD2xx = D2xxManager.getInstance(this);
        } catch (D2xxManager.D2xxException ex) {
            Log.e("SSP FTmanager", ex.toString());
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.setPriority(500);
        this.registerReceiver(mUsbReceiver, filter);


        deviceCom = new ITLDeviceCom();

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDevice();
                if (ftDev != null) {
                    deviceCom.setup(ftDev, 0, false, false, 0);
                    deviceCom.start();
                } else {
                    Toast.makeText(MainActivity.this, "No USB connection detected!", Toast.LENGTH_SHORT).show();
                }
            }
        });


        /**
         * Escrow enable/disable toggle
         */
        swEscrow = (Switch) findViewById(R.id.swEscrow);
        swEscrow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    deviceCom.SetEscrowMode(true);
                } else {
                    deviceCom.SetEscrowMode(false);
                }
            }
        });
        /**
         * Device enable/disable toggle
         */
        Switch swDisable = (Switch) findViewById(R.id.swEnable);
        swDisable.setChecked(true);
        swDisable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    deviceCom.SetDeviceEnable(true);
                } else {
                    deviceCom.SetDeviceEnable(false);
                }

            }
        });
        /**
         * Accept a bill from escrow button
         */
        bttnAccept = (Button) findViewById(R.id.bttnAccept);
        bttnAccept.setVisibility(View.INVISIBLE);
        bttnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceCom.SetEscrowAction(SSPSystem.BillAction.Accept);
                bttnReject.setVisibility(View.INVISIBLE);
                bttnAccept.setVisibility(View.INVISIBLE);
            }
        });
        /**
         * Reject a bill from escrow button
         */
        bttnReject = (Button) findViewById(R.id.bttnReject);
        bttnReject.setVisibility(View.INVISIBLE);
        bttnReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceCom.SetEscrowAction(SSPSystem.BillAction.Reject);
                bttnReject.setVisibility(View.INVISIBLE);
                bttnAccept.setVisibility(View.INVISIBLE);
            }
        });


    }




    public static MainActivity getInstance(){

        return instance;
    }


    public static void DisplaySetUp(SSPDevice dev)
    {

        sspDevice = dev;



        fab.setVisibility(View.INVISIBLE);
        bvDisplay.setVisibility(View.VISIBLE);

        // check for type comapable
        if(dev.type != SSPDeviceType.BillValidator){
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.getInstance());
            // 2. Chain together various setter methods to set the dialog characteristics
            builder.setMessage("Connected device is not BNV (" + dev.type.toString() + ")")
                    .setTitle("BNV");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });

            // 3. Get the AlertDialog from create()
            AlertDialog dialog = builder.create();

            // 4. Show the dialog
            dialog.show();// show error
            return;

        }


                /* device details  */
        txtFirmware.append(" " + dev.firmwareVersion);
        txtDevice.append(" " + dev.headerType.toString());
        txtSerial.append(" " + String.valueOf(dev.serialNumber));
        txtDataset.append(dev.datasetVersion);

                /* display the channel info */
        channelValues.clear();
        for (ItlCurrency itlCurrency : dev.currency) {
            String v = itlCurrency.country + " " + String.format("%.2f", itlCurrency.realvalue);
            channelValues.add(v);
        }

        adapterChannels.notifyDataSetChanged();


        // if device has barcode hardware
        if (dev.barCodeReader.hardWareConfig != SSPDevice.BarCodeStatus.None) {
            // send new configuration
            BarCodeReader cfg = new BarCodeReader();
            cfg.barcodeReadEnabled = true;
            cfg.billReadEnabled = true;
            cfg.numberOfCharacters = 18;
            cfg.format = SSPDevice.BarCodeFormat.Interleaved2of5;
            cfg.enabledConfig = SSPDevice.BarCodeStatus.Both;
            deviceCom.SetBarcocdeConfig(cfg);
        }
    }


    public static void DisplayEvents(DeviceEvent ev) {

        switch (ev.event) {
            case CommunicationsFailure:

                break;
            case Ready:
                eventValues[0] = "Ready";
                eventValues[1] = "";
                break;
            case BillRead:
                eventValues[0] = "Reading";
                eventValues[1] = "";
                break;
            case BillEscrow:
                eventValues[0] = "Bill Escrow";
                eventValues[1] = ev.currency + " " +
                        String.format("%.2f", ev.value);
                if (swEscrow.isChecked()) {
                    bttnAccept.setVisibility(View.VISIBLE);
                    bttnReject.setVisibility(View.VISIBLE);
                }
                break;
            case BillStacked:

                break;
            case BillReject:
                eventValues[0] = "Bill Reject";
                eventValues[1] = "";
                if (swEscrow.isChecked()) {
                    bttnAccept.setVisibility(View.INVISIBLE);
                    bttnReject.setVisibility(View.INVISIBLE);
                }
                break;
            case BillJammed:
                eventValues[0] = "Bill jammed";
                eventValues[1] = "";
                break;
            case BillFraud:
                eventValues[0] = "Bill Fraud";
                eventValues[1] = ev.currency + " " +
                        String.format("%.2f", ev.value);
                break;
            case BillCredit:
                eventValues[0] = "Bill Credit";
                eventValues[1] = ev.currency + " " +
                        String.format("%.2f", ev.value);
                break;
            case Full:
                eventValues[0] = "Bill Cashbox full";
                eventValues[1] = "";
                break;
            case Initialising:

                break;
            case Disabled:
                eventValues[0] = "Disabled";
                eventValues[1] = "";
                break;
            case SoftwareError:
                eventValues[0] = "Software error";
                eventValues[1] = "";
                break;
            case AllDisabled:
                eventValues[0] = "All channels disabled";
                eventValues[1] = "";
                break;
            case CashboxRemoved:
                eventValues[0] = "Cashbox removed";
                eventValues[1] = "";
                break;
            case CashboxReplaced:
                eventValues[0] = "Cashbox replaced";
                eventValues[1] = "";
                break;
            case NotePathOpen:
                eventValues[0] = "Note path open";
                eventValues[1] = "";
                break;
            case BarCodeTicketEscrow:
                eventValues[0] = "Barcode ticket escrow:";
                eventValues[1] = ev.currency;
                if (swEscrow.isChecked()) {
                    bttnAccept.setVisibility(View.VISIBLE);
                    bttnReject.setVisibility(View.VISIBLE);
                }
                break;
            case BarCodeTicketStacked:
                eventValues[0] = "Barcode ticket stacked";
                eventValues[1] = "";
                break;
        }
        adapterEvents.notifyDataSetChanged();


    }


    public static void DeviceDisconnected(SSPDevice dev) {

        eventValues[0] = "DISCONNECTED!!!";
        eventValues[1] = "";
        adapterEvents.notifyDataSetChanged();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_downloadFile:
                openFolder();
                return true;
            case R.id.action_shutdown:
                deviceCom.Stop();
                closeDevice();
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }


    }


    /***
     *  Handler for selecting a download file
     *  All download files need to be in the Download folder
     */
    public void openFolder()
    {

        if(deviceCom == null){
            return;
        }

        int devcode = deviceCom.GetDeviceCode();
       if(devcode < 0){
            return;
        }

        Intent intent = new Intent(this,ListFiles.class);
        // send the current device code
        intent.putExtra("deviceCode", (byte)devcode);
        startActivityForResult(intent,123);


    }


    public static void UpdateFileDownload(SSPUpdate sspUpdate)
    {


        switch (sspUpdate.UpdateStatus) {
            case dwnInitialise:
                progress.setMessage("Downloading Ram");
                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progress.setIndeterminate(false);
                progress.setProgress(0);
                progress.setMax(sspUpdate.numberOfRamBlocks);
                progress.setCanceledOnTouchOutside(false);
                progress.show();
                break;
            case dwnRamCode:
                progress.setProgress(sspUpdate.blockIndex);
                break;
            case dwnMainCode:
                progress.setMessage("Downloading flash");
                progress.setMax(sspUpdate.numberOfBlocks);
                progress.setProgress(sspUpdate.blockIndex);
                break;
            case dwnComplete:
                progress.dismiss();
                break;
            case dwnError:
                progress.dismiss();
                break;
        }


    }


    //The select file screen returns to here with a selected file string
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == 123 && resultCode == RESULT_OK) {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();

            path += "/";
            String flname = "";

            if (data.hasExtra("filename")) {
                flname = data.getStringExtra("filename");
                path += flname;

            } else {
                txtDevice.setText(R.string.no_file_data_error);
                return;
            }


            sspUpdate = new SSPUpdate(flname);
            try {
                final File up = new File(path);

                sspUpdate.fileData = new byte[(int) up.length()];
                DataInputStream dis = new DataInputStream(new FileInputStream(up));
                dis.readFully(sspUpdate.fileData);
                dis.close();

                sspUpdate.SetFileData();
                ClearDisplay();
                deviceCom.SetSSPDownload(sspUpdate);


            } catch (IOException e) {
                e.printStackTrace();
             //   txtEvents.append(R.string.unable_to_load + "\r\n");
            }
        }
    }


    private void ClearDisplay()
    {
        progress.setProgress(0);
        txtFirmware.setText(getResources().getString(R.string.firmware_title));
        txtDevice.setText(getResources().getString(R.string.device_title));
        txtDataset.setText(getResources().getString(R.string.dataset_title));
        txtSerial.setText(getResources().getString(R.string.serial_number_title));

        adapterChannels.clear();
        adapterChannels.notifyDataSetChanged();

        eventValues[0] = "";
        eventValues[1] = "";

        adapterEvents.notifyDataSetChanged();


    }



    /**********   USB functions   ******************************************/


    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // never come here(when attached, go to onNewIntent)
                openDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeDevice();
            }
        }
    };


    private void openDevice() {


        if (ftDev != null) {
            if (ftDev.isOpen()) {
                // if open and run thread is stopped, start thread
                SetConfig(9600, (byte) 8, (byte) 2, (byte) 0, (byte) 0);
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ftDev.restartInTask();
                return;
            }
        }

        int devCount = 0;

        if (ftD2xx != null) {
            // Get the connected USB FTDI devoces
            devCount = ftD2xx.createDeviceInfoList(this);
        } else {
            return;
        }

        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);
        // none connected
        if (devCount <= 0) {
            return;
        }
        if (ftDev == null) {
            ftDev = ftD2xx.openByIndex(this, 0);
        } else {
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(this, 0);
            }
        }
        // run thread
        if (ftDev.isOpen()) {
            SetConfig(9600, (byte) 8, (byte) 2, (byte) 0, (byte) 0);
            ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
            ftDev.restartInTask();
        }
    }


    private static void closeDevice() {

        if (ftDev != null) {
            deviceCom.Stop();
            ftDev.close();
        }
    }


    public static void SetConfig(int baud, byte dataBits, byte stopBits, byte parity, byte flowControl) {
        if (!ftDev.isOpen()) {
            return;
        }

        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
        ftDev.setBaudRate(baud);

        switch (dataBits) {
            case 7:
                dataBits = D2xxManager.FT_DATA_BITS_7;
                break;
            case 8:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
            default:
                dataBits = D2xxManager.FT_DATA_BITS_8;
                break;
        }

        switch (stopBits) {
            case 1:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
            case 2:
                stopBits = D2xxManager.FT_STOP_BITS_2;
                break;
            default:
                stopBits = D2xxManager.FT_STOP_BITS_1;
                break;
        }

        switch (parity) {
            case 0:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
            case 1:
                parity = D2xxManager.FT_PARITY_ODD;
                break;
            case 2:
                parity = D2xxManager.FT_PARITY_EVEN;
                break;
            case 3:
                parity = D2xxManager.FT_PARITY_MARK;
                break;
            case 4:
                parity = D2xxManager.FT_PARITY_SPACE;
                break;
            default:
                parity = D2xxManager.FT_PARITY_NONE;
                break;
        }

        ftDev.setDataCharacteristics(dataBits, stopBits, parity);

        short flowCtrlSetting;
        switch (flowControl) {
            case 0:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
            case 1:
                flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
                break;
            case 2:
                flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
                break;
            case 3:
                flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
                break;
            default:
                flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
                break;
        }

        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);
    }


}
