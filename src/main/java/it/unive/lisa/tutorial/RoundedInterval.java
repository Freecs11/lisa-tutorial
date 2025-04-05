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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The RoundedInterval domain tracks numeric values using intervals with
 * configurable
 * rounding directions and precision. It maintains both lower and upper bounds
 * for variables while applying specified rounding rules during arithmetic
 * operations.
 *
 * <p>
 * This domain supports configurable precision (decimal places) and rounding
 * directions (up, down, none) to model different numerical analysis scenarios.
 * </p>
 */
public class RoundedInterval
		implements BaseNonRelationalValueDomain<RoundedInterval> {

	public static final RoundedInterval ZERO = new RoundedInterval(IntInterval.ZERO);
	public static final RoundedInterval TOP = new RoundedInterval(IntInterval.INFINITY);
	public static final RoundedInterval BOTTOM = new RoundedInterval(
			new IntInterval(MathNumber.MINUS_INFINITY, MathNumber.MINUS_INFINITY));

	// Default rounding direction: -1 = down, 0 = none, 1 = up
	private static final int DEFAULT_ROUNDING_DIRECTION = 0;

	// Default precision is 2 digits after decimal point
	private static final int DEFAULT_PRECISION = 2;

	// The abstract information carried by this instance is an interval for a single
	// variable
	public final IntInterval interval;

	// Rounding direction: -1 = round down, 0 = no rounding, 1 = round up
	private final int roundingDirection;

	// Precision: number of decimal places to keep (e.g., 2 means round to 2 decimal
	// places)
	private final int precision;

	/**
	 * Constructs a RoundedInterval with default rounding direction and precision
	 * 
	 * @param interval The initial interval bounds
	 */
	public RoundedInterval(IntInterval interval) {
		this(interval, DEFAULT_ROUNDING_DIRECTION, DEFAULT_PRECISION);
	}

	/**
	 * Constructs a RoundedInterval with specified rounding direction and default
	 * precision
	 * 
	 * @param interval          The initial interval bounds
	 * @param roundingDirection -1 for down, 0 for none, 1 for up
	 */
	public RoundedInterval(IntInterval interval, int roundingDirection) {
		this(interval, roundingDirection, DEFAULT_PRECISION);
	}

	/**
	 * Primary constructor for RoundedInterval with full configuration
	 * 
	 * @param interval          The initial interval bounds
	 * @param roundingDirection -1 for down, 0 for none, 1 for up
	 * @param precision         Number of decimal places to maintain (≥0)
	 */
	public RoundedInterval(IntInterval interval, int roundingDirection, int precision) {
		this.interval = interval;
		this.roundingDirection = roundingDirection;
		this.precision = Math.max(0, precision); // Precision cannot be negative
	}

	/**
	 * Constructs from MathNumber bounds with default rounding/precision
	 * 
	 * @param low  Lower bound of the interval
	 * @param high Upper bound of the interval
	 */
	public RoundedInterval(MathNumber low, MathNumber high) {
		this(new IntInterval(low, high), DEFAULT_ROUNDING_DIRECTION, DEFAULT_PRECISION);
	}

	public RoundedInterval(MathNumber low, MathNumber high, int roundingDirection) {
		this(new IntInterval(low, high), roundingDirection, DEFAULT_PRECISION);
	}

	public RoundedInterval(MathNumber low, MathNumber high, int roundingDirection, int precision) {
		this(new IntInterval(low, high), roundingDirection, precision);
	}

	public RoundedInterval() {
		this(IntInterval.INFINITY, DEFAULT_ROUNDING_DIRECTION, DEFAULT_PRECISION);
	}

	@Override
	public int hashCode() {
		return Objects.hash(interval, roundingDirection, precision);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		RoundedInterval other = (RoundedInterval) o;
		return Objects.equals(interval, other.interval) &&
				roundingDirection == other.roundingDirection &&
				precision == other.precision;
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
	/**
	 * Checks if this interval is less than or equal to another in the lattice
	 * 
	 * @param other The other interval to compare with
	 * @return true if this interval is contained within the other
	 * @throws SemanticException If semantic analysis fails
	 */
	public boolean lessOrEqualAux(
			RoundedInterval other)
			throws SemanticException {
		return other.interval.includes(interval);
	}

	@Override
	/**
	 * Computes the least upper bound (join) of two intervals
	 * 
	 * @param other The other interval to join with
	 * @return New interval covering both inputs with conservative
	 *         rounding/precision
	 * @throws SemanticException If semantic analysis fails
	 */
	public RoundedInterval lubAux(
			RoundedInterval other)
			throws SemanticException {
		MathNumber newLow = interval.getLow().min(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().max(other.interval.getHigh());
		// Take the most conservative values from both intervals
		int newRoundingDirection = Math.max(roundingDirection, other.roundingDirection);
		int newPrecision = Math.min(precision, other.precision); // Use the lower precision
		return newLow.isMinusInfinity() && newHigh.isPlusInfinity()
				? top()
				: new RoundedInterval(newLow, newHigh, newRoundingDirection, newPrecision);
	}

	@Override
	/**
	 * Computes the greatest lower bound (meet) of two intervals
	 * 
	 * @param other The other interval to meet with
	 * @return New interval representing the overlap of both inputs with precise
	 *         rounding
	 */
	public RoundedInterval glbAux(
			RoundedInterval other) {
		MathNumber newLow = interval.getLow().max(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().min(other.interval.getHigh());

		if (newLow.compareTo(newHigh) > 0)
			return bottom();

		// Take the least conservative values from both intervals
		int newRoundingDirection = Math.min(roundingDirection, other.roundingDirection);
		int newPrecision = Math.max(precision, other.precision); // Use the higher precision

		return newLow.isMinusInfinity() && newHigh.isPlusInfinity()
				? top()
				: new RoundedInterval(newLow, newHigh, newRoundingDirection, newPrecision);
	}

	@Override
	/**
	 * Performs widening operation to ensure convergence of analysis
	 * 
	 * @param other The previous interval in the iteration
	 * @return New interval with extended bounds where growth is detected
	 * @throws SemanticException If semantic analysis fails
	 */
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

		// Keep the rounding direction and precision from this instance
		return newLow.isMinusInfinity() && newHigh.isPlusInfinity()
				? top()
				: new RoundedInterval(newLow, newHigh, roundingDirection, precision);
	}

	/**
	 * Rounds a double value according to the specified precision and rounding
	 * direction.
	 * 
	 * @param value the value to round
	 * @return the rounded value
	 */
	private double roundValue(double value) {
		if (Double.isInfinite(value) || Double.isNaN(value)) {
			return value; // Can't round infinities or NaN
		}

		BigDecimal bd = BigDecimal.valueOf(value);
		RoundingMode mode;

		if (roundingDirection < 0) {
			mode = RoundingMode.DOWN;
		} else if (roundingDirection > 0) {
			mode = RoundingMode.UP;
		} else {
			mode = RoundingMode.HALF_UP; // Standard mathematical rounding when no direction specified
		}

		return bd.setScale(precision, mode).doubleValue();
	}

	/**
	 * Applies rounding to a result based on the current precision and rounding
	 * direction.
	 * 
	 * @param result the interval to round
	 * @return the rounded interval
	 */
	private RoundedInterval applyRounding(IntInterval result) throws MathNumberConversionException {
		// If interval is invalid, return as is
		if (result == null) {
			return new RoundedInterval(result, roundingDirection, precision);
		}

		MathNumber low = result.getLow();
		MathNumber high = result.getHigh();

		// Only apply rounding to finite values
		if (!low.isMinusInfinity()) {
			// Convert to double to apply precision-based rounding
			double lowValue = low.toDouble();
			// Apply rounding according to precision and direction
			double roundedLow = roundValue(lowValue);
			// Convert back to MathNumber
			low = new MathNumber(roundedLow);
		}

		if (!high.isPlusInfinity()) {
			// Convert to double to apply precision-based rounding
			double highValue = high.toDouble();
			// Apply rounding according to precision and direction
			double roundedHigh = roundValue(highValue);
			// Convert back to MathNumber
			high = new MathNumber(roundedHigh);
		}

		// Create a new interval with the rounded values
		// and preserve the precision and rounding direction
		return new RoundedInterval(new IntInterval(low, high), roundingDirection, precision);
	}

	@Override
	public StructuredRepresentation representation() {
		if (isBottom())
			return Lattice.bottomRepresentation();

		String roundingIndicator = "";
		if (roundingDirection < 0)
			roundingIndicator = "↓";
		else if (roundingDirection > 0)
			roundingIndicator = "↑";

		return new StringRepresentation(interval.toString() + roundingIndicator + "(p=" + precision + ")");
	}

	@Override
	public String toString() {
		return representation().toString();
	}

	// Logic for evaluating expressions below

	@Override
	/**
	 * Evaluates a constant value, applying rounding based on configured precision
	 * 
	 * @param constant The constant to evaluate
	 * @param pp       The program point where evaluation occurs
	 * @param oracle   The semantic oracle for context information
	 * @return A precise interval representing the rounded constant value
	 */
	public RoundedInterval evalNonNullConstant(
			Constant constant,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (constant.getValue() instanceof Integer) {
			Integer i = (Integer) constant.getValue();
			return new RoundedInterval(new MathNumber(i), new MathNumber(i), roundingDirection, precision);
		} else if (constant.getValue() instanceof Float || constant.getValue() instanceof Double) {
			// For floating point constants, apply precise rounding
			Number n = (Number) constant.getValue();
			double value = n.doubleValue();

			// Apply rounding based on precision
			double roundedValue = roundValue(value);

			// Create precise interval with rounded value
			MathNumber mathNumber = new MathNumber(roundedValue);
			return new RoundedInterval(mathNumber, mathNumber, roundingDirection, precision);
		}

		return top();
	}

	@Override
	/**
	 * Evaluates unary expressions, handling numeric negation and string length
	 * 
	 * @param operator The unary operator being applied
	 * @param arg      The operand's interval
	 * @param pp       The program point where evaluation occurs
	 * @param oracle   The semantic oracle for context information
	 * @return Result interval after applying the unary operation
	 */
	public RoundedInterval evalUnaryExpression(
			UnaryOperator operator,
			RoundedInterval arg,
			ProgramPoint pp,
			SemanticOracle oracle) {
		if (operator == NumericNegation.INSTANCE) {
			if (arg.isTop())
				return top();
			else {
				try {
					return applyRounding(arg.interval.mul(IntInterval.MINUS_ONE));
				} catch (MathNumberConversionException e) {
					throw new RuntimeException(e);
				}
			}
		} else if (operator == StringLength.INSTANCE) {
			// String length is always non-negative
			return new RoundedInterval(MathNumber.ZERO, MathNumber.PLUS_INFINITY, roundingDirection, precision);
		} else {
			// Unknown operator, return top
			return top();
		}
	}

	/**
	 * addition of two intervals with proper precision handling
	 * 
	 * @param left              the left operand
	 * @param right             the right operand
	 * @param roundingDirection the rounding direction to apply
	 * @param precision         the decimal precision to maintain
	 * @return a new rounded interval with the result
	 */
	private RoundedInterval preciseAdd(RoundedInterval left, RoundedInterval right, int roundingDirection,
			int precision) {
		// Handle special cases
		if (left.isBottom() || right.isBottom())
			return bottom();
		if (left.isTop() || right.isTop())
			return top();

		try {
			// Extract exact numeric bounds for direct computation
			MathNumber leftLow = left.interval.getLow();
			MathNumber leftHigh = left.interval.getHigh();
			MathNumber rightLow = right.interval.getLow();
			MathNumber rightHigh = right.interval.getHigh();

			// Compute the new values manually for precise control
			double newLowValue, newHighValue;

			// Calculate the new low bound (handle infinities)
			if (leftLow.isMinusInfinity() || rightLow.isMinusInfinity()) {
				newLowValue = Double.NEGATIVE_INFINITY;
			} else {
				// Precise addition of lower bounds with explicit handling of precision
				newLowValue = roundValue(leftLow.toDouble() + rightLow.toDouble());
			}

			// Calculate the new high bound (handle infinities)
			if (leftHigh.isPlusInfinity() || rightHigh.isPlusInfinity()) {
				newHighValue = Double.POSITIVE_INFINITY;
			} else {
				// Precise addition of upper bounds with explicit handling of precision
				newHighValue = roundValue(leftHigh.toDouble() + rightHigh.toDouble());
			}

			// Create new bounds with properly rounded values
			MathNumber newLow = newLowValue == Double.NEGATIVE_INFINITY ? MathNumber.MINUS_INFINITY
					: new MathNumber(newLowValue);
			MathNumber newHigh = newHighValue == Double.POSITIVE_INFINITY ? MathNumber.PLUS_INFINITY
					: new MathNumber(newHighValue);

			// Create a new interval with the rounded bounds
			return new RoundedInterval(new IntInterval(newLow, newHigh), roundingDirection, precision);
		} catch (Exception e) {
			// In case of any mathematical errors, return top
			return top();
		}
	}

	/**
	 * subtraction of two intervals with proper precision handling
	 * 
	 * @param left              the left operand
	 * @param right             the right operand
	 * @param roundingDirection the rounding direction to apply
	 * @param precision         the decimal precision to maintain
	 * @return a new rounded interval with the result
	 */
	private RoundedInterval preciseSubtract(RoundedInterval left, RoundedInterval right, int roundingDirection,
			int precision) {
		// Handle special cases
		if (left.isBottom() || right.isBottom())
			return bottom();
		if (left.isTop() || right.isTop())
			return top();

		try {
			// Extract exact numeric bounds for direct computation
			MathNumber leftLow = left.interval.getLow();
			MathNumber leftHigh = left.interval.getHigh();
			MathNumber rightLow = right.interval.getLow();
			MathNumber rightHigh = right.interval.getHigh();

			// Compute the new values manually for precise control (low - high, high - low)
			double newLowValue, newHighValue;

			// Calculate the new low bound (handle infinities)
			if (leftLow.isMinusInfinity() || rightHigh.isPlusInfinity()) {
				newLowValue = Double.NEGATIVE_INFINITY;
			} else {
				// Precise subtraction with explicit rounding
				newLowValue = roundValue(leftLow.toDouble() - rightHigh.toDouble());
			}

			// Calculate the new high bound (handle infinities)
			if (leftHigh.isPlusInfinity() || rightLow.isMinusInfinity()) {
				newHighValue = Double.POSITIVE_INFINITY;
			} else {
				// Precise subtraction with explicit rounding
				newHighValue = roundValue(leftHigh.toDouble() - rightLow.toDouble());
			}

			// Create new bounds with properly rounded values
			MathNumber newLow = newLowValue == Double.NEGATIVE_INFINITY ? MathNumber.MINUS_INFINITY
					: new MathNumber(newLowValue);
			MathNumber newHigh = newHighValue == Double.POSITIVE_INFINITY ? MathNumber.PLUS_INFINITY
					: new MathNumber(newHighValue);

			// Create a new interval with the rounded bounds
			return new RoundedInterval(new IntInterval(newLow, newHigh), roundingDirection, precision);
		} catch (Exception e) {
			// In case of any mathematical errors, return top
			return top();
		}
	}

	/**
	 * multiplication of two intervals with proper precision handling
	 * 
	 * @param left              the left operand
	 * @param right             the right operand
	 * @param roundingDirection the rounding direction to apply
	 * @param precision         the decimal precision to maintain
	 * @return a new rounded interval with the result
	 */
	private RoundedInterval preciseMultiply(RoundedInterval left, RoundedInterval right, int roundingDirection,
			int precision) {
		// Handle special cases
		if (left.isBottom() || right.isBottom())
			return bottom();
		if (left.equals(ZERO) || right.equals(ZERO))
			return new RoundedInterval(IntInterval.ZERO, roundingDirection, precision);
		if (left.isTop() || right.isTop())
			return top();

		try {
			// Extract the bounds for computation
			MathNumber leftLow = left.interval.getLow();
			MathNumber leftHigh = left.interval.getHigh();
			MathNumber rightLow = right.interval.getLow();
			MathNumber rightHigh = right.interval.getHigh();

			// Calculate all possible products for the interval bounds
			double ll = Double.NEGATIVE_INFINITY, lh = Double.NEGATIVE_INFINITY;
			double hl = Double.NEGATIVE_INFINITY, hh = Double.NEGATIVE_INFINITY;

			// Calculate products, handling infinities
			if (!leftLow.isMinusInfinity() && !rightLow.isMinusInfinity())
				ll = leftLow.toDouble() * rightLow.toDouble();
			if (!leftLow.isMinusInfinity() && !rightHigh.isPlusInfinity())
				lh = leftLow.toDouble() * rightHigh.toDouble();
			if (!leftHigh.isPlusInfinity() && !rightLow.isMinusInfinity())
				hl = leftHigh.toDouble() * rightLow.toDouble();
			if (!leftHigh.isPlusInfinity() && !rightHigh.isPlusInfinity())
				hh = leftHigh.toDouble() * rightHigh.toDouble();

			// Find the minimum and maximum of these products
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;

			for (double val : new double[] { ll, lh, hl, hh }) {
				if (val != Double.NEGATIVE_INFINITY && val < min)
					min = val;
				if (val > max)
					max = val;
			}

			// Handle the case where all values were infinities
			if (min == Double.POSITIVE_INFINITY)
				min = Double.NEGATIVE_INFINITY;
			if (max == Double.NEGATIVE_INFINITY)
				max = Double.POSITIVE_INFINITY;

			// Round the result values according to precision
			double roundedMin = min == Double.NEGATIVE_INFINITY ? min : roundValue(min);
			double roundedMax = max == Double.POSITIVE_INFINITY ? max : roundValue(max);

			// Create MathNumber objects with the rounded values
			MathNumber newLow = roundedMin == Double.NEGATIVE_INFINITY ? MathNumber.MINUS_INFINITY
					: new MathNumber(roundedMin);
			MathNumber newHigh = roundedMax == Double.POSITIVE_INFINITY ? MathNumber.PLUS_INFINITY
					: new MathNumber(roundedMax);

			// Create the result interval with the proper bounds
			return new RoundedInterval(new IntInterval(newLow, newHigh), roundingDirection, precision);
		} catch (Exception e) {
			// In case of any mathematical errors, return top
			return top();
		}
	}

	/**
	 * division of two intervals with proper precision handling
	 * 
	 * @param left              the left operand
	 * @param right             the right operand
	 * @param roundingDirection the rounding direction to apply
	 * @param precision         the decimal precision to maintain
	 * @return a new rounded interval with the result
	 */
	private RoundedInterval preciseDivide(RoundedInterval left, RoundedInterval right, int roundingDirection,
			int precision) {
		// Handle special cases
		if (left.isBottom() || right.isBottom())
			return bottom();
		if (right.equals(ZERO))
			return bottom(); // Division by zero
		if (left.equals(ZERO))
			return new RoundedInterval(IntInterval.ZERO, roundingDirection, precision);
		if (left.isTop() || right.isTop())
			return top();

		// Check if zero is in the right interval
		if ((right.interval.getLow().compareTo(MathNumber.ZERO) <= 0 &&
				right.interval.getHigh().compareTo(MathNumber.ZERO) >= 0))
			return top(); // Potential division by zero

		try {
			// Extract the bounds for computation
			MathNumber leftLow = left.interval.getLow();
			MathNumber leftHigh = left.interval.getHigh();
			MathNumber rightLow = right.interval.getLow();
			MathNumber rightHigh = right.interval.getHigh();

			// Calculate all possible divisions for the interval bounds
			double ll = Double.NEGATIVE_INFINITY, lh = Double.NEGATIVE_INFINITY;
			double hl = Double.NEGATIVE_INFINITY, hh = Double.NEGATIVE_INFINITY;

			// Calculate divisions while avoiding division by zero
			if (!leftLow.isMinusInfinity() && !rightLow.isMinusInfinity() && rightLow.toDouble() != 0)
				ll = leftLow.toDouble() / rightLow.toDouble();
			if (!leftLow.isMinusInfinity() && !rightHigh.isPlusInfinity() && rightHigh.toDouble() != 0)
				lh = leftLow.toDouble() / rightHigh.toDouble();
			if (!leftHigh.isPlusInfinity() && !rightLow.isMinusInfinity() && rightLow.toDouble() != 0)
				hl = leftHigh.toDouble() / rightLow.toDouble();
			if (!leftHigh.isPlusInfinity() && !rightHigh.isPlusInfinity() && rightHigh.toDouble() != 0)
				hh = leftHigh.toDouble() / rightHigh.toDouble();

			// Find the minimum and maximum of these divisions
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;

			for (double val : new double[] { ll, lh, hl, hh }) {
				if (val != Double.NEGATIVE_INFINITY && val < min)
					min = val;
				if (val > max)
					max = val;
			}

			// Handle the case where all values were infinities
			if (min == Double.POSITIVE_INFINITY)
				min = Double.NEGATIVE_INFINITY;
			if (max == Double.NEGATIVE_INFINITY)
				max = Double.POSITIVE_INFINITY;

			// Round the result values according to precision
			double roundedMin = min == Double.NEGATIVE_INFINITY ? min : roundValue(min);
			double roundedMax = max == Double.POSITIVE_INFINITY ? max : roundValue(max);

			// Create MathNumber objects with the rounded values
			MathNumber newLow = roundedMin == Double.NEGATIVE_INFINITY ? MathNumber.MINUS_INFINITY
					: new MathNumber(roundedMin);
			MathNumber newHigh = roundedMax == Double.POSITIVE_INFINITY ? MathNumber.PLUS_INFINITY
					: new MathNumber(roundedMax);

			// Create the result interval with the proper bounds
			return new RoundedInterval(new IntInterval(newLow, newHigh), roundingDirection, precision);
		} catch (Exception e) {
			// In case of any mathematical errors, return top
			return top();
		}
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

		// Take the most conservative rounding direction and the minimum precision
		int newRoundingDirection = (left.roundingDirection != 0) ? left.roundingDirection : right.roundingDirection;
		int newPrecision = Math.min(left.precision, right.precision);

		// Use our precise operation methods instead of IntInterval operations
		if (operator instanceof AdditionOperator) {
			return preciseAdd(left, right, newRoundingDirection, newPrecision);
		} else if (operator instanceof SubtractionOperator) {
			return preciseSubtract(left, right, newRoundingDirection, newPrecision);
		} else if (operator instanceof MultiplicationOperator) {
			return preciseMultiply(left, right, newRoundingDirection, newPrecision);
		} else if (operator instanceof DivisionOperator) {
			return preciseDivide(left, right, newRoundingDirection, newPrecision);
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
		RoundedInterval low_inf = new RoundedInterval(eval.interval.getLow(), MathNumber.PLUS_INFINITY,
				eval.roundingDirection, eval.precision);
		RoundedInterval lowp1_inf = new RoundedInterval(eval.interval.getLow().add(MathNumber.ONE),
				MathNumber.PLUS_INFINITY,
				eval.roundingDirection, eval.precision);
		RoundedInterval inf_high = new RoundedInterval(MathNumber.MINUS_INFINITY, eval.interval.getHigh(),
				eval.roundingDirection, eval.precision);
		RoundedInterval inf_highm1 = new RoundedInterval(MathNumber.MINUS_INFINITY,
				eval.interval.getHigh().subtract(MathNumber.ONE),
				eval.roundingDirection, eval.precision);

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