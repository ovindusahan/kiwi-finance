package nz.kiwifinance.movement;

/**
 * How money moved. A transfer stays within the person's accounts; a withdrawal leaves them; a
 * deposit arrives from outside.
 */
public enum MovementType {
    TRANSFER,
    WITHDRAWAL,
    DEPOSIT
}
