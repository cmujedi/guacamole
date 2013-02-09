package net.sourceforge.guacamole.net.basic.crud.connections;

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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.GuacamoleSecurityException;
import net.sourceforge.guacamole.net.auth.Connection;
import net.sourceforge.guacamole.net.auth.Directory;
import net.sourceforge.guacamole.net.auth.User;
import net.sourceforge.guacamole.net.auth.UserContext;
import net.sourceforge.guacamole.net.auth.permission.ConnectionDirectoryPermission;
import net.sourceforge.guacamole.net.auth.permission.ConnectionPermission;
import net.sourceforge.guacamole.net.auth.permission.ObjectPermission;
import net.sourceforge.guacamole.net.auth.permission.Permission;
import net.sourceforge.guacamole.net.auth.permission.SystemPermission;
import net.sourceforge.guacamole.net.basic.AuthenticatingHttpServlet;

/**
 * Simple HttpServlet which outputs XML containing a list of all authorized
 * configurations for the current user.
 *
 * @author Michael Jumper
 */
public class List extends AuthenticatingHttpServlet {

    /**
     * Checks whether the given user has permission to perform the given
     * system operation. Security exceptions are handled appropriately - only
     * non-security exceptions pass through.
     * 
     * @param user The user whose permissions should be verified.
     * @param type The type of operation to check for permission for.
     * @return true if permission is granted, false otherwise.
     * 
     * @throws GuacamoleException If an error occurs while checking permissions.
     */
    private boolean hasConfigPermission(User user, SystemPermission.Type type)
    throws GuacamoleException {

        // Build permission
        Permission permission =
                new ConnectionDirectoryPermission(type);

        try {
            // Return result of permission check, if possible
            return user.hasPermission(permission);
        }
        catch (GuacamoleSecurityException e) {
            // If cannot check due to security restrictions, no permission
            return false;
        }

    }

    /**
     * Checks whether the given user has permission to perform the given
     * object operation. Security exceptions are handled appropriately - only
     * non-security exceptions pass through.
     * 
     * @param user The user whose permissions should be verified.
     * @param type The type of operation to check for permission for.
     * @param identifier The identifier of the connection the operation
     *                   would be performed upon.
     * @return true if permission is granted, false otherwise.
     * 
     * @throws GuacamoleException If an error occurs while checking permissions.
     */
    private boolean hasConfigPermission(User user, ObjectPermission.Type type,
            String identifier)
    throws GuacamoleException {

        // Build permission
        Permission permission = new ConnectionPermission(
            type,
            identifier
        );

        try {
            // Return result of permission check, if possible
            return user.hasPermission(permission);
        }
        catch (GuacamoleSecurityException e) {
            // If cannot check due to security restrictions, no permission
            return false;
        }

    }
    
    @Override
    protected void authenticatedService(
            UserContext context,
            HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {

        // Do not cache
        response.setHeader("Cache-Control", "no-cache");

        // Write XML content type
        response.setHeader("Content-Type", "text/xml");

        // Attempt to get connections 
        Directory<String, Connection> directory;
        try {

            // Get connection directory
            directory = context.getConnectionDirectory();

        }
        catch (GuacamoleException e) {
            throw new ServletException("Unable to retrieve connections.", e);
        }
       
        // Write actual XML
        try {

            // Get self 
            User self = context.self();
            
            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            XMLStreamWriter xml = outputFactory.createXMLStreamWriter(response.getWriter());

            // Begin document
            xml.writeStartDocument();
            xml.writeStartElement("connections");
            
            // Save connection create permission attribute
            if (hasConfigPermission(self, SystemPermission.Type.CREATE))
                xml.writeAttribute("create", "yes");
            
            // For each entry, write corresponding connection element
            for (String identifier : directory.getIdentifiers()) {

                // Get connection 
                Connection connection = directory.get(identifier);

                // Write connection
                xml.writeEmptyElement("connection");
                xml.writeAttribute("id", identifier);
                xml.writeAttribute("protocol",
                        connection.getConfiguration().getProtocol());

                // Save update permission attribute
                if (hasConfigPermission(self, ObjectPermission.Type.UPDATE,
                        identifier))
                    xml.writeAttribute("update", "yes");
                
                // Save admin permission attribute
                if (hasConfigPermission(self, ObjectPermission.Type.ADMINISTER,
                        identifier))
                    xml.writeAttribute("admin", "yes");
                
                // Save delete permission attribute
                if (hasConfigPermission(self, ObjectPermission.Type.DELETE,
                        identifier))
                    xml.writeAttribute("delete", "yes");
                
            }

            // End document
            xml.writeEndElement();
            xml.writeEndDocument();

        }
        catch (XMLStreamException e) {
            throw new IOException("Unable to write configuration list XML.", e);
        }
        catch (GuacamoleException e) {
            throw new ServletException("Unable to read configurations.", e);
        }

    }

}

