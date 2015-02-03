package dk.cs.au.skeen.AR;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.List;
import java.util.ArrayList;

import dk.ar.opencv.UtilAR;

import org.opencv.core.Core;
import org.opencv.highgui.VideoCapture;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class MyGdxGame implements ApplicationListener
{
    public PerspectiveCamera cam;
    public ModelBatch modelBatch;
    public Model model = null;
    public List<ModelInstance> instances = new ArrayList<ModelInstance>();
    ModelInstance box = null;
    Environment environment = null;
	
	@Override
	public void create()
    {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

	@Override
	public void render()
    {
        Mat eye = Mat.eye(128, 128, CvType.CV_8UC1);
        Core.multiply(eye, new Scalar(255), eye);
        UtilAR.imDrawBackground(eye);
    }

    @Override
    public void dispose()
    {
    }

    @Override
	public void resize(int width, int height) {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}
}
