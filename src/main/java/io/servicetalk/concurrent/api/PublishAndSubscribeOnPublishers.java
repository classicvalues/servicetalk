/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import org.reactivestreams.Subscriber;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Publisher}.
 */
final class PublishAndSubscribeOnPublishers {

    private PublishAndSubscribeOnPublishers() {
        // No instance.
    }

    static <T> Publisher<T> publishAndSubscribeOn(Publisher<T> original, Executor executor) {
        if (original.getExecutor() == executor) {
            return original;
        }
        return new PublishAndSubscribeOn<>(executor, original);
    }

    static <T> Publisher<T> publishAndSubscribeOnOverride(Publisher<T> original, Executor executor) {
        if (original.getExecutor() == executor) {
            return original;
        }
        // This operator is to make sure that we override the executor for the entire execution chain. This is the
        // normal mode of operation if we create a Publisher with an executor, i.e. all operators behave the same way.
        // Hence, we simply use AbstractSynchronousPublisherOperator which does not do any extra offloading, it just
        //         // overrides the Executor that will be used to do the offloading.
        return new PublishAndSubscribeOnOverride<>(original, executor);
    }

    private static final class PublishAndSubscribeOn<T> extends AbstractNoHandleSubscribePublisher<T> {
        private final Publisher<T> original;

        PublishAndSubscribeOn(final Executor executor, final Publisher<T> original) {
            super(executor);
            this.original = original;
        }

        @Override
        void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader) {
            // This operator is to make sure that we use the executor to subscribe to the Publisher that is returned
            // by this operator.
            //
            // Here we offload signals from original to subscriber using signalOffloader.
            // We use executor to create the returned Publisher which means executor will be used
            // to offload handleSubscribe as well as the Subscription that is sent to the subscriber here.
            //
            // This operator acts as a boundary that changes the Executor from original to the rest of the execution
            // chain. If there is already an Executor defined for original, it will be used to offload signals until
            // they hit this operator.
            original.subscribe(signalOffloader.offloadSubscriber(subscriber));
        }
    }

    private static final class PublishAndSubscribeOnOverride<T> extends AbstractSynchronousPublisherOperator<T, T> {
        PublishAndSubscribeOnOverride(final Publisher<T> original, final Executor executor) {
            super(original, executor);
        }

        @Override
        public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
            // We are using AbstractSynchronousPublisherOperator just to override the Executor. We do not intend to
            // do any extra offloading that is done by a regular Publisher created with an Executor.
            return subscriber;
        }
    }
}
