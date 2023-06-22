package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.beans.factory.annotation.Autowired;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;


public class Parser extends RecursiveAction {

    private final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final boolean isSingleLink;

    private IndexingServiceImpl indexingService;

    private Site site;

    private String url;

    private Set<String> uniqueLinks;

    AtomicBoolean isInterrupted;

    @Autowired
    IndexRepository indexRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    PageRepository pageRepository;
    @Autowired
    SiteRepository siteRepository;

    public Parser() {
        isSingleLink = false;
    }

    public Parser(Site site, IndexingServiceImpl indexingService, boolean isSingleLink) {
        this.isSingleLink = isSingleLink;
        this.site = site;
        url = site.getUrl();
        this.indexingService = indexingService;
        this.uniqueLinks = indexingService.uniqueLinks;
        this.isInterrupted = indexingService.isInterrupted;
        this.indexRepository = indexingService.indexRepository;
        this.lemmaRepository = indexingService.lemmaRepository;
        this.pageRepository = indexingService.pageRepository;
        this.siteRepository = indexingService.siteRepository;
    }

    public Parser(Site site, IndexingServiceImpl indexingService, boolean isSingleLink, String url) {
        this.isSingleLink = isSingleLink;
        this.site = site;
        this.indexingService = indexingService;
        this.uniqueLinks = indexingService.uniqueLinks;
        this.isInterrupted = indexingService.isInterrupted;
        this.indexRepository = indexingService.indexRepository;
        this.lemmaRepository = indexingService.lemmaRepository;
        this.pageRepository = indexingService.pageRepository;
        this.siteRepository = indexingService.siteRepository;
        this.url = url;
    }

    @Override
    protected void compute() {
        if (!isInterrupted.get()) {
            if (!uniqueLinks.add(url)) {
                return;
            }
            System.out.println("РАБОТАЕМ!!!");

            Connection.Response connectResult = indexingService.getConnectToUrl(url);
            Document document = indexingService.getDocument(connectResult);

            Page pageRecord = addPage(url, connectResult, document);

            try {
                LemmaFinder lemmaFinder = new LemmaFinder(document.html());
                Map<String, Integer> lemmaMap = lemmaFinder.collectLemmas();

                addLemma(lemmaMap, pageRecord);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (isSingleLink) {
                return;
            }

            Elements links = document.select("a[href]");
            if (links.size() > AVAILABLE_PROCESSORS) {
                ForkJoinPool newPool = new ForkJoinPool();

                for (Element link : links) {
                    if (isInterrupted.get()) {
                        return;
                    }
                    if (!isValidURL(link)) {
                        continue;
                    }
                    String newUrl = link.absUrl("href");
                    Parser newTask = new Parser(site, indexingService, false, newUrl);
                    newPool.invoke(newTask);
                }
            } else {
                for (Element link : links) {
                    if (isInterrupted.get()) {
                        return;
                    }
                    if (!isValidURL(link)) {
                        continue;
                    }
                    String newUrl = link.absUrl("href");
                    Parser newTask = new Parser(site, indexingService, false, newUrl);
                    newTask.compute();
                }
            }
        }
    }


    private boolean isValidURL(Element url) {
        return url.attr("abs:href").startsWith(site.getUrl())
                && !uniqueLinks.contains(url.attr("abs:href"))
                && !url.attr("abs:href").contains("#")
                && !url.attr("abs:href").toLowerCase()
                .matches(".*(.jpg|.png|.jpeg|.pdf|.pptx|.docx|.txt|.svg|.xlsx|.xls|" +
                        ".xml|.avi|.mpeg|.doc|.ppt|.rtf|.gif|.eps|.books/about/svetlovka-prosveshchaet-ale).*")
                ;
    }

    private Page addPage(String url, Connection.Response response, Document document) {
        String newUrl = url.replaceAll(site.getUrl(), "/");
        synchronized (pageRepository) {
            Page page = pageRepository.findByPath(newUrl);
            if (page == null) {
                page = new Page();
                page.setCode(response.statusCode());
                page.setPath(newUrl);
                String content = document.html();
                if (content.getBytes().length > 16777215) {
                    content = content.substring(0, 16777215);
                }
                page.setContent(content);
                page.setSite(site);
                pageRepository.save(page);

                updateSiteStatusTime();
            }
            return page;
        }
    }

    private void addLemma(Map<String, Integer> lemmaMap, Page pageRecord) {
        Lemma lemmaRecord;
        for (Map.Entry<String, Integer> lemma : lemmaMap.entrySet()) {
            synchronized (lemmaRepository) {
                lemmaRecord = lemmaRepository.findByLemmaAndSite(lemma.getKey(), site);
                if (lemmaRecord == null) {
                    lemmaRecord = new Lemma();
                    lemmaRecord.setLemma(lemma.getKey());
                    lemmaRecord.setFrequency(1);
                    lemmaRecord.setSite(site);
                    lemmaRepository.save(lemmaRecord);
                } else {
                    lemmaRecord.increaseFrequency();
                    lemmaRepository.save(lemmaRecord);
                }
            }
            synchronized (indexRepository) {
                Index indexRecord = new Index();
                indexRecord.setPage(pageRecord);
                indexRecord.setLemma(lemmaRecord);
                indexRecord.setRank((float) lemma.getValue());
                indexRepository.save(indexRecord);
            }
        }
    }

    private void updateSiteStatusTime() {
        Optional<Site> isSiteFound = siteRepository.findById(site.getId());
        if (isSiteFound.isPresent()) {
            Site siteToUpdate = isSiteFound.get();
            siteToUpdate.setStatusTime(new Date());
            siteRepository.save(siteToUpdate);
        }
    }
}
