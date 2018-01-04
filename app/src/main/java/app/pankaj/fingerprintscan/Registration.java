package app.pankaj.fingerprintscan;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGAutoOnEventNotifier;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import SecuGen.FDxSDKPro.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.SGFingerInfo;
import SecuGen.FDxSDKPro.SGFingerPresentEvent;
import app.pankaj.fingerprintscan.utils.CommonUtils;

public class Registration extends AppCompatActivity
        implements java.lang.Runnable, SGFingerPresentEvent {

    String TAG="Fingerprint scan==";
    Button btnRegister;
    ImageView ivCapturedImg;
    Bitmap bitmapCaptured;

    boolean enabled;


    private PendingIntent mPermissionIntent;
    private ImageView mImageViewRegister;
    private ImageView mImageViewVerify;
    private byte[] mRegisterImage;
    private byte[] mVerifyImage;
    private byte[] mRegisterTemplate;
    private byte[] mVerifyTemplate;
    private int[] mMaxTemplateSize;
    private int mImageWidth;
    private int mImageHeight;
    private int mImageDPI;
    private int[] grayBuffer;
    private Bitmap grayBitmap;
    private IntentFilter filter; //2014-04-11
    private SGAutoOnEventNotifier autoOn;
    private boolean mLed;
    private boolean mAutoOnEnabled;
    private int nCaptureModeN;
    private Button mButtonSetBrightness0;
    private Button mButtonSetBrightness100;
    private Button mButtonReadSN;
    private boolean bSecuGenDeviceOpened;
    private JSGFPLib sgfplib;
    private boolean usbPermissionRequested;



    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Log.d(TAG,"Enter mUsbReceiver.onReceive()");
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //DEBUG Log.d(TAG, "Vendor ID : " + device.getVendorId() + "\n");
                            //DEBUG Log.d(TAG, "Product ID: " + device.getProductId() + "\n");
//    						debugMessage("USB BroadcastReceiver VID : " + device.getVendorId() + "\n");
//    						debugMessage("USB BroadcastReceiver PID: " + device.getProductId() + "\n");
                        }
                        else
                            Log.e(TAG, "mUsbReceiver.onReceive() Device is null");
                    }
                    else
                        Log.e(TAG, "mUsbReceiver.onReceive() permission denied for device " + device);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        btnRegister= (Button) findViewById(R.id.btnRegister);
        ivCapturedImg= (ImageView) findViewById(R.id.ivCapturedImg);
        sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            if(!enabled)
            {
                Toast.makeText(Registration.this, "Button not enabled", Toast.LENGTH_SHORT).show();
                return;
            }
                registerUser();
                //CaptureFingerPrint();
            }
        });

        otherInitialization();
    }

    private void otherInitialization()
    {
        grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES*JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
        for (int i=0; i<grayBuffer.length; ++i)
            grayBuffer[i] = android.graphics.Color.GRAY;
        grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
        grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);
        //mImageViewFingerprint.setImageBitmap(grayBitmap);

        int[] sintbuffer = new int[(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2)*(JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2)];
        for (int i=0; i<sintbuffer.length; ++i)
            sintbuffer[i] = android.graphics.Color.GRAY;
        Bitmap sb = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2, Bitmap.Config.ARGB_8888);
        sb.setPixels(sintbuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2);
        //mImageViewRegister.setImageBitmap(grayBitmap);
        //mImageViewVerify.setImageBitmap(grayBitmap);
        mMaxTemplateSize = new int[1];

        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
    //   	registerReceiver(mUsbReceiver, filter);
        sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
//        this.mToggleButtonSmartCapture.toggle();
        bSecuGenDeviceOpened = false;
        usbPermissionRequested = false;

//		debugMessage("Starting Activity\n");
//		debugMessage("jnisgfplib version: " + Integer.toHexString((int)sgfplib.Version()) + "\n");
        mLed = false;
        mAutoOnEnabled = false;
        autoOn = new SGAutoOnEventNotifier(sgfplib, this);
        nCaptureModeN = 0;

    }

    private void registerUser()
    {
        if (mRegisterImage != null)
            mRegisterImage = null;
        mRegisterImage = new byte[mImageWidth*mImageHeight];

//        	this.mCheckBoxMatched.setChecked(false);
        //dwTimeStart = System.currentTimeMillis();
        long result = sgfplib.GetImage(mRegisterImage);
        //DumpFile("register.raw", mRegisterImage);
        //dwTimeEnd = System.currentTimeMillis();
        //dwTimeElapsed = dwTimeEnd-dwTimeStart;
        //debugMessage("GetImage() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
        //Bitmap bitmap_scanned_image=this.toGrayscale(mRegisterImage);
        //mImageViewFingerprint.setImageBitmap(bitmap_scanned_image);
        //dwTimeStart = System.currentTimeMillis();
        result = sgfplib.SetTemplateFormat(SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
        //dwTimeEnd = System.currentTimeMillis();
        //dwTimeElapsed = dwTimeEnd-dwTimeStart;
        //debugMessage("SetTemplateFormat(SG400) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");
        SGFingerInfo fpInfo = new SGFingerInfo();
        Toast.makeText(this, "length==="+mRegisterTemplate.length, Toast.LENGTH_SHORT).show();
        for (int i=0; i< mRegisterTemplate.length; ++i)
            mRegisterTemplate[i] = 0;
        //dwTimeStart = System.currentTimeMillis();
        result = sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);
        DumpFile("register.min", mRegisterTemplate);
        //dwTimeEnd = System.currentTimeMillis();
        //dwTimeElapsed = dwTimeEnd-dwTimeStart;
        //debugMessage("CreateTemplate() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
        //Registration.user_info=new User_info();
        //Registration.user_info.setImage(mRegisterImage);
        Toast.makeText(this, "data 1==="+mRegisterImage, Toast.LENGTH_SHORT).show();
        ivCapturedImg.setImageBitmap(bitmapCaptured=toGrayscale(mRegisterImage));
//    	mRegisterImage = null;
        //Log.e("data====","datadata======================================================="+"data 1.1==="+Registration.user_info.getImage());
//        Intent it=new Intent(this,Registration.class);
//        startActivity(it);
    }

    public void CaptureFingerPrint(){
        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
//		this.mCheckBoxMatched.setChecked(false);
        byte[] buffer = new byte[mImageWidth*mImageHeight];
        dwTimeStart = System.currentTimeMillis();
        //long result = sgfplib.GetImage(buffer);
        long result = sgfplib.GetImageEx(buffer, 10000,50);
        String NFIQString;
//	    //if (this.mToggleButtonNFIQ.isChecked()) {
//	    if (this.mToggleButtonNFIQ.isChecked()) {
//	    	long nfiq = sgfplib.ComputeNFIQ(buffer, mImageWidth, mImageHeight);
//	    	//long nfiq = sgfplib.ComputeNFIQEx(buffer, mImageWidth, mImageHeight,500);
//	    	NFIQString =  new String("NFIQ="+ nfiq);
//	    }
//	    else
        NFIQString = "";
        DumpFile("capture2016.raw", buffer);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
//	    debugMessage("getImageEx(10000,50) ret:" + result + " [" + dwTimeElapsed + "ms]" + NFIQString +"\n");
//		mTextViewResult.setText("getImageEx(10000,50) ret: " + result + " [" + dwTimeElapsed + "ms] " + NFIQString +"\n");
        ivCapturedImg.setImageBitmap(this.toGrayscale(buffer));

        buffer = null;
    }

    public Bitmap toGrayscale(byte[] mImageBuffer)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(mImageWidth,mImageHeight, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }

    public void DumpFile(String fileName, byte[] buffer)
    {
        //Uncomment section below to dump images and templates to SD card

        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            fOut.close();
        } catch (Exception e) {
            //debugMessage("Exception when writing file" + fileName);
        }

    }


    @Override
    public void onResume(){
        //Log.d(TAG, "onResume()");
        super.onResume();
//        DisableControls();
        registerReceiver(mUsbReceiver, filter);
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){
            Toast.makeText(this, "in if error !=", Toast.LENGTH_SHORT).show();
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND)
                dlgAlert.setMessage("The attached fingerprint device is not supported on Android");
            else
                dlgAlert.setMessage("Fingerprint device initialization failed!");
            dlgAlert.setTitle("SecuGen Fingerprint SDK");
            dlgAlert.setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton){
                            finish();
                            return;
                        }
                    }
            );
            dlgAlert.setCancelable(false);
            dlgAlert.create().show();
        }
        else {
            UsbDevice usbDevice = sgfplib.GetUsbDevice();
            if (usbDevice == null){
                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
                dlgAlert.setMessage("SecuGen fingerprint sensor not found!");
                dlgAlert.setTitle("SecuGen Fingerprint SDK");
                dlgAlert.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int whichButton){
                                finish();
                                return;
                            }
                        }
                );
                dlgAlert.setCancelable(false);
                dlgAlert.create().show();
            }
            else {
                boolean hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
                if (!hasPermission) {
                    if (!usbPermissionRequested)
                    {
//			    		debugMessage("Requesting USB Permission\n");
                        //Log.d(TAG, "Call GetUsbManager().requestPermission()");
                        usbPermissionRequested = true;
                        sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
                    }
                    else
                    {
                        //wait up to 20 seconds for the system to grant USB permission
                        hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
//			    		debugMessage("Waiting for USB Permission\n");
                        int i=0;
                        while ((hasPermission == false) && (i <= 400))
                        {
                            ++i;
                            hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            //Log.d(TAG, "Waited " + i*50 + " milliseconds for USB permission");

                        }
                    }
                }
                if (hasPermission) {
//		    		debugMessage("Opening SecuGen Device\n");
                    error = sgfplib.OpenDevice(0);
//					debugMessage("OpenDevice() ret: " + error + "\n");
                    if (error == SGFDxErrorCode.SGFDX_ERROR_NONE)
                    {
                        bSecuGenDeviceOpened = true;
                        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
                        mImageWidth = deviceInfo.imageWidth;
                        mImageHeight= deviceInfo.imageHeight;
                        //CommonUtils.mImageDPI = deviceInfo.imageDPI;
                        sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
                        sgfplib.GetMaxTemplateSize(mMaxTemplateSize);
//						debugMessage("TEMPLATE_FORMAT_SG400 SIZE: " + mMaxTemplateSize[0] + "\n");
                        mRegisterTemplate = new byte[mMaxTemplateSize[0]];
                        mVerifyTemplate = new byte[mMaxTemplateSize[0]];
                        enableButton();
                    }
                    else
                    {
//						debugMessage("Waiting for USB Permission\n");
                    }
                }
                //Thread thread = new Thread(this);
                //thread.start();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy()");
        sgfplib.CloseDevice();
        mRegisterImage = null;
        //mVerifyImage = null;
        mRegisterTemplate = null;
        mVerifyTemplate = null;
        sgfplib.Close();
    	//unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private void enableButton()
    {
        enabled=true;
        btnRegister.setEnabled(true);
        btnRegister.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
    }

    @Override
    public void SGFingerPresentCallback() {
        //autoOn.stop();
        fingerDetectedHandler.sendMessage(new Message());
    }

    public Handler fingerDetectedHandler = new Handler(){
        // @Override
        public void handleMessage(Message msg) {
            //Handle the message
            //CaptureFingerPrint();
//	    	if (mAutoOnEnabled) {
//				mToggleButtonAutoOn.toggle();
//		    	EnableControls();
//	    	}
        }
    };

    @Override
    public void onPause() {
        //Log.d(TAG, "onPause()");
        if (bSecuGenDeviceOpened)
        {
            //autoOn.stop();
//    		EnableControls();
            sgfplib.CloseDevice();
            bSecuGenDeviceOpened = false;
        }
        unregisterReceiver(mUsbReceiver);
        mRegisterImage = null;
        //mVerifyImage = null;
        mRegisterTemplate = null;
        mVerifyTemplate = null;
        //mImageViewFingerprint.setImageBitmap(grayBitmap);
        //mImageViewRegister.setImageBitmap(grayBitmap);
        //mImageViewVerify.setImageBitmap(grayBitmap);
        super.onPause();
    }

    @Override
    public void run() {

        //Log.d(TAG, "Enter run()");
        //ByteBuffer buffer = ByteBuffer.allocate(1);
        //UsbRequest request = new UsbRequest();
        //request.initialize(mSGUsbInterface.getConnection(), mEndpointBulk);
        //byte status = -1;
        while (true) {


            // queue a request on the interrupt endpoint
            //request.queue(buffer, 1);
            // send poll status command
            //  sendCommand(COMMAND_STATUS);
            // wait for status event
            /*
            if (mSGUsbInterface.getConnection().requestWait() == request) {
                byte newStatus = buffer.get(0);
                if (newStatus != status) {
                    Log.d(TAG, "got status " + newStatus);
                    status = newStatus;
                    if ((status & COMMAND_FIRE) != 0) {
                        // stop firing
                        sendCommand(COMMAND_STOP);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            } else {
                Log.e(TAG, "requestWait failed, exiting");
                break;
            }
            */
        }
    }
}
