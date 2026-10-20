package eu.uniche.iguide.resource.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Paging parameters of every collection: {@code page} from 0, {@code size} 1 to 200, default 50. */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private PageRequests() {
    }

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        List<ErrorItem> errors = new ArrayList<>();
        if (p < 0) {
            errors.add(new ErrorItem("page", "OUT_OF_RANGE", "Must be 0 or more."));
        }
        if (s < 1 || s > MAX_SIZE) {
            errors.add(new ErrorItem("size", "OUT_OF_RANGE", "Must be between 1 and " + MAX_SIZE + "."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException("The paging parameters are invalid.", errors);
        }
        return PageRequest.of(p, s, sort);
    }
}
