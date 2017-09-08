package cz.parsetsql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the SQL statements with parse and provides exploration methods.
 */
class Parser {

    private List<PlainSelect> selects = new ArrayList<>();
    private List<String> tableNames = new ArrayList<>();
    private List<String> columnNames = new ArrayList<>();

    /**
     * Parses the SQL statements and saves them in this class.
     *
     * @param stringStmnts statements as string
     * @throws JSQLParserException
     * @throws IOException
     * @throws URISyntaxException
     */
    void parse(List<String> stringStmnts) throws JSQLParserException, IOException, URISyntaxException {
        for (String stringStmnt : stringStmnts) {
            try {
                processStatement(stringStmnt);
            } catch (JSQLParserException e) {
                if (e.getCause() instanceof ParseException) {
                    ParseException parseException = (ParseException) e.getCause();
                    // if the query is subquery, we find a bracket, then cut the sequence here
                    if (")".equals(parseException.currentToken.next.toString())) {
                        String shortened = cutStatement(stringStmnt, parseException.currentToken.endLine, parseException.currentToken.endColumn + 1);
                        processStatement(shortened);
                    }
                }
            }
        }
    }

    /**
     * Extracts table and column names appearing in joins.
     */
    void extractJoins() {
        ExpressionVisitorImpl expressionVisitor = new ExpressionVisitorImpl();
        FromItemVisitorImpl fromItemVisitor = new FromItemVisitorImpl(expressionVisitor);
        for (PlainSelect select : selects) {
            if (select.getFromItem() == null) {
                // no from statement, skiping
                continue;
            }
            select.getFromItem().accept(fromItemVisitor);
            List<Join> joins = select.getJoins();
            if (joins != null) {
                for (Join join : joins) {
                    final Expression onExpression = join.getOnExpression();
                    if (onExpression != null) {
                        onExpression.accept(expressionVisitor);
                    }
                    join.getRightItem().accept(fromItemVisitor);
                }
            }
        }
        tableNames.addAll(fromItemVisitor.tableNames);
        columnNames.addAll(expressionVisitor.columnNames);
    }

    private void processStatement(String stringStmnt) throws JSQLParserException {
        Statement stmt = CCJSqlParserUtil.parse(stringStmnt);
        if (stmt == null || !(stmt instanceof Select)) {
            System.err.println("Statement was not recognized: \n" + stringStmnt);
        } else {
            // get select statement
            SelectBody selectStatement = ((Select) stmt).getSelectBody();
            // recursivelly get select statements (could be in union)
            List<PlainSelect> extractedSelects = extractSelects(selectStatement);
            // add to collection
            selects.addAll(extractedSelects);
        }
    }

    private static List<PlainSelect> extractSelects(SelectBody selectBody) {
        List<PlainSelect> selects = new ArrayList<>();
        if (selectBody instanceof SetOperationList) {
            for (SelectBody sb : ((SetOperationList) selectBody).getSelects()) {
                selects.addAll(extractSelects(sb));
            }
        } else if (selectBody instanceof PlainSelect) {
            selects.add((PlainSelect) selectBody);
        }
        return selects;
    }


    private static String cutStatement(String stmnt, int lineNum, int colNum) {
        StringBuilder builder = new StringBuilder();
        String[] lines = stmnt.split("\n");
        // add all lines before the last
        for (int i = 0; i < lineNum - 1; i++) {
            builder.append(lines[i]).append('\n');
        }
        // add last line up to the colNum
        builder.append(lines[lineNum - 1].substring(0, colNum - 1));

        return builder.toString();
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }
}
