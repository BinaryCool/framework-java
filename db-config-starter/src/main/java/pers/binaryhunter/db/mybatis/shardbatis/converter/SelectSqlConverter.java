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

	private class TableNameModifier implements SelectVisitor, FromItemVisitor,
			ExpressionVisitor, ItemsListVisitor {
		private Object params;
		private String mapperId;

		TableNameModifier(Object params, String mapperId) {
			this.params = params;
			this.mapperId = mapperId;
		}

		@SuppressWarnings("unchecked")
		public void visit(PlainSelect plainSelect) {
			plainSelect.getFromItem().accept(this);

			if (plainSelect.getJoins() != null) {
				for (Iterator joinsIt = plainSelect.getJoins().iterator(); joinsIt
						.hasNext();) {
					Join join = (Join) joinsIt.next();
					join.getRightItem().accept(this);
				}
			}
			if (plainSelect.getWhere() != null)
				plainSelect.getWhere().accept(this);

		}

		@SuppressWarnings("unchecked")
		public void visit(Union union) {
			for (Iterator iter = union.getPlainSelects().iterator(); iter
					.hasNext();) {
				PlainSelect plainSelect = (PlainSelect) iter.next();
				visit(plainSelect);
			}
		}

		public void visit(Table tableName) {
			String table = tableName.getName();
			table = convertTableName(table, params, mapperId);
			// convert table name
			tableName.setName(table);
		}

		public void visit(SubSelect subSelect) {
			subSelect.getSelectBody().accept(this);
		}

		public void visit(Addition addition) {
			visitBinaryExpression(addition);
		}

		public void visit(AndExpression andExpression) {
			visitBinaryExpression(andExpression);
		}

		public void visit(Between between) {
			between.getLeftExpression().accept(this);
			between.getBetweenExpressionStart().accept(this);
			between.getBetweenExpressionEnd().accept(this);
		}

		public void visit(Column tableColumn) {
		}

		public void visit(Division division) {
			visitBinaryExpression(division);
		}

		public void visit(DoubleValue doubleValue) {
		}

		public void visit(EqualsTo equalsTo) {
			visitBinaryExpression(equalsTo);
		}

		public void visit(Function function) {
		}

		public void visit(GreaterThan greaterThan) {
			visitBinaryExpression(greaterThan);
		}

		public void visit(GreaterThanEquals greaterThanEquals) {
			visitBinaryExpression(greaterThanEquals);
		}

		public void visit(InExpression inExpression) {
			inExpression.getLeftExpression().accept(this);
			inExpression.getItemsList().accept(this);
		}

		public void visit(InverseExpression inverseExpression) {
			inverseExpression.getExpression().accept(this);
		}

		public void visit(IsNullExpression isNullExpression) {
		}

		public void visit(JdbcParameter jdbcParameter) {
		}

		public void visit(LikeExpression likeExpression) {
			visitBinaryExpression(likeExpression);
		}

		public void visit(ExistsExpression existsExpression) {
			existsExpression.getRightExpression().accept(this);
		}

		public void visit(LongValue longValue) {
		}

		public void visit(MinorThan minorThan) {
			visitBinaryExpression(minorThan);
		}

		public void visit(MinorThanEquals minorThanEquals) {
			visitBinaryExpression(minorThanEquals);
		}

		public void visit(Multiplication multiplication) {
			visitBinaryExpression(multiplication);
		}

		public void visit(NotEqualsTo notEqualsTo) {
			visitBinaryExpression(notEqualsTo);
		}

		public void visit(NullValue nullValue) {
		}

		public void visit(OrExpression orExpression) {
			visitBinaryExpression(orExpression);
		}

		public void visit(Parenthesis parenthesis) {
			parenthesis.getExpression().accept(this);
		}

		public void visit(Subtraction subtraction) {
			visitBinaryExpression(subtraction);
		}

		public void visitBinaryExpression(BinaryExpression binaryExpression) {
			binaryExpression.getLeftExpression().accept(this);
			binaryExpression.getRightExpression().accept(this);
		}

		@SuppressWarnings("unchecked")
		public void visit(ExpressionList expressionList) {
			for (Iterator iter = expressionList.getExpressions().iterator(); iter
					.hasNext();) {
				Expression expression = (Expression) iter.next();
				expression.accept(this);
			}

		}

		public void visit(DateValue dateValue) {
		}

		public void visit(TimestampValue timestampValue) {
		}

		public void visit(TimeValue timeValue) {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser
		 * .expression.CaseExpression)
		 */
		public void visit(CaseExpression caseExpression) {
			// TODO Auto-generated method stub

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser
		 * .expression.WhenClause)
		 */
		public void visit(WhenClause whenClause) {
			// TODO Auto-generated method stub

		}

		public void visit(AllComparisonExpression allComparisonExpression) {
			allComparisonExpression.GetSubSelect().getSelectBody().accept(this);
		}

		public void visit(AnyComparisonExpression anyComparisonExpression) {
			anyComparisonExpression.GetSubSelect().getSelectBody().accept(this);
		}

		public void visit(SubJoin subjoin) {
			subjoin.getLeft().accept(this);
			subjoin.getJoin().getRightItem().accept(this);
		}

		public void visit(Concat concat) {
			visitBinaryExpression(concat);
		}

		public void visit(Matches matches) {
			visitBinaryExpression(matches);

		}

		public void visit(BitwiseAnd bitwiseAnd) {
			visitBinaryExpression(bitwiseAnd);

		}

		public void visit(BitwiseOr bitwiseOr) {
			visitBinaryExpression(bitwiseOr);

		}

		public void visit(BitwiseXor bitwiseXor) {
			visitBinaryExpression(bitwiseXor);
		}

		public void visit(StringValue stringValue) {
		}
	}

}
