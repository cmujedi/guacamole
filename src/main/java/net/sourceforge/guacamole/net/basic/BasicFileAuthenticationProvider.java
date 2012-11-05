
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.auth.AuthenticationProvider;
import net.sourceforge.guacamole.net.auth.Credentials;
import net.sourceforge.guacamole.properties.FileGuacamoleProperty;
import net.sourceforge.guacamole.properties.GuacamoleProperties;
import net.sourceforge.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Authenticates users against a static list of username/password pairs.
 * Each username/password may be associated with multiple configurations.
 * This list is stored in an XML file which is reread if modified.
 *
 * @author Michael Jumper, Michal Kotas
 */
public class BasicFileAuthenticationProvider implements AuthenticationProvider {

    private Logger logger = LoggerFactory.getLogger(BasicFileAuthenticationProvider.class);

    private long mappingTime;
    private Map<String, AuthInfo> mapping;

    /**
     * The filename of the XML file to read the user mapping from.
     */
    public static final FileGuacamoleProperty BASIC_USER_MAPPING = new FileGuacamoleProperty() {

        @Override
        public String getName() { return "basic-user-mapping"; }

    };

    private File getUserMappingFile() throws GuacamoleException {

        // Get user mapping file
        return GuacamoleProperties.getProperty(BASIC_USER_MAPPING);

    }

    public synchronized void init() throws GuacamoleException {

        // Get user mapping file
        File mapFile = getUserMappingFile();
        if (mapFile == null)
            throw new GuacamoleException("Missing \"basic-user-mapping\" parameter required for basic login.");

        logger.info("Reading user mapping file: {}", mapFile);

        // Parse document
        try {

            // Set up parser
            BasicUserMappingContentHandler contentHandler = new BasicUserMappingContentHandler();

            XMLReader parser = XMLReaderFactory.createXMLReader();
            parser.setContentHandler(contentHandler);

            // Read and parse file
            Reader reader = new BufferedReader(new FileReader(mapFile));
            parser.parse(new InputSource(reader));
            reader.close();

            // Init mapping and record mod time of file
            mappingTime = mapFile.lastModified();
            mapping = contentHandler.getUserMapping();

        }
        catch (IOException e) {
            throw new GuacamoleException("Error reading basic user mapping file.", e);
        }
        catch (SAXException e) {
            throw new GuacamoleException("Error parsing basic user mapping XML.", e);
        }

    }

    @Override
    public Map<String, GuacamoleConfiguration> getAuthorizedConfigurations(Credentials credentials) throws GuacamoleException {

        // Check mapping file mod time
        File userMappingFile = getUserMappingFile();
        if (userMappingFile.exists() && mappingTime < userMappingFile.lastModified()) {

            // If modified recently, gain exclusive access and recheck
            synchronized (this) {
                if (userMappingFile.exists() && mappingTime < userMappingFile.lastModified()) {
                    logger.info("User mapping file {} has been modified.", userMappingFile);
                    init(); // If still not up to date, re-init
                }
            }

        }

        // If no mapping available, report as such
        if (mapping == null)
            throw new GuacamoleException("User mapping could not be read.");

        // Validate and return info for given user and pass
        AuthInfo info = mapping.get(credentials.getUsername());
        if (info != null && info.validate(credentials.getUsername(), credentials.getPassword()))
            return info.getConfigurations();

        // Unauthorized
        return null;

    }

    public static class AuthInfo {

        public static enum Encoding {
            PLAIN_TEXT,
            MD5
        }

        private String auth_username;
        private String auth_password;
        private Encoding auth_encoding;

        private Map<String, GuacamoleConfiguration> configs;

        public AuthInfo(String auth_username, String auth_password, Encoding auth_encoding) {
            this.auth_username = auth_username;
            this.auth_password = auth_password;
            this.auth_encoding = auth_encoding;

            configs = new HashMap<String, GuacamoleConfiguration>();
        }

        private static final char HEX_CHARS[] = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };

        public static String getHexString(byte[] bytes) {

            if (bytes == null)
                return null;

            StringBuilder hex = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                hex.append(HEX_CHARS[(b & 0xF0) >> 4])
                   .append(HEX_CHARS[(b & 0x0F)     ]);
            }

            return hex.toString();

        }


        public boolean validate(String username, String password) {

            // If username matches
            if (username != null && password != null && username.equals(auth_username)) {

                switch (auth_encoding) {

                    case PLAIN_TEXT:

                        // Compare plaintext
                        return password.equals(auth_password);

                    case MD5:

                        // Compare hashed password
                        try {
                            MessageDigest digest = MessageDigest.getInstance("MD5");
                            String hashedPassword = getHexString(digest.digest(password.getBytes()));
                            return hashedPassword.equals(auth_password.toUpperCase());
                        }
                        catch (NoSuchAlgorithmException e) {
                            throw new UnsupportedOperationException("Unexpected lack of MD5 support.", e);
                        }

                }

            }

            return false;

        }

        public GuacamoleConfiguration getConfiguration(String name) {

            // Create new configuration if not already in map
            GuacamoleConfiguration config = configs.get(name);
            if (config == null) {
                config = new GuacamoleConfiguration();
                configs.put(name, config);
            }

            return config;

        }

        public Map<String, GuacamoleConfiguration> getConfigurations() {
            return configs;
        }

    }

    private static class BasicUserMappingContentHandler extends DefaultHandler {

        private Map<String, AuthInfo> authMapping = new HashMap<String, AuthInfo>();

        public Map<String, AuthInfo> getUserMapping() {
            return Collections.unmodifiableMap(authMapping);
        }

        private enum State {
            ROOT,
            USER_MAPPING,

            /* Username/password pair */
            AUTH_INFO,

            /* Connection configuration information */
            CONNECTION,
            PROTOCOL,
            PARAMETER,

            /* Configuration information associated with default connection */
            DEFAULT_CONNECTION_PROTOCOL,
            DEFAULT_CONNECTION_PARAMETER,

            END;
        }

        private State state = State.ROOT;
        private AuthInfo current = null;
        private String currentParameter = null;
        private String currentConnection = null;

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            switch (state)  {

                case USER_MAPPING:

                    if (localName.equals("user-mapping")) {
                        state = State.END;
                        return;
                    }

                    break;

                case AUTH_INFO:

                    if (localName.equals("authorize")) {

                        // Finalize mapping for this user
                        authMapping.put(
                            current.auth_username,
                            current
                        );

                        state = State.USER_MAPPING;
                        return;
                    }

                    break;

                case CONNECTION:

                    if (localName.equals("connection")) {
                        state = State.AUTH_INFO;
                        return;
                    }

                    break;

                case PROTOCOL:

                    if (localName.equals("protocol")) {
                        state = State.CONNECTION;
                        return;
                    }

                    break;

                case PARAMETER:

                    if (localName.equals("param")) {
                        state = State.CONNECTION;
                        return;
                    }

                    break;

                case DEFAULT_CONNECTION_PROTOCOL:

                    if (localName.equals("protocol")) {
                        state = State.AUTH_INFO;
                        return;
                    }

                    break;

                case DEFAULT_CONNECTION_PARAMETER:

                    if (localName.equals("param")) {
                        state = State.AUTH_INFO;
                        return;
                    }

                    break;

            }

            throw new SAXException("Tag not yet complete: " + localName);

        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

            switch (state)  {

                // Document must be <user-mapping>
                case ROOT:

                    if (localName.equals("user-mapping")) {
                        state = State.USER_MAPPING;
                        return;
                    }

                    break;

                case USER_MAPPING:

                    if (localName.equals("authorize")) {

                        AuthInfo.Encoding encoding;
                        String encodingString = attributes.getValue("encoding");
                        if (encodingString == null)
                            encoding = AuthInfo.Encoding.PLAIN_TEXT;
                        else if (encodingString.equals("plain"))
                            encoding = AuthInfo.Encoding.PLAIN_TEXT;
                        else if (encodingString.equals("md5"))
                            encoding = AuthInfo.Encoding.MD5;
                        else
                            throw new SAXException("Invalid encoding type");


                        current = new AuthInfo(
                            attributes.getValue("username"),
                            attributes.getValue("password"),
                            encoding
                        );

                        // Next state
                        state = State.AUTH_INFO;
                        return;
                    }

                    break;

                case AUTH_INFO:

                    if (localName.equals("connection")) {

                        currentConnection = attributes.getValue("name");
                        if (currentConnection == null)
                            throw new SAXException("Attribute \"name\" required for connection tag.");

                        // Next state
                        state = State.CONNECTION;
                        return;
                    }

                    if (localName.equals("protocol")) {

                        // Associate protocol with default connection
                        currentConnection = "DEFAULT";

                        // Next state
                        state = State.DEFAULT_CONNECTION_PROTOCOL;
                        return;
                    }

                    if (localName.equals("param")) {

                        // Associate parameter with default connection
                        currentConnection = "DEFAULT";

                        currentParameter = attributes.getValue("name");
                        if (currentParameter == null)
                            throw new SAXException("Attribute \"name\" required for param tag.");

                        // Next state
                        state = State.DEFAULT_CONNECTION_PARAMETER;
                        return;
                    }

                    break;

                case CONNECTION:

                    if (localName.equals("protocol")) {
                        // Next state
                        state = State.PROTOCOL;
                        return;
                    }

                    if (localName.equals("param")) {

                        currentParameter = attributes.getValue("name");
                        if (currentParameter == null)
                            throw new SAXException("Attribute \"name\" required for param tag.");

                        // Next state
                        state = State.PARAMETER;
                        return;
                    }

                    break;

            }

            throw new SAXException("Unexpected tag: " + localName);

        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {

            String str = new String(ch, start, length);

            switch (state) {

                case PROTOCOL:
                case DEFAULT_CONNECTION_PROTOCOL:

                    current.getConfiguration(currentConnection)
                        .setProtocol(str);
                    return;

                case PARAMETER:
                case DEFAULT_CONNECTION_PARAMETER:

                    current.getConfiguration(currentConnection)
                            .setParameter(currentParameter, str);
                    return;

            }

            if (str.trim().length() != 0)
                throw new SAXException("Unexpected character data.");

        }


    }


}
