// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.googlecode.eyesfree.utils.ProxyInputMethodService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * An object that controls all playback related functionality 
 * for a voice command.
 * 
 * @author ardakara@google.com (Arda Kara)
 */
public class Playback implements TextToSpeech.OnInitListener {

    /**
     * Used as an argument in notification messages to indicate success.
     */
    public static final int SUCCESS = 1;

    /**
     * Used as an argument in notification messages to indicate failure.
     */
    public static final int FAILURE = 0;

    /**
     * Used to categorize notification messages about playback completion.
     */
    public static final int PLAYBACK_COMPLETE = 1;

    /**
     * Class name for list view nodes.
     */
    public static final String LIST_VIEW_CLASS_NAME = "android.widget.ListView";

    /**
     * Class name for switch widgets.
     */
    public static final String SWITCH_WIDGET_CLASS_NAME = "android.widget.Switch";

    /**
     * Wait duration in milliseconds after carrying out playback actions and
     * before attempting to read the screen.
     */
    private static final int WAIT_BEFORE_READING = 5000;

    /**
     * Rate of speech while reading {@link OutputSelection}s.
     */
    private static final float SPEECH_RATE = 1.2F;

    /**
     * Log tag for console logging.
     */
    private static final String LOG_TAG = "VoicifyService";
    
    /**
     * {@link Handler} object provided by the caller {@link VoicifyService}.
     */
    private Handler mVoicifyServiceHandler;

    /**
     * {@link Context} object provided by the caller {@link VoicifyService}.
     */
    private Context mContext;

    /**
     * {@link TextToSpeech} object used to speak {@link OutputSelection}s.
     */
    private TextToSpeech mTts;
    
    /**
     * Flag that indicates if TTS has successfully initialized.
     */
    private boolean mTtsInitialized;

    /**
     * {@link AccessibilityNodeInfo} of the currently active window. 
     * Used to search its children for views or to inject events. 
     * Only set and access using the thread-safe methods since it might 
     * get updated by main thread while Playback is run on a different thread.
     */
    private static AccessibilityNodeInfo sRootNodeInfo;
    
    /**
     * Synchronization lock for the root node info.
     */
    private static Object sRootNodeInfoLock;

    /**
     * Id of the currently active window. Used to easily detect changes. 
     * Only set and access using the thread-safe methods since it might get 
     * updated by main thread while Playback is run on a different thread.
     */
    private int mRootWindowId;
    
    /**
     * Used to get the currently selected node when d-padding through items.
     */
    private static AccessibilityEvent sLastSelectionEvent;
    
    /**
     * Synchronization lock for the last selection event.
     */
    private static Object sLastSelectionEventLock;
    
    /**
     * Lets us keep track of which event corresponds to which action.
     */
    private static Semaphore sLastSelectionEventSemaphore;

    /**
     * Checks if the given event type is one that can change the active window.
     * 
     * @param eventType The event type of an {@code AccessibilityEvent}.
     * @return {@code true} if event is likely to change active window,
     *         {@code false} if not.
     */
    public static boolean isActiveWindowEventType(int eventType) {
        return (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER || 
                eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
    }

    /**
     * Creates a new {@link Playback} object with the given {@link Context} and
     * the {@link Handler}.
     * 
     * @param voicifyServiceHandler The message handler to receive notifications.
     * @param context The {@link Context} of the caller service.
     */
    public Playback(Handler voicifyServiceHandler, Context context) {
        mVoicifyServiceHandler = voicifyServiceHandler;
        mContext = context;
        mRootWindowId = -1;
        mTts = new TextToSpeech(mContext, this);
        mTtsInitialized = false;
        sRootNodeInfoLock = new Object();
        sLastSelectionEventLock = new Object();
        sLastSelectionEventSemaphore = new Semaphore(0, true);

        /*
         * Setting the static fields of InstrumentationTask. Using the same
         * InstrumentationTask instance causes repeated task errors.
         */
        new InstrumentationTask(new Instrumentation());
    }

    /**
     * Plays the given {@link Demonstration} by carrying out its
     * {@link PlaybackAction}s, waiting an amount of time specified by the
     * {@code WAIT_BEFORE_READING} variable, then reading the
     * {@link OutputSelection}s. Notifies the {@link Handler} the status upon
     * completion of playback.
     * 
     * @param demonstration The {@link Demonstration} to play.
     */
    public void playDemonstration(Demonstration demonstration) {
        boolean playbackSuccess = doPlaybackActions(demonstration.getActions());
        /*
         * TODO(ardakara): Wait, try finding text, if not wait a little more if
         * still not, do not crash, put up a message
         */
        try {
            Thread.sleep(WAIT_BEFORE_READING);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Error while thread.sleep: " + e.toString());
        }
        boolean speakSuccess = speakOutputSelections(demonstration.getOutputSelections());
        notifyPlaybackComplete(playbackSuccess && speakSuccess);
    }

    /**
     * Updates the current window with the given {@link AccessibilityNodeInfo}
     * if necessary. Recycles the old one.
     * 
     * @param windowEventNodeInfo The {@link AccessibilityNodeInfo} of the
     *            current active window.
     */
    public void updateActiveWindow(AccessibilityNodeInfo windowEventNodeInfo) {
        if (windowEventNodeInfo == null) {
            Log.e(LOG_TAG, "Got null root node candidate!");
            return;
        }
        Log.v(LOG_TAG, "Checking window context.");
        AccessibilityNodeInfo nodeInfo = AccessibilityNodeInfo.obtain(windowEventNodeInfo);
        int windowId = nodeInfo.getWindowId();
        if (mRootWindowId == -1 || getRootNodeInfo(false) == null || mRootWindowId != windowId) {
            setRootNodeInfo(nodeInfo);
            mRootWindowId = windowId;
            Log.v(LOG_TAG, "Changed window context.");
        } else {
            nodeInfo.recycle();
        }
    }
    
    /**
     * Updates the cached last selection event to reflect a new selection. 
     * This cached event is used to keep track of the list item we're on while 
     * d-padding through a list. 
     * 
     * @param selectionEvent The most current TYPE_VIEW_SELECTED type event
     */
    public void updateLastSelectionEvent(AccessibilityEvent selectionEvent) {
        if (selectionEvent == null) {
            Log.e(LOG_TAG, "Got null lastSelectionEvent candidate dude.");
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(selectionEvent);
        setLastSelectionEvent(event);
    }

    /**
     * Used to check if the {@link TextToSpeech} is currently speaking.
     * 
     * @return {@code true} if speaking, {@code false} if not.
     */
    public boolean isSpeaking() {
        if (mTts == null) {
            Log.e(LOG_TAG, "TTS is not initialized!");
            return false;
        } else {
            return mTts.isSpeaking();
        }
    }

    /**
     * Stops the speaking of the {@link TextToSpeech} engine.
     */
    public void stopSpeaking() {
        if (mTts == null) {
            Log.e(LOG_TAG, "TTS is not initialized!");
        } else {
            mTts.stop();
        }
    }

    /**
     * Shuts down the {@link TextToSpeech} engine .
     */
    public void shutdownTts() {
        if (mTts == null) {
            Log.e(LOG_TAG, "TTS not initialized!");
        } else {
            mTts.shutdown();
            mTts = null;
        }
    }

    @Override
    public void onInit(int status) {        
        mTtsInitialized = true;  
        if (status != TextToSpeech.SUCCESS) {
            Log.e(LOG_TAG, "Failed to initialize TTS.");
            mTtsInitialized = false;
            return;
        }
        
        if (mTts.setLanguage(Locale.getDefault()) < 0) {
            Log.e(LOG_TAG, "Failed to set TTS locale.");
            mTtsInitialized = false;
            return;
        }
        
        if (mTts.setSpeechRate(SPEECH_RATE) != TextToSpeech.SUCCESS) {
            Log.e(LOG_TAG, "Failed to set TTS speech rate.");
            mTtsInitialized = false;
            return;
        }
    }
    
    /**
     * Synchronized accessor for {@code sRootNodeInfo}. Gets the current active
     * window's {@link AccessibilityNodeInfo}.
     * 
     * @param copy If {@code true}, a copy of the root node info is returned.
     * @return The current root {@link AccessibilityNodeInfo}. If asked for a
     *         copy, caller is responsible for recycling the returned object.
     */
    protected static AccessibilityNodeInfo getRootNodeInfo(boolean copy) {
        synchronized (sRootNodeInfoLock) {
            if (sRootNodeInfo == null) {
                Log.e(LOG_TAG, "Root node info is not updated!");
                return null;
            }

            if (copy) {
                return AccessibilityNodeInfo.obtain(sRootNodeInfo);
            } else {
                return sRootNodeInfo;
            }
        }
    }

    /**
     * Synchronized accessor for {@code sLastSelectionEvent}. Gets the most 
     * recent node selection event since last clearing. To use this, you should 
     * first clear the current cached events with {@code clearSelectedNodeInfo()} 
     * before carrying out the action that would cause the selection change. 
     * Each call to this method will block until a new selection event is 
     * available for it.
     * 
     * @param copy If {@code true}, a copy of the node selection event is returned.
     * @return The most recent selection {@link AccessibilityEvent}. If asked for a
     *         copy, caller is responsible for recycling the returned object.
     */
    protected static AccessibilityEvent getLastSelectionEvent(boolean copy) {
        Log.v(LOG_TAG, "Waiting on semaphore..");
        try {
            sLastSelectionEventSemaphore.acquire();
            Thread.sleep(100); // in case there are trailing events
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, e.toString());
            return null;
        }
        Log.v(LOG_TAG, "Acquired semaphore!");
        synchronized (sLastSelectionEventLock) {
            if (sLastSelectionEvent == null) {
                Log.e(LOG_TAG, "Last selection event is not updated!");
                return null;
            }
            if (copy) {
                return AccessibilityEvent.obtain(sLastSelectionEvent);
            } else {
                return sLastSelectionEvent;
            }
        }
    }

    /**
     * Synchronized mutator for {@code sRootNodeInfo}.
     * 
     * @param rootNodeInfo The new value.
     */
    private static void setRootNodeInfo(AccessibilityNodeInfo rootNodeInfo) {
        synchronized (sRootNodeInfoLock) {
            if (sRootNodeInfo != null) {
                sRootNodeInfo.recycle();
            }
            sRootNodeInfo = rootNodeInfo;
            if (rootNodeInfo != null) {
                Log.v(LOG_TAG, "Current active window: " + rootNodeInfo.toString());
            }
        }
    }
    
    /**
     * Synchronized mutator for {@code sLastSelectionEvent}.
     * 
     * @param selectionEvent The new value.
     */
    private static void setLastSelectionEvent(AccessibilityEvent selectionEvent) {
        synchronized (sLastSelectionEventLock) {
            if (sLastSelectionEvent != null) {
                sLastSelectionEvent.recycle();
            }
            sLastSelectionEvent = selectionEvent;
            sLastSelectionEventSemaphore.release();
            if (selectionEvent != null) {
                Log.v(LOG_TAG, 
                        "Semaphore released - New selection: " + selectionEvent.toString());
                Log.v(LOG_TAG, 
                        sLastSelectionEventSemaphore.availablePermits() + " permits available.");
            }
        }
    }
    
    private static void clearLastSelectionEvent() {
        synchronized (sLastSelectionEventLock) {
            if (sLastSelectionEvent != null) {
                sLastSelectionEvent.recycle();
            }
            sLastSelectionEvent = null;
            sLastSelectionEventSemaphore.drainPermits();
            Log.v(LOG_TAG, "Semaphore cleared!");
        }
    }

    private boolean doPlaybackActions(List<PlaybackAction> playbackActions) {
        long lastEventTime = -1;
        for (PlaybackAction playbackAction : playbackActions) {
            int type = playbackAction.getEventType();
    
            String contentDescription = null;
            if (playbackAction.hasContentDescription()) {
                contentDescription = playbackAction.getContentDescription().toString();
            }
    
            String text = null;
            if (playbackAction.hasText()) {
                text = playbackAction.getPrimaryText().toString();
            }
    
            int x = playbackAction.getX();
            int y = playbackAction.getY();
            long eventTime = playbackAction.getEventTime();
    
            if (getRootNodeInfo(false) == null) {
                Log.e(LOG_TAG, "No active window, aborting playback.");
                return false;
            }
    
            /*
             * TODO(ardakara): add other possible actions as they are added to
             * PlaybackAction TYPE_VIEW_SCROLLED, TYPE_VIEW_TEXT_CHANGED,
             * TYPE_VIEW_LONG_CLICKED
             */
            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (lastEventTime != -1 
                        && eventTime != PlaybackAction.UNINITIALIZED) {
                    /*
                     * need to run in its own thread so we can still get
                     * accessibility events and update the current window during
                     * playback, even if we are waiting for UI
                     */
                    waitForUi(eventTime - lastEventTime);
                }
    
                boolean success = false;
    
                /*
                 * If the target is likely to be static, just use X-Y
                 * coordinates.
                 */
                if (playbackAction.shouldUseXY()) {
                    Log.i(LOG_TAG, "Clicking on raw coordinates!");
                    injectTouchClickEvent(x, y, eventTime);
                    success = true;
                }
                
                /*
                 * If it's a list item, then find list on screen and then pick item.
                 */
                if (!success && 
                        playbackAction.getSpecialType() == PlaybackAction.SpecialType.LIST_ITEM) {
                    Log.d(LOG_TAG, "Trying to click on the list item.");
                    OutputSelection listBounds = null;
                    if (x != PlaybackAction.UNINITIALIZED && y != PlaybackAction.UNINITIALIZED) {
                        /* Giving it a point OutputSelection to find a list 
                         * that's overlapping with that point
                         */
                        listBounds = new OutputSelection(new Rect(x, y, x, y));
                    } else {
                        Log.w(LOG_TAG, "No location for list, will return any list on screen.");
                    }
                    /*
                     * If we don't have a source, listBounds will be null, this will
                     * find any list on the screen
                     */
                    success = clickOnListItem(text, contentDescription, eventTime, listBounds);
                }
                
                /* If failed and there is text, use text. */
                if (!success && text != null) {
                    Log.d(LOG_TAG, "Searching for text [" + text + "]");
                    success = clickOnLabelClosestToXY(text, x, y, eventTime, 
                            playbackAction.getSpecialType());
                }
    
                /* if failed, try finding the content description if there is any */
                if (!success && contentDescription != null) {
                     Log.d(LOG_TAG, 
                             "Couldn't find text. Searching for content description [" 
                                     + contentDescription + "] in the window.");
                    success = clickOnLabelClosestToXY(contentDescription, x, y, 
                            eventTime, playbackAction.getSpecialType());
                }
    
                if (success) {
                    lastEventTime = eventTime;
                    Log.i(LOG_TAG, "Clicked on:" + playbackAction.toString());
                } else {
                    Log.i(LOG_TAG,
                            "Aborting playback - Failed to click on:" + playbackAction.toString());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean clickOnListItem(String text, 
            String contentDescription,  
            long eventTime, 
            OutputSelection bounds) {
        /*
         * If we don't have a source, bounds will be null, this will
         * find any list on the screen.
         */
        AccessibilityNodeInfo listNode = getListNodeInSelection(bounds);
        if (listNode == null) {
            Log.e(LOG_TAG, "Can't find list node in the recorded bounds.");
            return false;
        }

        // this call does not actually click on the list, just focuses it
        if (!getFocusOnNode(listNode, false)) {
            Log.e(LOG_TAG, "Can't focus/select list.");
            return false;
        }
        listNode.recycle();
        
        // to make sure the first item in the list is last selected
        clearLastSelectionEvent();
        injectUpDownKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN);
        injectUpDownKeyEvent(KeyEvent.KEYCODE_DPAD_UP);
        

        getLastSelectionEvent(false);
        AccessibilityEvent selectedNode = getLastSelectionEvent(true);
        Log.v(LOG_TAG, "Currently selected node: " + selectedNode.toString());
        int lastSelectedHash = -1;
        while (selectedNode != null && lastSelectedHash != selectedNode.hashCode()) {
            if (nodeMatchesLabels(selectedNode, text, contentDescription)) {
                injectUpDownKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER);
                selectedNode.recycle();
                return true;
            }
            selectedNode.recycle();
            clearLastSelectionEvent();
            injectUpDownKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN);
            lastSelectedHash = selectedNode.hashCode();

            selectedNode = getLastSelectionEvent(true);
        }
        return false;
    }
    
    private static boolean nodeMatchesLabels(AccessibilityEvent node, 
            String text, String contentDescription) {
        List<CharSequence> nodeText = node.getText();
        CharSequence nodeCD = node.getContentDescription();
        
        if ((!PlaybackAction.isListWithText(nodeText) ^ TextUtils.isEmpty(text)) 
                || (TextUtils.isEmpty(nodeCD) ^ TextUtils.isEmpty(contentDescription))) {
            return false;
        }
        boolean textMatches = (text == null) || text.equals(node.getText().get(0));
        boolean descriptionMatches = (contentDescription == null) 
                || contentDescription.equals(node.getContentDescription());
        
                
        return textMatches && descriptionMatches;
    }

    /**
     * Searches for a view with the given text and performs a click event on it.
     * 
     * @param label The text of the view to click on
     * @return true if click successful, false if not
     */
    private boolean clickOnLabelClosestToXY(String label, int x, int y, 
            long eventTime, PlaybackAction.SpecialType specialType) {
        AccessibilityNodeInfo rootNodeInfo = getRootNodeInfo(false);
        if (rootNodeInfo == null) {
            Log.e(LOG_TAG, "No root node info, can't click!");
            return false;
        }
        List<AccessibilityNodeInfo> foundNodes = rootNodeInfo
                .findAccessibilityNodeInfosByText(label);

        AccessibilityNodeInfo nodeToClick = getClosestNodeToXY(foundNodes, x, y);
        if (nodeToClick == null) {
            return false;
        }
        
        /*
         * This is hacky. It works for Settings, but may not elsewhere.
         */
        if (specialType == PlaybackAction.SpecialType.SWITCH_TOGGLE) {
            boolean foundSwitch = false;
            AccessibilityNodeInfo listItemParent = nodeToClick.getParent().getParent();
            int childCount = listItemParent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = listItemParent.getChild(i);
                if (child.getClassName().equals(SWITCH_WIDGET_CLASS_NAME)) {
                    Log.i(LOG_TAG, "Found the switch node!");
                    nodeToClick = child;
                    foundSwitch = true;
                    break;
                } else {
                    child.recycle();
                }
            }
            if (!foundSwitch) {
                Log.e(LOG_TAG, "Can't find switch with label [" + label + "].");
                return false;
            }
        }

        if (getFocusOnNode(nodeToClick, true)) {
            Log.i(LOG_TAG, "Clicked with D-Pad");
        } else {
            clickOnNodeWithInjection(nodeToClick, eventTime);
        }

        for (AccessibilityNodeInfo nodeInfo : foundNodes) {
            nodeInfo.recycle();
        }

        return true;
    }
    
    private AccessibilityNodeInfo getClosestNodeToXY(List<AccessibilityNodeInfo> foundNodes, 
            int x, int y) {
        if (foundNodes == null || foundNodes.size() < 1 || foundNodes.get(0) == null) {
            return null;
        }
        
        if (x == -1 || y == -1) {
            return foundNodes.get(0);
        }
        
        AccessibilityNodeInfo leastDistanceNode = null;
        double leastDistance = -1;
        for (AccessibilityNodeInfo node : foundNodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            double distance = Math.pow((bounds.centerX() - x), 2) + 
                    Math.pow((bounds.centerY() - y), 2);
            if (leastDistance == -1 || distance < leastDistance) {
                leastDistance = distance;
                leastDistanceNode = node;
            }
        }
        return leastDistanceNode;
    }
    
    private void clickOnNodeWithInjection(AccessibilityNodeInfo nodeToClick, long eventTime) {
        Log.e(LOG_TAG, "Failover to injection to click on node:" + nodeToClick.toString());

        Rect boundingRect = new Rect();
        nodeToClick.getBoundsInScreen(boundingRect);

        float clickX = boundingRect.exactCenterX();
        float clickY = boundingRect.exactCenterY();

        injectTouchClickEvent(clickX, clickY, eventTime);
    }
    
    private void injectTouchClickEvent(float x, float y, long originalTime) {
        long currentTime = SystemClock.uptimeMillis();
        /* downTime is used for the original time and eventTime for current time */
        MotionEvent downEvent = MotionEvent.obtain(originalTime, currentTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent upEvent = MotionEvent.obtain(originalTime, currentTime, MotionEvent.ACTION_UP,
                x, y, 0);
        Log.d(LOG_TAG, "Clicking on X:" + x + " Y:" + y);
        new InstrumentationTask().execute(downEvent, upEvent);
    }

    /**
     * Attempts to focus/select the give node. If click is true performs 
     * a click on the node using the d-pad.
     * 
     * @param nodeToClick {@link AccessibilityNodeInfo} to focus/select.
     * @param click if {@code true} then a click is performed on the selected node
     * @return {@code true} if success, {@code false} otherwise
     */
    private boolean getFocusOnNode(AccessibilityNodeInfo nodeToClick, boolean click) {
        boolean viewFocused = nodeToClick.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        if (viewFocused) {
            Log.i(LOG_TAG, "Focus succeeded on " + nodeToClick.toString());
            if (click) {
                return injectUpDownKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER);
            } else {
                return true;
            }
        } else {
            Log.i(LOG_TAG, "Focus failed on " + nodeToClick.toString());
            return false;
        }
    }

    // TODO(ardakara): replace for IME use before Market
    @SuppressWarnings("unused")
    private boolean injectUpDownKeyEventwithIme(int keyCode) {
        KeyEvent dpadCenterDown = KeyEvent.changeFlags(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyCode),
                KeyEvent.FLAG_SOFT_KEYBOARD);
        KeyEvent dpadCenterUp = KeyEvent.changeFlags(
                new KeyEvent(KeyEvent.ACTION_UP, keyCode),
                KeyEvent.FLAG_SOFT_KEYBOARD);

        boolean downSuccess = ProxyInputMethodService.sendKeyEvent(dpadCenterDown);
        boolean upSuccess = ProxyInputMethodService.sendKeyEvent(dpadCenterUp);
        
        return downSuccess && upSuccess;
    }
    
    private boolean injectUpDownKeyEvent(int keyCode) {
        KeyEvent dpadCenterDown = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent dpadCenterUp = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        Log.v(LOG_TAG, "Injecting up/down key event with code " + keyCode);
        new InstrumentationTask().execute(dpadCenterDown, dpadCenterUp);
        return true;
    }

    private void waitForUi(long duration) {
        Log.i(LOG_TAG, "Waiting for " + duration + " milliseconds.");
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    private boolean speakOutputSelections(List<OutputSelection> outputSelections) {
        if (!mTtsInitialized) {
            Log.e(LOG_TAG, "TTS not initialized, aborting speech out!");
            return false;
        }
        
        Log.d(LOG_TAG, "Attempting to speak output selections..");
        List<AccessibilityNodeInfo> nodesInSelections = getTextNodesInSelections(outputSelections);
        for (AccessibilityNodeInfo selectionNode : nodesInSelections) {
            if (selectionNode == null) {
                return false;
            }
            CharSequence nodeText = selectionNode.getText();
            CharSequence nodeContentDescription = selectionNode.getContentDescription();
            if (nodeText == null && nodeContentDescription == null) {
                Log.d(LOG_TAG, "Skipping node without text or content description: " 
                        + selectionNode.toString());
                continue;
            } else {
                StringBuilder toRead = new StringBuilder();
                if (nodeContentDescription != null && nodeContentDescription.length() > 0) {
                    toRead.append(nodeContentDescription.toString() + ".");
                }
                if (nodeText != null && nodeText.length() > 0) {
                    toRead.append(nodeText.toString() + ".");
                }
                Log.d(LOG_TAG, "Leaf in selection: " + selectionNode.toString());
                Log.i(LOG_TAG, "Reading: " + toRead.toString());
                if (mTts == null) {
                    Log.e(LOG_TAG, "TTS is not initialized!");
                    return false;
                } else {
                    mTts.speak(toRead.toString(), TextToSpeech.QUEUE_ADD, null);
                }
            }
            selectionNode.recycle();
        }
        return true;
    }

    private List<AccessibilityNodeInfo> getTextNodesInSelections(
            List<OutputSelection> outputSelections) {
        List<AccessibilityNodeInfo> textLeaves = new ArrayList<AccessibilityNodeInfo>();
        AccessibilityNodeInfo rootNodeInfo = getRootNodeInfo(true);
        if (rootNodeInfo == null) {
            Log.e(LOG_TAG, "No root node info, can't get nodes in selections!");
            return textLeaves;
        }
        
        textLeaves.addAll(filterNodesInSelections(rootNodeInfo, outputSelections, 
                new NodeFilterFunction() {
            @Override
            public boolean isNodeWanted(AccessibilityNodeInfo node) {
                return ((node.getText() != null 
                        && node.getText().length() > 0) 
                        || (node.getContentDescription() != null 
                        && node.getContentDescription().length() > 0));
            }
        }));

        rootNodeInfo.recycle();
        return textLeaves;
    }
    
    /**
     * Searches the given area of the view hierarchy for a list node. If 
     * outputSelection is null, then it will find a list on the screen regardless 
     * of the location.
     * 
     * @param outputSelection The point/area to locate the list. If null, then 
     * it will find a list on the screen.
     * @return The node for the found list.
     */
    private AccessibilityNodeInfo getListNodeInSelection(OutputSelection outputSelection) {
        AccessibilityNodeInfo rootNodeInfo = getRootNodeInfo(true);
        if (rootNodeInfo == null) {
            Log.e(LOG_TAG, "No root node info, can't get a list node in selection!");
            return null;
        }
        
        List<OutputSelection> outputSelectionList = null;
        if (outputSelection != null) {
            outputSelectionList = new ArrayList<OutputSelection>(1);
            outputSelectionList.add(outputSelection);
        }
        
        /*
         * If we don't have a source, outputSelectionList will be null, this will
         * find any list on the screen.
         */
        List<AccessibilityNodeInfo> foundListNodes = 
                filterNodesInSelections(rootNodeInfo, outputSelectionList, 
                new NodeFilterFunction() {
                    @Override
                    public boolean isNodeWanted(AccessibilityNodeInfo node) {
                        return node.getClassName().equals(LIST_VIEW_CLASS_NAME);
                    }
                });
        
        rootNodeInfo.recycle();
        if (foundListNodes != null && foundListNodes.size() > 0) {
            return foundListNodes.get(0);
        } else {
            return null;
        }
    }

    interface NodeFilterFunction {
        public boolean isNodeWanted(AccessibilityNodeInfo node);
    }

    /**
     * Recursive method to get all textual leaves of parentNode in selections.
     *
     * @param parentNode The root node to start searching from.
     * @param selections The user's output selections. If null, will not check.
     * @return List of nodes with readable info that are in the selections.
     */
    private List<AccessibilityNodeInfo> filterNodesInSelections(AccessibilityNodeInfo parentNode,
            List<OutputSelection> selections, NodeFilterFunction filter) {
        if (parentNode == null) {
            Log.e(LOG_TAG, "Got null node while going through nodes to filter.");
            return new ArrayList<AccessibilityNodeInfo>();
        }

        List<AccessibilityNodeInfo> resultsFromChildren = new ArrayList<AccessibilityNodeInfo>();
        if (filter.isNodeWanted(parentNode)) {
            Log.i(LOG_TAG, "Filtered node: " + parentNode.toString());
            resultsFromChildren.add(AccessibilityNodeInfo.obtain(parentNode));
        }
        
        int childCount = parentNode.getChildCount();
        if (childCount < 1) {
            return resultsFromChildren;
        }

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = parentNode.getChild(i);
            if (childNode == null) {
                Log.e(LOG_TAG, "Got null child while going through nodes to filter.");
                continue;
            }
            if (selections == null || isNodeInSelections(childNode, selections)) {
                resultsFromChildren.addAll(filterNodesInSelections(childNode, selections, filter));
            }
            childNode.recycle();
        }
        return resultsFromChildren;
    }

    private boolean isNodeInSelections(AccessibilityNodeInfo node, 
            List<OutputSelection> selections) {
        for (OutputSelection selection : selections) {
            Rect nodeBounds = new Rect();
            node.getBoundsInScreen(nodeBounds);
            if (nodeBounds.intersect(selection.getRect())) {
                return true;
            }
        }
        return false;
    }

    private void notifyPlaybackComplete(boolean success) {
        Message message = Message.obtain(mVoicifyServiceHandler);
        if (success) {
            message.arg1 = SUCCESS;
        } else {
            message.arg1 = FAILURE;
        }
        message.what = PLAYBACK_COMPLETE;
        message.sendToTarget();
    }
}
