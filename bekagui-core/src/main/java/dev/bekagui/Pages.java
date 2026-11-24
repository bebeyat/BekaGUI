package dev.bekagui;

import java.util.Collections;
import java.util.List;

/** Page math used by {@link View#paginate}. Pages are 0-based. */
public final class Pages {

    private Pages() {
    }

    public static int count(int total, int perPage) {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    public static int clamp(int page, int total, int perPage) {
        return Math.max(0, Math.min(page, count(total, perPage) - 1));
    }

    public static <T> List<T> slice(List<T> all, int page, int perPage) {
        if (perPage <= 0 || all.isEmpty()) {
            return Collections.emptyList();
        }
        int p = clamp(page, all.size(), perPage);
        int from = p * perPage;
        return all.subList(from, Math.min(all.size(), from + perPage));
    }
}
