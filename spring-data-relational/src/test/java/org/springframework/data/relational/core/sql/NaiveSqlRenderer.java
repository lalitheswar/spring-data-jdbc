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

		SelectStatementVisitor visitor = new SelectStatementVisitor();
		select.visit(visitor);

		return visitor.builder.toString();
	}

	interface ValuedVisitor extends Visitor {
		String getValue();
	}

	static class SelectStatementVisitor implements Visitor {

		private Stack<Visitor> visitors = new Stack<>();
		private StringBuilder builder = new StringBuilder();
		private ValuedVisitor conditionVisitor = new ConditionVisitor();

		private SelectListVisitor selectListVisitor = new SelectListVisitor();
		private FromClauseVisitor fromClauseVisitor = new FromClauseVisitor();
		private JoinTableAndConditionVisitor joinTableAndConditionVisitor;
		private JoinVisitor joinVisitor = new JoinVisitor();
		private OrderByClauseVisitor orderByClauseVisitor;

		{
			visitors.push(this);
			visitors.push(joinVisitor);
			visitors.push(fromClauseVisitor);
			visitors.push(selectListVisitor);
		}

		@Override
		public void enter(Visitable segment) {

			if (segment instanceof Select) {

				builder.append("SELECT ");
				if (((Select) segment).isDistinct()) {
					builder.append("DISTINCT ");
				}

			} else if (segment instanceof From) {

				builder.append(selectListVisitor.getValue());

			} else if (segment instanceof Where) {

				builder.append(" WHERE ");
				visitors.push(conditionVisitor);
			} else {
				if (segment instanceof OrderByField && !(visitors.peek() instanceof OrderByClauseVisitor)) {

					orderByClauseVisitor = new OrderByClauseVisitor();
					visitors.push(orderByClauseVisitor);
				}

				visitors.peek().enter(segment);
			}
		}

		@Override
		public void leave(Visitable segment) {

			if (segment instanceof From) {

				builder.append(" FROM ");
				builder.append(fromClauseVisitor.getValue());

			} else if (segment instanceof Where) {

				builder.append(conditionVisitor.getValue());
				visitors.pop();

			} else if (segment instanceof Select) {

				if (orderByClauseVisitor != null) {

					builder.append(orderByClauseVisitor.getValue());
				}

				OptionalLong limit = ((Select) segment).getLimit();
				if (limit.isPresent())
					builder.append(" LIMIT ").append(limit.getAsLong());

				OptionalLong offset = ((Select) segment).getOffset();
				if (offset.isPresent())
					builder.append(" OFFSET ").append(offset.getAsLong());
			} else {
				visitors.peek().leave(segment);
			}
		}

		/**
		 * Handles a sequence of {@link Visitable} until encountering the first that does not matches the expectations. When
		 * a not matching element is encountered it pops itself from the stack and delegates the call to the now top most
		 * element of the stack.
		 */
		abstract class ReadWhileMatchesVisitor implements Visitor {

			private Visitable currentSegment = null;
			private Visitor nextVisitor;

			abstract boolean matches(Visitable segment);

			void enterMatched(Visitable segment) {}

			void enterSub(Visitable segment) {}

			void leaveMatched(Visitable segment) {}

			void leaveSub(Visitable segment) {}

			@Override
			public void enter(Visitable segment) {

				if (currentSegment == null) {

					if (matches(segment)) {

						currentSegment = segment;
						enterMatched(segment);
					} else {

						Visitor popped = visitors.pop();

						Assert.isTrue(popped == this, "Popped the wrong visitor from the stack!");

						nextVisitor = visitors.peek();
						nextVisitor.enter(segment);
					}

				} else {
					enterSub(segment);
				}
			}

			@Override
			public void leave(Visitable segment) {

				if (currentSegment == null) {
					// we are receiving the leave event of the element above
					visitors.pop();
					nextVisitor = visitors.peek();
					nextVisitor.leave(segment);
				} else if (segment == currentSegment) {

					currentSegment = null;
					leaveMatched(segment);
				} else {
					leaveSub(segment);
				}
			}
		}

		/**
		 * Visits exactly one element that must match the expectations as defined in {@link #matches(Visitable)}. Ones
		 * handled it pops itself from the stack.
		 */
		abstract class ReadOneVisitor implements Visitor {

			private Visitable currentSegment;

			abstract boolean matches(Visitable segment);

			void enterMatched(Visitable segment) {}

			void enterSub(Visitable segment) {}

			void leaveMatched(Visitable segment) {}

			void leaveSub(Visitable segment) {}

			@Override
			public void enter(Visitable segment) {

				if (currentSegment == null) {

					Assert.isTrue(matches(segment), "Unexpected, not matching segment " + segment);

					currentSegment = segment;
					enterMatched(segment);
				} else {
					enterSub(segment);
				}
			}

			@Override
			public void leave(Visitable segment) {

				if (segment == currentSegment) {
					leaveMatched(segment);
					Assert.isTrue(visitors.pop() == this, "Popped wrong visitor instance.");
					System.out.println(String.format("popping %s next one is %s", this, visitors.peek()));
				} else {
					leaveSub(segment);
				}

			}
		}

		class SelectListVisitor extends ReadWhileMatchesVisitor implements ValuedVisitor {

			private StringBuilder builder = new StringBuilder();
			private boolean first = true;
			private boolean insideFunction = false; // this is hackery and should be fix with a proper visitor for
																							// subelements.

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Expression;
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
			void leaveSub(Visitable segment) {

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
			public String getValue() {
				return builder.toString();
			}
		}

		private class FromClauseVisitor extends ReadWhileMatchesVisitor implements ValuedVisitor {

			private final StringBuilder builder = new StringBuilder();
			private boolean first = true;

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Table;
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
			public String getValue() {
				return builder.toString();
			}
		}

		private class JoinVisitor extends ReadWhileMatchesVisitor {

			private JoinTableAndConditionVisitor subvisitor;

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Join;
			}

			@Override
			void enterMatched(Visitable segment) {

				subvisitor = new JoinTableAndConditionVisitor();
				visitors.push(subvisitor);
			}

			@Override
			void leaveMatched(Visitable segment) {
				builder.append(" JOIN ").append(subvisitor.getValue());
			}

		}

		private class JoinTableAndConditionVisitor extends ReadWhileMatchesVisitor implements ValuedVisitor {

			private final StringBuilder builder = new StringBuilder();
			boolean inCondition = false;

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Table || segment instanceof Condition;
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
			public String getValue() {
				return builder.toString();
			}
		}

		private class ConditionVisitor extends ReadOneVisitor implements ValuedVisitor {

			private StringBuilder builder = new StringBuilder();

			ValuedVisitor left;
			ValuedVisitor right;

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Condition;
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

					builder.append(left.getValue()) //
							.append(" AND ") //
							.append(right.getValue());

				} else if (segment instanceof OrCondition) {

					builder.append("(") //
							.append(left.getValue()) //
							.append(" OR ") //
							.append(right.getValue()) //
							.append(")");

				} else if (segment instanceof IsNull) {

					builder.append(left.getValue());
					if (((IsNull) segment).isNegated()) {
						builder.append(" IS NOT NULL");
					} else {
						builder.append(" IS NULL");
					}

				} else if (segment instanceof Equals) {

					builder.append(left.getValue()).append(" = ").append(right.getValue());

				} else if (segment instanceof In) {

					builder.append(left.getValue()).append(" IN ").append("(").append(right.getValue()).append(")");
				}
			}

			@Override
			public String getValue() {
				return builder.toString();
			}
		}

		private class ExpressionVisitor extends ReadOneVisitor implements ValuedVisitor {

			private String value = "";

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof Expression;
			}

			@Override
			void enterMatched(Visitable segment) {

				if (segment instanceof Column) {
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
			public String getValue() {
				return value;
			}
		}

		private class OrderByClauseVisitor extends ReadWhileMatchesVisitor implements ValuedVisitor {

			StringBuilder builder = new StringBuilder();
			boolean first = true;

			@Override
			boolean matches(Visitable segment) {
				return segment instanceof OrderByField;
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
			void leaveSub(Visitable segment) {

				if (segment instanceof Column) {
					builder.append(((Column) segment).getReferenceName());
				}
			}

			@Override
			public String getValue() {
				return builder.toString();
			}
		}

	}

}
