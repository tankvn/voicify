/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.utils;

import com.google.android.marvin.voicify.SimpleOverlay;
import com.google.android.marvin.voicify.VoicifyService;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.inputmethodservice.AbstractInputMethodService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ImageView;

/**
 * When set as the default input method, allows the screen reader to send key
 * events without the INJECT_EVENTS permission. Use the following code in your
 * manifest:
 * <pre class="prettyprint">
 * &lt;service
 *     android:name=".ProxyInputMethod"
 *     android:permission="android.permission.BIND_INPUT_METHOD" &gt;
 *     &lt;intent-filter &gt;
 *         &lt;action android:name="android.view.InputMethod" /&gt;
 *     &lt;/intent-filter&gt;
 *
 *     &lt;meta-data
 *         android:name="android.view.im"
 *         android:resource="@xml/method" /&gt;
 * &lt;/service&gt;
 * </pre>
 * <p>
 * Use the following code in your res/xml/method.xml:
 * <pre class="prettyprint">
 * &lt;input-method xmlns:android="http://schemas.android.com/apk/res/android"&gt;
 *     &lt;subtype
 *         android:icon="@drawable/icon"
 *         android:label="@string/app_name" /&gt;
 * &lt;/input-method&gt;
 * </pre>
 * <p>
 * To send a key event through the proxy service, use the following code:
 * <pre class="prettyprint">
 * boolean success = ProxyInputMethodService.sendKeyEvent(event);
 * </pre>
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ProxyInputMethodService extends AbstractInputMethodService {
    private static ProxyInputMethodService sProxyInputMethodService;
    
    public static boolean sendKeyEvent(KeyEvent event) {
        final ProxyInputMethodService proxyInputMethodService = sProxyInputMethodService;
        
        if (proxyInputMethodService == null) {
            Log.e(VoicifyService.LOG_TAG, "Input method service is null!");
            return false;
        }
        
        final InputConnection inputConnection = proxyInputMethodService.mInputConnection;
        
        if (inputConnection == null) {
            Log.e(VoicifyService.LOG_TAG, "Input connection is null!");
            return false;
        }
        
        Log.v(VoicifyService.LOG_TAG, "Sending key event!");
        
        return inputConnection.sendKeyEvent(event);
    }

    private InputConnection mInputConnection;
    private ProxyInputOverlay mOverlay;

    @Override
    public void onCreate() {
        super.onCreate();

        mOverlay = new ProxyInputOverlay(this);
        mOverlay.show();
        
        sProxyInputMethodService = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mOverlay.hide();
        
        sProxyInputMethodService = null;
    }

    /**
     * Concrete implementation of
     * {@link android.inputmethodservice.AbstractInputMethodService.AbstractInputMethodImpl}
     * that provides all of the standard behavior for an input method.
     */
    public class InputMethodImpl extends AbstractInputMethodImpl {
        @Override
        public void attachToken(IBinder token) {
            // Do nothing.
        }

        /**
         * Handle a new input binding, calling
         * {@code InputMethodService.onBindInput()} when done.
         */
        @Override
        public void bindInput(InputBinding binding) {
            mInputConnection = binding.getConnection();
            onBindInput();
        }

        /**
         * Clear the current input binding.
         */
        @Override
        public void unbindInput() {
            onUnbindInput();
            mInputConnection = null;
        }

        @Override
        public void startInput(InputConnection ic, EditorInfo attribute) {
            // Do nothing.
        }

        @Override
        public void restartInput(InputConnection ic, EditorInfo attribute) {
            // Do nothing.
        }

        @Override
        public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            // Do nothing.
        }

        @Override
        public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            // Do nothing.
        }

        @Override
        public void changeInputMethodSubtype(InputMethodSubtype subtype) {
            // Do nothing.
        }
    }

    /**
     * Concrete implementation of
     * {@link android.inputmethodservice.AbstractInputMethodService.AbstractInputMethodSessionImpl}
     * that provides all of the standard behavior for an input method session.
     */
    public class InputMethodSessionImpl extends AbstractInputMethodSessionImpl {
        @Override
        public void finishInput() {
            // Do nothing.
        }

        @Override
        public void displayCompletions(CompletionInfo[] completions) {
            // Do nothing.
        }

        @Override
        public void updateExtractedText(int token, ExtractedText text) {
            // Do nothing.
        }

        @Override
        public void updateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                int candidatesStart, int candidatesEnd) {
            // Do nothing.
        }

        @Override
        public void viewClicked(boolean focusChanged) {
            // Do nothing.
        }

        @Override
        public void updateCursor(Rect newCursor) {
            // Do nothing.
        }

        @Override
        public void appPrivateCommand(String action, Bundle data) {
            // Do nothing.
        }

        @Override
        public void toggleSoftInput(int showFlags, int hideFlags) {
            // Do nothing.
        }
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return new InputMethodImpl();
    }

    @Override
    public AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface() {
        return new InputMethodSessionImpl();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    public void onBindInput() {
        mOverlay.onBindInput();
    }

    public void onUnbindInput() {
        mOverlay.onUnbindInput();
    }

    private static class ProxyInputOverlay extends SimpleOverlay {
        private final ImageView mStatus;

        public ProxyInputOverlay(Context context) {
            super(context);

            final LayoutParams params = getParams();
            params.width = LayoutParams.WRAP_CONTENT;
            params.height = LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.RIGHT | Gravity.BOTTOM;
            params.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
            params.flags |= LayoutParams.FLAG_NOT_TOUCHABLE;
            setParams(params);

            mStatus = new ImageView(context);
            mStatus.setImageResource(android.R.drawable.ic_popup_sync);
            mStatus.setColorFilter(Color.RED);

            setContentView(mStatus);
        }

        public void onBindInput() {
            mStatus.setColorFilter(Color.GREEN);
        }

        public void onUnbindInput() {
            mStatus.setColorFilter(Color.RED);
        }
    }
}
