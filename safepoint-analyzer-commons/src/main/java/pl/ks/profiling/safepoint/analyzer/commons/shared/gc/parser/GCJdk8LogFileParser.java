/*
 * Copyright 2020 Krzysztof Slusarski
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
package pl.ks.profiling.safepoint.analyzer.commons.shared.gc.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.ks.profiling.safepoint.analyzer.commons.FileParser;
import pl.ks.profiling.safepoint.analyzer.commons.shared.ParserUtils;

public class GCJdk8LogFileParser implements FileParser<GCLogFile> {

    private static final Pattern patternTimestamp = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}(Z|[+-])(\\d{4})");

    private static final Pattern patternGCAllocationFailure = Pattern.compile(
            //             "\\[GC \\((.*?)\\) \\[PSYoungGen: (.*?)(.)->(.*?)(.)\\((.*?)(.)\\)\\] (.*?)(.)->(.*?)(.)\\((.*?)(.)\\), (.*?) secs\\] \\[Times: user=(.*?), sys=(.*?), real=(.*?) secs\\]"
            "\\[GC \\((.*?)\\) \\[PSYoungGen: (.*?)(.)->(.*?)(.)\\((.*?)(.)\\)\\] (.*?)(.)->(.*?)(.)\\((.*?)(.)\\), (.*?) secs\\] (.*)"
    );

    private static final Pattern patternFullGC = Pattern.compile(
            "\\[Full GC \\((.*?)\\) \\[PSYoungGen: (.*?)(.)->(.*?)(.)\\((.*?)(.)\\)\\] \\[ParOldGen: (.*?)(.)->(.*?)(.)\\((.*?)(.)\\)\\] (.*?)(.)->(.*?)(.)\\((.*?)(.)\\), \\[Metaspace: (.*?)(.)->(.*?)(.)\\((.*?)(.)\\)\\], (.*?) secs\\] (.*)"
    );

    public static final BigDecimal D1024 = new BigDecimal(1024L);
    public static final BigDecimal JAVA_8_GB_MULTIPLIER = D1024;
    public static final BigDecimal JAVA_8_MB_MULTIPLIER = BigDecimal.ONE;
    public static final BigDecimal JAVA_8_KB_MULTIPLIER = BigDecimal.ONE.divide(D1024, 12, RoundingMode.HALF_EVEN);
    public static final BigDecimal JAVA_8_B_MULTIPLIER = JAVA_8_KB_MULTIPLIER.divide(D1024, 12, RoundingMode.HALF_EVEN);
    public static final BigDecimal TO_MS_MULTIPLIER = new BigDecimal(1000);

    private GCLogFile gcLogFile = new GCLogFile();
    private long java8SequenceId;

    @Override
    public void parseLine(String line) {
        parseJava8File(line);
    }

    @Override
    public GCLogFile fetchData() {
        gcLogFile.parsingCompleted();
        return gcLogFile;
    }

    public void endCycle() {
        gcLogFile.finishCycle(java8SequenceId);
    }

    private void parseJava8File(String line) {
        // Gestion de fin de cycle quand on rencontre un debut de log avec une date
        if (java8SequenceId > 0 && line.indexOf(": ") > 0) {
            String timestamp = line.substring(0, line.indexOf(": "));
            if (patternTimestamp.matcher(timestamp).matches()) {
                gcLogFile.finishCycle(java8SequenceId);
            }
        }

        // Allocation failure, GC sur young et old
        if (line.contains("[GC (Allocation Failure)")) {
            gcLogFile.newPhase(++java8SequenceId, "Pause Young (mixed)", getJava8TimeStamp(line));
            //addJava8Time(java8SequenceId, line);
            // Get size and time
            addGCAllocationFailureSizes(java8SequenceId, line, "[GC (Allocation Failure)", false, false);

        }
        // GCLocker Initiated GC
        else if (line.contains("[GC (GCLocker Initiated GC)")) {
            gcLogFile.newPhase(++java8SequenceId, "(GC (mixed))", getJava8TimeStamp(line));
            //addJava8Time(java8SequenceId, line);
            addGCLockerSizes(java8SequenceId, line, "[GC (GCLocker Initiated GC)", false, false);
            //gcLogFile.finishCycle(java8SequenceId);
        }
        // GC (System.gc())
        else if (line.contains("[GC (System.gc())")) {
            gcLogFile.newPhase(++java8SequenceId, "Major GC", getJava8TimeStamp(line));
            //addJava8Time(java8SequenceId, line);
            // Revoir la mutualisation
            addGCLockerSizes(java8SequenceId, line, "[GC (System.gc())", false, false);
            //gcLogFile.finishCycle(java8SequenceId);
        }
        // FullGC (System.gc())
        else if (line.contains("[Full GC (System.gc())")) {
            gcLogFile.newPhase(++java8SequenceId, "Full", getJava8TimeStamp(line));
            addJava8Time(java8SequenceId, line);
            addFullGCSizes(java8SequenceId, line, "[Full GC (System.gc())", false, false);
        }
        // Full GC (Ergonomics)
        else if (line.contains("[Full GC (Ergonomics)")) {
            gcLogFile.newPhase(++java8SequenceId, "Pause Full", getJava8TimeStamp(line));
            addFullGCSizes(java8SequenceId, line, "[Full GC (Ergonomics)", false, false);
        } else if (line.contains("GC pause")) {
            gcLogFile.newPhase(++java8SequenceId, getJava8Phase(line), getJava8TimeStamp(line));
            if (line.contains("secs")) {
                addJava8Time(java8SequenceId, line);
            }
        } else if (line.contains("Full GC") && line.contains("->")) {
            gcLogFile.newPhase(++java8SequenceId, "Pause Full", getJava8TimeStamp(line));
            addJava8Time(java8SequenceId, line.substring(line.lastIndexOf(", ")));
            addJava8Sizes(java8SequenceId, line, "Full GC (Allocation Failure)", false, false);
        } else if (line.contains("[GC (") && line.contains("->")) {
            gcLogFile.newPhase(++java8SequenceId, "Minor GC", getJava8TimeStamp(line));
            addJava8Time(java8SequenceId, line.substring(line.lastIndexOf(", ")));
            addJava8Sizes(java8SequenceId, line, "[GC (", false, false);
        } else if (line.contains("GC cleanup")) {
            gcLogFile.newPhase(++java8SequenceId, "Pause Cleanup", getJava8TimeStamp(line));
            addJava8Time(java8SequenceId, line.substring(line.lastIndexOf(", ")));
            addJava8Sizes(java8SequenceId, line, "GC cleanup", false, false);
        } else if (line.contains("GC remark")) {
            gcLogFile.newPhase(++java8SequenceId, "Pause Remark", getJava8TimeStamp(line));
            addJava8Time(java8SequenceId, line.substring(line.lastIndexOf(", ")));
        }
        // duration of the GC on multilines GC trace. Ex : , 0.0165161 secs]
        else if (( (line.startsWith(", ") || (line.startsWith(" (to-space exhausted), "))) && line.contains("secs") && !line.contains("Times"))) {
            addJava8Time(java8SequenceId, line.replace(" (to-space exhausted)",""));
        } else if (line.contains("Heap: ") && line.contains("->")) {
            addJava8Sizes(java8SequenceId, line, "Heap", true, true);
        } else if (line.startsWith("Desired survivor size")) {
            addJava8SurvivorStats(java8SequenceId, line);
        } else if (line.startsWith("- age")) {
            addJava8AgeCount(java8SequenceId, line);
        } else if (line.startsWith("   [") && !line.contains("->")) {
            addJava8PhaseYoungAndMixed(java8SequenceId, line, gcLogFile, false);
        } else if (line.startsWith("      [") && !line.contains("GC Worker Start") && !line.contains("GC Worker End")) {
            addJava8PhaseYoungAndMixed(java8SequenceId, line, gcLogFile, true);
        }
    }


    private void addJava8PhaseYoungAndMixed(Long sequenceId, String line, GCLogFile gcLogFile, boolean subSubPhase) {
        String phase = line.replaceFirst(".*\\[", "").replaceFirst(":.*", "");
        if (subSubPhase) {
            phase = "|______" + phase;
        }
        if (line.contains("Max:")) {
            String time = line.replaceFirst(".*Max:", "").replaceFirst(", Diff.*", "").trim().replace(',', '.');
            gcLogFile.addSubPhaseTime(sequenceId, phase, new BigDecimal(time));
        } else {
            String time = line.replaceFirst("ms.*", "").replaceFirst(".*:", "").trim().replace(',', '.');
            gcLogFile.addSubPhaseTime(sequenceId, phase, new BigDecimal(time));
        }
    }

    private void addJava8AgeCount(Long sequenceId, String line) {
        String ageStr = line
                .replaceFirst("- age", "")
                .replaceFirst(":.*", "")
                .trim();
        String sizeStr = line
                .replaceFirst(".*:", "")
                .replaceFirst("bytes.*", "")
                .trim();

        int age = Integer.parseInt(ageStr);
        long size = Long.parseLong(sizeStr);

        gcLogFile.addAgeWithSize(sequenceId, age, size);
    }

    private void addJava8SurvivorStats(Long sequenceId, String line) {
        int desiredSizePos = line.indexOf("Desired survivor size");
        int newThresholdPos = line.indexOf("new threshold", desiredSizePos);
        int maxThresholdPos = line.indexOf("max", newThresholdPos);

        long desiredSize = ParserUtils.parseFirstNumber(line, desiredSizePos);
        long newThreshold = ParserUtils.parseFirstNumber(line, newThresholdPos);
        long maxThreshold = ParserUtils.parseFirstNumber(line, maxThresholdPos);

        gcLogFile.addSurvivorStats(sequenceId, desiredSize, newThreshold, maxThreshold);
    }

    private void addJava8Time(long sequenceId, String line) {
        // retrait de ', ' du debut
        String stringToSearch = line.replace(", ", "");
        // Retrait du ] de fin
        stringToSearch = stringToSearch.replaceFirst("secs]", "");

        BigDecimal time = ParserUtils.parseFirstBigDecimal(stringToSearch, 0).multiply(TO_MS_MULTIPLIER);
        gcLogFile.addTime(sequenceId, time);
    }

    private void addJava8Sizes(long sequenceId, String line, String startingString, boolean containsComma, boolean containsMaxHeapSizeBeforeGC) {

        String stringToSearch = line.substring(line.indexOf(startingString));
        // Retrait du startingString
        stringToSearch = stringToSearch.replace(startingString, "");
        // Retrait de ":"
        stringToSearch = stringToSearch.replaceFirst(":", "");
        // Split avec '->'
        String[] tokens = stringToSearch.split("->");
        String before = tokens[0].trim();
        // Entre parenthèse, la taille globale de la Heap avant GC
        // On supprime tout ce qui se trouve dans la parenthèse apres la taille
        if ( before.indexOf("(") >= 0 ) {
            before = before.substring(0, before.indexOf("("));
        }
        String after = tokens[1].trim();
        // Entre parenthèse, la taille globale de la Heap après GC
        String heapSize = "0.0M";
        if (after.indexOf("(") >= 0) {
            heapSize = after.substring(after.indexOf("(") + 1);
            heapSize = heapSize.substring(0, heapSize.indexOf(")"));
            after = after.substring(0, after.indexOf("("));
        }

        if (getSizeWithUnit(heapSize).intValue() == 0) {
            System.out.println("Error getting heap size of GC for sequence id " + sequenceId);
        }

        gcLogFile.addSizes(sequenceId, getSizeWithUnit(before).intValue(), getSizeWithUnit(after).intValue(), getSizeWithUnit(heapSize).intValue());
    }

    private void addFullGCSizes(long sequenceId, String line, String startingString, boolean containsComma, boolean containsMaxHeapSizeBeforeGC) {

        String stringToParse = line.substring(line.indexOf(startingString));

        Matcher matcher = patternFullGC.matcher(stringToParse);

        if (matcher.matches()) {
            System.out.println("GC Cause: " + matcher.group(1));
            System.out.println("GC Generation: " + matcher.group(2));
            // Info youg and Old a gérer plus tard
            System.out.println("Heap Before: " + matcher.group(14) + matcher.group(15));
            String before = matcher.group(14) + matcher.group(15);
            System.out.println("Heap After: " + matcher.group(16) + matcher.group(17));
            String after = matcher.group(16) + matcher.group(17);
            System.out.println("Heap Capacity: " + matcher.group(18) + matcher.group(19));
            String heapSize = matcher.group(18) + matcher.group(19);
            // Ensuite taille Metaspace
            // matcher.group(20) à matcher.group(25)
            // Puis duree GC
            // On positionne le temps
            BigDecimal time = ParserUtils.parseFirstBigDecimal(matcher.group(26), 0).multiply(TO_MS_MULTIPLIER);
            gcLogFile.addTime(sequenceId, time);
            //System.out.println("GC Time: " + matcher.group(18) + " secs");
            // Regexp ne fonctionne pas, voir quand on aura besoin de cette information
            //System.out.println("User Time: " + matcher.group(13) + " secs");
            //System.out.println("System Time: " + matcher.group(14) + " secs");
            //System.out.println("Real Time: " + matcher.group(15) + " secs");

            gcLogFile.addSizes(sequenceId, getSizeWithUnit(before).intValue(), getSizeWithUnit(after).intValue(), getSizeWithUnit(heapSize).intValue());
        }
    }

    private void addGCAllocationFailureSizes(long sequenceId, String line, String startingString, boolean containsComma, boolean containsMaxHeapSizeBeforeGC) {

        String stringToParse = line.substring(line.indexOf(startingString));

        Matcher matcher = patternGCAllocationFailure.matcher(stringToParse);

        if (matcher.matches()) {
            System.out.println("GC Cause: " + matcher.group(1));
            System.out.println("GC Generation: " + matcher.group(2));
            System.out.println("Heap Before: " + matcher.group(8) + matcher.group(9));
            String before = matcher.group(8) + matcher.group(9);
            System.out.println("Heap After: " + matcher.group(10) + matcher.group(11));
            String after = matcher.group(10) + matcher.group(11);
            System.out.println("Heap Capacity: " + matcher.group(12) + matcher.group(13));
            String heapSize = matcher.group(12) + matcher.group(13);
            System.out.println("GC Time: " + matcher.group(14) + " secs");

            // On positionne le temps
            BigDecimal time = ParserUtils.parseFirstBigDecimal(matcher.group(14), 0).multiply(TO_MS_MULTIPLIER);
            gcLogFile.addTime(sequenceId, time);

            // Regexp ne fonctionne pas, voir quand on aura besoin de cette information
            //System.out.println("User Time: " + matcher.group(13) + " secs");
            //System.out.println("System Time: " + matcher.group(14) + " secs");
            //System.out.println("Real Time: " + matcher.group(15) + " secs");

            gcLogFile.addSizes(sequenceId, getSizeWithUnit(before).intValue(), getSizeWithUnit(after).intValue(), getSizeWithUnit(heapSize).intValue());
        }
    }

    private void addGCLockerSizes(long sequenceId, String line, String startingString, boolean containsComma, boolean containsMaxHeapSizeBeforeGC) {
        // Same as GCAllocationFailureSizes
        this.addGCAllocationFailureSizes(sequenceId, line, startingString, containsComma, containsMaxHeapSizeBeforeGC);
    }

    private BigDecimal getSizeWithUnit(String size) {
        BigDecimal multiplier = getMultiplier(size);
        return multiplier.multiply(new BigDecimal(size.substring(0, size.length() - 1))).setScale(2, RoundingMode.HALF_EVEN);
    }

    private BigDecimal getMultiplier(String size) {
        if (size.charAt(size.length() - 1) == 'K') {
            return JAVA_8_KB_MULTIPLIER;
        }
        if (size.charAt(size.length() - 1) == 'M') {
            return JAVA_8_MB_MULTIPLIER;
        }
        if (size.charAt(size.length() - 1) == 'G') {
            return JAVA_8_GB_MULTIPLIER;
        }
        return JAVA_8_B_MULTIPLIER;
    }

    private BigDecimal getJava8TimeStamp(String line) {
        // JDK 8 - timestamp au format yyyy-mm-ddThh:mm:ss.SSS+0100: 2591798.694: (derniere partie big decimal)
        // Pattern pattern = Pattern.compile("\\d+,\\d+: ");
        // Matcher matcher = pattern.matcher(line);
        // matcher.find();
        // return new BigDecimal(matcher.group().replace(',', '.').replace(": ", "").trim());
        // tokenize with ": " separator the line
        String[] tokens = line.split(": ");
        // Si il n'y a pas 2 token, c'est une ligne informative sans timestamp
        if (tokens.length < 2) {
            return new BigDecimal("0.0");
        }
        return new BigDecimal(tokens[1].replace(",", "."));

    }

    private String getJava8Phase(String line) {
        return line.substring(line.indexOf("("), line.lastIndexOf(")") + 1);
    }
}
