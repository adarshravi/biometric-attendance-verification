package app.pankaj.fingerprintscan.backupData;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.nio.ByteBuffer;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGAutoOnEventNotifier;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import SecuGen.FDxSDKPro.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.SGFingerInfo;
import SecuGen.FDxSDKPro.SGFingerPresentEvent;
import app.pankaj.fingerprintscan.R;
import app.pankaj.fingerprintscan.Registration;
import app.pankaj.fingerprintscan.utils.SqliteUtil;

public class BackupActivity  extends Activity
        implements View.OnClickListener, SGFingerPresentEvent {

    private static final String TAG = "SecuGen USB";

    private Button mButtonRegister;
    private PendingIntent mPermissionIntent;
    private ImageView mImageViewFingerprint;
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
    private boolean bSecuGenDeviceOpened;
    private JSGFPLib sgfplib;
    private boolean usbPermissionRequested;

    private EditText etEmail,etName;


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
//                            debugMessage("USB BroadcastReceiver PID: " + device.getProductId() + "\n");
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

    public Handler fingerDetectedHandler = new Handler(){
        // @Override
        public void handleMessage(Message msg) {
            //CaptureFingerPrint();
            if (mAutoOnEnabled) {
                EnableControls();
            }
        }
    };

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void EnableControls(){
        this.mButtonRegister.setClickable(true);
        this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.white));
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void DisableControls(){
        this.mButtonRegister.setClickable(false);
        this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.black));
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        initializeViews();
        mButtonRegister = (Button)findViewById(R.id.buttonRegister);
        mButtonRegister.setOnClickListener(this);
        mImageViewFingerprint = (ImageView)findViewById(R.id.imageViewFingerprint);

        grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES*JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
        for (int i=0; i<grayBuffer.length; ++i)
            grayBuffer[i] = android.graphics.Color.GRAY;
        grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
        grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);
        mImageViewFingerprint.setImageBitmap(grayBitmap);

        int[] sintbuffer = new int[(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2)*(JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2)];
        for (int i=0; i<sintbuffer.length; ++i)
            sintbuffer[i] = android.graphics.Color.GRAY;
        Bitmap sb = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2, Bitmap.Config.ARGB_8888);
        sb.setPixels(sintbuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2);
        mMaxTemplateSize = new int[1];

        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        bSecuGenDeviceOpened = false;
        usbPermissionRequested = false;
        mLed = false;
        mAutoOnEnabled = false;
        autoOn = new SGAutoOnEventNotifier (sgfplib, this);
        nCaptureModeN = 0;
        SqliteUtil.createTables(this);

    }

    private void initializeViews()
    {
        etEmail=findViewById(R.id.etEmail);
        etName=findViewById(R.id.etName);

    }

    @Override
    public void onPause() {
        if (bSecuGenDeviceOpened)
        {
            autoOn.stop();
            EnableControls();
            sgfplib.CloseDevice();
            bSecuGenDeviceOpened = false;
        }
        unregisterReceiver(mUsbReceiver);
        mRegisterImage = null;
        mVerifyImage = null;
        mRegisterTemplate = null;
        mVerifyTemplate = null;
        mImageViewFingerprint.setImageBitmap(grayBitmap);
        super.onPause();
    }

    @Override
    public void onResume(){
        //Log.d(TAG, "onResume()");
        super.onResume();
        DisableControls();
        registerReceiver(mUsbReceiver, filter);
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){
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
//                        debugMessage("Requesting USB Permission\n");
                        //Log.d(TAG, "Call GetUsbManager().requestPermission()");
                        usbPermissionRequested = true;
                        sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
                    }
                    else
                    {
                        //wait up to 20 seconds for the system to grant USB permission
                        hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
//                        debugMessage("Waiting for USB Permission\n");
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
                        }
                    }
                }
                if (hasPermission) {
                    error = sgfplib.OpenDevice(0);
                    if (error == SGFDxErrorCode.SGFDX_ERROR_NONE)
                    {
                        bSecuGenDeviceOpened = true;
                        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
                        error = sgfplib.GetDeviceInfo(deviceInfo);
//                        debugMessage("GetDeviceInfo() ret: " + error + "\n");
                        mImageWidth = deviceInfo.imageWidth;
                        mImageHeight= deviceInfo.imageHeight;
                        mImageDPI = deviceInfo.imageDPI;
//                        debugMessage("Image width: " + mImageWidth + "\n");
//                        debugMessage("Image height: " + mImageHeight + "\n");
//                        debugMessage("Image resolution: " + mImageDPI + "\n");
//                        debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");
                        sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
                        sgfplib.GetMaxTemplateSize(mMaxTemplateSize);
//                        debugMessage("TEMPLATE_FORMAT_SG400 SIZE: " + mMaxTemplateSize[0] + "\n");
                        mRegisterTemplate = new byte[mMaxTemplateSize[0]];
                        mVerifyTemplate = new byte[mMaxTemplateSize[0]];
                        EnableControls();
                            sgfplib.WriteData((byte)5, (byte)0);
                        if (mAutoOnEnabled){
                            autoOn.start();
                            DisableControls();
                        }
                    }
                    else
                    {
//                        debugMessage("Waiting for USB Permission\n");
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy()");
        sgfplib.CloseDevice();
        mRegisterImage = null;
        mVerifyImage = null;
        mRegisterTemplate = null;
        mVerifyTemplate = null;
        sgfplib.Close();
//    	unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer, int width, int height)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }


    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }


    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y=0; y< height; ++y) {
            for (int x=0; x< width; ++x){
                int color = bmpOriginal.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int gray = (r+g+b)/3;
                color = Color.rgb(gray, gray, gray);
                //color = Color.rgb(r/3, g/3, b/3);
                bmpGrayscale.setPixel(x, y, color);
            }
        }
        return bmpGrayscale;
    }

    //Converts image to binary (OLD)
    public Bitmap toBinary(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public void DumpFile(String fileName, byte[] buffer)
    {
        //Uncomment section below to dump images and templates to SD card
    	/*
        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            fOut.close();
        } catch (Exception e) {
            debugMessage("Exception when writing file" + fileName);
        }
       */
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void SGFingerPresentCallback (){
        autoOn.stop();
        fingerDetectedHandler.sendMessage(new Message());
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public void onClick(View v) {
        if (v == this.mButtonRegister) {
            if(etEmail.getText().toString().trim().equals(""))
            {
                etEmail.setError("Email is required.");
                return;
            }
            if(etName.getText().toString().trim().equals(""))
            {
                etName.setError("Name is required.");
                return;
            }
            if (mRegisterImage != null)
                mRegisterImage = null;
            mRegisterImage = new byte[mImageWidth*mImageHeight];
            long result = sgfplib.GetImage(mRegisterImage);
            DumpFile("register.raw", mRegisterImage);
            SGFingerInfo fpInfo = new SGFingerInfo();
            for (int i=0; i< mRegisterTemplate.length; ++i)
                mRegisterTemplate[i] = 0;
            result=sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);
            Toast.makeText(this, "data 1==="+mRegisterImage, Toast.LENGTH_SHORT).show();
            mImageViewFingerprint.setImageBitmap(toGrayscale(mRegisterImage));
            mRegisterImage = null;

            int response=SqliteUtil.registerUser(this,etEmail.getText().toString().trim(),
                    etName.getText().toString().trim(),mRegisterTemplate.toString());
            if(response==-1)
                Toast.makeText(this, "Email already exists.", Toast.LENGTH_LONG).show();
            else if(response>0)
            {
                Toast.makeText(this,"Rgistered successfully.",Toast.LENGTH_LONG).show();
                etName.setText("");
                etEmail.setText("");
            }
            else
            {
                Toast.makeText(this, "Error in registeration.", Toast.LENGTH_LONG).show();
            }
        }
    }
}