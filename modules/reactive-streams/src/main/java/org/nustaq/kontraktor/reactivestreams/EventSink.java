package org.nustaq.kontraktor.reactivestreams;

import org.nustaq.kontraktor.Actor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ruedi on 28/06/15.
 */
public class EventSink<T> implements KPublisher<T> {

    protected AtomicLong credits = new AtomicLong(0);
    protected Actor actorSubs;
    protected volatile Subscriber subs;

    public boolean offer(T event) {
        if ( event == null )
            throw new RuntimeException("event cannot be null");
        if ( ( (actorSubs != null && ! actorSubs.isMailboxPressured()) || actorSubs == null ) &&
             credits.get() > 0 && subs != null ) {
            subs.onNext(event);
            credits.decrementAndGet();
            return true;
        }
        return false;
    }

    public void complete() {
        subs.onComplete();
    }

    public void error( Throwable th ) {
        subs.onError(th);
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if ( subs != null ) {
            throw new RuntimeException("only one subscription supported");
        }
        subs = subscriber;
        if ( subs instanceof Actor) {
            actorSubs = (Actor)subs;
        }
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long l) {
                if ( l <= 0 ) {
                    subs.onError(new IllegalArgumentException("spec rule 3.9: request > 0 elements"));
                }
                credits.addAndGet(l);
            }

            @Override
            public void cancel() {
                subs = null;
            }
        });
    }
}