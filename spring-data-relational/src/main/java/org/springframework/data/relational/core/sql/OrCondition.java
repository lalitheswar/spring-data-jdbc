package org.springframework.data.relational.core.sql;

import org.springframework.util.Assert;

/**
 * {@link Condition} representing an {@code OR} relation between two {@link Condition}s.
 *
 * @author Mark Paluch
 * @see Condition#or(Condition)
 */
public class OrCondition implements Condition {

	private final Condition left;
	private final Condition right;

	OrCondition(Condition left, Condition right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public void visit(Visitor visitor) {

		Assert.notNull(visitor, "Visitor must not be null!");

		visitor.enter(this);
		left.visit(visitor);
		right.visit(visitor);
		visitor.leave(this);
	}

	/**
	 * @return the left {@link Condition}.
	 */
	public Condition getLeft() {
		return left;
	}

	/**
	 * @return the right {@link Condition}.
	 */
	public Condition getRight() {
		return right;
	}

	@Override
	public String toString() {
		return left.toString() + " OR " + right.toString();
	}
}

