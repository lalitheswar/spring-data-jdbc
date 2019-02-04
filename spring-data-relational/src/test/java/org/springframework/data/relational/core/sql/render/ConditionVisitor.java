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

import org.springframework.data.relational.core.sql.AndCondition;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Equals;
import org.springframework.data.relational.core.sql.In;
import org.springframework.data.relational.core.sql.IsNull;
import org.springframework.data.relational.core.sql.OrCondition;

/**
 * {@link org.springframework.data.relational.core.sql.Visitor} delegating {@link Condition} rendering to condition {@link org.springframework.data.relational.core.sql.Visitor}s.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 * @see AndCondition
 * @see OrCondition
 * @see IsNull
 * @see Equals
 * @see In
 */
class ConditionVisitor extends TypedSubtreeVisitor<Condition> implements PartRenderer {

	private StringBuilder builder = new StringBuilder();

	@Override
	DelegatingVisitor enterMatched(Condition segment) {

		if (segment instanceof AndCondition) {
			return new MultiConcatConditionVisitor((AndCondition) segment, builder::append);
		}

		if (segment instanceof OrCondition) {
			return new MultiConcatConditionVisitor((OrCondition) segment, builder::append);
		}

		if (segment instanceof IsNull) {
			return new IsNullVisitor(builder::append);
		}

		if (segment instanceof Equals) {
			return new ComparisonVisitor((Equals) segment, builder::append);
		}

		if (segment instanceof In) {
			return new InVisitor(builder::append);
		}

		return this;
	}

	@Override
	public CharSequence getRenderedPart() {
		return builder;
	}
}
