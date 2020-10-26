package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class ExposureFusion extends Node {

    public ExposureFusion(String name) {
        super(0, name);
    }
    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }
    @Override
    public void Compile() {}

    GLTexture expose(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,3),null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
    GLTexture unexpose(GLTexture in){
        glProg.useProgram(R.raw.unexpose);
        glProg.setTexture("InputBuffer",in);
        //glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,3),null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }

    @Override
    public void Run() {
        GLTexture in = previousNode.WorkingTexture;

        GLUtils.Pyramid highExpo = glUtils.createPyramid(6,2, expose(in,(float)(1.0/(PhotonCamera.getSettings().compressor))*2.0f));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(6,2, expose(in,(float)(1.0/(PhotonCamera.getSettings().compressor))/5.f));
        glProg.useProgram(R.raw.fusion);
        glProg.setVar("useUpsampled",0);
        GLTexture wip = new GLTexture(normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("normalExpo",normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("highExpo",highExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("highExpoDiff",highExpo.gauss[highExpo.gauss.length - 1]);
        glProg.drawBlocks(wip);
        glProg.close();
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
                //GLTexture upsampleWip = glUtils.blur(glUtils.upscale(wip,2),5.0);
                GLTexture upsampleWip = (glUtils.interpolate(wip,2));
                glProg.useProgram(R.raw.fusion);

                glProg.setTexture("upsampled", upsampleWip);
                glProg.setVar("useUpsampled", 1);
                //if(i!=0)glProg.setVar("useUpsampled", 1);
                //else glProg.setVar("useUpsampled", 0);
                // We can discard the previous work in progress merge.
                wip.close();
                wip = new GLTexture(normalExpo.laplace[i]);

                // Weigh full image.
                glProg.setTexture("normalExpo", normalExpo.gauss[i]);
                glProg.setTexture("highExpo", highExpo.gauss[i]);

                // Blend feature level.
                glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
                glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

                glProg.drawBlocks(wip);
                glProg.close();

        }
        WorkingTexture = unexpose(wip);
        //WorkingTexture = glUtils.interpolate(glUtils.gaussdown(Overexpose(in),10),10);
        glProg.close();
        highExpo.releasePyramid();
        normalExpo.releasePyramid();
        //WorkingTexture = normalExpo.gauss[1];
        //WorkingTexture = glUtils.gaussdown(glUtils.upscale(in,2),2);
    }
}
