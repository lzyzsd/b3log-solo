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
package org.b3log.solo.processor.console;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.action.AbstractAction;
import org.b3log.latke.annotation.RequestProcessing;
import org.b3log.latke.annotation.RequestProcessor;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.solo.model.Common;
import org.b3log.solo.service.LinkMgmtService;
import org.b3log.solo.service.LinkQueryService;
import org.b3log.solo.util.QueryResults;
import org.b3log.solo.util.Users;
import org.b3log.latke.util.Requests;
import org.json.JSONObject;

/**
 * Link console request processing.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.0.0.2, Oct 31, 2011
 * @since 0.4.0
 */
@RequestProcessor
public final class LinkConsole {

    /**
     * Logger.
     */
    private static final Logger LOGGER =
            Logger.getLogger(LinkConsole.class.getName());
    /**
     * User utilities.
     */
    private Users userUtils = Users.getInstance();
    /**
     * Link query service.
     */
    private LinkQueryService linkQueryService = LinkQueryService.getInstance();
    /**
     * Link management service.
     */
    private LinkMgmtService linkMgmtService = LinkMgmtService.getInstance();
    /**
     * Language service.
     */
    private LangPropsService langPropsService = LangPropsService.getInstance();
    /**
     * Link URI prefix.
     */
    private static final String LINK_URI_PREFIX = "/console/link/";
    /**
     * Links URI prefix.
     */
    private static final String LINKS_URI_PREFIX = "/console/links/";
    /**
     * Link order URI prefix.
     */
    private static final String LINK_ORDER_URI_PREFIX = LINK_URI_PREFIX
                                                        + "order/";

    /**
     * Removes a link by the specified request.
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param request the specified http servlet request
     * @param response the specified http servlet response
     * @param context the specified http request context
     * @throws Exception exception
     */
    @RequestProcessing(value = LINK_URI_PREFIX + "*",
                       method = HTTPRequestMethod.DELETE)
    public void removeLink(final HttpServletRequest request,
                           final HttpServletResponse response,
                           final HTTPRequestContext context)
            throws Exception {
        if (!userUtils.isAdminLoggedIn(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        final JSONObject jsonObject = new JSONObject();
        renderer.setJSONObject(jsonObject);

        try {
            final String linkId =
                    request.getRequestURI().substring((Latkes.getContextPath() + LINK_URI_PREFIX).length());

            linkMgmtService.removeLink(linkId);

            jsonObject.put(Keys.STATUS_CODE, true);
            jsonObject.put(Keys.MSG, langPropsService.get("removeSuccLabel"));
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("removeFailLabel"));
        }
    }

    /**
     * Updates a link by the specified request.
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param request the specified http servlet request, for example,
     * <pre>
     * {
     *     "link": {
     *         "oId": "",
     *         "linkTitle": "",
     *         "linkAddress": "",
     *         "linkDescription": ""
     *     }
     * }, see {@link org.b3log.solo.model.Link} for more details
     * </pre>
     * @param context the specified http request context
     * @param response the specified http servlet response
     * @throws Exception exception
     */
    @RequestProcessing(value = LINK_URI_PREFIX,
                       method = HTTPRequestMethod.PUT)
    public void updateLink(final HttpServletRequest request,
                           final HttpServletResponse response,
                           final HTTPRequestContext context)
            throws Exception {
        if (!userUtils.isAdminLoggedIn(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        final JSONObject ret = new JSONObject();

        try {
            final JSONObject requestJSONObject =
                    AbstractAction.parseRequestJSONObject(request, response);

            linkMgmtService.updateLink(requestJSONObject);

            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("updateSuccLabel"));

            renderer.setJSONObject(ret);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            final JSONObject jsonObject = QueryResults.defaultResult();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("updateFailLabel"));
        }
    }

    /**
     * Changes a link order by the specified link id and direction.
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param request the specified http servlet request, for example,
     * <pre>
     * {
     *     "oId": "",
     *     "direction": "" // "up"/"down"
     * }
     * </pre>
     * @param response the specified http servlet response
     * @param context the specified http request context
     * @throws Exception exception 
     */
    @RequestProcessing(value = LINK_ORDER_URI_PREFIX,
                       method = HTTPRequestMethod.PUT)
    public void changeOrder(final HttpServletRequest request,
                            final HttpServletResponse response,
                            final HTTPRequestContext context)
            throws Exception {
        if (!userUtils.isAdminLoggedIn(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        final JSONObject ret = new JSONObject();

        try {
            final JSONObject requestJSONObject =
                    AbstractAction.parseRequestJSONObject(request, response);
            final String linkId =
                    requestJSONObject.getString(Keys.OBJECT_ID);
            final String direction =
                    requestJSONObject.getString(Common.DIRECTION);

            linkMgmtService.changeOrder(linkId, direction);

            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("updateSuccLabel"));

            renderer.setJSONObject(ret);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            final JSONObject jsonObject = QueryResults.defaultResult();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("updateFailLabel"));
        }
    }

    /**
     * Adds a link with the specified request.
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "oId": "", // Generated link id
     *     "msg": ""
     * }
     * </pre>
     * </p>
     * 
     * @param request the specified http servlet request, for example,
     * <pre>
     * {
     *     "link": {
     *         "linkTitle": "",
     *         "linkAddress": "",
     *         "linkDescription": ""
     *     }
     * }
     * </pre>
     * @param response the specified http servlet response
     * @param context the specified http request context
     * @throws Exception exception
     */
    @RequestProcessing(value = LINK_URI_PREFIX,
                       method = HTTPRequestMethod.POST)
    public void addLink(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final HTTPRequestContext context)
            throws Exception {
        if (!userUtils.isAdminLoggedIn(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        final JSONObject ret = new JSONObject();

        try {
            final JSONObject requestJSONObject =
                    AbstractAction.parseRequestJSONObject(request, response);

            final String linkId = linkMgmtService.addLink(requestJSONObject);

            ret.put(Keys.OBJECT_ID, linkId);
            ret.put(Keys.MSG, langPropsService.get("addSuccLabel"));
            ret.put(Keys.STATUS_CODE, true);

            renderer.setJSONObject(ret);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            final JSONObject jsonObject = QueryResults.defaultResult();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("addFailLabel"));
        }
    }

    /**
     * Gets links by the specified request.
     * 
     * <p>
     * The request URI contains the pagination arguments. For example, the 
     * request URI is /console/links/1/10/20, means the current page is 1, the
     * page size is 10, and the window size is 20.
     * </p>
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "links": [{
     *         "oId": "",
     *         "linkTitle": "",
     *         "linkAddress": "",
     *         "linkDescription": ""
     *      }, ....]
     * }
     * </pre>
     * </p>
     *
     * @param request the specified http servlet request
     * @param response the specified http servlet response
     * @param context the specified http request context
     * @throws Exception exception 
     */
    @RequestProcessing(value = LINKS_URI_PREFIX + Requests.PAGINATION_PATH_PATTERN,
                       method = HTTPRequestMethod.GET)
    public void getLinks(final HttpServletRequest request,
                         final HttpServletResponse response,
                         final HTTPRequestContext context) throws Exception {
        if (!userUtils.isLoggedIn(request, response)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        try {
            final String requestURI = request.getRequestURI();
            final String path = requestURI.substring((Latkes.getContextPath() + LINKS_URI_PREFIX).length());

            final JSONObject requestJSONObject = Requests.buildPaginationRequest(path);

            final JSONObject result = linkQueryService.getLinks(requestJSONObject);
            result.put(Keys.STATUS_CODE, true);

            renderer.setJSONObject(result);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            final JSONObject jsonObject = QueryResults.defaultResult();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }

    /**
     * Gets the file with the specified request.
     * 
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "link": {
     *         "oId": "",
     *         "linkTitle": "",
     *         "linkAddress": "",
     *         "linkDescription": ""
     *     }
     * }
     * </pre>
     * </p>
     * 
     * @param request the specified http servlet request
     * @param response the specified http servlet response
     * @param context the specified http request context
     * @throws Exception exception
     */
    @RequestProcessing(value = LINK_URI_PREFIX + "*", method = HTTPRequestMethod.GET)
    public void getLink(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final HTTPRequestContext context)
            throws Exception {
        if (!userUtils.isLoggedIn(request, response)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);

        try {
            final String requestURI = request.getRequestURI();
            final String linkId = requestURI.substring((Latkes.getContextPath() + LINK_URI_PREFIX).length());

            final JSONObject result = linkQueryService.getLink(linkId);

            if (null == result) {
                renderer.setJSONObject(QueryResults.defaultResult());

                return;
            }

            renderer.setJSONObject(result);
            result.put(Keys.STATUS_CODE, true);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

            final JSONObject jsonObject = QueryResults.defaultResult();
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }
}
