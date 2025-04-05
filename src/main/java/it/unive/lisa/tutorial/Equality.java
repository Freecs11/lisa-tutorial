package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.*;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonNe;
import it.unive.lisa.symbolic.value.operator.unary.LogicalNegation;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.*;
import java.util.function.Predicate;

/**
 * The Equality domain tracks equivalence relationships between program
 * variables
 * during abstract interpretation. It maintains sets of identifiers that are
 * known to be equal at each program point.
 *
 * <p>
 * This domain can prove whether two variables must be equal, not equal,
 * or if their relationship is unknown based on the current state.
 * </p>
 */
public class Equality implements ValueDomain<Equality> {
    public static final Equality BOTTOM = new Equality(Collections.emptySet());
    public static final Equality TOP = new Equality();
    private final Set<Set<Identifier>> equalities;

    public Equality() {
        this.equalities = new HashSet<>();
    }

    public Equality(Equality other) {
        this.equalities = new HashSet<>();
        for (Set<Identifier> eq : other.equalities) {
            this.equalities.add(new HashSet<>(eq));
        }
    }

    private Equality(Set<Set<Identifier>> equalities) {
        this.equalities = new HashSet<>(equalities);
    }

    /**
     * Establishes equality between two identifiers by merging their equivalence
     * sets
     * 
     * @param a First identifier to equate
     * @param b Second identifier to equate
     */
    private void addEquality(Identifier a, Identifier b) {
        Set<Identifier> setA = findEqualitySet(a);
        Set<Identifier> setB = findEqualitySet(b);

        if (setA != null && setB != null) {
            setA.addAll(setB);
            equalities.remove(setB);
        } else if (setA != null) {
            setA.add(b);
        } else if (setB != null) {
            setB.add(a);
        } else {
            equalities.add(new HashSet<>(Arrays.asList(a, b)));
        }
    }

    /**
     * Finds the equivalence set containing the given identifier
     * 
     * @param identifier The identifier to search for
     * @return The equivalence set containing the identifier, or null if not found
     */
    private Set<Identifier> findEqualitySet(Identifier identifier) {
        return equalities.stream().filter(set -> set.contains(identifier)).findFirst().orElse(null);
    }

    /**
     * Resets the given identifier's equivalences by moving it to a new singleton
     * set
     * 
     * @param identifier The identifier to reassign
     */
    private void reassign(Identifier identifier) {
        equalities.removeIf(set -> set.remove(identifier));
        equalities.add(new HashSet<>(Collections.singleton(identifier)));
    }

    @Override
    public boolean lessOrEqual(Equality other) {
        return other.equalities.stream().allMatch(o -> equalities.stream().anyMatch(e -> e.containsAll(o)));
    }

    @Override
    public Equality lub(Equality other) {
        if (isBottom())
            return other;
        if (other.isBottom())
            return this;

        Equality result = new Equality();
        equalities.forEach(set -> set.forEach(result::reassign));
        other.equalities.forEach(set -> set.forEach(result::reassign));
        return result;
    }

    @Override
    public Equality top() {
        return TOP;
    }

    @Override
    public boolean isTop() {
        return equalities.isEmpty();
    }

    @Override
    public Equality bottom() {
        return BOTTOM;
    }

    @Override
    public boolean isBottom() {
        return this == BOTTOM;
    }

    @Override
    /**
     * Handles assignment of a value to an identifier by either:
     * - Establishing equality if assigning from another identifier
     * - Resetting equivalences if assigning from a non-identifier value
     *
     * @param identifier      The target identifier being assigned to
     * @param valueExpression The source value expression
     * @param programPoint    The program point where assignment occurs
     * @param semanticOracle  The semantic oracle for context
     * @return A new Equality state reflecting the assignment
     */
    public Equality assign(Identifier identifier, ValueExpression valueExpression, ProgramPoint programPoint,
            SemanticOracle semanticOracle) {
        Equality res = new Equality(this);
        if (valueExpression instanceof Identifier) {
            res.addEquality(identifier, (Identifier) valueExpression);
        } else {
            res.reassign(identifier);
        }
        return res;
    }

    @Override
    public Equality assume(ValueExpression valueExpression, ProgramPoint programPoint, ProgramPoint programPoint1,
            SemanticOracle semanticOracle) {
        Satisfiability result = satisfies(valueExpression, programPoint, semanticOracle);
        return result == Satisfiability.NOT_SATISFIED ? bottom() : this;
    }

    @Override
    public boolean knowsIdentifier(Identifier identifier) {
        return equalities.stream().anyMatch(set -> set.contains(identifier));
    }

    @Override
    public Equality forgetIdentifier(Identifier identifier) {
        Equality res = new Equality(this);
        res.equalities.forEach(set -> set.remove(identifier));
        return res;
    }

    @Override
    public Equality forgetIdentifiersIf(Predicate<Identifier> predicate) {
        Equality res = new Equality(this);
        res.equalities.forEach(set -> set.removeIf(predicate));
        return res;
    }

    @Override
    public Satisfiability satisfies(ValueExpression valueExpression, ProgramPoint programPoint,
            SemanticOracle semanticOracle) {
        boolean inverted = false;
        if (valueExpression instanceof UnaryExpression unary && unary.getOperator() instanceof LogicalNegation) {
            valueExpression = (ValueExpression) unary.getExpression();
            inverted = true;
        }

        if (!(valueExpression instanceof BinaryExpression binary) ||
                !(binary.getOperator() instanceof ComparisonEq || binary.getOperator() instanceof ComparisonNe) ||
                !(binary.getLeft() instanceof Identifier left && binary.getRight() instanceof Identifier right)) {
            return Satisfiability.UNKNOWN;
        }

        boolean equal = equalities.stream().anyMatch(set -> set.contains(left) && set.contains(right));
        return equal == (binary.getOperator() instanceof ComparisonEq) != inverted
                ? Satisfiability.SATISFIED
                : Satisfiability.NOT_SATISFIED;
    }

    @Override
    public Equality pushScope(ScopeToken scopeToken) {
        return this;
    }

    @Override
    public Equality popScope(ScopeToken scopeToken) {
        return this;
    }

    @Override
    public StructuredRepresentation representation() {
        return new StringRepresentation(isBottom() ? "⊥"
                : equalities.stream()
                        .map(set -> String.join(" = ", set.stream().map(Identifier::getName).toArray(String[]::new)))
                        .toList());
    }

    @Override
    public Equality smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }
}
