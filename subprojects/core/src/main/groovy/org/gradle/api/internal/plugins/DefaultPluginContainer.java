/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.util.SingleMessageLogger;

public class DefaultPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {

    private final PluginRegistry pluginRegistry;
    private final Instantiator instantiator;
    private final PluginApplicator applicator;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, Instantiator instantiator, PluginApplicator applicator) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.instantiator = instantiator;
        this.applicator = applicator;
    }

    public Plugin apply(String id) {
        SingleMessageLogger.nagUserOfReplacedMethod("PluginContainer.apply(String)", "PluginAware.apply(Map) or PluginAware.apply(Closure)");
        PotentialPluginWithId potentialPlugin = pluginRegistry.lookup(id);
        if (potentialPlugin == null) {
            throw new UnknownPluginException("Plugin with id '" + id + "' not found.");
        }

        Class<? extends Plugin<?>> pluginClass = potentialPlugin.asImperativeClass();

        if (pluginClass == null) {
            throw new IllegalArgumentException("Plugin implementation '" + potentialPlugin.asClass().getName() + "' does not implement the Plugin interface. This plugin cannot be applied directly via the PluginContainer.");
        } else {
            return addPluginInternal(potentialPlugin.getPluginId().toString(), pluginClass);
        }
    }

    public <P extends Plugin> P apply(Class<P> type) {
        SingleMessageLogger.nagUserOfReplacedMethod("PluginContainer.apply(Class)", "PluginAware.apply(Map) or PluginAware.apply(Closure)");
        return addPluginInternal(null, type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    public Plugin findPlugin(String id) {
        PotentialPluginWithId potentialPlugin = pluginRegistry.lookup(id);
        if (potentialPlugin == null) {
            return null;
        }

        // TODO - doesn't handle case where plugin is applied by class and it's not from the registry's inherent scope
        // TODO - also doesn't handle case where there's a different instance of the same plugin class

        Class<? extends Plugin<?>> pluginClass = potentialPlugin.asImperativeClass();
        if (pluginClass == null) {
            throw new IllegalArgumentException("Plugin implementation '" + potentialPlugin.asClass().getName() + "' does not implement the Plugin interface. This plugin cannot be applied directly via the PluginContainer.");
        } else {
            return findPlugin(potentialPlugin.asImperativeClass());
        }
    }

    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    private <P extends Plugin<?>> P addPluginInternal(@Nullable String pluginId, Class<P> type) {
        P existing = findPlugin(type);
        if (existing == null) {
            P plugin = providePlugin(type);
            return addPluginInternal(pluginId, plugin);
        } else {
            return existing;
        }
    }

    private <P extends Plugin<?>> P addPluginInternal(String pluginId, P plugin) {
        PotentialPlugin potentialPlugin = pluginRegistry.inspect(plugin.getClass());
        if (potentialPlugin.hasRules()) {
            applicator.applyImperativeRulesHybrid(pluginId, plugin);
        } else {
            applicator.applyImperative(pluginId, plugin);
        }

        add(plugin);
        return plugin;
    }

    public Plugin getPlugin(String id) {
        Plugin plugin = findPlugin(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    public Plugin getAt(String id) throws UnknownPluginException {
        return getPlugin(id);
    }

    public <P extends Plugin> P getAt(Class<P> type) throws UnknownPluginException {
        return getPlugin(type);
    }

    public <P extends Plugin> P getPlugin(Class<P> type) throws UnknownPluginException {
        P plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    public void withId(final String pluginId, Action<? super Plugin> action) {
        PotentialPluginWithId potentialPlugin = pluginRegistry.lookup(pluginId);
        if (potentialPlugin != null) {
            Class<? extends Plugin<?>> pluginClass = potentialPlugin.asImperativeClass();
            if (pluginClass == null) {
                String message = String.format("The type for id '%s' (class: '%s') is not a plugin implementing the Plugin interface. Please use PluginAware.withPlugin() instead to detect it.", pluginId, potentialPlugin.asClass().getName());
                throw new IllegalArgumentException(message);
            }
        }
        matching(new Spec<Plugin>() {
            public boolean isSatisfiedBy(Plugin element) {
                PotentialPluginWithId lookup = pluginRegistry.lookup(pluginId);
                if (lookup == null || !lookup.asClass().equals(element.getClass())) {
                    lookup = pluginRegistry.lookup(pluginId, element.getClass().getClassLoader());
                }
                return lookup != null && lookup.asClass().equals(element.getClass());
            }
        }).all(action);
    }

    private <T extends Plugin<?>> T providePlugin(Class<T> type) {
        try {
            return instantiator.newInstance(type);
        } catch (ObjectInstantiationException e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.", type.getSimpleName()), e.getCause());
        }
    }

    @Override
    public <S extends Plugin> PluginCollection<S> withType(Class<S> type) {
        // runtime check because method is used from Groovy where type bounds are not respected
        if (!Plugin.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(String.format("'%s' does not implement the Plugin interface.", type.getName()));
        }

        return super.withType(type);
    }
}
