package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

public class RoundedIntervalEqualityCartesian
    extends CartesianProduct<RoundedIntervalEqualityCartesian, Equality, ValueEnvironment<RoundedInterval>, ValueExpression, Identifier>
    implements ValueDomain<RoundedIntervalEqualityCartesian>
{
    public RoundedIntervalEqualityCartesian() {
        this(new Equality(), new ValueEnvironment<>(new RoundedInterval()));
    }

    public RoundedIntervalEqualityCartesian(Equality left, ValueEnvironment<RoundedInterval> right) {
        super(left, right);
    }

    @Override
    public RoundedIntervalEqualityCartesian mk(Equality equality, ValueEnvironment<RoundedInterval> roundedIntervalDomain) {
        return new RoundedIntervalEqualityCartesian(equality, roundedIntervalDomain);
    }

    @Override
    public boolean knowsIdentifier(Identifier identifier) {
        return left.knowsIdentifier(identifier);
    }
}