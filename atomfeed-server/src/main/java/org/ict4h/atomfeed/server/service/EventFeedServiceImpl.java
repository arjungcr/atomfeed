package org.ict4h.atomfeed.server.service;

import com.sun.syndication.feed.atom.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.server.domain.EventFeed;
import org.ict4h.atomfeed.server.domain.EventRecord;
import org.ict4h.atomfeed.server.domain.EventRecordComparator;
import org.ict4h.atomfeed.server.domain.FeedBuilder;
import org.ict4h.atomfeed.server.service.feedgenerator.FeedGenerator;
import org.joda.time.DateTime;

import java.net.URI;
import java.util.*;

public class EventFeedServiceImpl implements EventFeedService {

    private static final String ATOM_MEDIA_TYPE = "application/atom+xml";
    private static final String LINK_TYPE_SELF = "self";
    private static final String LINK_TYPE_VIA = "via";
    private static final String ATOMFEED_MEDIA_TYPE = "application/vnd.atomfeed+xml";
    private final Logger logger = Logger.getLogger(this.getClass());

	private FeedGenerator feedGenerator;
    private ResourceBundle bundle;

    public EventFeedServiceImpl(FeedGenerator generator) {
        this.feedGenerator = generator;
        try {
            bundle = ResourceBundle.getBundle("atomfeed");
        }catch (MissingResourceException e){
            bundle = null;
        }
    }

    @Override
	public Feed getRecentFeed(URI requestUri, String category) {
    	EventFeed recentFeed = feedGenerator.getRecentFeed(category);

        return new FeedBuilder()
                .type("atom_1.0")
                .id(generateIdForEventFeed(recentFeed.getId()))
                .title(getPropertyWithDefault("feed.title", "Event feed"))
                .generator(getGenerator())
                .authors(getAuthors())
                .entries(getEntries(recentFeed.getEvents()))
                .updated(newestEventDate(recentFeed.getEvents()))
                .link(getLink(requestUri.toString(), LINK_TYPE_SELF, ATOM_MEDIA_TYPE))
                .link(getLink(generateCanonicalUri(requestUri, recentFeed.getId()), LINK_TYPE_VIA, ATOM_MEDIA_TYPE))
                .links(generatePagingLinks(requestUri, recentFeed,category))
                .build();
    }

    @Override
    public Feed getEventFeed(URI requestUri, String category, Integer feedId) {
        EventFeed feedForId = feedGenerator.getFeedForId(feedId, category);
        return new FeedBuilder()
                .type("atom_1.0")
                .id(generateIdForEventFeed(feedId))
                .title(getPropertyWithDefault("feed.title", "Event feed"))
                .generator(getGenerator())
                .authors(getAuthors())
                .entries(getEntries(feedForId.getEvents()))
                .updated(newestEventDate(feedForId.getEvents()))
                .link(getLink(requestUri.toString(), LINK_TYPE_SELF, ATOM_MEDIA_TYPE))
                .link(getLink(requestUri.toString(), LINK_TYPE_VIA, ATOM_MEDIA_TYPE))
                .links(generatePagingLinks(requestUri, feedForId, category))
                .build();
    }


    private List<Person> getAuthors() {
        Person person = new Person();
        person.setName(getPropertyWithDefault("feed.author","Atomfeed"));
        return Arrays.asList(person);
    }

    private String generateCanonicalUri(URI requestUri, Integer feedId) {
        return getServiceUri(requestUri) + "/" + feedId;
    }
    
    private List<Link> generatePagingLinks(URI requestUri, EventFeed feed, String category) {
        ArrayList<Link> links = new ArrayList<Link>();
        int feedCount = feedGenerator.getRecentFeed(category).getId();

        if (feed.getId() < feedCount) {
            Link next = new Link();
            next.setRel("next-archive");
            next.setType(ATOM_MEDIA_TYPE);
            next.setHref(generateCanonicalUri(requestUri, feed.getId() + 1));
            links.add(next);
        }

        if (feed.getId() > 1) {
            Link prev = new Link();
            prev.setRel("prev-archive");
            prev.setType(ATOM_MEDIA_TYPE);
            prev.setHref(generateCanonicalUri(requestUri, feed.getId()-1));
            links.add(prev);
        }
        return links;
    }

    private String generateIdForEventFeed(Integer feedId){
        return getPropertyWithDefault("feed.id.prefix", "") + "+" + feedId;
    }

    private String getPropertyWithDefault(String property, String defaultValue) {
        return bundle != null && bundle.containsKey(property) ? bundle.getString(property) : defaultValue;
    }

    private Generator getGenerator() {
        Generator generator = new Generator();
        generator.setUrl(getPropertyWithDefault("feed.generator.uri", "https://github.com/ICT4H/atomfeed"));
        generator.setValue(getPropertyWithDefault("feed.generator.title", "Atomfeed"));
        return generator;
    }

    private Date newestEventDate(List<EventRecord> eventRecordList) {
        if(eventRecordList.isEmpty()){
            return new DateTime().toDateMidnight().toDate();
        }
        return Collections.max(eventRecordList, new EventRecordComparator()).getTimeStamp();
    }

    private Link getLink(String href, String rel, String type) {
        Link link = new Link();

        link.setHref(href);
        link.setRel(rel);
        link.setType(type);

        return link;
    }

    private String getServiceUri(URI requestUri) {
        String scheme = requestUri.getScheme();
        String hostname = requestUri.getHost();
        int port = requestUri.getPort();
        String path = requestUri.getPath().substring(0,
                requestUri.getPath().lastIndexOf("/"));
        if (port != 80 && port != -1) {
            return scheme + "://" + hostname + ":" + port + path;
        } else {
            return scheme + "://" + hostname + path;
        }
    }

    private List<Entry> getEntries(List<EventRecord> eventRecordList) {
        List<Entry> entryList = new ArrayList<Entry>();

        for (EventRecord eventRecord : eventRecordList) {
            final Entry entry = new Entry();
            entry.setId(eventRecord.getTagUri());
            entry.setTitle(eventRecord.getTitle());
            entry.setPublished(eventRecord.getTimeStamp());
            entry.setUpdated(eventRecord.getTimeStamp());
            entry.setCreated(getDateCreated(eventRecord));
            entry.setContents(generateContents(eventRecord));
            String category = eventRecord.getCategory();
            final String[] tagList = StringUtils.split(eventRecord.getTags(), ",");
            entry.setCategories(getCategories(tagList, category));
            entryList.add(entry);
        }

        return entryList;
    }

    private Date getDateCreated(EventRecord eventRecord) {
        return eventRecord.getDateCreated() != null ? eventRecord.getDateCreated() : eventRecord.getTimeStamp();
    }

    private List<Category> getCategories(String[] tagList, String category) {
        HashSet<String> categorySet = new HashSet<>();
        if ((tagList != null) && (tagList.length > 0)) {
            categorySet.addAll(Arrays.asList(tagList));
        }

        if (!StringUtils.isBlank(category)) {
            categorySet.add(category);
        }

        List<Category> categories = new ArrayList<>();
        for (String aCat : categorySet) {
            Category eventCategory = new Category();
            eventCategory.setTerm(aCat);
            categories.add(eventCategory);
        }

        return categories;
    }

    private List<Content> generateContents(EventRecord eventRecord) {
        Content content = new Content();
        content.setType(ATOMFEED_MEDIA_TYPE);
        content.setValue(wrapInCDATA(eventRecord.getContents()));
        return Arrays.asList(content);
    }

    private String wrapInCDATA(String contents){
        if(contents == null){
            return null;
        }
        return String.format("%s%s%s","<![CDATA[",contents,"]]>");
    }
}
