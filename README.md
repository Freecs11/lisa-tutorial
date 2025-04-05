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

## Analysis
