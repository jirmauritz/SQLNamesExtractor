package cz.parsetsql;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processing of the SQL text, to be ready for parsing.
 * Removing stop words, stop functions, finding JOINS - only statements with JOINS are considered.
 */
class Preprocessing {

    // patterns
    private static final Pattern selectPattern = Pattern.compile("SELECT", Pattern.CASE_INSENSITIVE);
    private static final Pattern endStatementPattern = Pattern.compile("\n\\s*\n|\\sGO\\s|\\sUSE\\s|\\sCREATE\\s", Pattern.CASE_INSENSITIVE);

    // parameters
    private final String replaceForStopFunction;
    private final List<String> stopFunctions;
    private final List<String> stopwords;

    Preprocessing(List<String> stopwords, List<String> stopFunctions, String replaceForStopFunction) {
        this.stopwords = stopwords;
        this.stopFunctions = stopFunctions;
        this.replaceForStopFunction = replaceForStopFunction;
    }

    List<String> getClearStatements(List<String> lines) {
        // compile regexes
        List<Pattern> stopWordsPatterns = new ArrayList<>();
        stopwords.forEach(stopWord -> stopWordsPatterns.add(Pattern.compile(stopWord, Pattern.CASE_INSENSITIVE)));
        Pattern stopFunctionsPattern = Pattern.compile("(^|,|\\(|\\s)(" + String.join("|", stopFunctions) + ")(\\(|\\s|$)", Pattern.CASE_INSENSITIVE);

        // remove stop lines and stop characters
        StringBuilder builder = new StringBuilder();
        List<Integer> joinIdx = new ArrayList<>();
        // balance of the brackets
        int stopFunctionBrackets = 0;
        // stop function was declared but has not started with opening bracket
        boolean functionDidNotStartYet = false;
        int idx = 0;
        for (String line : lines) {
            // remove all stopwords before further processing
            for (Pattern stopWordPattern : stopWordsPatterns) {
                line = stopWordPattern.matcher(line).replaceAll("");
            }

            // check if stop function did not start yet
            if (functionDidNotStartYet) {
                if (doesFunctionStarts(line)) {
                    // function starts here, cut the line and let the next block process content of the function
                    line = line.split("\\(",2)[1];
                    stopFunctionBrackets = 1;
                    functionDidNotStartYet = false;
                } else {
                    continue;
                }
            }

            // if stopFunction is active, delete rest
            if (stopFunctionBrackets > 0) {
                Pair endAndBrackets = findFunctionEnd(line, stopFunctionBrackets);
                stopFunctionBrackets = (Integer) endAndBrackets.getRight();
                if (stopFunctionBrackets > 0) {
                    // if the function still did not end, continue and load another line
                    continue;
                } else {
                    // function ends here, throw away the beginning
                    line = line.substring((Integer) endAndBrackets.getLeft() + 1);
                }

            }

            // remove stop functions and replace them with REPLACE_FOR_STOP_FUNCTION
            Matcher m = stopFunctionsPattern.matcher(line);
            while (m.find()) {
                // if there is stop function, but bracket on the next line
                if (!doesFunctionStarts(line.substring(m.end() - 1))) {
                    // replace content
                    line = line.substring(0, m.start() + 1) + replaceForStopFunction;
                    functionDidNotStartYet = true;
                    break;
                }
                Pair endAndBrackets = findFunctionEnd(line.substring(m.end()), 1);
                stopFunctionBrackets = (Integer) endAndBrackets.getRight();
                int functionEnd = (Integer) endAndBrackets.getLeft();
                if (stopFunctionBrackets == 0) {
                    // delete content of the brackets and replace, so that we follow syntax rules
                    line = line.substring(0, m.start() + 1) + replaceForStopFunction + line.substring(functionEnd + m.end() + 1);
                } else {
                    // function continues on the next line
                    // first, cut this line
                    int offset = 0;
                    if (line.startsWith(",") || line.startsWith(" ") || line.startsWith("\t")) {
                        // if the function is not at the end of the line, cut one more sign
                        offset = 1;
                    }
                    line = line.substring(0, m.start() + offset) + replaceForStopFunction;
                    break;
                }
                m = stopFunctionsPattern.matcher(line);
            }

            // replace aphostrophes with quotes
            line = line.replaceAll("'", "\"");

            // change CROSS APPLY to JOIN, since jsqlparser does not know cross apply
            line = line.replaceAll("(?i)CROSS APPLY", "JOIN");

            // append line to builder
            builder.append(line).append('\n');

            // find joins
            m = Pattern.compile("JOIN").matcher(line);
            while (m.find()) {
                joinIdx.add(idx + m.start());
            }

            // increment the index (says total length of the statement in chars)
            idx += line.length() + 1;
        }

        String script = builder.toString();
        List<String> stmnts = new ArrayList<>();
        int endOfLastStmnt = 0;
        for (int join : joinIdx)

        {
            // find select before join
            int stmntStart = findLastSelect(script.substring(0, join));
            while (selectPattern.matcher(script.substring(0, stmntStart)).find() &&
                    moreClosingThanOpeningBrackets(script.substring(stmntStart, join))) {
                // if there are more closing brackets, we need to look for earlier SELECT clause
                stmntStart = findLastSelect(script.substring(0, stmntStart));
            }

            // check if the statement does not intervene with the previous join
            if (stmntStart < endOfLastStmnt) {
                continue;
            }

            // find newlines or 'create' after join
            Matcher m = endStatementPattern.matcher(script.substring(join));
            int stmntEnd = script.length() - 1;
            if (m.find()) {
                stmntEnd = m.start() + join;
            }
            endOfLastStmnt = stmntEnd;
            stmnts.add(script.substring(stmntStart, stmntEnd));
        }

        return stmnts;
    }


    /**
     * Returns true if there are more closing than opening brackets in the string.
     *
     * @param string to be analyzed
     * @return true if more opening
     */
    private boolean moreClosingThanOpeningBrackets(String string) {
        int opening = StringUtils.countMatches(string, "(");
        int closing = StringUtils.countMatches(string, ")");
        return closing > opening;
    }

    /**
     * Finds last SELECT clause in the string and returns index of the S from SELECT.
     *
     * @param string to be analyzed
     * @return starting index of the last SELECT from the string
     */
    private int findLastSelect(String string) {
        Matcher m = selectPattern.matcher(string);
        int stmntStart = 0;
        while (m.find()) {
            stmntStart = m.start();
        }
        return stmntStart;
    }



    /**
     * Counting the brackets and looks for closing, i.e. brackets count == 0
     *
     * @param line     string to look for brackets
     * @param brackets balance of brackets so far. Positive means opening brackets
     * @return pair: (index of the function end, number of brackets)
     */
    private Pair<Integer, Integer> findFunctionEnd(String line, int brackets) {
        char[] chars = line.toCharArray();
        for (int i = 0; i < line.length(); i++) {
            // go char after char and count brackets, so that we catch all content of the function
            if (chars[i] == ')') {
                brackets--;
            } else if (chars[i] == '(') {
                brackets++;
            }
            if (brackets == 0) {
                return Pair.of(i, brackets);
            }
        }
        return Pair.of(line.length(), brackets);
    }

    public boolean doesFunctionStarts(String line) {
        char[] chars = line.toCharArray();
        for (int i = 0; i < line.length(); i++) {
            if (chars[i] == '(') {
                return true;
            }
        }
        return false;
    }
}
