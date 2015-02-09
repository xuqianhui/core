/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.weld.bean.proxy;

import java.lang.reflect.Constructor;
import java.util.List;

import javax.enterprise.inject.spi.Bean;

import org.jboss.weld.bootstrap.api.Service;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.config.WeldConfiguration;
import org.jboss.weld.exceptions.UnproxyableResolutionException;
import org.jboss.weld.logging.BootstrapLogger;
import org.jboss.weld.resources.ClassLoaderResourceLoader;
import org.jboss.weld.util.reflection.Reflections;

import com.google.common.collect.ImmutableList;

/**
 * Implementations of this interface are capable of creating instances of a given proxy class. This can either be done simply by calling
 * {@link Class#newInstance()} or using more advanced mechanism (e.g. sun.misc.Unsafe)
 *
 * @author Jozef Hartinger
 *
 * @see DefaultProxyInstantiator
 * @see UnsafeProxyInstantiator
 * @see ReflectionFactoryProxyInstantiator
 *
 */
public interface ProxyInstantiator extends Service {

    /**
     * Create a new instance of a proxy class. This method needs to be run from a privileged context.
     *
     * @param <T> the proxy class
     * @param clazz the class
     * @return an instance of a proxy class
     */
    <T> T newInstance(Class<T> clazz) throws InstantiationException, IllegalAccessException;

    /**
     * Validate, whether the given constructor is sufficient for a class to be proxyable.
     *
     * @param constructor the given constructor
     * @param clazz the given class
     * @param declaringBean the declaring bean
     * @return an {@link UnproxyableResolutionException} if the given class is not proxyable due to the given constructor, null otherwise
     * @throws UnproxyableResolutionException
     */
    <T> UnproxyableResolutionException validateNoargConstructor(Constructor<T> constructor, Class<?> clazz, Bean<?> declaringBean)
            throws UnproxyableResolutionException;

    /**
     * Indicates whether this instantiator calls proxy class' no-arg constructor or whether it uses an alternative mechanism to obtain a proxy class instance.
     *
     * @return true if this implementation uses proxy class' no-arg constructor for creating new instances, false otherwise
     */
    boolean isUsingConstructor();

    class Factory {

        private static final List<String> IMPLEMENTATIONS = ImmutableList.of(UnsafeProxyInstantiator.class.getName(),
                ReflectionFactoryProxyInstantiator.class.getName());

        private Factory() {
        }

        /**
         * Obtains new ProxyInstantiator based on given {@link WeldConfiguration}.
         *
         * @param configuration
         * @return
         */
        public static ProxyInstantiator create(WeldConfiguration configuration) {
            ProxyInstantiator result = DefaultProxyInstantiator.INSTANCE;
            if (configuration.getBooleanProperty(ConfigurationKey.PROXY_UNSAFE)) {
                for (String implementation : IMPLEMENTATIONS) {
                    // use the first suitable implementation
                    try {
                        result = (ProxyInstantiator) Reflections.loadClass(implementation, new ClassLoaderResourceLoader(ProxyInstantiator.class.getClassLoader())).newInstance();
                        break;
                    } catch (Exception e) {
                        BootstrapLogger.LOG.catchingDebug(e);
                    } catch (LinkageError e) {
                        BootstrapLogger.LOG.catchingDebug(e);
                    }
                }
            }
            BootstrapLogger.LOG.debugv("Using instantiator: {0}", result.getClass().getName());
            return result;
        }
    }
}
