package app.pankaj.fingerprintscan;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

import SecuGen.FDxSDKPro.*;
import app.pankaj.fingerprintscan.utils.CommonUtils;

/**
 * Created by pankajpathak on 12/12/17.
 */

public class SDKConfig {

    public Bitmap toGrayscale(byte[] mImageBuffer)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(CommonUtils.mImageWidth, CommonUtils.mImageHeight, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }

}
