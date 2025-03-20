package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.StringLength;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.numeric.IntInterval;
import it.unive.lisa.util.numeric.MathNumber;
import it.unive.lisa.util.numeric.MathNumberConversionException;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Objects;

public class RoundedInterval
		implements BaseNonRelationalValueDomain<RoundedInterval> {

	public static final RoundedInterval ZERO = new RoundedInterval(IntInterval.ZERO);
	public static final RoundedInterval TOP = new RoundedInterval(IntInterval.INFINITY);
	public static final RoundedInterval BOTTOM = new RoundedInterval(new IntInterval(MathNumber.MINUS_INFINITY,MathNumber.MINUS_INFINITY));

	// The abstract information carried by this instance is an interval for a single variable
	public final IntInterval interval;
	
	// Rounding mode: -1 = round down, 0 = no rounding, 1 = round up
	private final int roundingMode;
	
	public RoundedInterval(
			IntInterval interval) {
		this(interval, 0);
	}
	
	public RoundedInterval(
			IntInterval interval,
			int roundingMode) {
		this.interval = interval;
		this.roundingMode = roundingMode;
	}

	public RoundedInterval(
			MathNumber low,
			MathNumber high) {
		this(new IntInterval(low, high), 0);
	}
	
	public RoundedInterval(
			MathNumber low,
			MathNumber high,
			int roundingMode) {
		this(new IntInterval(low, high), roundingMode);
	}


	public RoundedInterval() {
		this(IntInterval.INFINITY, 0);
	}

	@Override
	public int hashCode() {
		return Objects.hash(interval, roundingMode);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		RoundedInterval other = (RoundedInterval) o;
		return Objects.equals(interval, other.interval) && roundingMode == other.roundingMode;
	}

	@Override
	public RoundedInterval top() {
		return TOP;
	}

	@Override
	public RoundedInterval bottom() {
		return BOTTOM;
	}

	@Override
	public boolean lessOrEqualAux(
			RoundedInterval other)
			throws SemanticException {
		return other.interval.includes(interval);
	}

	@Override
	public RoundedInterval lubAux(
			RoundedInterval other)
			throws SemanticException {
		MathNumber newLow = interval.getLow().min(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().max(other.interval.getHigh());
		int newRoundingMode = Math.max(roundingMode, other.roundingMode);
		return newLow.isMinusInfinity() && newHigh.isPlusInfinity() 
			? top() 
			: new RoundedInterval(newLow, newHigh, newRoundingMode);
	}

	@Override
	public RoundedInterval glbAux(
			RoundedInterval other) {
		MathNumber newLow = interval.getLow().max(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().min(other.interval.getHigh());

		if (newLow.compareTo(newHigh) > 0)
			return bottom();
		int newRoundingMode = Math.min(roundingMode, other.roundingMode);
		return newLow.isMinusInfinity() && newHigh.isPlusInfinity() 
			? top() 
			: new RoundedInterval(newLow, newHigh, newRoundingMode);
	}

	@Override
	public RoundedInterval wideningAux(
			RoundedInterval other)
			throws SemanticException {
		MathNumber newLow, newHigh;
		if (other.interval.getHigh().compareTo(interval.getHigh()) > 0)
			newHigh = MathNumber.PLUS_INFINITY;
		else
			newHigh = interval.getHigh();

		if (other.interval.getLow().compareTo(interval.getLow()) < 0)
			newLow = MathNumber.MINUS_INFINITY;
		else
			newLow = interval.getLow();
			
		// Keep the rounding mode from this instance
		return newLow.isMinusInfinity() && newHigh.isPlusInfinity() 
			? top() 
			: new RoundedInterval(newLow, newHigh, roundingMode);
	}
	
	/**
	 * Applies rounding to a result based on the current rounding mode.
	 * 
	 * @param result the interval to round
	 * @return the rounded interval
	 */
	private RoundedInterval applyRounding(IntInterval result) throws MathNumberConversionException {
		// If no rounding needed or interval is invalid, return as is
		if (roundingMode == 0 || result == null){
			return new RoundedInterval(result, roundingMode);
		}
		
		MathNumber low = result.getLow();
		MathNumber high = result.getHigh();
		
		// When rounding down (mode -1), we decrease the upper bound if it's not an integer
		if (roundingMode < 0) {
			if (!high.isPlusInfinity()) {
				// Check if high is not an integer (i.e., has decimal part)
				if (high.toDouble() != Math.floor(high.toDouble())) {
					high = new MathNumber(Math.floor(high.toDouble()));
				}
			}
		}
		// When rounding up (mode 1), we increase the lower bound if it's not an integer
		else if (roundingMode > 0) {
			if (!low.isMinusInfinity()) {
				// Check if low is not an integer (i.e., has decimal part)
				if (low.toDouble() != Math.ceil(low.toDouble())) {
					low = new MathNumber(Math.ceil(low.toDouble()));
				}
			}
		}
		
		return new RoundedInterval(new IntInterval(low, high), roundingMode);
	}

	@Override
	public StructuredRepresentation representation() {
		if (isBottom())
			return Lattice.bottomRepresentation();
		
		String roundingIndicator = "";
		if (roundingMode < 0)
			roundingIndicator = "↓";
		else if (roundingMode > 0)
			roundingIndicator = "↑";
			
		return new StringRepresentation(interval.toString() + roundingIndicator);
	}

	@Override
	public String toString() {
		return representation().toString();
	}

	// Logic for evaluating expressions below

	@Override
	public RoundedInterval evalNonNullConstant(
			Constant constant,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (constant.getValue() instanceof Integer) {
			Integer i = (Integer) constant.getValue();
			return new RoundedInterval(new MathNumber(i), new MathNumber(i), roundingMode);
		} else if (constant.getValue() instanceof Float || constant.getValue() instanceof Double) {
			// For floating point constants, we apply the current rounding mode
			Number n = (Number) constant.getValue();
			double value = n.doubleValue();
			if (roundingMode < 0)
				value = Math.floor(value);
			else if (roundingMode > 0)
				value = Math.ceil(value);
			return new RoundedInterval(new MathNumber(value), new MathNumber(value), roundingMode);
		}

		return top();
	}

	@Override
	public RoundedInterval evalUnaryExpression(
			UnaryOperator operator,
			RoundedInterval arg,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (operator == NumericNegation.INSTANCE)
			if (arg.isTop())
				return top();
			else {
                try {
                    return applyRounding(arg.interval.mul(IntInterval.MINUS_ONE));
                } catch (MathNumberConversionException e) {
                    throw new RuntimeException(e);
                }
            }
		else if (operator == StringLength.INSTANCE)
			return new RoundedInterval(MathNumber.ZERO, MathNumber.PLUS_INFINITY, roundingMode);
		else
			return top();
	}

	@Override
	public RoundedInterval evalBinaryExpression(
			BinaryOperator operator,
			RoundedInterval left,
			RoundedInterval right,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (!(operator instanceof DivisionOperator) && (left.isTop() || right.isTop()))
			// with div, we can return zero or bottom even if one of the
			// operands is top
			return top();

		// Combine the rounding modes - take the most extreme one
		int newRoundingMode = (left.roundingMode != 0) ? left.roundingMode : right.roundingMode;

		try {
			if (operator instanceof AdditionOperator)
				return applyRounding(left.interval.plus(right.interval));
			else if (operator instanceof SubtractionOperator)
				return applyRounding(left.interval.diff(right.interval));
			else if (operator instanceof MultiplicationOperator) {
				if (left.equals(ZERO) || right.equals(ZERO))
					return new RoundedInterval(IntInterval.ZERO, newRoundingMode);
				else {
					// Special care for multiplication which can increase rounding errors
					IntInterval result = left.interval.mul(right.interval);
					RoundedInterval roundedResult = new RoundedInterval(result, newRoundingMode);
					// If both operands have the same rounding direction, we intensify the effect
					if (left.roundingMode == right.roundingMode && left.roundingMode != 0) {
						return applyRounding(result);
					}
					return applyRounding(result);
				}
			} else if (operator instanceof DivisionOperator) {
				if (right.equals(ZERO))
					return bottom();
				else if (left.equals(ZERO))
					return new RoundedInterval(IntInterval.ZERO, newRoundingMode);
				else if (left.isTop() || right.isTop())
					return top();
				else {
					// Division almost always requires rounding for non-integer results
					IntInterval result = left.interval.div(right.interval, false, false);
					if (result == null || result.equals(IntInterval.MINUS_ONE))
						return bottom();

					// Always apply rounding to division results, even if roundingMode is 0
					int divRoundingMode = (newRoundingMode != 0) ? newRoundingMode : 1; // Default to round up for safety
					return new RoundedInterval(result, divRoundingMode);
				}
			}

		} catch (MathNumberConversionException e) {
			return top();
		}

		return top();
	}

	@Override
	public ValueEnvironment<RoundedInterval> assumeBinaryExpression(
			ValueEnvironment<RoundedInterval> environment,
			BinaryOperator operator,
			ValueExpression left,
			ValueExpression right,
			ProgramPoint src,
			ProgramPoint dest,
			SemanticOracle oracle)
			throws SemanticException {
		Identifier id;
		RoundedInterval eval;
		boolean rightIsExpr;
		if (left instanceof Identifier) {
			eval = eval(right, environment, src, oracle);
			id = (Identifier) left;
			rightIsExpr = true;
		} else if (right instanceof Identifier) {
			eval = eval(left, environment, src, oracle);
			id = (Identifier) right;
			rightIsExpr = false;
		} else
			return environment;

		RoundedInterval starting = environment.getState(id);
		if (eval.isBottom() || starting.isBottom())
			return environment.bottom();

		boolean lowIsMinusInfinity = eval.interval.getLow().isMinusInfinity();
		RoundedInterval low_inf = new RoundedInterval(eval.interval.getLow(), MathNumber.PLUS_INFINITY, eval.roundingMode);
		RoundedInterval lowp1_inf = new RoundedInterval(eval.interval.getLow().add(MathNumber.ONE), MathNumber.PLUS_INFINITY, eval.roundingMode);
		RoundedInterval inf_high = new RoundedInterval(MathNumber.MINUS_INFINITY, eval.interval.getHigh(), eval.roundingMode);
		RoundedInterval inf_highm1 = new RoundedInterval(MathNumber.MINUS_INFINITY, eval.interval.getHigh().subtract(MathNumber.ONE), eval.roundingMode);

		RoundedInterval update = null;
		if (operator == ComparisonEq.INSTANCE)
			update = eval;
		else if (operator == ComparisonGe.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? null : starting.glb(low_inf);
			else
				update = starting.glb(inf_high);
		else if (operator == ComparisonGt.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
			else
				update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
		else if (operator == ComparisonLe.INSTANCE)
			if (rightIsExpr)
				update = starting.glb(inf_high);
			else
				update = lowIsMinusInfinity ? null : starting.glb(low_inf);
		else if (operator == ComparisonLt.INSTANCE)
			if (rightIsExpr)
				update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
			else
				update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);

		if (update == null)
			return environment;
		else if (update.isBottom())
			return environment.bottom();
		else
			return environment.putState(id, update);
	}
}