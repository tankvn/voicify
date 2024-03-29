/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils;

import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

/**
 * This activity waits for the IME to load, then finishes. Use the following
 * code in your application's manifest:
 * <pre class="prettyprint">
 * &lt;activity
 *     android:name=".ProxyInputMethodActivity"
 *     android:theme="@android:style/Theme.NoDisplay"
 *     android:windowSoftInputMode="stateAlwaysVisible|adjustNothing"
 *     android:exported="false" /&gt;
 * </pre>
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ProxyInputMethodActivity extends Activity {
    private InputMethodManager mInputManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            mInputManager.showSoftInput(null, InputMethodManager.SHOW_FORCED);
            finish();
        }
    }
}
