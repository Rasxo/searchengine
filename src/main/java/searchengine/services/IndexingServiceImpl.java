package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import searchengine.config.ParserConnection;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final ParserConnection parserConnection;

    Set<String> uniqueLinks = Collections.synchronizedSet(new HashSet<>());

    AtomicBoolean isInterrupted = new AtomicBoolean(false);

    ConcurrentHashMap<Long,Thread> threadMap = new ConcurrentHashMap<>();

    ExecutorService executor;

    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageRepository pageRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    IndexRepository indexRepository;

    @Override
    public String startIndexing() {
        JSONObject response = new JSONObject();

        isInterrupted.set(false);
        uniqueLinks.clear();

        try {
            if (isIndexing()) {
                response.put("result", false);
                response.put("error", "Индексация уже запущена");
            } else {
                response.put("result", true);

                List<searchengine.config.Site> sitesList = sites.getSites();
                executor = Executors.newFixedThreadPool(sitesList.size());

                for (searchengine.config.Site site : sitesList) {
                    String siteUrl = site.getUrl();
                    Site siteFromDB = siteRepository.findByUrl(siteUrl);
                    if (siteFromDB != null) {
                        lemmaRepository.deleteAll(); //переделать потом по сайту
                        indexRepository.deleteAll(); //переделать потом по сайту
                        pageRepository.deleteBySite(siteFromDB);
                        siteRepository.deleteById(siteFromDB.getId());
                    }
                    Site siteRecord = createSiteRecord(site);
                    siteRepository.save(siteRecord);

                    executor.execute(() -> indexSite(siteRecord));
                }
                executor.shutdown();
            }
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        return response.toString();
    }

    @Override
    public String stopIndexing() {
        JSONObject response = new JSONObject();

        try {
            if (!isIndexing()) {
                response.put("result", false);
                response.put("error", "Индексация не запущена");
            } else {
                response.put("result", true);

                System.out.println("СТОП!!!");
                isInterrupted.set(true);
                executor.shutdownNow();

                for (Site site : siteRepository.findAll()) {
                    if (site.getStatus() != Status.INDEXED) {
                        site.setStatus(Status.FAILED);
                        site.setLastError("Индексация остановлена пользователем");
                        siteRepository.save(site);
                    }
                }
            }

        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }

        return response.toString();
    }

    @Override
    public String pageIndexing(String url) {
        JSONObject response = new JSONObject();

        isInterrupted.set(false);
        //подумать надо ли проверяить была ли эта страничка в сете уникальных урлов

        try {
            searchengine.config.Site siteFromConfig = findSiteInConfig(url);
            if (siteFromConfig == null) {
                response.put("result", false);
                response.put("error", "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
                return response.toString();
            }

            response.put("result", true);

            String siteUrl = siteFromConfig.getUrl();
            Site siteFromDB = siteRepository.findByUrl(siteUrl);
            Site siteRecord;
            if (siteFromDB == null) {
                siteRecord = createSiteRecord(siteFromConfig);
                siteRepository.save(siteRecord);
            } else {
                siteRecord = siteFromDB;
            }

            executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> indexUrl(siteRecord, url));
            executor.shutdown();

            return response.toString();

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    boolean isIndexing() {
        return !threadMap.isEmpty();
    }

    searchengine.config.Site findSiteInConfig(String url) {
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.contains(site.getUrl())) {
                return site;
            }
        }
        return null;
    }

    void indexSite(Site site) {
        threadMap.put(Thread.currentThread().getId(), Thread.currentThread());
        ForkJoinPool pool = new ForkJoinPool();

        Parser task = new Parser(site, this, false);
        pool.invoke(task);

        if (!isInterrupted.get()) {
            updateSiteStatus(site);
        }

        threadMap.remove(Thread.currentThread().getId());
    }

    void indexUrl(Site site, String url){
        threadMap.put(Thread.currentThread().getId(), Thread.currentThread());

        ForkJoinPool pool = new ForkJoinPool();

        Parser task = new Parser(site, this, true, url);
        pool.invoke(task);

        //надо подумать что и как ставить по результатам индексирования отдельной страницы в БД сайта
        if (!isInterrupted.get()) {
            updateSiteStatus(site);
        }

        threadMap.remove(Thread.currentThread().getId());
    }

    Site createSiteRecord(searchengine.config.Site site) {
        Site siteRecord = new Site();
        siteRecord.setName(site.getName());
        siteRecord.setUrl(site.getUrl());
        siteRecord.setStatus(Status.INDEXING);
        siteRecord.setStatusTime(new Date());
        return siteRecord;
    }

    void updateSiteStatus(Site site) {
        Optional<Site> isSiteFound = siteRepository.findById(site.getId());
        if (isSiteFound.isPresent()) {
            Site siteToUpdate = isSiteFound.get();
            siteToUpdate.setStatus(Status.INDEXED);
            siteRepository.save(siteToUpdate);
        }
    }

    Connection.Response getConnectToUrl(String path) {
        try {
            int randomMls = (int) ((new Random().nextDouble() * 1.5 + 0.5) * 1000);
            Thread.sleep(randomMls);
            return Jsoup.connect(path)
                    .userAgent(parserConnection.getUserAgent())
                    .referrer(parserConnection.getReferrer())
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .ignoreContentType(true)
                    .execute();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Document getDocument(Connection.Response response) {
        try {
            return response.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
