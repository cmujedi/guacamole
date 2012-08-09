package net.sourceforge.guacamole.net.basic;

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
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.sourceforge.guacamole.protocol.GuacamoleConfiguration;

/**
 * Simple HttpServlet which outputs XML containing a list of all authorized
 * configurations for the current user.
 *
 * @author Michael Jumper
 */
public class ConfigurationList extends AuthenticatingHttpServlet {

    @Override
    protected void authenticatedService(
            Map<String, GuacamoleConfiguration> configs,
            HttpServletRequest request, HttpServletResponse response)
    throws IOException {

        // Do not cache
        response.setHeader("Cache-Control", "no-cache");

        // Write XML
        response.setHeader("Content-Type", "text/xml");
        PrintWriter out = response.getWriter();
        out.println("<configs>");

        for (Entry<String, GuacamoleConfiguration> entry : configs.entrySet()) {

            GuacamoleConfiguration config = entry.getValue();

            // Write config
            out.print("<config id=\"");
            out.print(entry.getKey());
            out.print("\" protocol=\"");
            out.print(config.getProtocol());
            out.println("\"/>");

        }

        out.println("</configs>");
    }

}

