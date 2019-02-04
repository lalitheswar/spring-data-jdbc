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
package org.springframework.data.relational.core.sql.render;

import java.util.OptionalLong;

import org.springframework.data.relational.core.sql.From;
import org.springframework.data.relational.core.sql.Join;
import org.springframework.data.relational.core.sql.OrderByField;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectList;
import org.springframework.data.relational.core.sql.Visitable;
import org.springframework.data.relational.core.sql.Where;

/**
 * {@link PartRenderer} for {@link Select} statements.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 */
class SelectStatementVisitor extends DelegatingVisitor implements PartRenderer {

	private StringBuilder builder = new StringBuilder();
	private StringBuilder selectList = new StringBuilder();
	private StringBuilder from = new StringBuilder();
	private StringBuilder join = new StringBuilder();
	private StringBuilder where = new StringBuilder();

	private SelectListVisitor selectListVisitor = new SelectListVisitor(selectList::append);
	private OrderByClauseVisitor orderByClauseVisitor = new OrderByClauseVisitor();
	private FromClauseVisitor fromClauseVisitor = new FromClauseVisitor(it -> {

		if (from.length() != 0) {
			from.append(", ");
		}

		from.append(it);
	});

	private WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor(where::append);


	@Override
	public DelegatingVisitor doEnter(Visitable segment) {

		if (segment instanceof SelectList) {
			return selectListVisitor;
		}

		if (segment instanceof OrderByField) {
			return orderByClauseVisitor;
		}

		if (segment instanceof From) {
			return fromClauseVisitor;
		}

		if (segment instanceof Join) {
			return new JoinVisitor(it -> {

				if (join.length() != 0) {
					join.append(' ');
				}

				join.append(it);
			});
		}

		if (segment instanceof Where) {
			return whereClauseVisitor;
		}

		return this;
	}

	@Override
	public DelegatingVisitor doLeave(Visitable segment) {

		if (segment instanceof Select) {

			builder.append("SELECT ");
			if (((Select) segment).isDistinct()) {
				builder.append("DISTINCT ");
			}

			builder.append(selectList);

			if (from.length() != 0) {
				builder.append(" FROM ").append(from);
			}

			if (join.length() != 0) {
				builder.append(' ').append(join);
			}

			if (where.length() != 0) {
				builder.append(" WHERE ").append(where);
			}

			CharSequence orderBy = orderByClauseVisitor.getRenderedPart();
			if (orderBy.length() != 0)
				builder.append(" ORDER BY ").append(orderBy);

			OptionalLong limit = ((Select) segment).getLimit();
			if (limit.isPresent()) {
				builder.append(" LIMIT ").append(limit.getAsLong());
			}

			OptionalLong offset = ((Select) segment).getOffset();
			if (offset.isPresent()) {
				builder.append(" OFFSET ").append(offset.getAsLong());
			}

			return null;
		}

		return this;
	}

	@Override
	public CharSequence getRenderedPart() {
		return builder;
	}
}
