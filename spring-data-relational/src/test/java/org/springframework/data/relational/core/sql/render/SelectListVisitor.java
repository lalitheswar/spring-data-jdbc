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

import org.springframework.data.relational.core.sql.Aliased;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.SelectList;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.core.sql.Visitable;

/**
 * {@link PartRenderer} for {@link SelectList}s.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 */
class SelectListVisitor extends TypedSubtreeVisitor<SelectList> implements PartRenderer {

	private final StringBuilder builder = new StringBuilder();
	private final RenderTarget target;
	private boolean requiresComma = false;
	private boolean insideFunction = false; // this is hackery and should be fix with a proper visitor for
	// subelements.

	SelectListVisitor(RenderTarget target) {
		this.target = target;
	}

	@Override
	DelegatingVisitor enterNested(Visitable segment) {

		if (requiresComma) {
			builder.append(", ");
			requiresComma = false;
		}
		if (segment instanceof SimpleFunction) {
			builder.append(((SimpleFunction) segment).getFunctionName()).append("(");
			insideFunction = true;
		} else {
			insideFunction = false;
		}

		return this;
	}

	@Override
	DelegatingVisitor leaveMatched(SelectList segment) {

		target.onRendered(builder);
		return super.leaveMatched(segment);
	}

	@Override
	DelegatingVisitor leaveNested(Visitable segment) {

		if (segment instanceof Table) {
			builder.append(((Table) segment).getReferenceName()).append('.');
		}

		if (segment instanceof SimpleFunction) {
			builder.append(")");
			requiresComma = true;
		} else if (segment instanceof Column) {
			builder.append(((Column) segment).getName());
			if (segment instanceof Aliased) {
				builder.append(" AS ").append(((Aliased) segment).getAlias());
			}
			requiresComma = true;
		}

		return this;
	}

	@Override
	public CharSequence getRenderedPart() {
		return builder;
	}
}
