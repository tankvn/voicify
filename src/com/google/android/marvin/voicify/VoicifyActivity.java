// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * The settings application for {@link VoicifyService}.
 * 
 * @author ardakara@google.com (Arda Kara)
 */
public class VoicifyActivity extends Activity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete:
                deleteFile(VoicifyService.DEMONSTRATION_SET_FILENAME);
                VoicifyService.reloadDemonstrationSet();
                Toast.makeText(this, R.string.demonstration_set_cleared, Toast.LENGTH_SHORT)
                    .show();
                return true;
            case R.id.menu_reclaim_focus:
                VoicifyService.registerForHeadsetHook();
                Toast.makeText(this, R.string.reclaimed_button_focus, Toast.LENGTH_SHORT)
                    .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}