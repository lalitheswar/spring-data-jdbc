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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@code IN} {@link Condition} clause.
 *
 * @author Jens Schauder
 * @author Mark Paluch
 */
public class In extends AbstractSegment implements Condition {

	private final Expression left;
	private final Collection<Expression> expressions;

	private In(Expression left, Collection<Expression> expressions) {

		super(toArray(left, expressions));

		this.left = left;
		this.expressions = expressions;
	}

	private static Segment[] toArray(Expression expression, Collection<Expression> expressions) {

		Segment[] segments = new Segment[1 + expressions.size()];
		segments[0] = expression;

		int index = 1;

		for (Expression e : expressions) {
			segments[index++] = e;
		}

		return segments;
	}

	/**
	 * Creates a new {@link In} {@link Condition} given left and right {@link Expression}s.
	 *
	 * @param columnOrExpression left hand side of the {@link Condition} must not be {@literal null}.
	 * @param arg right hand side (collection {@link Expression}) must not be {@literal null}.
	 * @return the {@link In} {@link Condition}.
	 */
	public static Condition create(Expression columnOrExpression, Expression arg) {

		Assert.notNull(columnOrExpression, "Comparison column or expression must not be null");
		Assert.notNull(arg, "Expression argument must not be null");

		return new In(columnOrExpression, Collections.singletonList(arg));
	}

	/**
	 * Creates a new {@link In} {@link Condition} given left and right {@link Expression}s.
	 *
	 * @param columnOrExpression left hand side of the {@link Condition} must not be {@literal null}.
	 * @param expressions right hand side (collection {@link Expression}) must not be {@literal null}.
	 * @return the {@link In} {@link Condition}.
	 */
	public static Condition create(Expression columnOrExpression, Collection<? extends Expression> expressions) {

		Assert.notNull(columnOrExpression, "Comparison column or expression must not be null");
		Assert.notNull(expressions, "Expression argument must not be null");

		return new In(columnOrExpression, new ArrayList<>(expressions));
	}

	/**
	 * Creates a new {@link In} {@link Condition} given left and right {@link Expression}s.
	 *
	 * @param columnOrExpression left hand side of the {@link Condition} must not be {@literal null}.
	 * @param expressions right hand side (collection {@link Expression}) must not be {@literal null}.
	 * @return the {@link In} {@link Condition}.
	 */
	public static Condition create(Expression columnOrExpression, Expression... expressions) {

		Assert.notNull(columnOrExpression, "Comparison column or expression must not be null");
		Assert.notNull(expressions, "Expression argument must not be null");

		return new In(columnOrExpression, Arrays.asList(expressions));
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return left + " IN (" + StringUtils.collectionToDelimitedString(expressions, ", ") + ")";
	}
}
