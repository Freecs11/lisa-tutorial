package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGt;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLt;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonNe;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An abstract domain that tracks equality relationships between variables.
 * It keeps track of which variables are equal to the current variable.
 */
public class Equality implements ValueDomain<Equality> {

    // We use a set to store the identifiers that are equal to this variable
    private final Set<String> equalVariables;
    
    // Constants for the top and bottom elements
    public static final Equality TOP = new Equality();
    public static final Equality BOTTOM = new Equality(new HashSet<>());
    
    /**
     * Creates a new top element.
     */
    public Equality() {
        this.equalVariables = new HashSet<>();
    }
    
    /**
     * Creates a new element with the given set of equal variables.
     * 
     * @param equalVariables the set of variables that are equal to this one
     */
    public Equality(Set<String> equalVariables) {
        this.equalVariables = equalVariables;
    }
    
    /**
     * Creates a new element with a single equal variable.
     * 
     * @param variable the variable that is equal to this one
     */
    public Equality(String variable) {
        this.equalVariables = new HashSet<>();
        if (variable != null) {
            this.equalVariables.add(variable);
        }
    }

    @Override
    public boolean lessOrEqual(Equality equality) throws SemanticException {
        return false;
    }

    @Override
    public Equality lub(Equality equality) throws SemanticException {
        return null;
    }

    @Override
    public Equality top() {
        return TOP;
    }
    
    @Override
    public Equality bottom() {
        return BOTTOM;
    }
    
    @Override
    public boolean isTop() {
        return equalVariables != null && equalVariables.isEmpty();
    }
    
    @Override
    public boolean isBottom() {
        return equalVariables == null;
    }
    
    /**
     * Returns the set of variables that are equal to this variable.
     * 
     * @return the set of equal variables
     */
    public Set<String> getEqualVariables() {
        return isBottom() ? Collections.emptySet() : Collections.unmodifiableSet(equalVariables);
    }
    
    /**
     * Checks if this variable is equal to the given variable.
     * 
     * @param variable the variable to check
     * @return true if the variables are equal
     */
    public boolean isEqualTo(String variable) {
        return !isBottom() && equalVariables.contains(variable);
    }
    

    
    @Override
    public Equality lubAux(Equality other) throws SemanticException {
        // If either is top, the result is top
        if (this.isTop() || other.isTop())
            return TOP;
            
        // The union of equality sets represents variables equal in either element
        Set<String> result = new HashSet<>(this.equalVariables);
        result.retainAll(other.equalVariables);
        
        // If there are no common variables, the result is top
        if (result.isEmpty())
            return TOP;
            
        return new Equality(result);
    }
    
    @Override
    public Equality glbAux(Equality other) throws SemanticException {
        // If either is top, the result is the other
        if (this.isTop())
            return other;
        if (other.isTop())
            return this;
            
        // The intersection represents variables equal in both elements
        Set<String> result = new HashSet<>(this.equalVariables);
        result.addAll(other.equalVariables);
        
        return new Equality(result);
    }
    
    @Override
    public Equality wideningAux(Equality other) throws SemanticException {
        return lubAux(other);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Equality equality = (Equality) o;
        
        if (this.isBottom() && equality.isBottom())
            return true;
        if (this.isBottom() || equality.isBottom())
            return false;
        if (this.isTop() && equality.isTop())
            return true;
        if (this.isTop() || equality.isTop())
            return false;
            
        return Objects.equals(equalVariables, equality.equalVariables);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(equalVariables);
    }
    
    @Override
    public StructuredRepresentation representation() {
        if (isBottom())
            return Lattice.bottomRepresentation();
        if (isTop())
            return Lattice.topRepresentation();
            
        return new StringRepresentation("Equal to: " + equalVariables);
    }
    
    @Override
    public Equality evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) {
        // Constants are not equal to any variable
        return TOP;
    }
    
    @Override
    public Equality evalUnaryExpression(UnaryOperator operator, Equality arg, ProgramPoint pp, SemanticOracle oracle) {
        if (arg.isBottom())
            return bottom();
            
        if (operator instanceof NumericNegation) {
            // Preserves the equality relationships
            return arg;
        }
        
        // For other operators, we lose track of equalities
        return TOP;
    }
    
    @Override
    public Equality evalBinaryExpression(BinaryOperator operator, Equality left, Equality right, ProgramPoint pp, SemanticOracle oracle) {
        if (left.isBottom() || right.isBottom())
            return bottom();
            
        if (operator instanceof AdditionOperator ||
            operator instanceof SubtractionOperator ||
            operator instanceof MultiplicationOperator ||
            operator instanceof DivisionOperator) {
            // For arithmetic operations, we lose track of equalities
            return TOP;
        }
        
        // For other operators, we also lose track of equalities
        return TOP;
    }
    
    @Override
    public Satisfiability satisfiesBinaryExpression(BinaryOperator operator, Equality left, Equality right, ProgramPoint pp, SemanticOracle oracle) {
        if (left.isBottom() || right.isBottom())
            return Satisfiability.BOTTOM;
        
        // If left and right have common equal variables, they might be equal
        boolean haveCommonVariables = false;
        for (String var : left.getEqualVariables()) {
            if (right.isEqualTo(var)) {
                haveCommonVariables = true;
                break;
            }
        }
        
        if (operator instanceof ComparisonEq) {
            if (haveCommonVariables)
                return Satisfiability.SATISFIED;
            if (left.isTop() || right.isTop())
                return Satisfiability.UNKNOWN;
            return Satisfiability.NOT_SATISFIED;
        } else if (operator instanceof ComparisonNe) {
            if (haveCommonVariables)
                return Satisfiability.NOT_SATISFIED;
            if (left.isTop() || right.isTop())
                return Satisfiability.UNKNOWN;
            return Satisfiability.SATISFIED;
        }
        
        // For other comparisons, we can't determine satisfiability
        return Satisfiability.UNKNOWN;
    }
    
    @Override
    public ValueEnvironment<Equality> assumeBinaryExpression(
            ValueEnvironment<Equality> environment,
            BinaryOperator operator,
            ValueExpression left,
            ValueExpression right,
            ProgramPoint src,
            ProgramPoint dest,
            SemanticOracle oracle) throws SemanticException {
        
        // We only handle equality comparisons between identifiers
        if (!(operator instanceof ComparisonEq) || !(left instanceof Identifier) || !(right instanceof Identifier))
            return environment;
            
        Identifier leftId = (Identifier) left;
        Identifier rightId = (Identifier) right;
        
        String leftName = leftId.getName();
        String rightName = rightId.getName();
        
        // Get the current equality information for both variables
        Equality leftEquality = environment.getState(leftId);
        Equality rightEquality = environment.getState(rightId);
        
        if (leftEquality.isBottom() || rightEquality.isBottom())
            return environment.bottom();
            
        // Create the updated equality sets for both variables
        Set<String> newLeftVars = new HashSet<>(leftEquality.getEqualVariables());
        newLeftVars.add(rightName);
        for (String var : rightEquality.getEqualVariables()) {
            newLeftVars.add(var);
        }
        
        Set<String> newRightVars = new HashSet<>(rightEquality.getEqualVariables());
        newRightVars.add(leftName);
        for (String var : leftEquality.getEqualVariables()) {
            newRightVars.add(var);
        }
        
        // Update the environment with the new equality information
        ValueEnvironment<Equality> result = environment;
        result = result.putState(leftId, new Equality(newLeftVars));
        result = result.putState(rightId, new Equality(newRightVars));
        
        // Update all other variables that are equal to either left or right
        for (Identifier id : environment.getKeys()) {
            if (!id.equals(leftId) && !id.equals(rightId)) {
                Equality state = environment.getState(id);
                if (state.isEqualTo(leftName) || state.isEqualTo(rightName)) {
                    Set<String> newVars = new HashSet<>(state.getEqualVariables());
                    newVars.addAll(newLeftVars);
                    result = result.putState(id, new Equality(newVars));
                }
            }
        }
        
        return result;
    }

    @Override
    public Equality assign(Identifier identifier, ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        return null;
    }

    @Override
    public Equality smallStepSemantics(ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        return null;
    }

    @Override
    public Equality assume(ValueExpression valueExpression, ProgramPoint programPoint, ProgramPoint programPoint1, SemanticOracle semanticOracle) throws SemanticException {
        return null;
    }

    @Override
    public boolean knowsIdentifier(Identifier identifier) {
        return false;
    }

    @Override
    public Equality forgetIdentifier(Identifier identifier) throws SemanticException {
        return null;
    }

    @Override
    public Equality forgetIdentifiersIf(Predicate<Identifier> predicate) throws SemanticException {
        return null;
    }

    @Override
    public Satisfiability satisfies(ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        return null;
    }

    @Override
    public Equality pushScope(ScopeToken scopeToken) throws SemanticException {
        return null;
    }

    @Override
    public Equality popScope(ScopeToken scopeToken) throws SemanticException {
        return null;
    }
}