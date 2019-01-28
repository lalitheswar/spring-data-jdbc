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
import java.util.function.Consumer;

import org.springframework.util.Assert;

/**
 * Naive SQL renderer that does not consider dialect specifics. This class is to evaluate requirements of a SQL
 * renderer.
 *
 * @author Mark Paluch
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

		DelegatingVisitor visitor = new DelegatingVisitor();
		select.visit(visitor);

		return visitor.builder.toString();
	}

	static class DelegatingVisitor implements Visitor {

		Stack<Visitor> visitors = new Stack<>();
		StringBuilder builder = new StringBuilder();

		{
			visitors.push(new SelectVisitor());
		}

		@Override
		public void enter(Visitable segment) {
			if (segment instanceof Select) {
				builder.append("SELECT ");
				visitors.push(new SelectListVisitor());
			}
			if (segment instanceof From) {
				visitors.pop();
				builder.append(" FROM ");
				visitors.push(new FromClauseVisitor());
			}
			if (segment instanceof OrderByField && !(visitors.peek() instanceof OrderByClauseVisitor)) {

				visitors.pop();
				builder.append(" ORDER BY ");
				visitors.push(new OrderByClauseVisitor());
			}

			visitors.peek().enter(segment);
		}

		@Override
		public void leave(Visitable segment) {
			visitors.peek().leave(segment);
		}

		class SelectVisitor implements Visitor {

			@Override
			public void enter(Visitable segment) {

			}
		}

		class ListVisitor {
			private boolean firstColumn = true;
			private boolean inColumn = false;

			protected void onColumnStart() {
				if (inColumn) {
					return;
				}
				inColumn = true;
				if (!firstColumn) {
					builder.append(", ");
				}
				firstColumn = false;

			}

			protected void onColumnEnd() {
				inColumn = false;
			}
		}

		class SelectListVisitor extends ListVisitor implements Visitor {

			@Override
			public void enter(Visitable segment) {
				onColumnStart();
				if (segment instanceof Distinct) {
					builder.append("DISTINCT ");
				}
				if (segment instanceof SimpleFunction) {
					onColumnStart();
					builder.append(((SimpleFunction) segment).getFunctionName()).append("(");
				}
			}

			@Override
			public void leave(Visitable segment) {

				if (segment instanceof Table) {
					builder.append(((Table) segment).getReferenceName()).append('.');
				} else if (segment instanceof SimpleFunction) {
					builder.append(")");
				} else if (segment instanceof Column) {
					builder.append(((Column) segment).getName());
					if (segment instanceof Column.AliasedColumn) {
						builder.append(" AS ").append(((Column.AliasedColumn) segment).getAlias());
					}
				}
				onColumnEnd();
			}

		}

		private class FromClauseVisitor extends ListVisitor implements Visitor {


			@Override
			public void enter(Visitable segment) {

				if (segment instanceof Join) {
					builder.append(" JOIN ");
					visitors.push(new JoinVisitor());
				} else if (segment instanceof Table) {
					onColumnStart();
					builder.append(((Table) segment).getName());
					if (segment instanceof Table.AliasedTable) {
						builder.append(" AS ").append(((Table.AliasedTable) segment).getAlias());
					}
					onColumnEnd();
				}
			}

		}

		private class JoinVisitor implements Visitor {
			boolean inCondition = false;


			@Override
			public void enter(Visitable segment) {
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
			public void leave(Visitable segment) {
				if (segment instanceof Join) {
					visitors.pop();
				}
			}
		}

		private class OrderByClauseVisitor extends ListVisitor implements Visitor {

			@Override
			public void enter(Visitable segment) {
				onColumnStart();

			}

			@Override
			public void leave(Visitable segment) {

				if (segment instanceof Column) {
					builder.append(((Column) segment).getReferenceName());
				}

				if (segment instanceof OrderByField) {
					OrderByField field = (OrderByField) segment;
					if (field.getDirection() != null) {
						builder.append(" ") //
								.append(field.getDirection());
					}
				}
				onColumnEnd();
			}
		}

	}

	/**
	 * {@link Visitor} to render the SQL.
	 */
	static class RenderVisitor implements Visitor {

		StringBuilder builder = new StringBuilder();

		private boolean hasDistinct = false;
		private boolean hasOrderBy = false;
		private boolean nextRequiresComma = false;
		private boolean nextExpressionRequiresContinue = false;
		private boolean nextConditionRequiresContinue = false;
		private boolean inSelectList = false;
		private Stack<Visitable> segments = new Stack<>();

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.relational.core.sql.Visitor#enter(org.springframework.data.relational.core.sql.Visitable)
		 */
		@Override
		public void enter(Visitable segment) {

			if (segment instanceof Select) {
				builder.append("SELECT ");
				inSelectList = true;
			}

			if (segment instanceof From) {

				nextRequiresComma = false;

				addSpaceIfNecessary();

				builder.append("FROM ");
			}

			if (segment instanceof From || segment instanceof Join) {
				inSelectList = false;
			}

			if (segment instanceof Distinct && !hasDistinct) {
				builder.append("DISTINCT ");
				hasDistinct = true;
			}

			if (segment instanceof OrderByField && !hasOrderBy) {

				addSpaceIfNecessary();

				builder.append("ORDER BY ");
				nextRequiresComma = false;
				hasOrderBy = true;
			}

			if (segment instanceof SimpleFunction) {

				nextRequiresComma = false;
				builder.append(((SimpleFunction) segment).getFunctionName()).append("(");
			}

			if (segment instanceof Table && segments.peek() instanceof From) {

				addCommaIfNecessary();

				Table table = (Table) segment;

				builder.append(table.getName());

				ifAliased(segment, aliased -> builder.append(" AS ").append(aliased.getAlias()));

				nextRequiresComma = true;
			}

			if (segment instanceof Join) {

				addSpaceIfNecessary();

				Join join = (Join) segment;

				builder.append(join.getType());
			}

			if (segment instanceof Table && segments.peek() instanceof Join) {

				addSpaceIfNecessary();

				Table table = (Table) segment;

				builder.append(table.getName());
				ifAliased(segment, aliased -> builder.append(" AS ").append(aliased.getAlias()));
			}

			if (segment instanceof Condition) {
				nextRequiresComma = false;
			}

			if (segment instanceof Condition && segments.peek() instanceof Join) {

				addSpaceIfNecessary();

				builder.append("ON ");
			}

			if ((segment instanceof Expression || segment instanceof Condition) && segments.peek() instanceof Condition) {

				if (segment instanceof Expression) {

					if (!nextExpressionRequiresContinue) {
						nextExpressionRequiresContinue = true;
					} else {

						renderCombinator((Condition) segments.peek());
						nextExpressionRequiresContinue = false;
					}
				}
			}

			if (segment instanceof Condition && segments.peek() instanceof Condition) {

				if (!nextConditionRequiresContinue) {
					nextConditionRequiresContinue = true;
				} else {

					renderCombinator((Condition) segments.peek());
					nextConditionRequiresContinue = false;
				}
			}

			segments.add(segment);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.relational.core.sql.Visitor#leave(org.springframework.data.relational.core.sql.Visitable)
		 */
		@Override
		public void leave(Visitable segment) {

			segments.pop();
			Visitable parent = segments.isEmpty() ? null : segments.peek();

			if (segment instanceof Condition) {
				nextExpressionRequiresContinue = false;
			}

			if (segment instanceof Select) {

				// Postgres syntax
				Select select = (Select) segment;

				OptionalLong limit = select.getLimit();
				OptionalLong offset = select.getOffset();

				limit.ifPresent(count -> {
					addSpaceIfNecessary();
					builder.append("LIMIT ").append(count);
				});

				offset.ifPresent(count -> {
					addSpaceIfNecessary();
					builder.append("OFFSET ").append(count);
				});
			}

			if (segment instanceof Table && parent instanceof Column) {

				if (inSelectList || !(parent instanceof Aliased)) {

					if (nextRequiresComma) {
						builder.append(", ");
						nextRequiresComma = false;
					}

					builder.append(((Table) segment).getReferenceName()).append('.');
				}
			}

			if (segment instanceof Column
					&& (parent instanceof Select || parent instanceof Distinct || parent instanceof SimpleFunction)) {

				addCommaIfNecessary();

				Column column = (Column) segment;

				builder.append(column.getName());

				if (!(parent instanceof SimpleFunction)) {
					ifAliased(segment, aliased -> builder.append(" AS ").append(aliased.getAlias()));
				}

				nextRequiresComma = true;
			}

			if (segment instanceof Column && (parent instanceof Condition || parent instanceof OrderByField)) {

				addCommaIfNecessary();

				Column column = (Column) segment;

				builder.append(column.getReferenceName());
			}

			if (segment instanceof OrderByField) {

				OrderByField orderBy = (OrderByField) segment;

				if (orderBy.getDirection() != null) {
					builder.append(' ').append(orderBy.getDirection());
				}
				nextRequiresComma = true;
			}

			if (segment instanceof SimpleFunction) {

				builder.append(")");
				ifAliased(segment, aliased -> builder.append(" AS ").append(aliased.getAlias()));

				nextRequiresComma = true;
			}

			if (segment instanceof SimpleCondition) {

				nextRequiresComma = false;

				SimpleCondition condition = (SimpleCondition) segment;

				builder.append(' ').append(condition.getPredicate()).append(' ').append(condition.getPredicate());
			}
		}

		private void addCommaIfNecessary() {
			if (nextRequiresComma) {
				builder.append(", ");
			}
		}

		private void addSpaceIfNecessary() {

			if (requiresSpace()) {
				builder.append(' ');
			}
		}

		private void renderCombinator(Condition condition) {

			if (condition instanceof Equals) {
				builder.append(" = ");
			}

			if (condition instanceof AndCondition) {
				builder.append(" AND ");
			}

			if (condition instanceof OrCondition) {
				builder.append(" OR ");
			}
		}

		private boolean requiresSpace() {
			return builder.length() != 0 && builder.charAt(builder.length() - 1) != ' ';
		}

		private void ifAliased(Object segment, Consumer<Aliased> aliasedConsumer) {

			if (segment instanceof Aliased) {
				aliasedConsumer.accept((Aliased) segment);
			}
		}
	}
}
