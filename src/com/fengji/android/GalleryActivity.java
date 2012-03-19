package com.fengji.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class GalleryActivity extends Activity {
    private static int[] SAMPLE_IMAGES = new int[] {
            R.drawable.gallery_photo_1,
            R.drawable.gallery_photo_2,
            R.drawable.gallery_photo_3,
            R.drawable.gallery_photo_4,
            R.drawable.gallery_photo_5,
            R.drawable.gallery_photo_6,
            R.drawable.gallery_photo_7,
            R.drawable.gallery_photo_8
    };

    private CoverFlowOpenGL mCoverFlow;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(GalleryActivity.this, (String) msg.obj, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCoverFlow = new CoverFlowOpenGL(this);
        mCoverFlow.setCoverFlowListener(new CoverFlowOpenGL.CoverFlowListener() {
            @Override
            public int getCount(CoverFlowOpenGL view) {
                return SAMPLE_IMAGES.length;
            }

            @Override
            public Bitmap getImage(CoverFlowOpenGL anotherCoverFlow, int position) {
                return BitmapFactory.decodeResource(getResources(), SAMPLE_IMAGES[position]);
            }

            @Override
            public void tileOnTop(CoverFlowOpenGL view, int position) {
                // you can control what will happen when one image is in middle
                mHandler.obtainMessage(0, String.format("Image %d is on top.", position)).sendToTarget();
            }

            @Override
            public void topTileClicked(CoverFlowOpenGL view, int position) {
                // you can control what will happen when the image in middle is clicked
                mHandler.obtainMessage(0, String.format("Image %d is clicked", position)).sendToTarget();
            }
        });

        //mCoverFlow.setSelection(0);
        mCoverFlow.setBackgroundTexture(R.drawable.bg);

        setContentView(mCoverFlow);
    }
}
