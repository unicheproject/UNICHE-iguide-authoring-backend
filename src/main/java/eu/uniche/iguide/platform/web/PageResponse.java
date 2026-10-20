package eu.uniche.iguide.platform.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * One page of a collection ({@code 06} section 16): zero-based {@code page}, {@code size} up to 200.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
