# LiSA Tutorial Project

## Development History

**Domain Implementations:**

- **Equality Domain** (`Equality.java`)  
  Developed by TRUONG Do

    - Equality propagation through assignments
    - Conditional branch analysis
    - Loop relationship tracking

- **RoundedInterval Domain** (`RoundedInterval.java`)  
  Developped by BOUHMAD Rachid

    - Arithmetic operation rounding (+, -, \*, /)
    - Widening/narrowing operators
    - Precision-aware interval merging

- **Cartesian Product** (`RoundedIntervalEqualityCartesian.java`)  
  Worked on by BOUHMAD Rachid
    - Parallel state maintenance
    - Cross-domain information sharing
    - Combined result reporting

## IMP Program Analysis

### Equality Domain Tests (`inputs/equality.imp`)

Written by Rachid BOUHMAD

### RoundedInterval Tests (`inputs/roundedinterval.imp`)

Written by TRUONG Do

### Combined Analysis (`inputs/roundedintervalequality.imp`)

Written by BOUHMAD Rachid

## Test Classes

- **Equality Tests** (`EqualityTest.java`)

- **RoundedInterval Tests** (`RoundedIntervalTest.java`)

- **Combined Analysis Tests** (`RoundedIntervalEqualityTest.java`)

# Analysis

## Implemented Domains

### 1. Equality Domain (Relational)

The Equality domain tracks equivalence relationships between program variables during abstract interpretation. It maintains sets of identifiers that are known to be equal at each program point.

**Key Features:**
- Tracks which variables must be equal to each other
- Handles assignment operations by updating equivalence sets
- Supports condition evaluation based on equality
- Propagates equality relationships across the program flow

**Implementation Details:**
- Equivalence classes are maintained as sets of identifiers
- When a variable is assigned another variable's value, they join the same equivalence class
- When a variable is assigned a non-variable expression, it's removed from its current class
- The domain can determine if two variables must be equal, not equal, or if their relationship is unknown

**Example Operations:**
```
def a = 5;
def b = a;     // b = a (equality established)
def c = b;     // c = b = a (transitive equality)

if (a == c) {  // This condition is satisfied based on equality tracking
    def d = a; // d = a = b = c
}

a = 10;        // a is removed from equality with b and c
```

### 2. RoundedInterval Domain (Non-relational)

The RoundedInterval domain tracks numeric values using intervals with configurable rounding directions and precision. It maintains both lower and upper bounds for variables while applying specified rounding rules during arithmetic operations.

**Key Features:**
- Supports numeric bound tracking with decimal precision
- Configurable rounding directions (up, down, or standard)
- Precise arithmetic operations with rounding
- Interval widening for loop analysis termination

**Implementation Details:**
- Each interval has a lower and upper bound with rounding configuration
- Rounding is applied consistently after arithmetic operations
- Special handling for infinity values and division by zero
- Widening operator detects growth patterns and ensures termination

**Example Operations:**
```
def i = 4;
def j = 2.4;
def a = i + j;   // [6.4 .. 6.4] with precision=2
def s = i - j;   // [1.6 .. 1.6] with precision=2
def m = i * j;   // [9.6 .. 9.6] with precision=2
def d = i / j;   // [1.67 .. 1.67] with precision=2
```

**Loop Analysis:**
```
def z = 0.0;
while (z < 10.0) {
    z = z + 1.0;  // Widening detects pattern and sets interval to [0.0, +∞]
}
```

### 3. RoundedIntervalEqualityCartesian Domain (Combined)

A Cartesian product domain combining:
- **Equality Domain**.
- **RoundedInterval Domain**.

**Key Features:**
- Simultaneous tracking of equality relationships and numeric intervals
- Cross-domain information sharing
- Maintains the precision of both component domain

**Example Analysis:**
```
def x = 3.14;
def y = 3.14;  // Equality domain doesn't track equality based on value
def z = y;     // Equality domain tracks z = y

x = x + 2.5;   // RoundedInterval tracks x = [5.64, 5.64]
y = y * 2.0;   // RoundedInterval tracks y = [6.28, 6.28]

def a = z;     // Equality domain knows a = z = y

if (x < 6.0) { // RoundedInterval domain can evaluate this condition
    y = x;     // Now Equality knows y = x, and RoundedInterval knows y = [5.64, 5.64]
    z = z + 0.5; // Equality breaks z's relationship with y and a
                 // RoundedInterval tracks z = [6.78, 6.78]
}
```


### RoundedInterval Domain challenges
- **Challenge**: Implementing precise rounding with controlled direction and precision
- **Solution**: Custom arithmetic operations with explicit rounding control
