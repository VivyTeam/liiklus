package com.github.bsideup.liiklus;

import com.github.bsideup.liiklus.protocol.PublishRequest;
import com.github.bsideup.liiklus.protocol.ReceiveReply;
import com.github.bsideup.liiklus.protocol.ReceiveRequest;
import com.github.bsideup.liiklus.protocol.SubscribeRequest;
import com.github.bsideup.liiklus.test.AbstractIntegrationTest;
import com.google.protobuf.ByteString;
import org.assertj.core.api.Condition;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class SmokeTest extends AbstractIntegrationTest {

    @Test
    public void testPublishSubscribe() throws Exception {
        SubscribeRequest subscribeAction = SubscribeRequest.newBuilder()
                .setTopic("test-" + UUID.randomUUID())
                .setGroup(testName.getMethodName())
                .setAutoOffsetReset(SubscribeRequest.AutoOffsetReset.EARLIEST)
                .build();

        String key = "foo";
        List<String> values = IntStream.range(0, 10).mapToObj(i -> "bar-" + i).collect(Collectors.toList());
        List<ReceiveReply> records = Flux.fromIterable(values)
                .flatMapSequential(it -> stub.publish(Mono.just(PublishRequest.newBuilder()
                        .setTopic(subscribeAction.getTopic())
                        .setKey(ByteString.copyFromUtf8(key))
                        .setValue(ByteString.copyFromUtf8(it))
                        .build()
                )))
                .thenMany(
                        stub.subscribe(Mono.just(subscribeAction))
                                .flatMap(it -> stub.receive(Mono.just(
                                        ReceiveRequest.newBuilder()
                                                .setGroup(subscribeAction.getGroup())
                                                .setTopic(subscribeAction.getTopic())
                                                .setPartition(it.getAssignment().getPartition())
                                                .build()
                                        ))
                                )
                )
                .take(values.size())
                .collectList()
                .timeout(Duration.ofSeconds(10))
                .log("consumer", Level.WARNING, SignalType.ON_ERROR)
                .block();

        assertThat(records)
                .hasSize(10)
                .are(new Condition<ReceiveReply>("key is '" + key + "'") {
                    @Override
                    public boolean matches(ReceiveReply value) {
                        return key.equals(value.getRecord().getKey().toStringUtf8());
                    }
                })
                .extracting(it -> it.getRecord().getValue().toStringUtf8())
                .containsSubsequence(values.toArray(new String[values.size()]));
    }
}