// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/**
 * A subclass of {@link SimpleOverlay} that allows the user to draw 
 * a rectangle over the contents of the screen and returns the drawn rectangle.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class SelectionOverlay extends SimpleOverlay {
    
    private static final String LOG_TAG = "VoicifyService";
    
    /**
     * Determines the transparency of the rectangle. [0..255]
     */
    private static final int RECTANGLE_ALPHA = 128;
    
    /**
     * The caller {@link VoicifyService} object.
     */
    private final VoicifyService mVoicifyService;
    
    /**
     * Creates a new {@link SimpleOverlay} and sets the {@link VoicifyService} 
     * to be notified with the rectangle that the user draws. Does not show the 
     * overlay until {@code show()} is called.
     * 
     * @param voicifyService The {@link VoicifyService} object to be notified.
     */
    public SelectionOverlay(VoicifyService voicifyService) {
        super(voicifyService);
        mVoicifyService = voicifyService;
        setContentView(new SelectionView());
    }
    
    @Override
    public void onShow() {
        Log.d(LOG_TAG, "Selection overlay on screen.");
        Toast.makeText(mVoicifyService, 
                R.string.long_press_and_drag_to_select, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onHide() { }
    
    /**
     * Passes the user's selection to the {@link VoicifyService} 
     * as a {@link Rect} object.
     * 
     * @param selection
     */
    private void onSelectionResult(Rect selection) {
        mVoicifyService.onSelectionOverlayResult(selection);
    }

    /**
     * A view that intercepts all touch events to allow the user to 
     * draw a rectangle over the screen contents. 
     * 
     * @author ardakara@google.com (Arda Kara)
     *
     */
    private class SelectionView extends View {
        
        private boolean mStartedRectangle;
        private int mInitialX;
        private int mInitialY; 
        private ShapeDrawable mShapeDrawable;
        
        /**
         * Creates a new {@link SelectionView}.
         */
        public SelectionView() {
            super(mVoicifyService);
            mStartedRectangle = false;
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null) {
                Log.e(LOG_TAG, "Got null motion event!");
                return false;
            }
            int action = event.getAction();
            int x = (int) event.getX();
            int y = (int) event.getY();
            
            if (action == MotionEvent.ACTION_DOWN && !mStartedRectangle) {
                mStartedRectangle = true;
                mInitialX = x;
                mInitialY = y;
                mShapeDrawable = new ShapeDrawable();
                mShapeDrawable.getPaint().setColor(Color.MAGENTA);
                mShapeDrawable.setAlpha(RECTANGLE_ALPHA);
            } else if (action == MotionEvent.ACTION_MOVE && mStartedRectangle) {
                mShapeDrawable
                    .setBounds(getRectFromCorners(mInitialX, mInitialY, x, y));
                invalidate();
            } else if (action == MotionEvent.ACTION_UP && mStartedRectangle) {
                mStartedRectangle = false;
                onSelectionResult(getRectFromCorners(mInitialX, mInitialY, x, y));
            }
            return true;
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            if (mStartedRectangle) {
                mShapeDrawable.draw(canvas);
            }
        }
        
        private Rect getRectFromCorners(int x1, int y1, int x2, int y2) {
            int left = ((x1 <= x2) ? x1 : x2);
            int right = ((x1 <= x2) ? x2 : x1);
            int top = ((y1 <= y2) ? y1 : y2);
            int bottom = ((y1 <= y2) ? y2 : y1);
            
            return new Rect(left, top, right, bottom);
        }
    }
}
