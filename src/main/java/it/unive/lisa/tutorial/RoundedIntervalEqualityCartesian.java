package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

/**
 * The Cartesian product domain combining the Equality and RoundedInterval
 * domains.
 * This domain tracks both variable equality relationships and numeric intervals
 * simultaneously, maintaining precision from both components during analysis.
 */
public class RoundedIntervalEqualityCartesian
        extends
        CartesianProduct<RoundedIntervalEqualityCartesian, Equality, ValueEnvironment<RoundedInterval>, ValueExpression, Identifier>
        implements ValueDomain<RoundedIntervalEqualityCartesian> {
    /**
     * Default constructor initializes with fresh instances of both domains
     */
    public RoundedIntervalEqualityCartesian() {
        this(new Equality(), new ValueEnvironment<>(new RoundedInterval()));
    }

    /**
     * Creates a Cartesian product from existing domain instances
     * 
     * @param left  The Equality domain component
     * @param right The RoundedInterval domain component
     */
    public RoundedIntervalEqualityCartesian(Equality left, ValueEnvironment<RoundedInterval> right) {
        super(left, right);
    }

    @Override
    /**
     * Factory method to create new instances of the Cartesian product
     * 
     * @param equality              The Equality domain component
     * @param roundedIntervalDomain The RoundedInterval domain component
     * @return New instance combining both domains
     */
    public RoundedIntervalEqualityCartesian mk(Equality equality,
            ValueEnvironment<RoundedInterval> roundedIntervalDomain) {
        return new RoundedIntervalEqualityCartesian(equality, roundedIntervalDomain);
    }

    @Override
    /**
     * Checks if an identifier is known to the Equality domain component
     * 
     * @param identifier The identifier to check
     * @return true if the identifier exists in the Equality domain
     */
    public boolean knowsIdentifier(Identifier identifier) {
        return left.knowsIdentifier(identifier);
    }
}