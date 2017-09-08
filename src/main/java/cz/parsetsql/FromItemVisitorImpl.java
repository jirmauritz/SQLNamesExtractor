package cz.parsetsql;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;

public class FromItemVisitorImpl implements FromItemVisitor {

    FromItemVisitorImpl(ExpressionVisitorImpl expressionVisitor) {
        this.expressionVisitor = expressionVisitor;
    }

    private ExpressionVisitorImpl expressionVisitor;
    List<String> tableNames = new ArrayList<>();

    @Override
    public void visit(Table table) {
        tableNames.add(table.getFullyQualifiedName());
    }

    @Override
    public void visit(SubSelect subSelect) {

    }

    @Override
    public void visit(SubJoin subJoin) {
        subJoin.getJoin().getRightItem().accept(this);
        subJoin.getJoin().getOnExpression().accept(expressionVisitor);
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(ValuesList valuesList) {

    }

    @Override
    public void visit(TableFunction tableFunction) {

    }
}
