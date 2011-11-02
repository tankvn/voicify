// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * A BroadcastReceiver object to detect button presses.
 * (i.e. headset hook button - {@code ACTION_MEDIA_BUTTON})
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class ButtonReceiver extends BroadcastReceiver 
        implements View.OnTouchListener, GestureDetector.OnGestureListener {

    private static final String LOG_TAG = "VoicifyService";
    
    enum EventType {KEY_EVENT, MOTION_EVENT}

    /**
     * Static field since it gets initialized once and 
     * new {@link ButtonReceiver} objects get instantiated by system.
     */
    private static VoicifyService mVoicifyService;
    
    /**
     * Static field since it gets initialized once and 
     * new {@link ButtonReceiver} objects get instantiated by system.
     */
    private static boolean mInLongPress;
    
    /**
     * To detect long-presses on the soft button.
     */
    private static GestureDetector mGestureDetector;

    /**
     * Required empty constructor as a {@link BroadcastReceiver}.
     */
    public ButtonReceiver() { }

    /**
     * Constructor method used to initialize the static fields of a 
     * {@link ButtonReceiver}.
     * 
     * @param callerService The service that should be notified on a button press.
     */
    public ButtonReceiver(VoicifyService callerService) {
        mVoicifyService = callerService;
        mInLongPress = false;
        mGestureDetector = new GestureDetector(mVoicifyService, this);
        mGestureDetector.setIsLongpressEnabled(true);
        Log.d(LOG_TAG, "Button receiver started.");
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {        
        String intentAction = intent.getAction();
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            return;
        }

        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null) {
            return;
        }

        processButtonAction(event.getAction(), event.isLongPress(), true);
    }
    
    @Override
    public boolean onTouch(View v, MotionEvent event) { 
        return mGestureDetector.onTouchEvent(event);
    }
    
    @Override
    public boolean onDown(MotionEvent e) {
        return processButtonAction(KeyEvent.ACTION_DOWN, false, false);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // DO NOTHING
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        processButtonAction(KeyEvent.ACTION_DOWN, true, false);
    }
    
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // DO NOTHING
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) { }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return processButtonAction(KeyEvent.ACTION_UP, false, false);
    }

    private boolean processButtonAction(int action, boolean longpress, boolean headset) {
        if (mVoicifyService == null) {
            Log.e(LOG_TAG, "VoicifyService is null at the ButtonReceiver.");
            return false;
        }
        
        if (action == KeyEvent.ACTION_DOWN) {
            if (longpress) {
                if (headset) {
                    mInLongPress = true;
                }
                Log.d(LOG_TAG, "Hook long pressed.");
                if (mVoicifyService.isSpeaking()) {
                    mVoicifyService.stopSpeaking();
                } else if (mVoicifyService.isRecording()){
                    mVoicifyService.endRecording();
                } else {
                    mVoicifyService.startRecording(); 
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {  
            if (headset && mInLongPress) {
                mInLongPress = false;
            } else {
                Log.d(LOG_TAG, "Hook pressed.");
                if (mVoicifyService.isSpeaking()) {
                    mVoicifyService.stopSpeaking();
                } else if (mVoicifyService.isRecording()) {
                    mVoicifyService.endRecording();
                } else {
                    mVoicifyService.listenForCommand();
                }
            }
        }
        return true;
    }
}
