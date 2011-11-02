// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * An {@link AccessibilityService} that lets users create custom 
 * voice commands through demonstration and use them.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class VoicifyService extends AccessibilityService {

    /**
     * Tag for logging.
     */
    public static final String LOG_TAG = "VoicifyService";

    /**
     * The name of the {@link DemonstrationSet} serialization in the filesystem.
     */
    public static final String DEMONSTRATION_SET_FILENAME = "voicify.ser";
    
    /**
     * The edit-distance value past which no match is found for a command.
     */
    private static final int COMMAND_DISTANCE_THRESHOLD = 100;
    
    /**
     * If the time gap between two actionable {@link AccessibilityEvent}s is 
     * below this, they will be treated as redundant.
     */
    private static final long MIN_HUMAN_ACTION_DELAY = 500;

    /**
     * ID used in registering permanent notification. 
     */
    private static final int VOICIFY_NOTIFICATION_ID = 1;
    
    /**
     * Static instance of {@link VoicifyService}.
     */
    private static VoicifyService sVoicifyService;

    /**
     * Time of last action event that Voicify received an {@link AccessibilityEvent} 
     * for. This is used to filter out duplicate events.
     */
    private static long sLastActionEvent;

    /**
     * The states that the service can be in.
     */
    enum State {RECORDING, SAVING, PLAYING, IDLE}

    /**
     * Holds current state of the service. Only use with the getter and setter.
     */
    private State mServiceState;

    /**
     * All available voice commands. 
     */
    private DemonstrationSet mDemonstrationSet;

    /**
     * The {@link Demonstration} that's currently being recorded
     */
    private Demonstration mDemonstration;

    /**
     * The object responsible for everything related to playback.
     */
    private Playback mPlayback;

    /**
     * The output field selection overlay.
     */
    private SelectionOverlay mSelectionOverlay;

    /**
     * Used to register for the media button events.
     */
    private static ButtonReceiver sButtonReceiver;
    
    /**
     * Used to register for the media button events.
     */
    private static AudioManager sAudioManager;
    
    /**
     * Used to register for the media button events.
     */
    private static ComponentName sRemoteControlResponder;

    /**
     * Used to put up the permanent notification.
     */
    private NotificationManager mNotificationManager;
    
    /**
     * Used to parse speech input.
     */
    private SpeechRecognizer mRecognizer;
    
    /**
     * Used to dismiss the keyguard.
     */
    private KeyguardLock mKeyguardLock;
    
    /**
     * Used to turn on the screen and keep it turned on.
     */
    private PowerManager.WakeLock mWakeLock;
    
    /**
     * Soft button to use as a replacement of headset hook button.
     */
    private SoftButtonOverlay mSoftButton;

    // TODO(ardakara): Uncomment to use IME before Market
//    /**
//     * Proxy IME to send key events without the INJECT_EVENTS permission.
//     */
//    private ProxyInputMethodMonitor mInputMethodMonitor;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "VoicifyService started.");

        sButtonReceiver = new ButtonReceiver(this);
        sAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        sRemoteControlResponder = 
                new ComponentName(getPackageName(), ButtonReceiver.class.getName());
        mNotificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        setServiceState(State.IDLE);
        mDemonstration = null;
        mDemonstrationSet = null;

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        mRecognizer.setRecognitionListener(mSpeechListener);

        mPlayback = new Playback(mHandler, this);
        
        mSelectionOverlay = null;

        loadDemonstrationSet();
        
        /* just registering the new way */
        registerButton();
        
        putNotification();
        
        KeyguardManager keyguardManager = 
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mKeyguardLock = keyguardManager.newKeyguardLock("Voicify");
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Voicify");
        
        sLastActionEvent = -1;
        
        mSoftButton = new SoftButtonOverlay(this);
        mSoftButton.setOnTouchListener(sButtonReceiver);
        mSoftButton.show();
        
        // TODO(ardakara): Uncomment to use IME before Market
//        mInputMethodMonitor = new ProxyInputMethodMonitor(this, ProxyInputMethodService.class);
//        mInputMethodMonitor.registerObserver();
//        mInputMethodMonitor.checkInputMethod();
        
        sVoicifyService = this;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected(); 
        Log.i(LOG_TAG, "VoicifyService connected.");
        registerButton(); 
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterButton();
        mPlayback.shutdownTts();
        mNotificationManager.cancel(VOICIFY_NOTIFICATION_ID);
        mSoftButton.hide();
        // TODO(ardakara): Uncomment to use IME before Market
//        mInputMethodMonitor.unregisterObserver();
        sVoicifyService = null;
        Toast.makeText(this, R.string.service_stopped_message, Toast.LENGTH_SHORT)
            .show();
        Log.i(LOG_TAG, "VoicifyService stopped.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {    
        if (event == null) {
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }

        Log.v(LOG_TAG, event.toString());

        if (getServiceState() == State.RECORDING && 
                mDemonstration != null && 
                PlaybackAction.isActionable(event) &&
                isUniqueEvent(event)) {
            sLastActionEvent = event.getEventTime();
            boolean success = mDemonstration.addEvent(event);
            if (!success) {
                Toast.makeText(this, R.string.recording_error, Toast.LENGTH_SHORT)
                    .show();
            }
        }
        
        if (Playback.isActiveWindowEventType(event.getEventType())) {
            // reregister for the headset hook at reasonable intervals
            registerForHeadsetHook();
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo != null) {
                mPlayback.updateActiveWindow(nodeInfo);
                nodeInfo.recycle();
            }
        }
        
        /* Always keep track of last TYPE_VIEW_SELECTED to see what items we're 
         * on while d-padding through a list. However, sometimes we get a 
         * TYPE_VIEW_SCROLLED after d-padding, and no selection, so let the lock 
         * acquire after scroll events too.
         */
        if (getServiceState() == State.PLAYING 
                && (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED 
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
            mPlayback.updateLastSelectionEvent(event);
        }
    }

    @Override
    public void onInterrupt() { }

    /**
     * Starts a new recording if possible.
     * @return {@code true} if successfully started recording, {@code false} otherwise.
     */
    public boolean startRecording() {
        AccessibilityNodeInfo rootNodeInfo = Playback.getRootNodeInfo(false);
        if (rootNodeInfo == null) {
            Log.e(LOG_TAG, "No root node info, can't create demonstration!");
            Toast.makeText(this, R.string.not_initiated, Toast.LENGTH_SHORT)
            .show();
            return false;
        }
        if (getServiceState() == State.SAVING) {
            Log.i(LOG_TAG, "Still saving last recording.");
            Toast.makeText(this, R.string.still_saving, Toast.LENGTH_SHORT)
            .show();
            return false;
        }
        if (getServiceState() == State.PLAYING) {
            Log.i(LOG_TAG, "Still carrying out voice command.");
            Toast.makeText(this, R.string.still_playing, Toast.LENGTH_SHORT)
            .show();
            return false;
        }
        Log.i(LOG_TAG, "Started recording..");
        Toast.makeText(this, R.string.started_recording_message, Toast.LENGTH_SHORT)
        .show();
        setServiceState(State.RECORDING);
        mDemonstration = new Demonstration(rootNodeInfo);
        return true;
    }

    /**
     * Checks if a recording is in session now.
     * @return {@code true} if currently recording, {@code false} if not. 
     */
    public boolean isRecording() {
        return getServiceState() == State.RECORDING;
    }

    /**
     * Ends recording, starts saving. Prompts the user for saving information.
     */
    public void endRecording() {
        Log.i(LOG_TAG, "Ended recording.");
        setServiceState(State.SAVING);
        promptForName();
        return;
    }

    /**
     * Starts listening for a voice command and carries it out.
     */
    public void listenForCommand() {
        disableKeyguard();
        if (SpeechRecognizer.isRecognitionAvailable(getApplicationContext())) {
            setServiceState(State.PLAYING);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            /* Uses WEB_SEARCH for voice recognition which favors keywords
             * The alternative is FREE_FORM which is for dictation */
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, 
                    getClass().getPackage().getName());
            mRecognizer.startListening(intent);
            Toast.makeText(this, R.string.listening_for_command, Toast.LENGTH_SHORT)
            .show();
            Log.i(LOG_TAG, "Listening for command..");
        } else {
            Toast.makeText(this, R.string.no_speech_recognizer, Toast.LENGTH_SHORT)
            .show();
            Log.e(LOG_TAG, "No speech recognizer!");
        }
    }
    
    /**
     * Checks if the service is speaking right now.
     * @return {@code true} if currently speaking, {@code false} if not.
     */
    public boolean isSpeaking() {
        return mPlayback.isSpeaking();
    }

    /**
     * Stops speaking.
     */
    public void stopSpeaking() {
        mPlayback.stopSpeaking();
    }

    protected static void reloadDemonstrationSet() {
        // OK to silently fail, turning on service will actually load set anyway
        VoicifyService voicifyService = getStaticInstance();
        if (voicifyService != null) {
            voicifyService.loadDemonstrationSet();
        } else {
            Log.e(LOG_TAG, "VoicifyService not initialized!");
        }
    }
    
    /**
     * Makes sure that Voicify is the latest registered service 
     * for MEDIA_BUTTON events.
     */
    protected static void registerForHeadsetHook() {
        /* just registering the new way */
        registerButton();
    }

    /**
     * Unregisters from receiving MEDIA_BUTTON events.
     */
    protected static void unregisterForHeadsetHook() {
        unregisterButton();
    }

    /**
     * Looks up views under the selection and adds them to Demonstration
     * Returns to saving dialog
     * @param rect User's output selection
     */
    protected void onSelectionOverlayResult(Rect rect) {
        mSelectionOverlay.hide();
        mSelectionOverlay = null;
        AccessibilityNodeInfo currentRootNodeInfo = Playback.getRootNodeInfo(true);
        if (currentRootNodeInfo == null) {
            Log.e(LOG_TAG, "No current root node, can't add OutputSelection");
            Toast.makeText(this, R.string.failed_to_add_output_selection, 
                    Toast.LENGTH_SHORT).show();
            
        } else {
            mDemonstration.addOutputSelection(
                    new OutputSelection(rect));
            currentRootNodeInfo.recycle();
        }
        promptForName();
    }
    
    /**
     * Gets the static instance of {@link VoicifyService}.
     * @return A {@link VoicifyService}, or {@code null} if not initialized.
     */
    private static VoicifyService getStaticInstance() {
        if (sVoicifyService == null) {
            Log.e(LOG_TAG, "VoicifyService not initialized!");
        }
        return sVoicifyService;
    }

    /**
     * Registers the media button in the currently recommended way.
     * It needs to have the broadcast receiver declared in the manifest as well.
     * Since it just takes a component name, the {@link ButtonReceiver} object
     * should have its fields as static and already initialized.
     */
    private static void registerButton() {
        if (sAudioManager != null && sRemoteControlResponder != null) {
            sAudioManager.registerMediaButtonEventReceiver(sRemoteControlResponder);
        } else {
            Log.e(LOG_TAG, "Static fields not initialized!");
        }
    }

    /**
     * Registers the media button receiver the old way for legacy support.
     */
    @SuppressWarnings("unused")
    private static void registerReceiver() {
        IntentFilter mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        mediaFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        VoicifyService voicifyService = getStaticInstance();
        if (voicifyService != null) {
            voicifyService.registerReceiver(sButtonReceiver, mediaFilter);
        } else {
            Log.e(LOG_TAG, "VoicifyService not initialized!");
        }
    }

    /**
     * Unregisters the media button receiver for when the service is stopped.
     */
    private static void unregisterButton() {
        sAudioManager.unregisterMediaButtonEventReceiver(sRemoteControlResponder);
    }

    /**
     * Unregisters the media button receiver for when the service is stopped.
     */
    @SuppressWarnings("unused")
    private static void unregisterReceiver() {
        VoicifyService voicifyService = getStaticInstance();
        if (voicifyService != null) {
            voicifyService.unregisterReceiver(sButtonReceiver);
        } else {
            Log.e(LOG_TAG, "VoicifyService not initialized!");
        }
    }

    private void setServiceState(State newState) {
        synchronized (this) {
            mServiceState = newState;
        }
    }

    private State getServiceState() {
        synchronized (this) {
            return mServiceState;
        }
    }

    /**
     * Prompts the user for a name and {@link OutputSelection}s for the voice command.
     */
    private void promptForName() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle(R.string.saving_voice_command);
        alertBuilder.setMessage(R.string.please_type_command);
        final EditText input = new EditText(this);
        alertBuilder.setView(input);

        alertBuilder.setPositiveButton(R.string.save, 
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                CharSequence value = input.getText().toString();
                onNamePromptReturn(value);
                dialog.dismiss();
            }
        });

        alertBuilder.setNegativeButton(R.string.cancel, 
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                setServiceState(State.IDLE);
                clearDemonstration();
                dialog.cancel();
            }
        });

        alertBuilder.setNeutralButton(R.string.select_output_area, 
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                displayOutputSelectionOverlay();
            }
        });

        AlertDialog alert = alertBuilder.create();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alert.show();
    }

    private void onNamePromptReturn(CharSequence commandName) {
        Log.d(LOG_TAG, "Saving command with name: " + commandName);
        if (mDemonstration == null) {
            Log.e(LOG_TAG, "No demonstration!");
            return;
        }
        mDemonstration.setCommand(commandName); 
        addDemonstrationToSet();
        saveDemonstrationSet();
        setServiceState(State.IDLE);
    }
    
    /** 
     * Inserts full screen invisible overlay to select output fields
     * touch_up notes down views under rectangle in the demonstration
     * puts up the dialog again
     */
    private void displayOutputSelectionOverlay() {
        mSelectionOverlay = new SelectionOverlay(this);
        mSelectionOverlay.show();
    }
    
    private void addDemonstrationToSet() {
        Log.d(LOG_TAG, mDemonstration.toString());
        mDemonstrationSet.add(mDemonstration);
        clearDemonstration();
    }
    
    private void clearDemonstration() {
        mDemonstration = null;
    }

    private void saveDemonstrationSet() {
        Log.d(LOG_TAG, "Trying to save the demonstration set..");
        try {
            FileOutputStream fos = openFileOutput(DEMONSTRATION_SET_FILENAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(mDemonstrationSet);
            oos.flush();
            oos.close();
            Log.i(LOG_TAG, "DemonstrationSet saved.");
            Log.i(LOG_TAG, mDemonstrationSet.toString());
            Toast.makeText(this, 
                    R.string.demonstration_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Saving failed! - " + e.getMessage());
            Toast.makeText(this, 
                    R.string.saving_failed, Toast.LENGTH_SHORT).show();
        }    
    }

    private void loadDemonstrationSet() {
        Log.d(LOG_TAG, "Trying to load the DemonstrationSet..");
        boolean loadedDemonstrationSet = false;

        try {
            FileInputStream fis = openFileInput(DEMONSTRATION_SET_FILENAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            mDemonstrationSet = (DemonstrationSet) ois.readObject();
            ois.close();
            loadedDemonstrationSet = true;
            Log.d(LOG_TAG, "DemonstrationSet loaded.");
            Log.i(LOG_TAG, mDemonstrationSet.toString());
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }    

        if (!loadedDemonstrationSet) {
            Log.i(LOG_TAG, "No archive found, creating new demonstration set.");
            mDemonstrationSet = new DemonstrationSet();
        }
    }

    private void putNotification() {
        Notification.Builder nBuilder = 
                new Notification.Builder(getApplicationContext());

        Intent notificationIntent = new Intent(this, VoicifyActivity.class);
        PendingIntent contentIntent = 
                PendingIntent.getActivity(this, 0, notificationIntent, 0);
        nBuilder.setContentIntent(contentIntent);

        CharSequence contentText = getText(R.string.touch_for_settings);
        nBuilder.setContentText(contentText);

        CharSequence contentTitle = getText(R.string.app_name);
        nBuilder.setContentTitle(contentTitle);
        nBuilder.setSmallIcon(android.R.drawable.ic_btn_speak_now);
        nBuilder.setOngoing(true);

        CharSequence tickerText = getText(R.string.voicify_activated);
        nBuilder.setTicker(tickerText);

        Notification notification = nBuilder.getNotification();
        mNotificationManager.notify(VOICIFY_NOTIFICATION_ID, notification);
    }

    private final RecognitionListener mSpeechListener = new RecognitionListener() {
        @Override
        public void onResults(Bundle results) {
            String result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0);
            onListeningResults(true, result);
        }
        @Override
        public void onError(int error) { 
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    Log.e(LOG_TAG, "Audio recoding error.");
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    Log.e(LOG_TAG, "Other client side errors.");
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    Log.e(LOG_TAG, "Insufficient permissions");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    Log.e(LOG_TAG, "Other network related errors.");
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    Log.e(LOG_TAG, "Network operation timed out.");
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    Log.e(LOG_TAG, "No recognition result matched.");
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    Log.e(LOG_TAG, "RecognitionService busy.");
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    Log.e(LOG_TAG, "Server sends error status.");
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    Log.e(LOG_TAG, "No speech input");
                    break;
                default:
                    Log.e(LOG_TAG, "Unknown error.");
                    break;
            }
            onListeningResults(false, "");
        }
        @Override
        public void onBeginningOfSpeech() { }
        @Override
        public void onBufferReceived(byte[] buffer) { }
        @Override
        public void onEndOfSpeech() { }
        @Override
        public void onEvent(int eventType, Bundle params) { }
        @Override
        public void onPartialResults(Bundle partialResults) { }
        @Override
        public void onReadyForSpeech(Bundle params) { }
        @Override
        public void onRmsChanged(float rmsdB) { }
    };

    private void onListeningResults(boolean successful, String result) {
        if (successful) {
            Log.i(LOG_TAG, "Heard [" + result + "]");
            onReceivedVoiceCommand(result);
        } else {
            setServiceState(State.IDLE);
            Log.e(LOG_TAG, "Speech recognition failed.");
            Toast.makeText(this, R.string.speech_recognition_failed, Toast.LENGTH_SHORT)
            .show();
            reenableKeyguard();
        }
    }

    private void onReceivedVoiceCommand(String command) {
        AccessibilityNodeInfo rootNodeInfo = Playback.getRootNodeInfo(false);
        if (mDemonstrationSet == null 
                || rootNodeInfo == null) {
            Log.e(LOG_TAG, "DemonstrationSet or rootNodeInfo not there!");
            Toast.makeText(this, R.string.not_initiated, Toast.LENGTH_SHORT).show();
            setServiceState(State.IDLE);
            reenableKeyguard();
            return;
        }

        Demonstration selectedDemo = mDemonstrationSet.findDemonstrationForPackage(
                command, rootNodeInfo.getPackageName());
        if (selectedDemo != null && 
                selectedDemo.getMatchDistance() <= COMMAND_DISTANCE_THRESHOLD) {
            Toast.makeText(this, 
                    "Heard [" + command + "] - Doing demo [" + 
                            selectedDemo.getCommand() + "]", Toast.LENGTH_SHORT).show();
            Log.i(LOG_TAG, "Doing demo for [" + selectedDemo.getCommand() + "]");
            playDemonstrationThreaded(selectedDemo);
        } else {
            setServiceState(State.IDLE);
            Toast.makeText(this, 
                    "No good match for command [" + command + "]", Toast.LENGTH_SHORT).show();
            Log.i(LOG_TAG, "No good match for command [" + command + "]");
            reenableKeyguard();
        }
    }
    
    /**
     * Runs the given {@code Demonstration} in its own thread.
     * Playback needs to be on a separate thread so we can still get 
     * accessibility events and update the current window
     * during playback, even if we are waiting for UI.
     * 
     * @param selectedDemo The {@code Demonstration} object to be played
     */
    private void playDemonstrationThreaded(final Demonstration selectedDemo) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                mPlayback.playDemonstration(selectedDemo);
            }
        };
        thread.start();
    }

    private void onDonePlayback(boolean success) {
        if (success) {
            Log.i(LOG_TAG, "Demonstration ended successfully.");
            Toast.makeText(this, R.string.demonstration_ended_successfully, 
                    Toast.LENGTH_SHORT).show();
        } else {
            Log.e(LOG_TAG, "Parts of demonstration might be aborted.");
            Toast.makeText(this, R.string.demonstration_ended_with_issues, 
                    Toast.LENGTH_SHORT).show();
        }
        setServiceState(State.IDLE);
        reenableKeyguard();
    }
    
    /**
     * Handler object to receive results from helper classes
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            onDonePlayback(msg.arg1 == Playback.SUCCESS);
        }
    };
    
    private void disableKeyguard() {
        mWakeLock.acquire(100);
        mKeyguardLock.disableKeyguard();
    }
    
    private void reenableKeyguard() {
        mKeyguardLock.reenableKeyguard();
    }
    
    private static boolean isUniqueEvent(AccessibilityEvent event) {
        return (sLastActionEvent == -1) || 
                (event.getEventTime() - sLastActionEvent > MIN_HUMAN_ACTION_DELAY);
    }
    
    private static class SoftButtonOverlay extends SimpleOverlay {
        private final ImageView mStatus;

        public SoftButtonOverlay(Context context) {
            super(context);

            final LayoutParams params = getParams();
            params.width = LayoutParams.WRAP_CONTENT;
            params.height = LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER | Gravity.TOP;
            params.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
            setParams(params);

            mStatus = new ImageView(context);
            mStatus.setImageResource(android.R.drawable.ic_btn_speak_now);
            mStatus.setColorFilter(Color.RED);

            setContentView(mStatus);
        }
    }
}
