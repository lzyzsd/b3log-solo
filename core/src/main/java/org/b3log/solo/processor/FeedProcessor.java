/*
 * Copyright (c) 2009, 2010, 2011, 2012, B3log Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.solo.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringEscapeUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.annotation.RequestProcessing;
import org.b3log.latke.annotation.RequestProcessor;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.renderer.AtomRenderer;
import org.b3log.latke.servlet.renderer.RssRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Strings;
import org.b3log.solo.SoloServletListener;
import org.b3log.solo.model.Article;
import org.b3log.solo.model.Preference;
import org.b3log.solo.model.Tag;
import org.b3log.solo.model.feed.atom.Category;
import org.b3log.solo.model.feed.atom.Entry;
import org.b3log.solo.model.feed.atom.Feed;
import org.b3log.solo.model.feed.rss.Channel;
import org.b3log.solo.model.feed.rss.Item;
import org.b3log.solo.repository.ArticleRepository;
import org.b3log.solo.repository.TagArticleRepository;
import org.b3log.solo.repository.TagRepository;
import org.b3log.solo.repository.impl.ArticleRepositoryImpl;
import org.b3log.solo.repository.impl.TagArticleRepositoryImpl;
import org.b3log.solo.repository.impl.TagRepositoryImpl;
import org.b3log.solo.service.PreferenceQueryService;
import org.b3log.solo.util.Articles;
import org.b3log.solo.util.TimeZones;
import org.b3log.solo.util.Users;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Feed (Atom/RSS) processor.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.1.0.0, May 10, 2012
 * @since 0.3.1
 */
@RequestProcessor
public final class FeedProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FeedProcessor.class.getName());
    /**
     * Article repository.
     */
    private ArticleRepository articleRepository = ArticleRepositoryImpl.getInstance();
    /**
     * Preference query service.
     */
    private PreferenceQueryService preferenceQueryService = PreferenceQueryService.getInstance();
    /**
     * Count of output entry.
     */
    public static final int ENTRY_OUTPUT_CNT = 10;
    /**
     * Article utilities.
     */
    private Articles articleUtils = Articles.getInstance();
    /**
     * Tag repository.
     */
    private TagRepository tagRepository = TagRepositoryImpl.getInstance();
    /**
     * Tag-Article repository.
     */
    private TagArticleRepository tagArticleRepository = TagArticleRepositoryImpl.getInstance();

    /**
     * Blog articles Atom output.
     * 
     * @param context the specified context
     */
    @RequestProcessing(value = {"/blog-articles-feed.do"}, method = {HTTPRequestMethod.GET, HTTPRequestMethod.HEAD})
    public void blogArticlesAtom(final HTTPRequestContext context) {
        final AtomRenderer renderer = new AtomRenderer();
        context.setRenderer(renderer);

        final Feed feed = new Feed();
        try {
            final JSONObject preference = preferenceQueryService.getPreference();

            final String blogTitle = preference.getString(Preference.BLOG_TITLE);
            final String blogSubtitle = preference.getString(Preference.BLOG_SUBTITLE);
            final String blogHost = preference.getString(Preference.BLOG_HOST);

            feed.setTitle(StringEscapeUtils.escapeXml(blogTitle));
            feed.setSubtitle(StringEscapeUtils.escapeXml(blogSubtitle));
            feed.setUpdated(TimeZones.getTime(preference.getString(Preference.TIME_ZONE_ID)));
            feed.setAuthor(StringEscapeUtils.escapeXml(blogTitle));
            feed.setLink("http://" + blogHost + "/blog-articles-feed.do");
            feed.setId("http://" + blogHost + "/");

            final Query query = new Query().setCurrentPageNum(1).
                    setPageSize(ENTRY_OUTPUT_CNT).
                    addFilter(Article.ARTICLE_IS_PUBLISHED, FilterOperator.EQUAL, true).
                    addSort(Article.ARTICLE_UPDATE_DATE, SortDirection.DESCENDING).
                    setPageCount(1);

            final boolean hasMultipleUsers = Users.getInstance().hasMultipleUsers();
            String authorName = "";

            final JSONObject articleResult = articleRepository.get(query);
            final JSONArray articles = articleResult.getJSONArray(Keys.RESULTS);

            if (!hasMultipleUsers && 0 != articles.length()) {
                authorName = articleUtils.getAuthor(articles.getJSONObject(0)).getString(User.USER_NAME);
            }

            final boolean isFullContent = "fullContent".equals(preference.getString(Preference.FEED_OUTPUT_MODE));

            for (int i = 0; i < articles.length(); i++) {
                final JSONObject article = articles.getJSONObject(i);
                final Entry entry = new Entry();
                feed.addEntry(entry);
                final String title = StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_TITLE));
                entry.setTitle(title);
                final String summary = isFullContent ? StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_CONTENT))
                                       : StringEscapeUtils.escapeXml(article.optString(Article.ARTICLE_ABSTRACT));
                entry.setSummary(summary);
                final Date updated = (Date) article.get(Article.ARTICLE_UPDATE_DATE);
                entry.setUpdated(updated);

                final String link = "http://" + blogHost + article.getString(Article.ARTICLE_PERMALINK);
                entry.setLink(link);
                entry.setId(link);

                if (hasMultipleUsers) {
                    authorName = StringEscapeUtils.escapeXml(articleUtils.getAuthor(article).getString(User.USER_NAME));
                }
                entry.setAuthor(authorName);

                final String tagsString = article.getString(Article.ARTICLE_TAGS_REF);
                final String[] tagStrings = tagsString.split(",");
                for (int j = 0; j < tagStrings.length; j++) {
                    final Category catetory = new Category();
                    entry.addCatetory(catetory);
                    final String tag = tagStrings[j];
                    catetory.setTerm(tag);
                }
            }

            renderer.setContent(feed.toString());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Get blog article feed error", e);

            try {
                context.getResponse().sendError(
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Tag articles Atom output.
     * 
     * @param context the specified context
     * @throws IOException io exception 
     */
    @RequestProcessing(value = {"/tag-articles-feed.do"}, method = {HTTPRequestMethod.GET, HTTPRequestMethod.HEAD})
    public void tagArticlesAtom(final HTTPRequestContext context) throws IOException {
        final AtomRenderer renderer = new AtomRenderer();
        context.setRenderer(renderer);

        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        final String queryString = request.getQueryString();

        if (Strings.isEmptyOrNull(queryString)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final String oIdMap = queryString.split("&")[0];
        final String tagId = oIdMap.split("=")[1];

        final Feed feed = new Feed();
        try {
            final String tagTitle = tagRepository.get(tagId).getString(Tag.TAG_TITLE);

            final JSONObject preference = preferenceQueryService.getPreference();
            if (null == preference) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final String blogTitle = preference.getString(Preference.BLOG_TITLE);
            final String blogSubtitle = preference.getString(Preference.BLOG_SUBTITLE) + ", " + tagTitle;
            final String blogHost = preference.getString(Preference.BLOG_HOST);

            feed.setTitle(StringEscapeUtils.escapeXml(blogTitle));
            feed.setSubtitle(StringEscapeUtils.escapeXml(blogSubtitle));
            feed.setUpdated(TimeZones.getTime(preference.getString(Preference.TIME_ZONE_ID)));
            feed.setAuthor(StringEscapeUtils.escapeXml(blogTitle));
            feed.setLink("http://" + blogHost + "/tag-articles-feed.do");
            feed.setId("http://" + blogHost + "/");

            final JSONObject tagArticleResult = tagArticleRepository.getByTagId(tagId, 1, ENTRY_OUTPUT_CNT);
            final JSONArray tagArticleRelations = tagArticleResult.getJSONArray(Keys.RESULTS);
            if (0 == tagArticleRelations.length()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final List<JSONObject> articles = new ArrayList<JSONObject>();
            for (int i = 0; i < tagArticleRelations.length(); i++) {
                final JSONObject tagArticleRelation = tagArticleRelations.getJSONObject(i);
                final String articleId = tagArticleRelation.getString(Article.ARTICLE + "_" + Keys.OBJECT_ID);
                final JSONObject article = articleRepository.get(articleId);
                if (article.getBoolean(Article.ARTICLE_IS_PUBLISHED)) {  // Skips the unpublished article
                    articles.add(article);
                }
            }

            final boolean hasMultipleUsers = Users.getInstance().hasMultipleUsers();
            String authorName = "";

            if (!hasMultipleUsers && !articles.isEmpty()) {
                authorName = articleUtils.getAuthor(articles.get(0)).getString(User.USER_NAME);
            }

            final boolean isFullContent = "fullContent".equals(preference.getString(Preference.FEED_OUTPUT_MODE));

            for (int i = 0; i < articles.size(); i++) {
                final JSONObject article = articles.get(i);
                final Entry entry = new Entry();
                feed.addEntry(entry);
                final String title = StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_TITLE));
                entry.setTitle(title);
                final String summary = isFullContent ? StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_CONTENT))
                                       : StringEscapeUtils.escapeXml(article.optString(Article.ARTICLE_ABSTRACT));
                entry.setSummary(summary);
                final Date updated = (Date) article.get(Article.ARTICLE_UPDATE_DATE);
                entry.setUpdated(updated);
                final String link = "http://" + blogHost + article.getString(Article.ARTICLE_PERMALINK);
                entry.setLink(link);
                entry.setId(link);

                if (hasMultipleUsers) {
                    authorName = StringEscapeUtils.escapeXml(articleUtils.getAuthor(article).getString(User.USER_NAME));
                }

                entry.setAuthor(authorName);

                final String tagsString = article.getString(Article.ARTICLE_TAGS_REF);
                final String[] tagStrings = tagsString.split(",");
                for (int j = 0; j < tagStrings.length; j++) {
                    final Category catetory = new Category();
                    entry.addCatetory(catetory);
                    final String tag = tagStrings[j];
                    catetory.setTerm(tag);
                }
            }

            renderer.setContent(feed.toString());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Get tag article feed error", e);

            try {
                context.getResponse().sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Blog articles RSS output.
     * 
     * @param context the specified context
     */
    @RequestProcessing(value = {"/blog-articles-rss.do"}, method = {HTTPRequestMethod.GET, HTTPRequestMethod.HEAD})
    public void blogArticlesRSS(final HTTPRequestContext context) {
        final HttpServletResponse response = context.getResponse();
        final RssRenderer renderer = new RssRenderer();
        context.setRenderer(renderer);

        final Channel channel = new Channel();
        try {
            final JSONObject preference = preferenceQueryService.getPreference();
            if (null == preference) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final String blogTitle = preference.getString(Preference.BLOG_TITLE);
            final String blogSubtitle = preference.getString(Preference.BLOG_SUBTITLE);
            final String blogHost = preference.getString(Preference.BLOG_HOST);

            channel.setTitle(StringEscapeUtils.escapeXml(blogTitle));
            channel.setLastBuildDate(TimeZones.getTime(preference.getString(Preference.TIME_ZONE_ID)));
            channel.setLink("http://" + blogHost);
            channel.setAtomLink("http://" + blogHost + "/blog-articles-rss.do");
            channel.setGenerator("B3log Solo, ver " + SoloServletListener.VERSION);
            final String localeString = preference.getString(Preference.LOCALE_STRING);
            final String country = Locales.getCountry(localeString).toLowerCase();
            final String language = Locales.getLanguage(localeString).toLowerCase();
            channel.setLanguage(language + '-' + country);
            channel.setDescription(blogSubtitle);

            final Query query = new Query().setCurrentPageNum(1).
                    setPageSize(ENTRY_OUTPUT_CNT).
                    addFilter(Article.ARTICLE_IS_PUBLISHED, FilterOperator.EQUAL, true).
                    addSort(Article.ARTICLE_UPDATE_DATE, SortDirection.DESCENDING).
                    setPageCount(1);

            final JSONObject articleResult = articleRepository.get(query);
            final JSONArray articles = articleResult.getJSONArray(Keys.RESULTS);

            final boolean hasMultipleUsers = Users.getInstance().hasMultipleUsers();
            String authorName = "";

            if (!hasMultipleUsers && 0 != articles.length()) {
                authorName = articleUtils.getAuthor(articles.getJSONObject(0)).getString(User.USER_NAME);
            }

            final boolean isFullContent = "fullContent".equals(preference.getString(Preference.FEED_OUTPUT_MODE));

            for (int i = 0; i < articles.length(); i++) {
                final JSONObject article = articles.getJSONObject(i);
                final Item item = new Item();
                channel.addItem(item);
                final String title = StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_TITLE));
                item.setTitle(title);
                final String description = isFullContent ? StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_CONTENT))
                                           : StringEscapeUtils.escapeXml(article.optString(Article.ARTICLE_ABSTRACT));
                item.setDescription(description);
                final Date pubDate = (Date) article.get(Article.ARTICLE_UPDATE_DATE);
                item.setPubDate(pubDate);
                final String link = "http://" + blogHost + article.getString(Article.ARTICLE_PERMALINK);
                item.setLink(link);
                item.setGUID(link);

                final String authorEmail = article.getString(Article.ARTICLE_AUTHOR_EMAIL);
                if (hasMultipleUsers) {
                    authorName = StringEscapeUtils.escapeXml(articleUtils.getAuthor(article).getString(User.USER_NAME));
                }

                item.setAuthor(authorEmail + "(" + authorName + ")");

                final String tagsString = article.getString(Article.ARTICLE_TAGS_REF);
                final String[] tagStrings = tagsString.split(",");
                for (int j = 0; j < tagStrings.length; j++) {
                    final org.b3log.solo.model.feed.rss.Category catetory = new org.b3log.solo.model.feed.rss.Category();
                    item.addCatetory(catetory);
                    final String tag = tagStrings[j];
                    catetory.setTerm(tag);
                }
            }

            renderer.setContent(channel.toString());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Get blog article rss error", e);

            try {
                context.getResponse().sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Tag articles RSS output.
     * 
     * @param context the specified context
     * @throws IOException io exception 
     */
    @RequestProcessing(value = {"/tag-articles-rss.do"}, method = {HTTPRequestMethod.GET, HTTPRequestMethod.HEAD})
    public void tagArticlesRSS(final HTTPRequestContext context) throws IOException {
        final HttpServletResponse response = context.getResponse();
        final HttpServletRequest request = context.getRequest();

        final RssRenderer renderer = new RssRenderer();
        context.setRenderer(renderer);

        final String queryString = request.getQueryString();
        if (Strings.isEmptyOrNull(queryString)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        final String oIdMap = queryString.split("&")[0];
        final String tagId = oIdMap.split("=")[1];

        final Channel channel = new Channel();
        try {
            final String tagTitle = tagRepository.get(tagId).getString(Tag.TAG_TITLE);

            final JSONObject preference = preferenceQueryService.getPreference();
            if (null == preference) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final String blogTitle = preference.getString(Preference.BLOG_TITLE);
            final String blogSubtitle = preference.getString(Preference.BLOG_SUBTITLE) + ", " + tagTitle;
            final String blogHost = preference.getString(Preference.BLOG_HOST);

            channel.setTitle(StringEscapeUtils.escapeXml(blogTitle));
            channel.setLastBuildDate(TimeZones.getTime(preference.getString(Preference.TIME_ZONE_ID)));
            channel.setLink("http://" + blogHost);
            channel.setAtomLink("http://" + blogHost + "/tag-articles-rss.do");
            channel.setGenerator("B3log Solo, ver " + SoloServletListener.VERSION);
            final String localeString = preference.getString(Preference.LOCALE_STRING);
            final String country = Locales.getCountry(localeString).toLowerCase();
            final String language = Locales.getLanguage(localeString).toLowerCase();
            channel.setLanguage(language + '-' + country);
            channel.setDescription(blogSubtitle);

            final JSONObject tagArticleResult = tagArticleRepository.getByTagId(tagId, 1, ENTRY_OUTPUT_CNT);
            final JSONArray tagArticleRelations = tagArticleResult.getJSONArray(Keys.RESULTS);
            if (0 == tagArticleRelations.length()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final List<JSONObject> articles = new ArrayList<JSONObject>();
            for (int i = 0; i < tagArticleRelations.length(); i++) {
                final JSONObject tagArticleRelation = tagArticleRelations.getJSONObject(i);
                final String articleId = tagArticleRelation.getString(Article.ARTICLE + "_" + Keys.OBJECT_ID);
                final JSONObject article = articleRepository.get(articleId);
                if (article.getBoolean(Article.ARTICLE_IS_PUBLISHED)) {  // Skips the unpublished article
                    articles.add(article);
                }
            }

            final boolean hasMultipleUsers = Users.getInstance().hasMultipleUsers();
            String authorName = "";

            if (!hasMultipleUsers && !articles.isEmpty()) {
                authorName = articleUtils.getAuthor(articles.get(0)).getString(User.USER_NAME);
            }

            final boolean isFullContent = "fullContent".equals(preference.getString(Preference.FEED_OUTPUT_MODE));

            for (int i = 0; i < articles.size(); i++) {
                final JSONObject article = articles.get(i);
                final Item item = new Item();
                channel.addItem(item);
                final String title = StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_TITLE));
                item.setTitle(title);
                final String description = isFullContent ? StringEscapeUtils.escapeXml(article.getString(Article.ARTICLE_CONTENT))
                                           : StringEscapeUtils.escapeXml(article.optString(Article.ARTICLE_ABSTRACT));
                item.setDescription(description);
                final Date pubDate = (Date) article.get(Article.ARTICLE_UPDATE_DATE);
                item.setPubDate(pubDate);
                final String link = "http://" + blogHost + article.getString(Article.ARTICLE_PERMALINK);
                item.setLink(link);
                item.setGUID(link);

                final String authorEmail = article.getString(Article.ARTICLE_AUTHOR_EMAIL);
                if (hasMultipleUsers) {
                    authorName = StringEscapeUtils.escapeXml(articleUtils.getAuthor(article).getString(User.USER_NAME));
                }

                item.setAuthor(authorEmail + "(" + authorName + ")");

                final String tagsString = article.getString(Article.ARTICLE_TAGS_REF);
                final String[] tagStrings = tagsString.split(",");
                for (int j = 0; j < tagStrings.length; j++) {
                    final org.b3log.solo.model.feed.rss.Category catetory = new org.b3log.solo.model.feed.rss.Category();
                    item.addCatetory(catetory);
                    final String tag = tagStrings[j];
                    catetory.setTerm(tag);
                }
            }

            renderer.setContent(channel.toString());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Get tag article rss error", e);

            try {
                context.getResponse().sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
