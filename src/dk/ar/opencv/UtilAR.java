package dk.ar.opencv;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
//import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;

public class UtilAR {

	private static HashMap<String,MatView> panels = new HashMap<String,MatView>(32);

	public static boolean alwaysUseImageByteStream = true;

	private static int viewCount = 0;
	private static boolean closed = false;

	private static int curX=0,curY=0;
	private static int defaultWidth=640,defaultHeight=480;

	private static final int MAX_IMAGE_BYTES = 1920*1200*4;
	public static ByteBuffer imData = ByteBuffer.allocateDirect(MAX_IMAGE_BYTES);
	public static byte[] imArray = new byte[MAX_IMAGE_BYTES];
	public static int[] imArrayInt = new int[MAX_IMAGE_BYTES];
	private static SpriteBatch batch = new SpriteBatch(32);
	private static HashMap<Long,Texture> bgTextures = new HashMap<Long,Texture>(32);

	//Camera tracking
	private static Mat intrinsicParams = new Mat(3,3,CvType.CV_64F);
	private static MatOfDouble distCoeffs = new MatOfDouble();
	private static Mat camRotation = new Mat(3,3,CvType.CV_32F);
	private static Mat viewMatrix = new Mat(4,4,CvType.CV_64F);
	private static double[] tvecArray = new double[3];
	private static double[] rotArray = new double[9];
	private static double[] viewMatArr = new double[16];
	private static double[] homLine = {0,0,0,1};

	/**
	 * Returns a camera matrix with default intrinsic parameters based on the given camera resolution.
	 */
	public static Mat getDefaultIntrinsicMatrix(int camResX,int camResY) {
		intrinsicParams.put(0,0, 670 * (float)camResX/640,0,camResX/2, 0,670 * (float)camResY/480,camResY/2, 0,0,1);
		return intrinsicParams;
	}

	/**
	 * Returns default distortion coefficients.
	 */
	public static MatOfDouble getDefaultDistortionCoefficients() {
		distCoeffs.put(0,0, -0.027, -0.0171, -0.0042, 0.0047, 0.063);
		return distCoeffs;
	}

	private static void setCameraByViewTransform(double[] viewMatArr,Camera target) {
		target.direction.set((float)viewMatArr[2],(float)viewMatArr[6],(float)viewMatArr[10]);
		target.up.set((float)-viewMatArr[1],(float)-viewMatArr[5],(float)-viewMatArr[9]);
		target.position.set((float)viewMatArr[3],(float)viewMatArr[7],(float)viewMatArr[11]);
		target.update(true);
	}

	/**
	 * Sets the extrinsic values of a GDX camera according to the given camera rotation and translation.
	 * @param rvec the camera rotation vector.
	 * @param tvec the camera translation vector.
	 * @param target the target GDX camera to set the position and orientation.
	 */
	public static void setCameraByRT(Mat rvec,Mat tvec,Camera target) {
		Calib3d.Rodrigues(rvec,camRotation);

		if(target.near>0.01f)
			target.near = 0.01f;

		tvec.get(0,0, tvecArray);
		camRotation.get(0,0, rotArray);

		for(int row=0; row<3; ++row)
		{
		   for(int col=0; col<3; ++col)
		   {
			   viewMatrix.put(row,col, rotArray[row*3+col]);
		   }
		   viewMatrix.put(row,3, tvecArray[row]);
		}
		viewMatrix.put(3,0, homLine);
		viewMatrix.inv().get(0,0, viewMatArr);

		setCameraByViewTransform(viewMatArr,target);
	}

	/**
	 * Displays the given OpenCV mat and associates the view with the given key. If there is already a view associated with the key, then it is refreshed. Otherwise, a new view is displayed.
	 * If an associated view was displayed but the user closed it, then imShow will not display the mat, unless reset was called accordingly. That is, calling reset beforehand ensures the mat to be displayed.
	 * @param key the key to associate the mat view with.
	 * @param mat the OpenCV mat to display.
	 */
	public static void imShow(String key,Mat mat) {
		MatView matView = panels.get(key);

		if(matView==null) {
			matView = new MatView(key);
			panels.put(key,matView);
			matView.setTitle(key);
			matView.setLocation(curX,curY);
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			curX += defaultWidth;
			if(curX+defaultWidth>screenSize.width+9) {
				curX = 0;
				curY += defaultHeight;
				if(curY+defaultHeight>screenSize.height)
					curY = screenSize.height-defaultHeight;
			}
			matView.setVisible(true);
			viewCount++;
			closed = false;
		}else
			if(matView.closed)
				return;

		matView.refresh(mat);
	}

	/**
	 * Displays the given mat with an automatically generated key identifying the instance.
	 * @param mat the mat to display.
	 */
	public static void imShow(Mat mat) {
		imShow(genKey(mat), mat);
	}

	/**
	 * Generates and returns a unique key to identify the given mat.
	 */
	public static String genKey(Mat mat) {
		return "Mat"+mat.nativeObj;
	}

	/**
	 * Tells whether or not a mat view associated with the given key is visible.
	 */
	public static boolean imViewExists(String key) {
		MatView view = panels.get(key);
		return view!=null && !view.closed && view.isVisible();
	}

	/**
	 * Closes the mat view associated with the given key, if existent. No mat view will be associated with the key afterwards.
	 * @param key the key of the view to close.
	 * @return true, if and only if, a view is associated with the given key.
	 */
	public static boolean imClose(String key) {
		MatView view = panels.get(key);
		if(view==null || view.closed)
			return false;
		view.setVisible(false);
		onClosed(view);
		return true;
	}

	/**
	 * Closes all views that contain the given mat.
	 */
	public static void imClose(Mat mat) {
		String key;
		do{
			key = null;
			for(MatView view:panels.values()) {
				if(view!=null && view.mat == mat && !view.closed) {
					key = view.getKey();
					break;
				}
			}
			if(key!=null)
				imClose(key);
		}while(key!=null);
	}

	private static void onClosed(MatView view) {
		viewCount--;
		view.dispose();
		view.closed = true;
		if(viewCount<=0) {
			closed = true;
		}
	}

	/**
	 * Resets the mat view associated with the key in order that it can be opened again even after the user closed it.
	 */
	public static void imReset(String key) {
		MatView view = panels.get(key);
		if(view.closed)
			panels.remove(key);
	}

	/**
	 * Sets the visibility of the mat view associated with the given key. The view is not closed when set to invisible.
	 * @param key the key of the view to set the visibility.
	 * @param visible visibility of the mat view.
	 */
	public static void imSetVisible(String key,boolean visible) {
		MatView matView = panels.get(key);
		if(matView==null) {
			throw new RuntimeException("View does not exist: "+key);
		}
		matView.setVisible(visible);
	}

	/**
	 * Sets the debug output value of the mat view associated with the given key. The value will be displayed in brackets next to the title. Does nothing, if no associated view exists.
	 * @param key the key of the view to set the debug value.
	 * @param value the value to output.
	 * @return true, if and only if, a view is associated with the given key.
	 */
	public static boolean imDebugOutput(String key,Object value) {
		MatView matView = panels.get(key);
		if(matView==null) {
			return false;
		}
		matView.setTitle(key+" ("+value+")");
		return true;
	}

	/**
	 * Sets the debug output value of the mat view associated with the given key. The value will be displayed in brackets next to the title. Does nothing, if no associated view exists.
	 * @param key the key of the view to set the debug value.
	 * @param value the value to output.
	 */
	public static boolean imDebugOutput(String key,double value) {
		return imDebugOutput(key,""+value);
	}

	/**
	 * Removes the output of the debug value of the mat view associated with the given key. Does nothing, if no associated view exists.
	 * @param key the key of the view to clear the debug value.
	 * @return true, if and only if, a view is associated with the given key.
	 */
	public static boolean imDebugClear(String key) {
		MatView matView = panels.get(key);
		if(matView==null) {
			return false;
		}
		matView.setTitle(key);
		return true;
	}

	/**
	 * Tells whether or not all mat views has been closed (either programmatically or by the user). At least one view must have been opened after application start, that is, it returns false, if no view has been created.
	 */
	public static boolean isAllClosed() {
		return closed;
	}


	//------------------------GFX-HELPERS---------------------------------------
	/**
	 * Uploads the given mat data to the given texture. The mat is interpreted as a BGR image or a gray scale image. Only one byte per channel is supported.
	 */
	public static void imToTexture(Mat mat,Texture texture) {
		int w = mat.width();
		int h = mat.height();
		boolean singleChannel = CvType.channels(mat.type())==1;
		boolean signed = CvType.depth(mat.type())==CvType.CV_8S;
		if(w!=texture.getWidth() || h!=texture.getHeight()) {
			throw new RuntimeException("Texture dimensions ("+texture.getWidth()+","+texture.getHeight()+") and mat dimensions ("+mat.width()+","+mat.height()+") do not match");
		}

		int byteCount = mat.get(0, 0, imArray);
		imData.rewind();

		if(!singleChannel) {
			for(int i=0;i<byteCount;i+=3) {
				byte v = imArray[i];
				imArray[i] = imArray[i+2];
				imArray[i+2] = v;
			}
		}
		imData.put(imArray,0,byteCount);

		Gdx.gl20.glBindTexture(GL20.GL_TEXTURE_2D,texture.getTextureObjectHandle());
		Gdx.gl20.glActiveTexture(GL20.GL_TEXTURE0);

		imData.rewind();
		Gdx.gl20.glTexImage2D(GL20.GL_TEXTURE_2D, 0, singleChannel?GL20.GL_LUMINANCE:GL20.GL_RGB, w,h, 0, singleChannel?GL20.GL_LUMINANCE:GL20.GL_RGB, signed?GL20.GL_BYTE:GL20.GL_UNSIGNED_BYTE, imData);

		assert Gdx.gl20.glGetError()==0;
	}

	/**
	 * Creates a texture using the dimensions of the given video capture. The result texture uses linear filtering and has an RGB888 format. This method is supposed to be called once per camera. To update the texture, pass it to the imToTexture method together with the updated video capture mat.
	 */
	public static Texture createCameraTexture(VideoCapture camera) {
		int width = (int)camera.get(Highgui.CV_CAP_PROP_FRAME_WIDTH);
		int height = (int)camera.get(Highgui.CV_CAP_PROP_FRAME_HEIGHT);

		Texture result = new Texture(width,height,Format.RGB888);
		result.setFilter(TextureFilter.Linear, TextureFilter.Linear);

		return result;
	}

	/**
	 * Creates a texture using the dimensions of the given video capture. The result texture uses linear filtering and has 1 or 3 channels depending on the mat. This method is supposed to be called once per mat. No image data is transfered to the texture. To update the texture, use the imToTexture method with the returned texture and the given mat.
	 */
	public static Texture createMatTexture(Mat mat) {
		int width = mat.width();
		int height = mat.height();

		Texture result;
		if(CvType.channels(mat.type())==3)
			result = new Texture(width,height,Format.RGB888);
		else
			result = new Texture(width,height,Format.LuminanceAlpha);

		result.setFilter(TextureFilter.Linear, TextureFilter.Linear);

		return result;
	}

	/**
	 * Draws the given texture as background image. If the ratio of the image is smaller than the surface's ratio, black bars will be drawn at the left and the right border. The method should be called within render() before drawing anything.
	 */
	public static void texDrawBackground(Texture tex) {

		Gdx.graphics.getGL20().glClearColor(0, 0, 0, 1);
		Gdx.graphics.getGL20().glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		float ratio = (float)Gdx.graphics.getWidth()/Gdx.graphics.getHeight();
		float imgRatio = (float)tex.getWidth()/tex.getHeight();
		OrthographicCamera cam = new OrthographicCamera(2*ratio,2);

		batch.begin();
		batch.setProjectionMatrix(cam.projection);
		batch.draw(tex, -imgRatio,-1,2*imgRatio,2);
		batch.end();
	}

	/**
	 * Draws the given mat interpreted as an image as background image. If the ratio of the image is smaller than the surface's ratio, black bars will be drawn at the left and the right border. The method should be called within render() before drawing anything.
	 * The given mat must have 1 or 3 channels with one byte per channel (signed or unsigned).
	 */
	public static void imDrawBackground(Mat mat) {
		Texture tex = bgTextures.get(mat.nativeObj);
		if(tex==null) {
			tex = createMatTexture(mat);
			bgTextures.put(mat.nativeObj,tex);
		}
		imToTexture(mat,tex);
		texDrawBackground(tex);
	}

	//------------------------MAT-VIEW------------------------------------------
	private static class MatView extends JFrame implements WindowListener {

		protected Mat mat;
		protected boolean closed = false;
		private static final long serialVersionUID = 1L;
		protected BufferedImage image;
		private MatPanel matPanel;
		private String key;

		private MatOfByte byteMat = new MatOfByte();

		protected class MatPanel extends JPanel {

			private static final long serialVersionUID = 1L;

			public MatPanel() {
				super();
			}

			@Override
			public void paint(Graphics g) {
				if(image==null)
					return;
				g.drawImage(image, 0,0, null);
			}
		}

		public MatView(String key) {
			this.key = key;
			matPanel = new MatPanel();
			this.setContentPane(matPanel);
			this.addWindowListener(this);
			this.setTitle(key==null?"mat":key);
			this.setResizable(false);
			this.setMinimumSize(new Dimension(240,128));
		}

		public void refresh(Mat mat) {
			this.mat = mat;
			int w = mat.width();
			int h = mat.height();
			if(image==null || w!=image.getWidth() || h!=image.getHeight()) {
				getContentPane().setPreferredSize(new Dimension(w,h));
				pack();
			}

			if(!alwaysUseImageByteStream && (mat.type()==CvType.CV_8UC3 || mat.type()==CvType.CV_8UC1) ) {
				mat.get(0,0,imArray);

				int s = imArray.length/3;
				int j = 0;
				if(CvType.channels(mat.type())==3) {
					for(int i=0;i<s;i++) {
						imArrayInt[i] = imArray[j] + (imArray[j+1]<<8) + (imArray[j+2]<<16);
						j += 3;
					}
				}else{
					for(int i=0;i<s;i++) {
						int v = imArray[i];
						imArrayInt[i] = v + (v<<8) + (v<<16);
					}
				}
				if(image==null) {
					image = new BufferedImage(mat.width(),mat.height(), BufferedImage.TYPE_INT_RGB);
				}
				image.setRGB(0, 0, w,h, imArrayInt, 0,image.getWidth());
			}else{
				Highgui.imencode(".bmp", mat, byteMat);

				byte[] bytes = byteMat.toArray();
                /*
				try {
                    // TODO: FIX!
					image = null;//ImageIO.read(new ByteInputStream(bytes,bytes.length));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
                */
			}

			repaint();
		}

		public String getKey() {
			return key;
		}

		@Override
		public void windowActivated(WindowEvent arg0) {

		}

		@Override
		public void windowClosed(WindowEvent arg0) {

		}

		@Override
		public void windowClosing(WindowEvent arg0) {
			UtilAR.onClosed(this);
			this.closed = true;
		}

		@Override
		public void windowDeactivated(WindowEvent arg0) {

		}

		@Override
		public void windowDeiconified(WindowEvent arg0) {

		}

		@Override
		public void windowIconified(WindowEvent arg0) {

		}

		@Override
		public void windowOpened(WindowEvent arg0) {

		}

	}
}
