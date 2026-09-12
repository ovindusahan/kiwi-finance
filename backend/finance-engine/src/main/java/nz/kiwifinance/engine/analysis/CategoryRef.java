package nz.kiwifinance.engine.analysis;

import java.util.Objects;

public record CategoryRef(String id, String name, CategoryGroup group) {

    public CategoryRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
    }
}
