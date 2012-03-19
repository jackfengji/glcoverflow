package com.fengji.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.animation.AnimationUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CoverFlowOpenGL extends GLSurfaceView implements GLSurfaceView.Renderer {
	private static final String TAG = "AnotherCoverFlow";
	
	private static final int TOUCH_MINIMUM_MOVE = 5;
	private static final int IMAGE_SIZE = 128; // the bitmap size we use for the texture
	private static final int MAX_TILES = 48; // the maximum tiles in the cache
	private static final int VISIBLE_TILES = 3; // the visble tiles left and right
	
	private static final float SCALE = 0.7f; // the scale of surface view
	private static final float SPREAD_IMAGE = 0.14f;
	private static final float FLANK_SPREAD = 0.4f;
	private static final float FRICTION = 10.0f;
	private static final float MAX_SPEED = 6.0f;
	
	private static final float[] GVertices = new float[]{
		-1.0f, -1.0f, 0.0f,
		 1.0f, -1.0f, 0.0f,
		-1.0f,  1.0f, 0.0f,
		 1.0f,  1.0f, 0.0f,
	};
	
	private static final float[] GTextures = new float[]{
		0.0f, 1.0f,
		1.0f, 1.0f,
		0.0f, 0.0f,
		1.0f, 0.0f,
	};
	
	private GL10 mGLContext;
	private FloatBuffer mVerticesBuffer;
	private FloatBuffer mTexturesBuffer;
	private float[] mMatrix;
	
	private int mBgTexture;
	private FloatBuffer mBgVerticesBuffer;
	private FloatBuffer mBgTexturesBuffer;
	private int mBgBitmapId;
	private boolean mInitBackground;
    
    private float mOffset;
    private int mLastOffset;
    private RectF mTouchRect;
    
    private int mWidth;
    private boolean mTouchMoved;
    private float mTouchStartPos;
    private float mTouchStartX;
    private float mTouchStartY;
    
    private float mStartOffset;
    private long mStartTime;
    
    private float mStartSpeed;
    private float mDuration;
    private Runnable mAnimationRunnable;
    private VelocityTracker mVelocity;
    
    private boolean mStopBackgroundThread;
    private CoverFlowListener mListener;
    private DataCache<Integer, CoverFlowRecord> mCache;
    
	public CoverFlowOpenGL(Context context) {
		super(context);
		
		setEGLConfigChooser(8, 8, 8, 8, 16, 0);
		
		setRenderer(this);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		
		getHolder().setFormat(PixelFormat.TRANSLUCENT);
		//setZOrderMediaOverlay(true);
		//setZOrderOnTop(true);
		
		mCache = new DataCache<Integer, CoverFlowRecord>(MAX_TILES);
        mLastOffset = 0;
	    mOffset = 0;
	    mInitBackground = false;
        mBgBitmapId = 0;
	}
	
	public void setCoverFlowListener(CoverFlowListener listener) {
		mListener = listener;
	}
	
	private float checkValid(float off) {
		int max = mListener.getCount(this) - 1;
		if (off < 0)
			return 0;
		else if (off > max)
			return max;
		
		return off;
	}
	
	public void setSelection(int position) {
		endAnimation();
		mOffset = position;
        requestRender();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		switch(action) {
		case MotionEvent.ACTION_DOWN:
			touchBegan(event);
			return true;
		case MotionEvent.ACTION_MOVE:
			touchMoved(event);
			return true;
		case MotionEvent.ACTION_UP:
			touchEnded(event);
			return true;
		}
		return false;
	}
	
	private void touchBegan(MotionEvent event) {
		endAnimation();
		
		float x = event.getX();
		mTouchStartX = x;
		mTouchStartY = event.getY();
		mStartTime = System.currentTimeMillis();
		mStartOffset = mOffset;
		
		mTouchMoved = false;
		
		mTouchStartPos = (x / mWidth) * 10 - 5;
		mTouchStartPos /= 2;
		
		mVelocity = VelocityTracker.obtain();
		mVelocity.addMovement(event);
	}
	
	private void touchMoved(MotionEvent event) {
		float pos = (event.getX() / mWidth) * 10 - 5;
		pos /= 2;
		
		if (!mTouchMoved) {
			float dx = Math.abs(event.getX() - mTouchStartX);
			float dy = Math.abs(event.getY() - mTouchStartY);
			
			if (dx < TOUCH_MINIMUM_MOVE && dy < TOUCH_MINIMUM_MOVE)
				return ;
			
			mTouchMoved = true;
		}
		
		mOffset = checkValid(mStartOffset + mTouchStartPos - pos);
		
		requestRender();
		mVelocity.addMovement(event);
	}
	
	private void touchEnded(MotionEvent event) {
		float pos = (event.getX() / mWidth) * 10 - 5;
		pos /= 2;
		
		if (mTouchMoved) {
			mStartOffset += mTouchStartPos - pos;
			mStartOffset = checkValid(mStartOffset);
			mOffset = mStartOffset;
			
			mVelocity.addMovement(event);
			
			mVelocity.computeCurrentVelocity(1000);
			double speed = mVelocity.getXVelocity();
			speed = (speed / mWidth) * 10;
			if (speed > MAX_SPEED)
				speed = MAX_SPEED;
			else if (speed < -MAX_SPEED)
				speed = -MAX_SPEED;
			
			startAnimation(-speed);
		} else {
			if (mTouchRect.contains(event.getX(), event.getY())) {
				mListener.topTileClicked(this, (int) (mOffset + 0.01));
			}
		}
	}
	
	private void startAnimation(double speed) {
		if (mAnimationRunnable != null)
			return ;
		
		double delta = speed * speed / (FRICTION * 2);
		if (speed < 0)
			delta = -delta;
		
		double nearest = mStartOffset + delta;
		nearest = Math.floor(nearest + 0.5);
		nearest = checkValid((float) nearest);
		
		mStartSpeed = (float) Math.sqrt(Math.abs(nearest - mStartOffset) * FRICTION * 2);
		if (nearest < mStartOffset)
			mStartSpeed = -mStartSpeed;
		
		mDuration = Math.abs(mStartSpeed / FRICTION);
		mStartTime = AnimationUtils.currentAnimationTimeMillis();
		
		mAnimationRunnable = new Runnable() {
			@Override
			public void run() {
				driveAnimation();
			}
		};
		post(mAnimationRunnable);
	}
	
	private void driveAnimation() {
		float elapsed = (AnimationUtils.currentAnimationTimeMillis() - mStartTime) / 1000.0f;
		if (elapsed >= mDuration)
			endAnimation();
		else {
			updateAnimationAtElapsed(elapsed);
			post(mAnimationRunnable);
		}
	}
	
	private void endAnimation() {
		if (mAnimationRunnable != null) {
			mOffset = (float) Math.floor(mOffset + 0.5);
			mOffset = checkValid(mOffset);
			
			requestRender();
			
			removeCallbacks(mAnimationRunnable);
			mAnimationRunnable = null;
		}
	}
	
	private void updateAnimationAtElapsed(float elapsed) {
		if (elapsed > mDuration)
			elapsed = mDuration;
		
		float delta = Math.abs(mStartSpeed) * elapsed - FRICTION * elapsed * elapsed / 2;
		if (mStartSpeed < 0)
			delta = -delta;
		
		mOffset = checkValid(mStartOffset + delta);
		requestRender();
	}
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		mCache.clear();

	    mGLContext = gl;
	    mVerticesBuffer = makeFloatBuffer(GVertices);
	    mTexturesBuffer = makeFloatBuffer(GTextures);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int w, int h) {
		/*if (getAnimation() != null)
			return;*/
		
		mWidth = w;
		
		float imagew = w * 0.45f / SCALE / 2.0f;
		float imageh = h * 0.45f / SCALE / 2.0f;
		mTouchRect = new RectF(w / 2 - imagew, h / 2 - imageh, w / 2 + imagew, h / 2 + imageh);
		
		gl.glViewport(0, 0, w, h);
	
	    float ratio = ((float) w) / h;
	    gl.glMatrixMode(GL10.GL_PROJECTION);
	    gl.glLoadIdentity();
	    gl.glOrthof(-ratio * SCALE, ratio * SCALE, -1 * SCALE, 1 * SCALE, 1, 3);
	    
	    float[] vertices = new float[] {
	    		-ratio * SCALE, -SCALE, 0,
	    		ratio * SCALE, -SCALE, 0,
	    		-ratio * SCALE, SCALE, 0,
	    		ratio * SCALE, SCALE, 0
	    };
	    mBgVerticesBuffer = makeFloatBuffer(vertices);
	}
	
	public void setBackgroundTexture(int res) {
        mBgBitmapId = res;
		mInitBackground = true;
	}

    public void clearTileCache() {
        mCache.clear();
    }
	
	private void initBg() {
		mInitBackground = false;
		if (mBgBitmapId != 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), mBgBitmapId);
            mBgBitmapId = 0;

			int tmp = 1;
			int w = bitmap.getWidth();
			int h = bitmap.getHeight();
			while (w > tmp || h > tmp) {
				tmp <<= 1;
			}
			
			int width = tmp;
			int height = tmp;
			Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
			Canvas cv = new Canvas(bm);
			
			int left = (width - w) / 2;
			int top = (height - h) / 2;
			cv.drawBitmap(bitmap, left, top, new Paint());
			
			GL10 gl = mGLContext;
			
			int[] tex = new int[1];
			gl.glGenTextures(1, tex, 0);
			mBgTexture = tex[0];
			
			gl.glBindTexture(GL10.GL_TEXTURE_2D, mBgTexture);
			GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bm, 0);
            bitmap.recycle();
			bm.recycle();
			
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
	        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
	        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
	        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
	        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);
			
	        float[] textcoor = new float[] {
	        		(tmp - w) / 2.0f / tmp, (tmp - h) / 2.0f / tmp,
	        		(tmp + w) / 2.0f / tmp, (tmp - h) / 2.0f / tmp,
	        		(tmp - w) / 2.0f / tmp, (tmp + h) / 2.0f / tmp,
	        		(tmp + w) / 2.0f / tmp, (tmp + h) / 2.0f / tmp 
	        };
	        mBgTexturesBuffer = makeFloatBuffer(textcoor);
		}
	}
	
	@Override
	public void onDrawFrame(GL10 gl) {
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		GLU.gluLookAt(gl, 0, 0, 2, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
		
		gl.glDisable(GL10.GL_DEPTH_TEST);
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
		
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		
		drawBg(gl);
		draw(gl);
	}
	
	public void drawBg(GL10 gl) {
        if (mInitBackground)
            initBg();

		if (mBgTexture != 0) {
			gl.glPushMatrix();
			
			gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mBgVerticesBuffer);
			gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mBgTexturesBuffer);
			gl.glEnable(GL10.GL_TEXTURE_2D);
			
			gl.glBindTexture(GL10.GL_TEXTURE_2D, mBgTexture); // bind texture
			gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
			
			gl.glPopMatrix();
		}
	}
	
	private void draw(GL10 gl) {
		mStopBackgroundThread = true;
		gl.glPushMatrix();
		
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVerticesBuffer); // vertices of square
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexturesBuffer); // texture vertices
		gl.glEnable(GL10.GL_TEXTURE_2D);
		
		gl.glEnable(GL10.GL_BLEND);
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

		final float offset = mOffset;
		int i = 0;
		int max = mListener.getCount(this) - 1;
	    int mid = (int) Math.floor(offset + 0.5);
	    int iStartPos = mid - VISIBLE_TILES;
	    
	    if (iStartPos < 0)
	        iStartPos = 0;
	    // draw the left tiles
	    for (i = iStartPos; i < mid; ++i) {
	    	drawTile(i, i - offset, gl);
	    }   
	    
	    // draw the right tiles
	    int iEndPos = mid + VISIBLE_TILES;
	    if (iEndPos > max)
	        iEndPos = max;
	    for (i = iEndPos; i >= mid; --i) {
	        drawTile(i, i - offset, gl);
	    }

        if (mLastOffset != (int) offset) {
	        mListener.tileOnTop(this, (int) offset);
            mLastOffset = (int) offset;
        }
	    
	    gl.glPopMatrix();
	    mStopBackgroundThread = false;
	    //preLoadCache(iStartPos - 3, iEndPos + 3);
	}
	
	private void drawTile(int position, float off, GL10 gl) {
		final CoverFlowRecord fcr = getTileAtIndex(position, gl);
        if (fcr != null && fcr.mTexture != 0)  {
            if (mMatrix == null) {
                mMatrix = new float[16];
                mMatrix[15] = 1;
                mMatrix[10] = 1;
                mMatrix[5] = 1;
                mMatrix[0] = 1;
            }

            float trans = off * SPREAD_IMAGE;
            float f = off * FLANK_SPREAD;
            if (f > FLANK_SPREAD)
                f = FLANK_SPREAD;
            else if (f < -FLANK_SPREAD)
                f = -FLANK_SPREAD;

            mMatrix[3] = -f;
            mMatrix[0] = 1 - Math.abs(f);
            float sc = 0.38f * mMatrix[0];
            trans += f * 1;

            gl.glPushMatrix();
            gl.glBindTexture(GL10.GL_TEXTURE_2D, fcr.mTexture); // bind texture

            // draw bitmap
            gl.glTranslatef(trans, 0, 0); // translate the picture to the right position
            gl.glScalef(sc, sc, 1.0f); // scale the picture
            gl.glMultMatrixf(mMatrix, 0); // rotate the picture
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);

            // draw the reflection
            gl.glTranslatef(0, -2, 0);
            gl.glScalef(1, -1, 1);
            gl.glColor4f(1f, 1f, 1f, 0.5f);
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
            gl.glColor4f(1, 1, 1, 1);

            gl.glPopMatrix();
        }
	}

	private CoverFlowRecord getTileAtIndex(int position, GL10 gl) {
		synchronized(this) {
			CoverFlowRecord fcr = mCache.objectForKey(position);
			if (fcr == null) {
                long bitmapDuration = 0;
				Bitmap bm = mListener.getImage(this, position);

				if (bm == null)
					return null;

				int texture = imageToTexture(bm, gl);

				fcr = new CoverFlowRecord(texture, gl);
				mCache.putObjectForKey(position, fcr);
			}
			return fcr;
		}
	}

    private static Bitmap createTextureBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        final Bitmap bm = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        if (width > IMAGE_SIZE || height > IMAGE_SIZE) {
            // scale the bitmap, make the width or height to the IMAGE_SIZE
            Rect src = new Rect(0, 0, width, height);

            float scale = 1.0f;
            if (width > height)
                scale = ((float) IMAGE_SIZE) / width;
            else
                scale = ((float) IMAGE_SIZE) / height;
            width = (int) (width * scale);
            height = (int) (height * scale);
            float left = (IMAGE_SIZE - width) / 2.0f;
            float top = (IMAGE_SIZE - height) / 2.0f;
            RectF dst = new RectF(left, top, left + width, top + height);

            cv.drawBitmap(bitmap, src, dst, new Paint());
        } else {
            float left = (IMAGE_SIZE - width) / 2.0f;
            float top = (IMAGE_SIZE - height) / 2.0f;
            cv.drawBitmap(bitmap, left, top, new Paint());
        }

        return bm;
    }

	private int imageToTexture(Bitmap bitmap, GL10 gl) {
		// generate texture
		int[] texture = new int[1];
		gl.glGenTextures(1, texture, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, texture[0]);
		
        final Bitmap bm = createTextureBitmap(bitmap);
        bitmap.recycle();
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bm, 0); // draw the bitmap in the texture
		bm.recycle();

		// some texture settings
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        //gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);
        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
		
		return texture[0];
	}
	
	// preload the cache from startindex(including) to endIndex(exclusive)
	// you just can preload the cache after the view has been attached to the window
	public void preLoadCache(final int startIndex, final int endIndex) {
		mStopBackgroundThread = false;
		if (mGLContext != null) {
			new Thread(new Runnable() {
				public void run() {
					int start = startIndex;
					if (start < 0)
						start = 0;
					
					int max = mListener.getCount(CoverFlowOpenGL.this);
					int end = endIndex > max ? max : endIndex;
					
					for (int i = start; i < end && !mStopBackgroundThread; ++i) {
						getTileAtIndex(i, mGLContext);
					}
				}
			}).run();
		}
	}
	
	private static FloatBuffer makeFloatBuffer(final float[] arr) {
    	ByteBuffer bb = ByteBuffer.allocateDirect(arr.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(arr);
        fb.position(0);
        return fb;
    }
	
    public static class CoverFlowRecord {
    	private int mTexture;
    	private GL10 gl;

    	public CoverFlowRecord(int texture, GL10 gl) {
    		mTexture = texture;
    		this.gl = gl;
    	}
    	
    	@Override
    	protected void finalize() throws Throwable {
    		if (mTexture != 0) {
    			gl.glDeleteTextures(1, new int[] {mTexture}, 0);
    		}
    		
    		super.finalize();
    	}
    }
    
	public static interface CoverFlowListener {
		public int getCount(CoverFlowOpenGL view);				// Number of images to display
		public Bitmap getImage(CoverFlowOpenGL anotherCoverFlow, int position);	// Image at position
		public void tileOnTop(CoverFlowOpenGL view, int position); // Notify what tile is on top after scroll or start
		public void topTileClicked(CoverFlowOpenGL view, int position);
	}
}
