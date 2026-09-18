package nz.kiwifinance.transaction;

public record ImportResult(int created, int updated, int unchanged) {

    public static final ImportResult EMPTY = new ImportResult(0, 0, 0);

    public int total() {
        return created + updated + unchanged;
    }
}
