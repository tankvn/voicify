// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.graphics.Rect;

import java.io.Serializable;

/**
 * An object that is used to represent the user's speech output selection on 
 * the resulting screen of a {@link Demonstration}. The selection is represented 
 * by the corner coordinates of a rectangle for easy serialization.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class OutputSelection implements Serializable {
    
    private int mRectLeft;
    private int mRectTop;
    private int mRectRight;
    private int mRectBottom;
    
    /**
     * Creates a new {@link OutputSelection} with the given {@link Rect}.
     * 
     * @param rect The {@link Rect} object that represents the user's output 
     * selection on the screen.
     */
    public OutputSelection(Rect rect) {
        mRectLeft = rect.left;
        mRectTop = rect.top;
        mRectRight = rect.right;
        mRectBottom = rect.bottom;
    }
    
    /**
     * Returns the {@link OutputSelection} as a {@link Rect} object.
     * 
     * @return The {@link Rect} object that represents the user's output 
     * selection on the screen.
     */
    public Rect getRect() {
        return new Rect(mRectLeft, mRectTop, mRectRight, mRectBottom);
    }
}
