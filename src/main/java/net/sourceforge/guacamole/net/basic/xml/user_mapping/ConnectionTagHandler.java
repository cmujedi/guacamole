package net.sourceforge.guacamole.net.basic.xml.user_mapping;

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

import net.sourceforge.guacamole.net.basic.xml.TagHandler;
import net.sourceforge.guacamole.protocol.GuacamoleConfiguration;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * TagHandler for the "connection" element.
 * 
 * @author Mike Jumper 
 */
public class ConnectionTagHandler implements TagHandler {

    /**
     * The GuacamoleConfiguration backing this tag handler.
     */
    private GuacamoleConfiguration config = new GuacamoleConfiguration();
    
    /**
     * The name associated with the connection being parsed.
     */
    private String name;
    
    /**
     * Creates a new handler for an "connection" tag having the given
     * attributes.
     * 
     * @param attributes The attributes of the "connection" tag.
     * @throws SAXException If the attributes given are not valid.
     */
    public ConnectionTagHandler(Attributes attributes) throws SAXException {
        name = attributes.getValue("name");
    }

    @Override
    public TagHandler childElement(String localName, Attributes attributes) throws SAXException {

        if (localName.equals("param"))
            return new ParamTagHandler(config, attributes);

        if (localName.equals("protocol"))
            return new ProtocolTagHandler(config);

        return null;
        
    }

    @Override
    public void complete(String textContent) throws SAXException {
        // Do nothing
    }
  
    /**
     * Returns a GuacamoleConfiguration whose contents are populated from data
     * within this connection element and child elements. This
     * GuacamoleConfiguration will continue to be modified as the user mapping
     * is parsed.
     * 
     * @return A GuacamoleConfiguration whose contents are populated from data
     *         within this connection element.
     */
    public GuacamoleConfiguration asGuacamoleConfiguration() {
        return config;
    }

    /**
     * Returns the name associated with this connection.
     * 
     * @return The name associated with this connection.
     */
    public String getName() {
        return name;
    }
    
}
