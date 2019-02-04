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

import org.springframework.data.relational.core.sql.BindMarker;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Named;
import org.springframework.data.relational.core.sql.SubselectExpression;
import org.springframework.data.relational.core.sql.Visitable;

/**
 * {@link PartRenderer} for {@link Expression}s.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 * @see Column
 * @see SubselectExpression
 */
class ExpressionVisitor extends TypedSubtreeVisitor<Expression> implements PartRenderer {

	private CharSequence value = "";
	private PartRenderer partRenderer;

	ExpressionVisitor() {
	}

	@Override
	DelegatingVisitor enterMatched(Expression segment) {

		if (segment instanceof SubselectExpression) {

			SelectStatementVisitor visitor = new SelectStatementVisitor();
			partRenderer = visitor;
			return visitor;
		}

		if (segment instanceof Column) {
			value = ((Column) segment).getTable().getReferenceName() + "." + ((Column) segment).getReferenceName();
		} else if (segment instanceof BindMarker) {

			if (segment instanceof Named) {
				value = ":" + ((Named) segment).getName();
			} else {
				value = segment.toString();
			}
		}

		return this;
	}

	@Override
	DelegatingVisitor enterNested(Visitable segment) {

		if (segment instanceof Condition) {
			ConditionVisitor visitor = new ConditionVisitor();
			partRenderer = visitor;
			return visitor;
		}

		return super.enterNested(segment);
	}

	@Override
	DelegatingVisitor leaveMatched(Expression segment) {

		if (partRenderer != null) {
			value = partRenderer.getRenderedPart();
			partRenderer = null;
		}

		return super.leaveMatched(segment);
	}

	@Override
	public CharSequence getRenderedPart() {
		return value;
	}
}
