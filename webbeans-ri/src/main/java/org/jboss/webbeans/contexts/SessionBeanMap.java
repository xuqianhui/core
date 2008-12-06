/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.webbeans.contexts;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpSession;
import javax.webbeans.manager.Bean;
import javax.webbeans.manager.Contextual;

import org.jboss.webbeans.CurrentManager;
import org.jboss.webbeans.log.LogProvider;
import org.jboss.webbeans.log.Logging;

/**
 * A BeanMap that uses a HTTP session as backing map
 * 
 * @author Nicklas Karlsson
 * 
 * @see org.jboss.webbeans.contexts.SessionContext
 */
public class SessionBeanMap implements BeanMap
{
   private static LogProvider log = Logging.getLogProvider(SessionBeanMap.class);

   // The HTTP session to use as backing map
   private HttpSession session;
   // The storage prefix to put before names
   private String keyPrefix;

   /**
    * Constructor
    * 
    * @param keyPrefix The storage names prefix
    */
   public SessionBeanMap(String keyPrefix)
   {
      super();
      this.keyPrefix = keyPrefix;
      log.trace("SessionBeanMap created with prefix " + keyPrefix);
   }

   /**
    * The SessionBeanMap requires a HTTP session to work. It is created without
    * one so this method must be called before it can be operated upon
    * 
    * @param session The session to use as a backing map
    */
   public void setSession(HttpSession session)
   {
      this.session = session;
      log.trace("Session context associated with session id " + session.getId());
   }

   /**
    * Used to check if the session has been set and throws an exception if it's
    * null.
    */
   private void checkSession()
   {
      if (session == null)
      {
         throw new IllegalArgumentException("Session has not been initialized in SessionBeanMap");
      }
   }

   /**
    * Returns a map key to a bean. Uses a known prefix and appends the index of
    * the Bean in the Manager bean list.
    * 
    * @param bean The bean to generate a key for.
    * @return A unique key;
    */
   private String getBeanKey(Contextual<?> bean)
   {
      return keyPrefix + CurrentManager.rootManager().getBeans().indexOf(bean);
   }

   /**
    * Gets a bean from the session
    * 
    * First, checks that the session is present. It determines an ID for the
    * bean which and looks for it in the session. The bean instance is returned
    * (null if not found in the session).
    * 
    * @param bean The bean to get from the session
    * @return An instance of the bean
    * 
    * @see org.jboss.webbeans.contexts.BeanMap#get(Bean)
    */
   @SuppressWarnings("unchecked")
   public <T> T get(Contextual<? extends T> bean)
   {
      checkSession();
      String key = getBeanKey(bean);
      T instance = (T) session.getAttribute(key);
      log.trace("Searched session for key " + key + " and got " + instance);
      return instance;
   }

   /**
    * Removes a bean instance from the session
    * 
    * First, checks that the session is present. It determines an ID for the
    * bean and that key is then removed from the session, whether it was present
    * in the first place or not.
    * 
    * @param bean The bean whose instance to remove.
    * @return The instance removed
    * 
    * @see org.jboss.webbeans.contexts.BeanMap#remove(Bean)
    */
   public <T> T remove(Contextual<? extends T> bean)
   {
      checkSession();
      T instance = get(bean);
      String key = getBeanKey(bean);
      session.removeAttribute(key);
      log.trace("Removed bean " + bean + " with key " + key + " from session");
      return instance;
   }

   /**
    * Clears the session of any beans.
    * 
    * First, checks that the session is present. Then, iterates over the
    * attribute names in the session and removes them if they start with the
    * know prefix.
    * 
    * @see org.jboss.webbeans.contexts.BeanMap#clear()
    */
   @SuppressWarnings("unchecked")
   public void clear()
   {
      checkSession();
      Enumeration names = session.getAttributeNames();
      while (names.hasMoreElements())
      {
         String name = (String) names.nextElement();
         session.removeAttribute(name);
      }
      log.trace("Session cleared");
   }

   /**
    * Gets an iterable over the beans present in the storage.
    * 
    * Iterates over the names in the session. If a name starts with the known
    * prefix, strips it out to get the index to the bean in the manager bean
    * list. Retrieves the bean from that list and puts it in the result-list.
    * Finally, returns the list.
    * 
    * @return An Iterable to the beans in the storage
    * 
    * @see org.jboss.webbeans.contexts.BeanMap#keySet()
    */
   @SuppressWarnings("unchecked")
   public Iterable<Contextual<? extends Object>> keySet()
   {
      checkSession();

      List<Contextual<?>> beans = new ArrayList<Contextual<?>>();

      Enumeration names = session.getAttributeNames();
      while (names.hasMoreElements())
      {
         String name = (String) names.nextElement();
         if (name.startsWith(keyPrefix))
         {
            String id = name.substring(keyPrefix.length());
            Contextual<?> bean = CurrentManager.rootManager().getBeans().get(Integer.parseInt(id));
            beans.add(bean);
         }
      }

      return beans;
   }

   /**
    * Puts a bean instance in the session
    * 
    * First, checks that the session is present. Generates a bean map key, puts
    * the instance in the session under that key.
    * 
    * @param bean The bean to use as key
    * @param instance The bean instance to add
    * 
    * @see org.jboss.webbeans.contexts.BeanMap#put(Bean, Object)
    */
   public <T> void put(Contextual<? extends T> bean, T instance)
   {
      checkSession();
      String key = getBeanKey(bean);
      session.setAttribute(key, instance);
      log.trace("Stored instance " + instance + " for bean " + bean + " under key " + key + " in session");
   }

   @SuppressWarnings("unchecked")
   @Override
   public String toString()
   {
      StringBuilder buffer = new StringBuilder();
      List<Contextual<?>> beans = (List) keySet();
      buffer.append("Bean -> bean instance mappings in HTTP session: " + beans.size() + "\n");
      int i = 0;
      for (Contextual<?> bean : beans)
      {
         Object instance = get(bean);
         buffer.append(++i + " - " + getBeanKey(bean) + ": " + instance + "\n");
      }
      return buffer.toString();
   }

}
