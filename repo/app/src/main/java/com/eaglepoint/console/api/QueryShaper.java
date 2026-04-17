package com.eaglepoint.console.api;

import com.eaglepoint.console.model.PagedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies optional {@code sort} and {@code fields} query parameters to a
 * {@link PagedResult}, returning a response-shaped {@link Map} ready for
 * {@code ctx.json(...)}.
 *
 * <p>Query param contracts:
 * <ul>
 *   <li>{@code sort=field} — ascending by {@code field}; {@code sort=-field}
 *       — descending; {@code sort=a,-b} — multi-key.</li>
 *   <li>{@code fields=a,b,c} — restrict each row to the listed keys (always
 *       includes {@code id} if present so clients can follow-up).</li>
 * </ul>
 *
 * <p>Absent params leave the response shape identical to
 * {@code PagedResponse.of(result)} so existing clients keep working.</p>
 *
 * <p>Sort/field selection is applied in-memory on the current page, which
 * honours the documented pagination bounds (default 50, max 500).</p>
 */
public final class QueryShaper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QueryShaper() {}

    public static <T> Map<String, Object> shape(Context ctx, PagedResult<T> result) {
        String sort = ctx.queryParam("sort");
        String fields = ctx.queryParam("fields");

        List<T> raw = result.getData() == null ? List.of() : result.getData();
        List<Map<String, Object>> rows = toMaps(raw);

        if (sort != null && !sort.isBlank()) {
            rows.sort(buildComparator(sort));
        }
        if (fields != null && !fields.isBlank()) {
            rows = selectFields(rows, fields);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows);
        response.put("total", result.getTotal());
        response.put("page", result.getPage());
        response.put("pageSize", result.getPageSize());
        response.put("totalPages", result.getPageSize() > 0
            ? (int) Math.ceil((double) result.getTotal() / result.getPageSize())
            : 0);
        return response;
    }

    private static <T> List<Map<String, Object>> toMaps(List<T> items) {
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (T item : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = MAPPER.convertValue(item, Map.class);
            out.add(m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m));
        }
        return out;
    }

    private static Comparator<Map<String, Object>> buildComparator(String sortSpec) {
        String[] parts = sortSpec.split(",");
        Comparator<Map<String, Object>> result = null;
        for (String part : parts) {
            String key = part.trim();
            if (key.isEmpty()) continue;
            boolean desc = key.startsWith("-");
            if (desc) key = key.substring(1);
            final String field = key;
            Comparator<Map<String, Object>> step =
                Comparator.comparing((Map<String, Object> m) -> asComparable(m.get(field)),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (desc) step = step.reversed();
            result = (result == null) ? step : result.thenComparing(step);
        }
        return result == null ? (a, b) -> 0 : result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Comparable asComparable(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue(); // unify int/long/double
        if (v instanceof Comparable c) return c;
        return v.toString();
    }

    private static List<Map<String, Object>> selectFields(List<Map<String, Object>> rows, String fields) {
        List<String> keep = new ArrayList<>();
        for (String f : fields.split(",")) {
            String t = f.trim();
            if (!t.isEmpty()) keep.add(t);
        }
        if (!keep.contains("id")) keep.add("id");
        List<String> whitelist = List.copyOf(keep);
        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> slim = new LinkedHashMap<>();
            for (String k : whitelist) {
                if (row.containsKey(k)) slim.put(k, row.get(k));
            }
            filtered.add(slim);
        }
        return filtered;
    }

    /** Utility used by tests/examples — does not touch request context. */
    public static String describeSortSpec(String sort) {
        return sort == null || sort.isBlank() ? "none"
            : Arrays.toString(sort.split(","));
    }
}
