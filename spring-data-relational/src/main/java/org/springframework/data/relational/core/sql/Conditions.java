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

import org.springframework.util.Assert;

/**
 * Factory for common {@link Condition}s.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 * @see SQL
 * @see Expressions
 * @see Functions
 */
public abstract class Conditions {

	/**
	 * Creates a plain {@code sql} {@link Condition}.
	 *
	 * @param sql the SQL, must not be {@literal null} or empty.
	 * @return a SQL {@link Expression}.
	 */
	public static Condition just(String sql) {
		return new ConstantCondition(sql);
	}

	/**
	 * Creates a {@code IS NULL} condition.
	 *
	 * @param expression the expression to check for nullability, must not be {@literal null}.
	 * @return the {@code IS NULL} condition.
	 */
	public static Condition isNull(Expression expression) {
		return IsNull.create(expression);
	}

	/**
	 * Creates a {@code =} (equals) {@link Condition}.
	 *
	 * @param leftColumnOrExpression left side of the comparison.
	 * @param rightColumnOrExpression right side of the comparison.
	 * @return the {@link Equals} condition.
	 */
	public static Equals isEqual(Expression leftColumnOrExpression, Expression rightColumnOrExpression) {
		return Equals.create(leftColumnOrExpression, rightColumnOrExpression);
	}

	/**
	 * Creates a {@code IN} {@link Condition clause}.
	 *
	 * @param columnOrExpression left side of the comparison.
	 * @param arg IN argument.
	 * @return the {@link In} condition.
	 */
	public static Condition in(Expression columnOrExpression, Expression arg) {

		Assert.notNull(columnOrExpression, "Comparison column or expression must not be null");
		Assert.notNull(columnOrExpression, "Expression argument must not be null");

		return In.create(columnOrExpression, arg);
	}

	/**
	 * Creates a {@code IN} {@link Condition clause} for a {@link Select subselect}.
	 *
	 * @param Column the column to compare.
	 * @param subselect the subselect.
	 * @return the {@link In} condition.
	 */
	public static Condition in(Column column, Select subselect) {

		Assert.notNull(column, "Column must not be null");
		Assert.notNull(subselect, "Subselect must not be null");

		return in(column, new SubselectExpression(subselect));
	}

	static class ConstantCondition extends AbstractSegment implements Condition {

		private final String condition;

		ConstantCondition(String condition) {
			this.condition = condition;
		}

		@Override
		public String toString() {
			return condition;
		}
	}

	// Utility constructor.
	private Conditions() {
	}
}




