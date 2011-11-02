// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.voicify;

/**
 * Helper class to calculate edit distance between two strings.
 * Used to compare a given voice command phrase to the ones in 
 * a {@code DemonstrationSet}.
 * 
 * @author ardakara@google.com (Arda Kara)
 *
 */
public class EditDistance {

    /**
     * Returns the case-insensitive edit distance between the given strings.
     * 
     * @param str1Raw The first string.
     * @param str2Raw The second string.
     * @return The edit distance value.
     */
    public static int computeEditDistance(CharSequence str1Raw, 
            CharSequence str2Raw) {
        
        CharSequence str1 = str1Raw.toString().trim().toLowerCase();
        CharSequence str2 = str2Raw.toString().trim().toLowerCase();
        
        int str1Length = str1.length();
        int str2Length = str2.length();
        
        int[][] dist = new int[str1Length + 1][str2Length + 1];
        
        for (int i = 0; i <= str1Length; i++) {
            dist[i][0] = i;
        }
        for (int j = 0; j <= str2Length; j++) {
            dist[0][j] = j;
        }
        
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                dist[i][j] = minimum(
                        dist[i - 1][j] + 1,
                        dist[i][j - 1] + 1,
                        dist[i - 1][j - 1] 
                                + ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));
            }
        }
        
        return dist[str1.length()][str2.length()];
    }

    private static int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }
}
