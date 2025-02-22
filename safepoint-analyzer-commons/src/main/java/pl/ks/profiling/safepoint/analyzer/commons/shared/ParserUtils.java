/*
 * Copyright 2020 Krzysztof Slusarski, Artur Owczarek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.ks.profiling.safepoint.analyzer.commons.shared;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class ParserUtils {
    public BigDecimal getTimeStamp(String line) {
        // JDK 8 - timestamp au format yyyy-mm-ddThh:mm:ss.SSS+0100: 2591798.694: (derniere partie big decimal)
        if (line.indexOf(0) == '[') {  // JDK 9 - timestamp au format yyyy-mm-ddThh:mm:ss.SSS+0100: [2019-06-25T15:06:01.000+0100]
            return new BigDecimal(denationalizeFloatString(getContentBetweenMarkers(line, "[", "s]")));
        } else {
            return new BigDecimal(denationalizeFloatString(getContentFromJdk8(line)));
        }
    }

    private static String getContentBetweenMarkers(String line, String startMarker, String endMarker) {
        int endMarkerIndex = line.indexOf(endMarker);
        String lineUntilEndMarker = line.substring(0, endMarkerIndex);
        int startMarkerIndex = lineUntilEndMarker.lastIndexOf(startMarker);
        return line.substring(startMarkerIndex + 1, endMarkerIndex);
    }

    private static String getContentFromJdk8(String line) {
        // tokenize with ": " separator the line
        String[] tokens = line.split(": ");
        // Si il n'y a pas 2 token, c'est une ligne informative
        if (tokens.length < 2) {
            return "0.0";
        }
        return tokens[1];
    }

    private static String denationalizeFloatString(String floatString) {
        return floatString.replace(",", ".");
    }

    public long parseFirstNumber(String line, int pos) {
        boolean started = false;
        long value = 0;
        for (int i = pos; i < line.length(); i++) {
            if (Character.isDigit(line.charAt(i))) {
                started = true;
                value *= 10;
                value += line.charAt(i) - '0';
            } else {
                if (started) {
                    return value;
                }
            }
        }
        return 0;
    }

    public static BigDecimal parseFirstBigDecimal(String line, int pos) {
        boolean started = false;
        boolean dot = false;

        long value = 0;
        long afterDot = 0;
        long afterDotDivisionBy = 1;

        for (int i = pos; i < line.length(); i++) {
            if (Character.isDigit(line.charAt(i))) {
                started = true;
                if (dot) {
                    afterDot *= 10;
                    afterDot += line.charAt(i) - '0';
                    afterDotDivisionBy *= 10;
                } else {
                    value *= 10;
                    value += line.charAt(i) - '0';
                }
            } else {
                if (line.charAt(i) == '.' || line.charAt(i) == ',') {
                    dot = true;
                } else if (started) {
                    BigDecimal ret = new BigDecimal(afterDot).divide(new BigDecimal(afterDotDivisionBy), 2, RoundingMode.HALF_DOWN);
                    return ret.add(new BigDecimal(value));
                }
            }
        }
        if (started) {
            BigDecimal ret = new BigDecimal(afterDot).divide(new BigDecimal(afterDotDivisionBy), 2, RoundingMode.HALF_DOWN);
            return ret.add(new BigDecimal(value));
        } else {
            return BigDecimal.ZERO;
        }
    }

    public String parseFirstHexadecimalNumber(String line, int pos) {
        int beginning = line.indexOf("0x", pos);
        int end = indexOfFirstNonHexadecimalCharacter(line, beginning + 2);
        return line.substring(beginning, end);
    }

    private int indexOfFirstNonHexadecimalCharacter(String line, int pos) {
        int nonHexadecimalPos = pos;
        while (isHexadecimalCharacter(line, nonHexadecimalPos)) {
            nonHexadecimalPos++;
        }
        return nonHexadecimalPos;
    }

    private boolean isHexadecimalCharacter(String line, int pos) {
        char character = line.charAt(pos);
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F');
    }
}
