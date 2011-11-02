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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

/**
 * Handles showing the input method selection and settings dialogs.
 * <p>
 * Use the following code in your onCreate():
 * <pre class="prettyprint">
 * mInputMethodMonitor = new ProxyInputMethodMonitor(this, ProxyInputMethodService.class);
 * mInputMethodMonitor.registerObserver();
 * mInputMethodMonitor.checkInputMethod();
 * </pre>
 * <p>
 * Use the following code in your onDestroy():
 * <pre class="prettyprint">
 * mInputMethodMonitor.unregisterObserver();
 * </pre>
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ProxyInputMethodMonitor {
    private static enum InputMethodState {
        ENABLED, DEFAULT, DISABLED
    }

    private final Context mContext;
    private final Class<?> mTargetClass;
    private final ContentResolver mContentResolver;
    private final SettingsContentObserver mContentObserver;

    public ProxyInputMethodMonitor(Context context, Class<?> targetClass) {
        mContext = context;
        mTargetClass = targetClass;
        mContentResolver = context.getContentResolver();
        mContentObserver = new SettingsContentObserver(new Handler());
    }

    public void registerObserver() {
        final Uri enabledUri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS);
        final Uri defaultUri = Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD);

        mContentResolver.registerContentObserver(enabledUri, false, mContentObserver);
        mContentResolver.registerContentObserver(defaultUri, false, mContentObserver);
    }

    public void unregisterObserver() {
        mContentResolver.unregisterContentObserver(mContentObserver);
    }

    /**
     * Checks the default input method and, if set to the target IME, forces the
     * input method editor open.
     */
    public void checkInputMethod() {
        final InputMethodState state = getInputMethodState(mTargetClass);

        switch (state) {
            case DISABLED:
                mContext.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case ENABLED:
                ((InputMethodManager) mContext
                        .getSystemService(ProxyInputMethodService.INPUT_METHOD_SERVICE))
                        .showInputMethodPicker();
                break;
            case DEFAULT:
             // XXX(ardakara): Was causing ANR before
//                mContext.startActivity(new Intent(mContext, ProxyInputMethodActivity.class)
//                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
        }
    }

    /**
     * Returns a code indicating the state of the IME.
     * 
     * @param imeClass The IME class to check.
     * @return {@code 2} if the specified IME is set as the default IME,
     *         {@code 1} if the IME is enabled but not set as default, or
     *         {@code 0} if the IME is disabled
     */
    private InputMethodState getInputMethodState(Class<?> imeClass) {
        final String targetImePackage = imeClass.getPackage().getName();
        final String targetImeClass = imeClass.getSimpleName();
        final String defaultImeId = Settings.Secure.getString(mContentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD);

        if (defaultImeId.contains(targetImePackage) && defaultImeId.contains(targetImeClass)) {
            return InputMethodState.DEFAULT;
        }

        final String enabledImeId = Settings.Secure.getString(mContentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS);

        if (enabledImeId.contains(targetImePackage) && enabledImeId.contains(targetImeClass)) {
            return InputMethodState.ENABLED;
        }

        return InputMethodState.DISABLED;
    }

    /**
     * Checks the current input method whenever the enabled or default input
     * method settings change.
     * 
     * @author alanv@google.com (Alan Viverette)
     */
    private class SettingsContentObserver extends ContentObserver {
        public SettingsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (!selfChange) {
                checkInputMethod();
            }
        }
    }
}
