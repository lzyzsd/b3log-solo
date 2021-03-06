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
package org.b3log.solo.repository.impl;

import java.util.logging.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.model.Role;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.solo.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * User repository.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.0.0.8, Nov 10, 2011
 * @since 0.3.1
 */
public final class UserRepositoryImpl extends AbstractRepository implements UserRepository {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserRepositoryImpl.class.getName());
    /**
     * Singleton.
     */
    private static final UserRepositoryImpl SINGLETON = new UserRepositoryImpl(User.USER);

    @Override
    public JSONObject getByEmail(final String email) throws RepositoryException {
        final Query query = new Query().setPageCount(1);
        query.addFilter(User.USER_EMAIL, FilterOperator.EQUAL, email.toLowerCase().trim());

        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        if (0 == array.length()) {
            return null;
        }

        return array.optJSONObject(0);
    }

    @Override
    public JSONObject getAdmin() throws RepositoryException {
        final Query query = new Query().addFilter(User.USER_ROLE, FilterOperator.EQUAL, Role.ADMIN_ROLE).setPageCount(1);
        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        if (0 == array.length()) {
            return null;
        }

        return array.optJSONObject(0);
    }

    @Override
    public boolean isAdminEmail(final String email) throws RepositoryException {
        final JSONObject user = getByEmail(email);

        if (null == user) {
            return false;
        }

        return Role.ADMIN_ROLE.equals(user.optString(User.USER_ROLE));
    }

    /**
     * Gets the {@link UserRepositoryImpl} singleton.
     *
     * @return the singleton
     */
    public static UserRepositoryImpl getInstance() {
        return SINGLETON;
    }

    /**
     * Private constructor.
     * 
     * @param name the specified name
     */
    private UserRepositoryImpl(final String name) {
        super(name);
    }
}
