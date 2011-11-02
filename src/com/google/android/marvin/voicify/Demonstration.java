// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a voice command, the associated {@link PlaybackAction} objects 
 * and the {@link OutputSelection} objects.
 * 
 * Can be stored in a {@link DemonstrationSet} for look-up.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class Demonstration implements Serializable {
    
    /**
     * Constant to indicate that the match distance field is not currently used.
     */
    private static final int NO_MATCH = 999;

    /**
     * List of actions to carry out for this voice command.
     */
    private List<PlaybackAction> mPlaybackActions;

    /**
     * The voice command phrase.
     */
    private CharSequence mCommand;

    /**
     * Field used to indicate how good the match is with a given voice command
     * when returning a demonstration from a demonstration set. 
     * Set by the Demonstration Set upon a search for a command.
     */
    private int mMatchDistance;
    
    /**
     * List of objects that represent fields to be read out loud after playback.
     */
    private List<OutputSelection> mOutputSelections;
    
    /**
     * The package name of the root node when the demonstration was recorded.
     */
    private CharSequence mPackageName;

    /**
     * Create a new {@link Demonstration} for the context of the given root node.
     * 
     * @param rootNode The {@link AccessibilityNodeInfo} of the active window 
     * for which this demonstration is recorded.
     */
    public Demonstration(AccessibilityNodeInfo rootNode) {
        mPlaybackActions = new ArrayList<PlaybackAction>();
        mCommand = "";
        mMatchDistance = NO_MATCH;
        mOutputSelections = new ArrayList<OutputSelection>();
        mPackageName = rootNode.getPackageName();
    }

    /**
     * Given an {@link AccessibilityEvent}, adds the necessary 
     * {@link PlaybackAction} objects to the {@link DemonstrationSet}.
     * 
     * @param event The {@link AccessibilityEvent} object received from the user 
     * interaction while recording.
     */
    public boolean addEvent(AccessibilityEvent event) {
        PlaybackAction playbackAction = PlaybackAction.obtain(event);
        if (playbackAction == null) {
            return false;
        } else {
            mPlaybackActions.add(playbackAction);
            return true;
        }
    }

    /**
     * Returns the {@link PlaybackAction}s for this {@link Demonstration}.
     * 
     * @return A list of {@link PlaybackAction} objects.
     */
    public List<PlaybackAction> getActions() {
        return mPlaybackActions;
    }

    /**
     * Sets the voice command phrase for this {@link Demonstration}.
     * 
     * @param command The voice command phrase.
     */
    public void setCommand(CharSequence command) {
        mCommand = command;
    }

    /**
     * Gets the voice command phrase for this {@link Demonstration}.
     * 
     * @return The voice command phrase.
     */
    public CharSequence getCommand() {
        return mCommand;
    }

    /**
     * Sets the match distance for this {@link Demonstration}.
     * 
     * Used when returning a {@link Demonstration} from a {@link DemonstrationSet}.
     * 
     * @param distance An integer representing the distance from the command 
     * that was searched for in the {@link DemonstrationSet}.
     */
    public void setMatchDistance(int distance) {
        mMatchDistance = distance;
    }

    /**
     * Gets the match distance for this {@link Demonstration}.
     * 
     * Match distance is usually set when returning a {@link Demonstration} 
     * from a {@link DemonstrationSet}.
     * 
     * @return An integer representing the distance from the command 
     * that was searched for in the {@link DemonstrationSet}.
     */
    public int getMatchDistance () {
        return mMatchDistance;
    }
    
    /**
     * Adds an {@link OutputSelection} to this {@link Demonstration}.
     * 
     * @param outputSelection The {@link OutputSelection} object to add.
     */
    public void addOutputSelection(OutputSelection outputSelection) {
        mOutputSelections.add(outputSelection);
    }
    
    /**
     * Gets the {@link OutputSelection}s of this {@link Demonstration}.
     * 
     * @return A list of {@link OutputSelection} objects.
     */
    public List<OutputSelection> getOutputSelections () {
        return mOutputSelections;
    }
    
    /**
     * Gets the name of the package this {@link Demonstration} was recorded for.
     * 
     * @return The package name.
     */
    public CharSequence getPackageName() {
        return mPackageName;
    }

    @Override
    public String toString() {
        return "Demonstration - "
                + mCommand
                + " : [" + mPlaybackActions.toString() + "]\n";
    }
}
