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

    private Set<Identifier> findEqualitySet(Identifier identifier) {
        return equalities.stream().filter(set -> set.contains(identifier)).findFirst().orElse(null);
    }

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
        if (isBottom()) return other;
        if (other.isBottom()) return this;
        
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
    public Equality assign(Identifier identifier, ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) {
        Equality res = new Equality(this);
        if (valueExpression instanceof Identifier) {
            res.addEquality(identifier, (Identifier) valueExpression);
        } else {
            res.reassign(identifier);
        }
        return res;
    }

    @Override
    public Equality assume(ValueExpression valueExpression, ProgramPoint programPoint, ProgramPoint programPoint1, SemanticOracle semanticOracle) {
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
    public Satisfiability satisfies(ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) {
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
        return new StringRepresentation(isBottom() ? "⊥" : 
            equalities.stream()
                .map(set -> String.join(" = ", set.stream().map(Identifier::getName).toArray(String[]::new)))
                .toList());
    }

    @Override
    public Equality smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }
}
