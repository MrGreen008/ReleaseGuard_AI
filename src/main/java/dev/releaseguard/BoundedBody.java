package dev.releaseguard;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private final int limit;
    private long received;
    private Flow.Subscription subscription;

    BoundedBody(long contentLength, int limit) {
        this.limit = limit;
        if (contentLength > limit) {
            received = contentLength;
        }
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        delegate.onSubscribe(value);
        if (received > limit) {
            reject();
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        received += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
        if (received > limit) {
            reject();
            return;
        }
        delegate.onNext(buffers);
    }

    @Override
    public void onError(Throwable error) {
        delegate.onError(error);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }

    private void reject() {
        delegate.onError(new IOException("Integration response exceeded its size limit."));
        subscription.cancel();
    }
}
