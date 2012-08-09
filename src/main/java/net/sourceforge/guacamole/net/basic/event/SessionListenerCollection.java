package net.sourceforge.guacamole.net.basic.event;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import javax.servlet.http.HttpSession;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.basic.properties.BasicGuacamoleProperties;
import net.sourceforge.guacamole.properties.GuacamoleProperties;

/**
 * A collection which iterates over instances of all listeners defined in
 * guacamole.properties. For each listener defined in guacamole.properties, a
 * new instance is created and stored in this collection. The contents of this
 * collection is stored within the HttpSession, and will be reused if available.
 * Each listener is instantiated once per session. Listeners are singleton
 * classes within the session, but not globally.
 *
 * @author Michael Jumper
 */
public class SessionListenerCollection extends AbstractCollection {

    /**
     * The name of the session attribute which will contain the listener
     * collection.
     */
    private static final String SESSION_ATTRIBUTE = "GUAC_LISTENERS";

    /**
     * The wrapped collection of listeners, possibly retrieved from the
     * session.
     */
    private Collection listeners;

    /**
     * Creates a new SessionListenerCollection which stores all listeners
     * defined in guacamole.properties in the provided session. If listeners
     * are already stored in the provided session, those listeners are used
     * instead.
     *
     * @param session The HttpSession to store listeners within.
     * @throws GuacamoleException If an error occurs while instantiating new
     *                            listeners.
     */
    public SessionListenerCollection(HttpSession session) throws GuacamoleException {

        // Pull cached listeners from session
        listeners = (Collection) session.getAttribute(SESSION_ATTRIBUTE);

        // If no listeners stored, listeners must be loaded first
        if (listeners == null) {

            // Load listeners from guacamole.properties
            listeners = new ArrayList();
            try {

                // Get all listener classes from properties
                Collection<Class> listenerClasses =
                        GuacamoleProperties.getProperty(BasicGuacamoleProperties.EVENT_LISTENERS);

                // Add an instance of each class to the list
                if (listenerClasses != null) {
                    for (Class listenerClass : listenerClasses) {

                        // Instantiate listener
                        Object listener = listenerClass.getConstructor().newInstance();

                        // Add listener to collection of listeners
                        listeners.add(listener);

                    }
                }

            }
            catch (InstantiationException e) {
                throw new GuacamoleException("Listener class is abstract.", e);
            }
            catch (IllegalAccessException e) {
                throw new GuacamoleException("No access to listener constructor.", e);
            }
            catch (IllegalArgumentException e) {
                // This should not happen, given there ARE no arguments
                throw new GuacamoleException("Illegal arguments to listener constructor.", e);
            }
            catch (InvocationTargetException e) {
                throw new GuacamoleException("Error while instantiating listener.", e);
            }
            catch (NoSuchMethodException e) {
                throw new GuacamoleException("Listener has no default constructor.", e);
            }
            catch (SecurityException e) {
                throw new GuacamoleException("Security restrictions prevent instantiation of listener.", e);
            }

            // Store listeners for next time
            session.setAttribute(SESSION_ATTRIBUTE, listeners);

        }

    }

    @Override
    public Iterator iterator() {
        return listeners.iterator();
    }

    @Override
    public int size() {
        return listeners.size();
    }

}
