package com.system.itl.ssp_note_float;

import com.ftdi.j2xx.FT_Device;

import java.util.ArrayList;

import device.itl.sspcoms.BarCodeReader;
import device.itl.sspcoms.DeviceEvent;
import device.itl.sspcoms.DeviceEventListener;
import device.itl.sspcoms.DeviceFileUpdateListener;
import device.itl.sspcoms.DevicePayoutEventListener;
import device.itl.sspcoms.DeviceSetupListener;
import device.itl.sspcoms.ItlCurrency;
import device.itl.sspcoms.ItlCurrencyValue;
import device.itl.sspcoms.PayoutRoute;
import device.itl.sspcoms.SSPComsConfig;
import device.itl.sspcoms.SSPDevice;
import device.itl.sspcoms.SSPPayoutEvent;
import device.itl.sspcoms.SSPSystem;
import device.itl.sspcoms.SSPUpdate;

/**
 * Created by tbeswick on 05/04/2017.
 */

public class ITLDeviceCom extends Thread implements DeviceSetupListener, DeviceEventListener, DeviceFileUpdateListener, DevicePayoutEventListener {


    private static boolean isrunning = false;
    private static SSPSystem ssp;
    private FT_Device ftDev = null;
    static final int READBUF_SIZE = 256;
    static final int WRITEBUF_SIZE = 4096;
    byte[] rbuf = new byte[READBUF_SIZE];
    byte[] wbuf = new byte[WRITEBUF_SIZE];
    int mReadSize = 0;
    private SSPDevice sspDevice = null;


    public ITLDeviceCom(){

        ssp = new SSPSystem();

        ssp.setOnDeviceSetupListener(this);
        ssp.setOnEventUpdateListener(this);
        ssp.setOnDeviceFileUpdateListener(this);
        ssp.setOnPayoutEventListener(this);

    }


    /**
     * 配置纸币器
     * @param ftdev
     * @param address
     * @param escrow
     * @param essp
     * @param key
     */
    public void setup(FT_Device ftdev,int address, boolean escrow, boolean essp, long key){

        ftDev = ftdev;
        ssp.SetAddress(address);//1.1设置地址
        ssp.EscrowMode(escrow);//1.4暂存模式
        ssp.SetESSPMode(essp, key);//1.6设置ESSP模式

    }

    @Override
    public void run(){

        int readSize = 0;
        ssp.Run();

        isrunning = true;
        while(isrunning){



            // poll for transmit data
            synchronized (ftDev) {
                int newdatalen = ssp.GetNewData(wbuf);//1.15获取新数据
                if (newdatalen > 0) {
                    //GetDownLoadState 1.12获取下载状态
                    if(ssp.GetDownloadState() != SSPSystem.DownloadSetupState.active) {
                        ftDev.purge((byte) 1);
                    }
                    ftDev.write(wbuf, newdatalen);
                    ssp.SetComsBufferWritten(true);//1.14设置指令缓冲
                }
            }

            // poll for received
            synchronized (ftDev) {
                readSize = ftDev.getQueueStatus();
                if (readSize > 0) {
                    mReadSize = readSize;
                    if (mReadSize > READBUF_SIZE) {
                        mReadSize = READBUF_SIZE;
                    }
                    readSize = ftDev.read(rbuf,mReadSize );
                    ssp.ProcessResponse(rbuf, readSize);
                }
                //    } // end of if(readSize>0)
            }  // end of synchronized


            // coms config changes
            final SSPComsConfig cfg = ssp.GetComsConfig();//1.13获取指令配置
            if(cfg.configUpdate == SSPComsConfig.ComsConfigChangeState.ccNewConfig){
                cfg.configUpdate = SSPComsConfig.ComsConfigChangeState.ccUpdating;
                MainActivity.mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.SetConfig(cfg.baud,cfg.dataBits,cfg.stopBits,cfg.parity,cfg.flowControl);
                    }
                });
                cfg.configUpdate = SSPComsConfig.ComsConfigChangeState.ccUpdated;
            }




            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }






    @Override
    public void OnNewDeviceSetup(final SSPDevice dev) {

        // set local device object
        sspDevice = dev;
        // call to Main UI
        MainActivity.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.DisplaySetUp(dev);
            }
        });

    }

    @Override
    public void OnDeviceDisconnect(final SSPDevice dev) {

        MainActivity.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.DeviceDisconnected(dev);
            }
        });
    }



    @Override
    public void OnDeviceEvent(final DeviceEvent ev)
    {
        MainActivity.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.DisplayEvents(ev);
            }
        });
    }


    @Override
    public void OnNewPayoutEvent(final SSPPayoutEvent ev) {

        MainActivity.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.DisplayPayoutEvents(ev);
            }
        });

    }


    @Override
    public void OnFileUpdateStatus(final SSPUpdate sspUpdate) {

        MainActivity.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.UpdateFileDownload(sspUpdate);
            }
        });

    }


    void Stop()
    {
        ssp.Close();
        isrunning = false;
    }


    boolean SetSSPDownload(final SSPUpdate update)
    {
        return ssp.SetDownload(update);//1.17设置下载

    }


    void SetEscrowMode(boolean mode)
    {
        if(ssp != null) {
            ssp.EscrowMode(mode);
        }

    }

    /**
     * A command to disable the device for Bill entry after start-up
     * A command to re-enable the device for Bill entry after a DisableDevice() command has been sent;
     * @param en
     */
    void SetDeviceEnable(boolean en) {
        if (ssp != null) {
            if (en) {
                ssp.EnableDevice();//允许设备进入纸币
            }else {
                ssp.DisableDevice();//禁止设备进入纸币
            }
        }
    }
    void SetEscrowAction(SSPSystem.BillAction action)
    {
        if(ssp != null){
            ssp.SetBillEscrowAction(action);
        }
    }


    void SetBarcocdeConfig(BarCodeReader cfg)
    {
        if(ssp != null){
            ssp.SetBarCodeConfiguration(cfg);//1.7设置条码配置
        }
    }


    int GetDeviceCode()
    {
        if(ssp != null){
            return sspDevice.headerType.getValue();
        }else{
            return -1;
        }
    }

    void SetPayoutRoute(ItlCurrency cur, PayoutRoute rt)
    {
        if(ssp != null) {
            ssp.SetPayoutRoute(cur, rt);//1.8设置支付路线
        }
    }


    void PayoutAmount(ItlCurrency cur)
    {
        if(ssp != null) {
            ssp.PayoutAmount(cur);//1.9设置支付金额
        }
    }

    void EmptyPayout()
    {
        if(ssp != null){
            ssp.EmptyPayout();//1.11清空支出箱
        }
    }


    ArrayList<ItlCurrencyValue> GetBillPositions()
    {
        if(ssp != null){
            return ssp.GetStoredBillPositions();//1.18获取纸币位置
        }else{
            return null;
        }
    }

    void NFBillAction(SSPSystem.BillActionRequest action)
    {
        if(ssp != null){
            ssp.BillPayoutAction(action);//1.19纸币支付动作
        }
    }

}
