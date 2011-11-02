// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

import android.util.Log;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An object to store package names and the {@link Demonstration} objects 
 * available in them.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class DemonstrationSet implements Serializable {

    private static final String LOG_TAG = "VoicifyService";
    
    private static final int INITIAL_MAP_SIZE = 8;

    /**
     * The parent map has the following structure.
     * Keys: Names of packages
     * Values: Maps of demonstrations available for that package
     * 
     * The values of the parent map are maps with the following structure.
     * Keys: Voice commands
     * Values: Demonstrations
     */
    private Map<CharSequence, Map<CharSequence, Demonstration>> mDemonstrations;

    /**
     * Creates an empty {@link DemonstrationSet}.
     */
    public DemonstrationSet() {
        mDemonstrations = 
                new HashMap<CharSequence, Map<CharSequence, Demonstration>>(
                        INITIAL_MAP_SIZE);
    }

    /**
     * Adds the given {@link Demonstration} object to the {@link DemonstrationSet}.
     * 
     * @param demonstration The {@link Demonstration} object to add.
     */
    public void add(Demonstration demonstration) {
        Map<CharSequence, Demonstration> packageDemos;
        if (mDemonstrations.containsKey(demonstration.getPackageName())) {
            packageDemos = 
                    mDemonstrations.get(demonstration.getPackageName());
        } else {
            packageDemos = new HashMap<CharSequence, Demonstration>(
                    INITIAL_MAP_SIZE);
        }
        
        Demonstration oldValue = 
                packageDemos.put(demonstration.getCommand(), demonstration);

        if (oldValue != null) {
            Log.i(LOG_TAG, 
                    "Replaced old command - " + oldValue.getCommand());
        }
        mDemonstrations.put(demonstration.getPackageName(), packageDemos);
    }

    /**
     * Looks for a {@code Demonstration} object with the closest matching 
     * voice command phrase recorded in the package with the given name.
     * 
     * @param command The voice command phrase to search for.
     * @param packageName The name of the package the {@link Demonstration} 
     * was recorded in.
     * @return closest matching {@code Demonstration} object with the match distance 
     * field set to the distance to the given voice command phrase or null if 
     * {@link DemonstrationSet} is empty.
     */
    public Demonstration findDemonstrationForPackage(String command, 
            CharSequence packageName) {
        if (mDemonstrations.containsKey(packageName)){
            return findClosestDemonstration(command, 
                    mDemonstrations.get(packageName));
        } else {
            return null;
        }
    }
    
    /**
     * Clears all data in the {@link DemonstrationSet}.
     */
    public void clear() {
        mDemonstrations.clear();
    }
    
    @Override
    public String toString() {
        StringBuilder toReturn = new StringBuilder("DemonstrationSet - " 
                + mDemonstrations.size() + " package(s)\n");
        Set<CharSequence> packageMaps = mDemonstrations.keySet();
        for (CharSequence packageName : packageMaps) {
            toReturn.append("Package name: " + packageName + "\n");
            toReturn.append(mDemonstrations.get(packageName).values().toString());
        }
        return toReturn.toString();
    }

    /**
     * Looks for the {@code Demonstration} object with the closest matching 
     * voice command phrase in the given map of voice commands to 
     * {@link Demonstration} objects.
     * 
     * @param command The voice command phrase to search for.
     * @param demonstrationMap The map of commands to {@link Demonstration} objects.
     * @return The {@code Demonstration} object with the closest command phrase, 
     *            null if no {@code Demonstration} objects in 
     *            {@code DemonstrationSet}.
     */
    private Demonstration findClosestDemonstration(String command, 
            Map<CharSequence, Demonstration> demonstrationMap) {
        Set<CharSequence> encodedKeys = demonstrationMap.keySet();
        int lowestDistance = -1;
        CharSequence closestMatch = null;

        for (CharSequence current : encodedKeys) {
            int distance = EditDistance.computeEditDistance(command, current);
            if (lowestDistance == -1 || distance < lowestDistance) {
                lowestDistance = distance;
                closestMatch = current;
            }
        }

        if (closestMatch == null) {
            return null;
        } else {
            Demonstration toReturn = demonstrationMap.get(closestMatch);
            toReturn.setMatchDistance(lowestDistance);
            return toReturn;
        }
    }
}
