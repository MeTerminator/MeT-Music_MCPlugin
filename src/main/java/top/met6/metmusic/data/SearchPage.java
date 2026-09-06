package top.met6.metmusic.data;

import java.util.Collections;
import java.util.List;

public class SearchPage {
    private final List<SearchResult> results;
    private final int totalCount;

    public SearchPage(List<SearchResult> results, int totalCount) {
        this.results = Collections.unmodifiableList(results);
        this.totalCount = totalCount;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
