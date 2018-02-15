/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.discovery;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.particleframework.cache.CacheConfiguration;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.util.ArrayUtils;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.particleframework.discovery.CompositeDiscoveryClient.SETTING_ENABLED;

/**
 * A composite implementation combining all registered {@link DiscoveryClient} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class CompositeDiscoveryClient implements DiscoveryClient {

    static final String SETTING_ENABLED = CacheConfiguration.PREFIX + ".discoveryClient.enabled";

    private final DiscoveryClient[] discoveryClients;

    protected CompositeDiscoveryClient(DiscoveryClient[] discoveryClients) {
        this.discoveryClients = discoveryClients;
    }

    @Override
    public String getDescription() {
        return "compositeDiscoveryClient";
    }

    @Override
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        if(ArrayUtils.isEmpty(discoveryClients)) {
            return Flowable.just(Collections.emptyList());
        }
        Stream<Flowable<List<ServiceInstance>>> flowableStream = Arrays.stream(discoveryClients).map(client -> Flowable.fromPublisher(client.getInstances(serviceId)));
        Maybe<List<ServiceInstance>> reduced = Flowable.merge(flowableStream.collect(Collectors.toList())).reduce((instances, otherInstances) -> {
            instances.addAll(otherInstances);
            return instances;
        });
        return reduced.toFlowable();
    }

    @Override
    public Flowable<List<String>> getServiceIds() {
        if(ArrayUtils.isEmpty(discoveryClients)) {
            return Flowable.just(Collections.emptyList());
        }
        Stream<Flowable<List<String>>> flowableStream = Arrays.stream(discoveryClients).map(client -> Flowable.fromPublisher(client.getServiceIds()));
        Maybe<List<String>> reduced = Flowable.merge(flowableStream.collect(Collectors.toList())).reduce((strings, strings2) -> {
            strings.addAll(strings2);
            return strings;
        });
        return reduced.toFlowable();
    }

    @Override
    public void close() throws IOException {
        for (DiscoveryClient discoveryClient : discoveryClients) {
            discoveryClient.close();
        }
    }
}
