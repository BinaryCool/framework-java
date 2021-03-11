/**
 * 
 */
package pers.binaryhunter.db.mybatis.shardbatis.converter;

import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.values.ValuesStatement;

import java.util.Iterator;

/**
 * @author sean.he
 * 
 */
public class SelectSqlConverter extends AbstractSqlConverter {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.shardbatis.converter.AbstractSqlConverter#doConvert(net
	 * .sf.jsqlparser.statement.Statement, java.lang.Object, java.lang.String)
	 */
	@Override
	protected Statement doConvert(Statement statement, final Object params,
			final String mapperId) {
		if (!(statement instanceof Select)) {
			throw new IllegalArgumentException(
					"The argument statement must is instance of Select.");
		}
		TableNameModifier modifier = new TableNameModifier(params, mapperId);
		((Select) statement).getSelectBody().accept(modifier);
		return statement;
	}

	private class TableNameModifier implements SelectVisitor, FromItemVisitor{
		private Object params;
		private String mapperId;

		TableNameModifier(Object params, String mapperId) {
			this.params = params;
			this.mapperId = mapperId;
		}

		@Override
		public void visit(Table tableName) {
			String table = tableName.getName();
			table = convertTableName(table, params, mapperId);
			tableName.setName(table);
		}

		@Override
		public void visit(SubSelect subSelect) {
			subSelect.getSelectBody().accept(this);
		}

		@Override
		public void visit(SubJoin subJoin) {

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

		@Override
		public void visit(ParenthesisFromItem parenthesisFromItem) {

		}

		@Override
		public void visit(PlainSelect plainSelect) {
			plainSelect.getFromItem().accept(this);

			if (plainSelect.getJoins() != null) {
				for (Iterator joinsIt = plainSelect.getJoins().iterator();
				     joinsIt.hasNext();) {
					Join join = (Join) joinsIt.next();
					join.getRightItem().accept(this);
				}
			}
		}

		@Override
		public void visit(SetOperationList setOpList) {

		}

		@Override
		public void visit(WithItem withItem) {

		}

		@Override
		public void visit(ValuesStatement aThis) {

		}
	}

}
