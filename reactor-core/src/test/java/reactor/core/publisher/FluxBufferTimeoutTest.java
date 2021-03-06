/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.core.publisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxBufferTimeoutTest {

	Flux<List<Integer>> scenario_bufferWithTimeoutAccumulateOnTimeOrSize() {
		return Flux.range(1, 6)
		           .delayElements(Duration.ofMillis(300))
		           .bufferTimeout(5, Duration.ofMillis(2000));
	}

	@Test
	public void bufferWithTimeoutAccumulateOnTimeOrSize() {
		StepVerifier.withVirtualTime(this::scenario_bufferWithTimeoutAccumulateOnTimeOrSize)
		            .thenAwait(Duration.ofMillis(1500))
		            .assertNext(s -> assertThat(s).containsExactly(1, 2, 3, 4, 5))
		            .thenAwait(Duration.ofMillis(2000))
		            .assertNext(s -> assertThat(s).containsExactly(6))
		            .verifyComplete();
	}

	Flux<List<Integer>> scenario_bufferWithTimeoutAccumulateOnTimeOrSize2() {
		return Flux.range(1, 6)
		           .delayElements(Duration.ofMillis(300))
		           .bufferTimeout(5, Duration.ofMillis(2000));
	}

	@Test
	public void bufferWithTimeoutAccumulateOnTimeOrSize2() {
		StepVerifier.withVirtualTime(this::scenario_bufferWithTimeoutAccumulateOnTimeOrSize2)
		            .thenAwait(Duration.ofMillis(1500))
		            .assertNext(s -> assertThat(s).containsExactly(1, 2, 3, 4, 5))
		            .thenAwait(Duration.ofMillis(2000))
		            .assertNext(s -> assertThat(s).containsExactly(6))
		            .verifyComplete();
	}

	Flux<List<Integer>> scenario_bufferWithTimeoutThrowingExceptionOnTimeOrSizeIfDownstreamDemandIsLow() {
		return Flux.range(1, 6)
		           .delayElements(Duration.ofMillis(300))
		           .bufferTimeout(5, Duration.ofMillis(100));
	}

	@Test
	public void bufferWithTimeoutThrowingExceptionOnTimeOrSizeIfDownstreamDemandIsLow() {
		StepVerifier.withVirtualTime(this::scenario_bufferWithTimeoutThrowingExceptionOnTimeOrSizeIfDownstreamDemandIsLow, 0)
		            .expectSubscription()
		            .expectNoEvent(Duration.ofMillis(300))
		            .thenRequest(1)
		            .expectNoEvent(Duration.ofMillis(100))
		            .assertNext(s -> assertThat(s).containsExactly(1))
		            .expectNoEvent(Duration.ofMillis(300))
		            .verifyErrorSatisfies(e ->
				            assertThat(e)
						            .hasMessage("Could not emit buffer due to lack of requests")
						            .isInstanceOf(IllegalStateException.class)
		            );
	}

	@Test
	public void scanSubscriber() {
		CoreSubscriber<List<String>> actual = new LambdaSubscriber<>(null, e -> {}, null, null);

		FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>> test = new FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>>(
						actual, 123, 1000, Schedulers.elastic().createWorker(), ArrayList::new);

		Subscription subscription = Operators.emptySubscription();
		test.onSubscribe(subscription);

		test.requested = 3L;
		test.index = 100;

		assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(subscription);
		assertThat(test.scan(Scannable.Attr.ACTUAL)).isSameAs(actual);

		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(3L);
		assertThat(test.scan(Scannable.Attr.CAPACITY)).isEqualTo(123);
		assertThat(test.scan(Scannable.Attr.BUFFERED)).isEqualTo(23);

		assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();

		test.onError(new IllegalStateException("boom"));
		assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		assertThat(test.scan(Scannable.Attr.TERMINATED)).isTrue();
	}

	@Test
	public void shouldShowActualSubscriberDemand() {
		Subscription[] subscriptionsHolder = new Subscription[1];
		CoreSubscriber<List<String>> actual = new LambdaSubscriber<>(null, e -> {}, null, s -> subscriptionsHolder[0] = s);

		FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>> test = new FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>>(
				actual, 123, 1000, Schedulers.elastic().createWorker(), ArrayList::new);

		Subscription subscription = Operators.emptySubscription();
		test.onSubscribe(subscription);
		subscriptionsHolder[0].request(10);
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(10L);
		subscriptionsHolder[0].request(5);
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(15L);
	}

	@Test
	public void downstreamDemandShouldBeAbleToDecreaseOnFullBuffer() {
		Subscription[] subscriptionsHolder = new Subscription[1];
		CoreSubscriber<List<String>> actual = new LambdaSubscriber<>(null, e -> {}, null, s -> subscriptionsHolder[0] = s);

		FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>> test = new FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>>(
				actual, 5, 1000, Schedulers.elastic().createWorker(), ArrayList::new);

		Subscription subscription = Operators.emptySubscription();
		test.onSubscribe(subscription);
		subscriptionsHolder[0].request(1);
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(1L);

		for (int i = 0; i < 5; i++) {
			test.onNext(String.valueOf(i));
		}

		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(0L);
	}

	@Test
	public void downstreamDemandShouldBeAbleToDecreaseOnTimeSpan() {
		Subscription[] subscriptionsHolder = new Subscription[1];
		CoreSubscriber<List<String>> actual = new LambdaSubscriber<>(null, e -> {}, null, s -> subscriptionsHolder[0] = s);

		VirtualTimeScheduler timeScheduler = VirtualTimeScheduler.getOrSet();
		FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>> test = new FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>>(
				actual, 5, 100, timeScheduler.createWorker(), ArrayList::new);

		Subscription subscription = Operators.emptySubscription();
		test.onSubscribe(subscription);
		subscriptionsHolder[0].request(1);
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(1L);
		timeScheduler.advanceTimeBy(Duration.ofMillis(100));
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(1L);
		test.onNext(String.valueOf("0"));
		timeScheduler.advanceTimeBy(Duration.ofMillis(100));
		assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(0L);
	}

	@Test
	public void scanSubscriberCancelled() {
		CoreSubscriber<List<String>>
				actual = new LambdaSubscriber<>(null, e -> {}, null, null);

		FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>> test = new FluxBufferTimeout.BufferTimeoutSubscriber<String, List<String>>(
						actual, 123, 1000, Schedulers.elastic().createWorker(), ArrayList::new);

		assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();

		test.cancel();
		assertThat(test.scan(Scannable.Attr.CANCELLED)).isTrue();
		assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();
	}
}