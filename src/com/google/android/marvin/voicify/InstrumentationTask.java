// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.app.Instrumentation;
import android.os.AsyncTask;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * An {@link AsyncTask} that injects the given {@code InputEvent} objects. 
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class InstrumentationTask extends AsyncTask<InputEvent, Integer, Integer> {

    private static final String LOG_TAG = "VoicifyService";
    
    private static Instrumentation sInst;
    
    /**
     * An empty constructor so that we can use a new AsyncTask for each task.
     * Reusing the same one would cause repeated task errors, since the task is 
     * likely to have already been executed and a task can be executed only once.
     */
    public InstrumentationTask() { }
    
    /**
     * Constructor that sets the static {@link Instrumentation} field for the 
     * future {@link InstrumentationTask}s.
     * 
     * @param inst The {@link Instrumentation} object to use when injecting 
     * {@link InputEvent}s.
     */
    public InstrumentationTask(Instrumentation inst) {
        sInst = inst;
    }
    
    /**
     * Injects the given InputEvent objects. 
     */
    @Override
    protected Integer doInBackground(InputEvent... events) {       
        for (InputEvent event : events) {
            if (event instanceof MotionEvent) {
                sInst.sendPointerSync((MotionEvent) event);
            } else if (event instanceof KeyEvent) {
                sInst.sendKeySync((KeyEvent) event);
            } else {
                Log.e(LOG_TAG, "Unknown InputEvent given to doInBackground.");
            }
            
            /* Manually wait in Playback for UI to finish processing
             * Can't use Instrumentation.waitForIdleSync() or waitForIdle
             * since for that to work Instrumentation needs to be initialized
             * in the ActivityThread and we don't have access 
             * to the current Activity
             */   
        }
        return events.length;
    }

    @Override
    protected void onPostExecute(Integer result) {
        Log.d(LOG_TAG, "Done syncing input events.");
    }
}
