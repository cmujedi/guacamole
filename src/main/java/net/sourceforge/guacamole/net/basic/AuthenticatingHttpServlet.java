
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
import java.util.Collection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.auth.AuthenticationProvider;
import net.sourceforge.guacamole.net.auth.Credentials;
import net.sourceforge.guacamole.net.auth.UserContext;
import net.sourceforge.guacamole.net.basic.event.SessionListenerCollection;
import net.sourceforge.guacamole.net.basic.properties.BasicGuacamoleProperties;
import net.sourceforge.guacamole.net.event.AuthenticationFailureEvent;
import net.sourceforge.guacamole.net.event.AuthenticationSuccessEvent;
import net.sourceforge.guacamole.net.event.listener.AuthenticationFailureListener;
import net.sourceforge.guacamole.net.event.listener.AuthenticationSuccessListener;
import net.sourceforge.guacamole.properties.GuacamoleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract servlet which provides an authenticatedService() function that
 * is only called if the HTTP request is authenticated, or the current
 * HTTP session has already been authenticated.
 *
 * The user context is retrieved using the authentication provider defined in
 * guacamole.properties. The authentication provider has access to the request
 * and session, in addition to any submitted username and password, in order
 * to authenticate the user.
 *
 * The user context will be stored in the current HttpSession.
 *
 * Success and failure are logged.
 *
 * @author Michael Jumper
 */
public abstract class AuthenticatingHttpServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(AuthenticatingHttpServlet.class);

    /**
     * The session attribute holding the current UserContext.
     */
    private static final String CONTEXT_ATTRIBUTE = "GUAC_CONTEXT";

    /**
     * The session attribute holding the credentials authorizing this session.
     */
    private static final String CREDENTIALS_ATTRIBUTE = "GUAC_CREDS";

    /**
     * The AuthenticationProvider to use to authenticate all requests.
     */
    private AuthenticationProvider authProvider;

    @Override
    public void init() throws ServletException {

        // Get auth provider instance
        try {
            authProvider = GuacamoleProperties.getRequiredProperty(BasicGuacamoleProperties.AUTH_PROVIDER);
        }
        catch (GuacamoleException e) {
            logger.error("Error getting authentication provider from properties.", e);
            throw new ServletException(e);
        }

    }

    /**
     * Notifies all listeners in the given collection that authentication has
     * failed.
     *
     * @param listeners A collection of all listeners that should be notified.
     * @param credentials The credentials associated with the authentication
     *                    request that failed.
     */
    private void notifyFailed(Collection listeners, Credentials credentials) {

        // Build event for auth failure
        AuthenticationFailureEvent event = new AuthenticationFailureEvent(credentials);

        // Notify all listeners
        for (Object listener : listeners) {
            try {
                if (listener instanceof AuthenticationFailureListener)
                    ((AuthenticationFailureListener) listener).authenticationFailed(event);
            }
            catch (GuacamoleException e) {
                logger.error("Error notifying AuthenticationFailureListener.", e);
            }
        }

    }

    /**
     * Notifies all listeners in the given collection that authentication was
     * successful.
     *
     * @param listeners A collection of all listeners that should be notified.
     * @param credentials The credentials associated with the authentication
     *                    request that succeeded.
     * @return true if all listeners are allowing the authentication success,
     *         or if there are no listeners, and false if any listener is
     *         canceling the authentication success. Note that once one
     *         listener cancels, no other listeners will run.
     * @throws GuacamoleException If any listener throws an error while being
     *                            notified. Note that if any listener throws an
     *                            error, the success is canceled, and no other
     *                            listeners will run.
     */
    private boolean notifySuccess(Collection listeners, Credentials credentials)
            throws GuacamoleException {

        // Build event for auth success
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(credentials);

        // Notify all listeners
        for (Object listener : listeners) {
            if (listener instanceof AuthenticationSuccessListener) {

                // Cancel immediately if hook returns false
                if (!((AuthenticationSuccessListener) listener).authenticationSucceeded(event))
                    return false;

            }
        }

        return true;

    }

    /**
     * Sends a predefined, generic error message to the user, along with a
     * "403 - Forbidden" HTTP status code in the response.
     *
     * @param response The response to send the error within.
     * @throws IOException If an error occurs while sending the error.
     */
    private void failAuthentication(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /**
     * Returns the credentials associated with the given session.
     *
     * @param session The session to retrieve credentials from.
     * @return The credentials associated with the given session.
     */
    protected Credentials getCredentials(HttpSession session) {
        return (Credentials) session.getAttribute(CREDENTIALS_ATTRIBUTE);
    }

    /**
     * Returns the UserContext associated with the given session.
     *
     * @param session The session to retrieve UserContext from.
     * @return The UserContext associated with the given session.
     */
    protected UserContext getUserContext(HttpSession session) {
        return (UserContext) session.getAttribute(CONTEXT_ATTRIBUTE);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {

        HttpSession httpSession = request.getSession(true);

        // Try to get user context from session
        UserContext context = getUserContext(httpSession);

        // If no context, try to authenticate the user to get the context using
        // this request.
        if (context == null) {

            SessionListenerCollection listeners;
            try {
                listeners = new SessionListenerCollection(httpSession);
            }
            catch (GuacamoleException e) {
                logger.error("Failed to retrieve listeners. Authentication canceled.", e);
                failAuthentication(response);
                return;
            }

            // Retrieve username and password from parms
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            // Build credentials object
            Credentials credentials = new Credentials();
            credentials.setSession(httpSession);
            credentials.setRequest(request);
            credentials.setUsername(username);
            credentials.setPassword(password);

            // Get authorized context
            try {
                context = authProvider.getUserContext(credentials);
            }


            /******** HANDLE FAILED AUTHENTICATION ********/

            // If error retrieving context, fail authentication, notify listeners
            catch (GuacamoleException e) {
                logger.error("Error retrieving context for user \"{}\".",
                        credentials.getUsername(), e);

                notifyFailed(listeners, credentials);
                failAuthentication(response);
                return;
            }

            // If no context, fail authentication, notify listeners
            if (context == null) {
                logger.warn("Authentication attempt from {} for user \"{}\" failed.",
                        request.getRemoteAddr(), credentials.getUsername());

                notifyFailed(listeners, credentials);
                failAuthentication(response);
                return;
            }


            /******** HANDLE SUCCESSFUL AUTHENTICATION ********/

            try {

                // Otherwise, authentication has been succesful
                logger.info("User \"{}\" successfully authenticated from {}.",
                        credentials.getUsername(), request.getRemoteAddr());

                // Notify of success, cancel if requested
                if (!notifySuccess(listeners, credentials)) {
                    logger.info("Successful authentication canceled by hook.");
                    failAuthentication(response);
                    return;
                }

            }
            catch (GuacamoleException e) {

                // Cancel authentication success if hook throws exception
                logger.error("Successful authentication canceled by error in hook.", e);
                failAuthentication(response);
                return;

            }

            // Associate context and credentials with session
            httpSession.setAttribute(CONTEXT_ATTRIBUTE,     context);
            httpSession.setAttribute(CREDENTIALS_ATTRIBUTE, credentials);


        }

        // Allow servlet to run now that authentication has been validated
        authenticatedService(context, request, response);

    }

    /**
     * Function called after the credentials given in the request (if any)
     * are authenticated. If the current session is not associated with
     * valid credentials, this function will not be called.
     * 
     * @param context The current UserContext.
     * @param request The HttpServletRequest being serviced.
     * @param response An HttpServletResponse which controls the HTTP response
     *                 of this servlet.
     * 
     * @throws ServletException If an error occurs that interferes with the
     *                          normal operation of this servlet.
     * @throws IOException If an error occurs that prevents this servlet from
     *                     communicating.
     */
    protected abstract void authenticatedService(
            UserContext context,
            HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException;

}
