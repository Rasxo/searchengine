package searchengine.services;

public interface IndexingService {

    String startIndexing();

    String stopIndexing();

    String pageIndexing(String url);
}
