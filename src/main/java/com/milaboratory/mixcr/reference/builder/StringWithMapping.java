/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.reference.builder;

/**
 * Created by dbolotin on 25/10/15.
 */
public final class StringWithMapping {
    public final int[] originalToModifiedMapping;
    public final String modifiedString;

    private StringWithMapping(int[] originalToModifiedMapping, String modifiedString) {
        this.originalToModifiedMapping = originalToModifiedMapping;
        this.modifiedString = modifiedString;
    }

    public static StringWithMapping removeSymbol(String originalString, char charToRemove) {
        int modifiedStringLength = 0;
        for (int i = 0; i < originalString.length(); i++)
            if (originalString.charAt(i) != charToRemove)
                ++modifiedStringLength;
        char[] modifiedString = new char[modifiedStringLength];
        int[] mapping = new int[originalString.length()];
        int j = 0;
        for (int i = 0; i < originalString.length(); i++)
            if (originalString.charAt(i) != charToRemove) {
                mapping[i] = j;
                modifiedString[j++] = originalString.charAt(i);
            } else {
                mapping[i] = -1;
            }
        return new StringWithMapping(mapping, new String(modifiedString));
    }
}
