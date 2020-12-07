package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.*;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.*;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    public int getRotation() {
        int rotation = PhotonCamera.getParameters().cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCameraFragment().mSensorOrientation);
        return rotation;
    }
    private Point getRotatedCoords(Point in){
        switch (getRotation()){
            case 0:
                return in;
            case 90:
                return new Point(in.y,in.x);
            case 180:
                return in;
            case 270:
                return new Point(in.y,in.x);
        }
        return in;
    }
    public void Run(ByteBuffer inBuffer, Parameters parameters){
        Point rotated = getRotatedCoords(parameters.rawSize);
        Bitmap output = Bitmap.createBitmap(rotated.x,rotated.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotated,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;
        add(new Bayer2Float(0,"Bayer2Float"));
        if(!IsoExpoSelector.HDR) {
            if (PhotonCamera.getSettings().cfaPattern != 4) {
                add(new Demosaic("Demosaic"));
                //add(new Debug3(R.raw.debugraw,"Debug3"));
            } else {
                add(new MonoDemosaic(R.raw.monochrome, "Monochrome"));
            }
        } else {
            add(new LFHDR(0, "LFHDR"));
        }
        /*
         * * * All filters after demosaicing * * *
         */
        add(new ExposureFusionFast2("ExposureFusion"));
        add(new Initial(R.raw.initial,"Initial"));
        add(new AWB(0,"AWB"));
        //add(new GlobalToneMapping(0,"GlobalTonemap"));
        if(PhotonCamera.getSettings().hdrxNR) {
            add(new SmartNR("SmartNR"));
        }
        //if(PhotonCamera.getParameters().focalLength <= 3.0)
        //add(new LensCorrection());
        add(new Sharpen(R.raw.sharpeningbilateral,"Sharpening"));
        add(new RotateWatermark(getRotation()));
        //add(new ShadowTexturing(R.raw.shadowtexturing,"Shadow Texturing"));
        Bitmap img = runAll();
        try {
            imageFileToSave.createNewFile();
            FileOutputStream fOut = new FileOutputStream(imageFileToSave);
            img.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
