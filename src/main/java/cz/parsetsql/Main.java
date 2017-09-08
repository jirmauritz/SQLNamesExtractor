package cz.parsetsql;

import net.sf.jsqlparser.JSQLParserException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {

    // stop words will be deleted before processing (!order matters if substrings!)
    private static final List<String> STOPWORDS = Arrays.asList("@", "WITH\\s+\\(\\s*NOLOCK\\s*\\)", "\\(\\s*NOLOCK\\s*\\)");
    // functions that will be removed together with params, since jsqlparser does not know them
    private static final List<String> STOP_FUNCTIONS = Arrays.asList("IIF");
    private static final String REPLACE_FOR_STOP_FUNCTION = "a";

    // file name
    private static final String FILE_NAME = "script.sql";


    public static void main(String[] args) throws JSQLParserException, IOException, URISyntaxException {
        Preprocessing preprocessing = new Preprocessing(STOPWORDS, STOP_FUNCTIONS, REPLACE_FOR_STOP_FUNCTION);
        Parser parser = new Parser();

        List<String> script = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(FILE_NAME).toURI()));
        System.out.println("Clearing script...");
        List<String> stringStmnts = preprocessing.getClearStatements(script);

        // process all statements
        System.out.println("Parsing statements...");
        parser.parse(stringStmnts);
        parser.extractJoins();

        System.out.println(parser.getTableNames());
        System.out.println(parser.getColumnNames());
    }
}
