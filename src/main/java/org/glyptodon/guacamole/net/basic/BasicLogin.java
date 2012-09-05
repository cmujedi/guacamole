package org.glyptodon.guacamole.net.basic;

/*
 *  Guacamole - Clientless Remote Desktop
 *  Copyright (C) 2010  Michael Jumper
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.glyptodon.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple dummy AuthenticatingHttpServlet which provides an endpoint for arbitrary
 * authentication requests that do not expect a response.
 *
 * @author Michael Jumper
 */
public class BasicLogin extends AuthenticatingHttpServlet {

    private Logger logger = LoggerFactory.getLogger(BasicLogin.class);

    @Override
    protected void authenticatedService(
            Map<String, GuacamoleConfiguration> configs,
            HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        logger.info("Login was successful.");
    }

}

