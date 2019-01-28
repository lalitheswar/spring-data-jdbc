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
			if (segment instanceof Where) {
				visitors.pop();
				builder.append(" WHERE ");
				visitors.push(new WhereClauseVisitor());
			}

			visitors.peek().enter(segment);
		}

		@Override
		public void leave(Visitable segment) {
			if (segment instanceof Select) {
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

		private class WhereClauseVisitor implements Visitor {
			@Override
			public void enter(Visitable segment) {
				if (segment instanceof IsNull) {
					visitors.push(new ExpressionVisitor());
				}
			}

			@Override
			public void leave(Visitable segment) {
				if (segment instanceof IsNull) {
					if (((IsNull) segment).isNegated()) {
						builder.append(" IS NOT NULL");
					} else {
						builder.append(" IS NULL");
					}
				}
			}
		}

		private class ExpressionVisitor implements Visitor {
			@Override
			public void enter(Visitable segment) {
				if (segment instanceof Column) {
					builder.append(((Column) segment).getTable().getName()) //
							.append('.') //
							.append(((Column) segment).getName());

				}
			}

			@Override
			public void leave(Visitable segment) {
				visitors.pop();
			}
		}

		private class SkipVisitor implements Visitor {

			@Override
			public void enter(Visitable segment) {

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

}
