/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.relational.core.sql;

import java.util.OptionalLong;
import java.util.Stack;
import java.util.function.Predicate;

import org.springframework.util.Assert;

/**
 * Naive SQL renderer that does not consider dialect specifics. This class is to evaluate requirements of a SQL
 * renderer.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 */
public class NaiveSqlRenderer {

	private final Select select;

	private NaiveSqlRenderer(Select select) {

		Assert.notNull(select, "Select must not be null!");

		this.select = select;
	}

	/**
	 * Creates a new {@link NaiveSqlRenderer}.
	 *
	 * @param select must not be {@literal null}.
	 * @return the renderer.
	 */
	public static NaiveSqlRenderer create(Select select) {
		return new NaiveSqlRenderer(select);
	}

	/**
	 * Renders a {@link Select} statement into its SQL representation.
	 *
	 * @param select must not be {@literal null}.
	 * @return the rendered statement.
	 */
	public static String render(Select select) {
		return create(select).render();
	}

	/**
	 * Render the {@link Select} AST into a SQL statement.
	 *
	 * @return the rendered statement.
	 */
	public String render() {

		StackBasedVisitor visitor = new StackBasedVisitor();
		select.visit(visitor);

		return visitor.selectStatementVisitor.getRenderedPart();
	}

	interface PartRenderer extends Visitor {

		String getRenderedPart();
	}

	static class StackBasedVisitor implements Visitor {

		private Stack<Visitor> visitors = new Stack<>();

		private SelectStatementVisitor selectStatementVisitor = new SelectStatementVisitor();

		{
			visitors.push(segment -> {
			});
			visitors.push(selectStatementVisitor);
		}

		@Override
		public void enter(Visitable segment) {

			Visitor delegate = visitors.peek();
			delegate.enter(segment);
		}

		@Override
		public void leave(Visitable segment) {

			Visitor delegate = visitors.peek();
			delegate.leave(segment);
		}

		/**
		 * Handles a sequence of {@link Visitable} until encountering the first that does not matches the expectations. When
		 * a not matching element is encountered it pops itself from the stack and delegates the call to the now top most
		 * element of the stack.
		 */
		class ForwardingSubtreeVisitor implements Visitor {

			private final Predicate<Visitable> predicate;

			private Visitable currentSegment = null;
			private Visitor nextVisitor;

			ForwardingSubtreeVisitor(Predicate<Visitable> predicate) {
				this.predicate = predicate;
			}

			void enterMatched(Visitable segment) {
			}

			void enterNested(Visitable segment) {
			}

			void leaveMatched(Visitable segment) {
			}

			void leaveNested(Visitable segment) {
			}

			@Override
			public final void enter(Visitable segment) {

				if (currentSegment == null) {

					if (predicate.test(segment)) {

						currentSegment = segment;
						enterMatched(segment);
					} else {

						Visitor popped = visitors.pop();

						Assert.isTrue(popped == this, "Popped the wrong visitor from the stack!");

						nextVisitor = visitors.peek();
						nextVisitor.enter(segment);
					}
				} else {
					enterNested(segment);
				}
			}

			@Override
			public final void leave(Visitable segment) {

				if (currentSegment == null) {
					// we are receiving the leave event of the element above
					visitors.pop();
					nextVisitor = visitors.peek();
					nextVisitor.leave(segment);
				} else if (segment == currentSegment) {

					currentSegment = null;
					leaveMatched(segment);
				} else {
					leaveNested(segment);
				}
			}
		}

		/**
		 * Visits exactly one element that must match the expectations as defined in {@link #matches(Visitable)}. Ones
		 * handled it pops itself from the stack.
		 */
		abstract class FilteredSubtreeVisitor implements Visitor {

			private final Predicate<Visitable> filter;

			private Visitable currentSegment;

			FilteredSubtreeVisitor(Predicate<Visitable> filter) {
				this.filter = filter;
			}

			void enterMatched(Visitable segment) {
			}

			void enterNested(Visitable segment) {
			}

			void leaveMatched(Visitable segment) {
			}

			void leaveNested(Visitable segment) {
			}

			@Override
			public final void enter(Visitable segment) {

				if (currentSegment == null) {

					if (filter.test(segment)) {

						currentSegment = segment;
						enterMatched(segment);
					} else {
						Assert.isTrue(visitors.pop() == this, "Popped wrong visitor instance.");
						visitors.peek().enter(segment);
					}
				} else {
					enterNested(segment);
				}
			}

			@Override
			public final void leave(Visitable segment) {

				if (currentSegment == null) {
					Assert.isTrue(visitors.pop() == this, "Popped wrong visitor instance.");
					visitors.peek().leave(segment);
				} else if (segment == currentSegment) {
					leaveMatched(segment);
					Assert.isTrue(visitors.pop() == this, "Popped wrong visitor instance.");
				} else {
					leaveNested(segment);
				}
			}
		}

		class SelectStatementVisitor extends FilteredSubtreeVisitor implements PartRenderer {

			private StringBuilder builder = new StringBuilder();

			private SelectListVisitor selectListVisitor = new SelectListVisitor();
			private FromClauseVisitor fromClauseVisitor = new FromClauseVisitor();
			private JoinVisitor joinVisitor = new JoinVisitor();
			private WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor();
			private OrderByClauseVisitor orderByClauseVisitor = new OrderByClauseVisitor();

			SelectStatementVisitor() {
				super(Select.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				visitors.push(orderByClauseVisitor);
				visitors.push(whereClauseVisitor);
				visitors.push(joinVisitor);
				visitors.push(fromClauseVisitor);
				visitors.push(selectListVisitor);
			}

			@Override
			void leaveMatched(Visitable segment) {

				builder.append("SELECT ");
				if (((Select) segment).isDistinct()) {
					builder.append("DISTINCT ");
				}

				builder.append(selectListVisitor.getRenderedPart()) //
						.append(fromClauseVisitor.getRenderedPart()) //
						.append(joinVisitor.getRenderedPart()) //
						.append(whereClauseVisitor.getRenderedPart());

				builder.append(orderByClauseVisitor.getRenderedPart());

				OptionalLong limit = ((Select) segment).getLimit();
				if (limit.isPresent()) {
					builder.append(" LIMIT ").append(limit.getAsLong());
				}

				OptionalLong offset = ((Select) segment).getOffset();
				if (offset.isPresent()) {
					builder.append(" OFFSET ").append(offset.getAsLong());
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}

		class SelectListVisitor extends ForwardingSubtreeVisitor implements PartRenderer {

			private StringBuilder builder = new StringBuilder();
			private boolean first = true;
			private boolean insideFunction = false; // this is hackery and should be fix with a proper visitor for
			// subelements.

			SelectListVisitor() {
				super(Expression.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (!first) {
					builder.append(", ");
				}
				if (segment instanceof SimpleFunction) {
					builder.append(((SimpleFunction) segment).getFunctionName()).append("(");
					insideFunction = true;
				} else {
					insideFunction = false;
				}
			}

			@Override
			void leaveMatched(Visitable segment) {

				first = false;

				if (segment instanceof SimpleFunction) {
					builder.append(")");
				} else if (segment instanceof Column) {
					builder.append(((Column) segment).getName());
					if (segment instanceof Column.AliasedColumn) {
						builder.append(" AS ").append(((Column.AliasedColumn) segment).getAlias());
					}
				}
			}

			@Override
			void leaveNested(Visitable segment) {

				if (segment instanceof Table) {
					builder.append(((Table) segment).getReferenceName()).append('.');
				}
				if (insideFunction) {

					if (segment instanceof SimpleFunction) {
						builder.append(")");
					} else if (segment instanceof Column) {
						builder.append(((Column) segment).getName());
						if (segment instanceof Column.AliasedColumn) {
							builder.append(" AS ").append(((Column.AliasedColumn) segment).getAlias());
						}
						first = false;
					}
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}

		class FromClauseVisitor extends FilteredSubtreeVisitor implements PartRenderer {

			private FromTableVisitor fromTableVisitor = new FromTableVisitor();

			FromClauseVisitor() {
				super(From.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {
				visitors.push(fromTableVisitor);
			}

			@Override
			public String getRenderedPart() {
				return " FROM " + fromTableVisitor.getRenderedPart();
			}
		}

		class FromTableVisitor extends ForwardingSubtreeVisitor implements PartRenderer {

			private final StringBuilder builder = new StringBuilder();
			private boolean first = true;

			FromTableVisitor() {
				super(Table.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (!first) {
					builder.append(", ");
				}
				first = false;

				builder.append(((Table) segment).getName());
				if (segment instanceof Table.AliasedTable) {
					builder.append(" AS ").append(((Table.AliasedTable) segment).getAlias());
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}

		class JoinVisitor extends ForwardingSubtreeVisitor implements PartRenderer {

			private StringBuilder internal = new StringBuilder();
			private JoinTableAndConditionVisitor subvisitor;

			JoinVisitor() {
				super(Join.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				subvisitor = new JoinTableAndConditionVisitor();
				visitors.push(subvisitor);
			}

			@Override
			void leaveMatched(Visitable segment) {
				append(" JOIN ");
				append(subvisitor.getRenderedPart());
			}

			@Override
			public String getRenderedPart() {
				return internal.toString();
			}

			void append(String s) {
				internal.append(s);
			}
		}

		class JoinTableAndConditionVisitor extends ForwardingSubtreeVisitor implements PartRenderer {

			private final StringBuilder builder = new StringBuilder();
			boolean inCondition = false;

			JoinTableAndConditionVisitor() {
				super(it -> it instanceof Table || it instanceof Condition);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (segment instanceof Table && !inCondition) {
					builder.append(((Table) segment).getName());
					if (segment instanceof Table.AliasedTable) {
						builder.append(" AS ").append(((Table.AliasedTable) segment).getAlias());
					}
				} else if (segment instanceof Condition && !inCondition) {
					builder.append(" ON ");
					builder.append(segment);
					inCondition = true;
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}

		private class WhereClauseVisitor extends FilteredSubtreeVisitor implements PartRenderer {

			private ConditionVisitor conditionVisitor = new ConditionVisitor();
			private StringBuilder internal = new StringBuilder();

			WhereClauseVisitor() {
				super(Where.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				internal.append(" WHERE ");
				visitors.push(conditionVisitor);
			}

			@Override
			void leaveMatched(Visitable segment) {

				internal.append(conditionVisitor.getRenderedPart());
				// builder.append(internal);
			}

			@Override
			public String getRenderedPart() {
				return internal.toString();
			}
		}

		private class ConditionVisitor extends FilteredSubtreeVisitor implements PartRenderer {

			private StringBuilder builder = new StringBuilder();

			PartRenderer left;
			PartRenderer right;

			ConditionVisitor() {
				super(Condition.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (segment instanceof MultipleCondition) {

					left = new ConditionVisitor();
					right = new ConditionVisitor();
					visitors.push(right);
					visitors.push(left);
				} else if (segment instanceof IsNull) {

					left = new ExpressionVisitor();
					visitors.push(left);
				} else if (segment instanceof Equals || segment instanceof In) {

					left = new ExpressionVisitor();
					right = new ExpressionVisitor();
					visitors.push(right);
					visitors.push(left);
				}
			}

			@Override
			void leaveMatched(Visitable segment) {

				if (segment instanceof AndCondition) {

					builder.append(left.getRenderedPart()) //
							.append(" AND ") //
							.append(right.getRenderedPart());
				} else if (segment instanceof OrCondition) {

					builder.append("(") //
							.append(left.getRenderedPart()) //
							.append(" OR ") //
							.append(right.getRenderedPart()) //
							.append(")");
				} else if (segment instanceof IsNull) {

					builder.append(left.getRenderedPart());
					if (((IsNull) segment).isNegated()) {
						builder.append(" IS NOT NULL");
					} else {
						builder.append(" IS NULL");
					}
				} else if (segment instanceof Equals) {

					builder.append(left.getRenderedPart()).append(" = ").append(right.getRenderedPart());
				} else if (segment instanceof In) {

					builder.append(left.getRenderedPart()).append(" IN ").append("(").append(right.getRenderedPart()).append(")");
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}

		private class ExpressionVisitor extends FilteredSubtreeVisitor implements PartRenderer {

			private String value = "";
			private SelectStatementVisitor valuedVisitor;

			ExpressionVisitor() {
				super(Expression.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (segment instanceof SubselectExpression) {

					valuedVisitor = new SelectStatementVisitor();
					visitors.push(valuedVisitor);
				} else if (segment instanceof Column) {
					value = ((Column) segment).getTable().getName() + "." + ((Column) segment).getName();
				} else if (segment instanceof BindMarker) {

					if (segment instanceof BindMarker.NamedBindMarker) {
						value = ":" + ((BindMarker.NamedBindMarker) segment).getName();
					} else {
						value = segment.toString();
					}
				}
			}

			@Override
			void leaveMatched(Visitable segment) {

				if (valuedVisitor != null) {
					value = valuedVisitor.getRenderedPart();
				}
			}

			@Override
			public String getRenderedPart() {
				return value;
			}
		}

		private class OrderByClauseVisitor extends ForwardingSubtreeVisitor implements PartRenderer {

			StringBuilder builder = new StringBuilder();
			boolean first = true;

			OrderByClauseVisitor() {
				super(OrderByField.class::isInstance);
			}

			@Override
			void enterMatched(Visitable segment) {

				if (!first) {
					builder.append(", ");
				} else {
					builder.append(" ORDER BY ");
				}
				first = false;
			}

			@Override
			void leaveMatched(Visitable segment) {

				OrderByField field = (OrderByField) segment;

				if (field.getDirection() != null) {
					builder.append(" ") //
							.append(field.getDirection());
				}
			}

			@Override
			void leaveNested(Visitable segment) {

				if (segment instanceof Column) {
					builder.append(((Column) segment).getReferenceName());
				}
			}

			@Override
			public String getRenderedPart() {
				return builder.toString();
			}
		}
	}
}
