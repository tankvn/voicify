// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that constitutes a singular action item for {@code Playback} class.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class PlaybackAction implements Serializable {

    public static final int UNINITIALIZED = -1;
    
    private static final String LOG_TAG = "VoicifyService";
    
    /**
     * Hard coded height for the top static region.
     */
    private static final int TOP_STATIC_REGION_HEIGHT = 60;

    /**
     * Type of the {@code AccessibilityEvent}. (e.g. View clicked, scrolled, etc.)
     */
    private int mEventType;

    /**
     * The content description of the source of the {@code AccessibilityEvent}, 
     * if available.
     */
    private CharSequence mContentDescription;

    /**
     * The list of text strings associated with 
     * the source of the {@code AccessibilityEvent}, if available.
     */
    private List<CharSequence> mText;
    
    /**
     * Raw X coordinate of the event.
     */
    private int mX;
    
    /**
     * Raw Y coordinate of the event.
     */
    private int mY;
    
    /**
     * The time of the action. Used to temporally separate during playback.
     */
    private long mEventTime;
    
    /**
     * The special types an action can be.
     */
    enum SpecialType {NORMAL, SWITCH_TOGGLE, LIST_ITEM}

    /**
     * The special type of the action. Indicates if the {@link PlaybackAction} 
     * should be considered as one of the special cases. e.g. a switch toggle
     */
    private SpecialType mSpecialType;
    
    /**
     * Given an actionable {@link AccessibilityEvent}, produces a 
     * {@link PlaybackAction} for it to be used by {@link Playback} class.
     * 
     * @param event Should be checked with {@code isActionable} before.
     * @return The {@link PlaybackAction} if successful, {@code null} if failed.
     */
    public static PlaybackAction obtain(AccessibilityEvent event) {
        PlaybackAction playbackAction = new PlaybackAction();
        boolean success = true;
        
        playbackAction.mEventType = event.getEventType();
    
        List<CharSequence> text = event.getText();
        if (isListWithText(text)) {
            playbackAction.mText = new ArrayList<CharSequence>(text);
        }
    
        CharSequence contentDescription = event.getContentDescription();
        if (contentDescription != null) {
            playbackAction.mContentDescription = contentDescription;
        }
        
        playbackAction.mEventTime = event.getEventTime();
        
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            Rect boundsInScreen = new Rect();
            source.getBoundsInScreen(boundsInScreen);
            playbackAction.mX = boundsInScreen.centerX();
            playbackAction.mY = boundsInScreen.centerY();
            source.recycle();
        } else {
            Log.w(LOG_TAG, "No source for this action!");
        }
        
        boolean isSwitchWidget = false;
        boolean isListItem = false;
        AccessibilityRecord record = null;
        if (event.getRecordCount() > 0) {
            record = event.getRecord(0);
            if (record != null) {
                Log.i(LOG_TAG, "Record: " + record.toString());
                isSwitchWidget = record.getClassName().equals(Playback.SWITCH_WIDGET_CLASS_NAME);
                isListItem = record.getClassName().equals(Playback.LIST_VIEW_CLASS_NAME); 
            }
        }
        
        /* SWITCH WIDGET 
         * When interaction is with a switch, we note the parent's text instead 
         * 
         * TODO(ardakara): can use isChecked() to make sure the action indicates 
         * only turning on a switch instead of toggling
         */
        if (isSwitchWidget) {
            Log.i(LOG_TAG, "We have ourselves a switch toggling case here.");
            playbackAction.mSpecialType = SpecialType.SWITCH_TOGGLE;
            List<CharSequence> recordTextList = record.getText();
            if (isListWithText(recordTextList)) {
                Log.i(LOG_TAG, "Switch record: " + record.toString());
                playbackAction.mText = new ArrayList<CharSequence>(recordTextList);
            } else {
                success = false;
                Log.e(LOG_TAG, "Switch record has no text. Sad day.");
            }
            return returnFromObtain(success, playbackAction);
        }
        /* END OF SWITCH WIDGET */
        
        /* LIST ITEM 
         * If the click was for a list-item, we need special scrolling */
        if (isListItem) {
            Log.i(LOG_TAG, "We have ourselves a ListView case here.");
            playbackAction.mSpecialType = SpecialType.LIST_ITEM;            
            return returnFromObtain(success, playbackAction);
        }
        /* END OF LIST ITEM */  
        
        return returnFromObtain(success, playbackAction);
    }
    
    /**
     * Checks to see if an {@link AccessibilityEvent} is a replayable action.
     * 
     * @param event An {@link AccessibilityEvent} fired while demonstration recording. 
     * @return {@code true} if event should be recorded as a {@link PlaybackAction}, 
     * {@code false} otherwise.
     */
    public static boolean isActionable(AccessibilityEvent event) {
        if (event == null) {
            return false;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d(LOG_TAG, "Discarded event - Not right type");
            return false;
        }
        return true;
    }

    /**
     * Checks to see if list contains text.
     * 
     * @param list The {@link List} to be checked.
     * @return {@code true} if list contains text, {@code false} otherwise.
     */
    public static boolean isListWithText(List<CharSequence> list) {
        return (list != null && list.size() > 0 && !TextUtils.isEmpty(list.get(0)));
    }

    /**
     * Checks if the {@link PlaybackAction} contains text.
     * @return {@code true} if action has text data, {@code false} if not.
     */
    public boolean hasText() {
        return isListWithText(mText);
    }

    /**
     * Checks if the {@link PlaybackAction} has a content description.
     * @return {@code true} if action has a content description, {@code false} if not.
     */
    public boolean hasContentDescription() {
        return (!TextUtils.isEmpty(mContentDescription));
    }

    public CharSequence getContentDescription() {
        return mContentDescription;
    }

    public List<CharSequence> getText() {
        return mText;
    }
    
    /**
     * Gets the first text string in the list of text items associated with this action.
     * @return The first text item in the list, or null if no text available.
     */
    public CharSequence getPrimaryText() {
        if (hasText()) {
            return mText.get(0);
        } else {
            return null;
        }
    }

    /**
     * Returns the type of {@link AccessibilityEvent} that this action was sourced by.
     * @return An integer for the type of the {@link AccessibilityEvent}.
     */
    public int getEventType() {
        return mEventType;
    }

    public long getEventTime() {
        return mEventTime;
    }
    
    public int getX() {
        return mX;
    }
    
    public int getY() {
        return mY;
    }
    
    /**
     * Returns the {@link SpecialType} of the playback action.
     * @return the {@link SpecialType} indicating the special treatment this 
     * action may need during playback.
     */
    public SpecialType getSpecialType() {
        return mSpecialType;
    }

    /**
     * Checks to see if we should just click on the raw X-Y coordinates for the action.
     * Target is likely to be statically located if no label is available,
     * or in the static region on screen.
     * TODO(ardakara): get static region (action and status bar) dimensions dynamically
     * 
     * @return True if OK to click on raw coordinates, false otherwise.
     */
    public boolean shouldUseXY() {
        Log.d(LOG_TAG, "Checking coordinates for staticness : [" 
                + getX() + ", " + getY() + "]");
        if (getX() < 0 || getY() < 0) {
            return false;
        } else if (!hasText() && !hasContentDescription()) {
            Log.i(LOG_TAG, "No label, can use raw coordinates.");
            return true;
        } else if (isInStaticRegion()) {
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return "PlaybackAction - Type:" + mEventType 
                + " | ContentDescription:" + mContentDescription 
                + " | Text:" + mText + " | X:" + mX + " | Y:" + mY
                + " | EventTime:" + mEventTime 
                + " | SpecialType:" + mSpecialType.toString();
    }

    private PlaybackAction() {
        mEventType = UNINITIALIZED;
        mContentDescription = "";
        mText = new ArrayList<CharSequence>();
        mX = UNINITIALIZED;
        mY = UNINITIALIZED;
        mEventTime = UNINITIALIZED;
        mSpecialType = SpecialType.NORMAL;
    }

    private static PlaybackAction returnFromObtain(boolean success, 
            PlaybackAction playbackAction) {
        if (success) {
            Log.i(LOG_TAG, playbackAction.toString());
            return playbackAction;
        } else {
            Log.e(LOG_TAG, "Could not create playback action!");
            return null;
        }
    }

    /**
     * Hard coded action and status bar heights for the Xoom
     * 
     * @return True if the PlaybackAction originated from a static region
     */
    private boolean isInStaticRegion() {
        int y = getY();
        int x = getX();
        
        /* TODO(ardakara): Also add bottom static region if IME 
         * still doesn't solve the issue. You'd have to have the Context.
         */
        return (x != -1 && y != -1) && (y <= TOP_STATIC_REGION_HEIGHT); 
    }
}
