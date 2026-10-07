package com.mendelev.mpos.print

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MPosPrintQueueTest {
    @Test fun sameEndpointIsFifoAndDifferentEndpointsRunTogether(){
        val first=CountDownLatch(2);val release=CountDownLatch(1);val done=CountDownLatch(4);val sent=Collections.synchronizedList(mutableListOf<String>())
        val queue=MPosPrintQueue<String>(endpoint={it.take(1)},send={job->if(job.endsWith("1")){first.countDown();check(release.await(5,TimeUnit.SECONDS))};sent.add(job);done.countDown()})
        try{assertTrue(queue.submit(listOf("a1","b1","a2","b2")));assertTrue(first.await(5,TimeUnit.SECONDS));assertEquals(0,sent.size);release.countDown();assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(sent.indexOf("a1")<sent.indexOf("a2"));assertTrue(sent.indexOf("b1")<sent.indexOf("b2"))}finally{release.countDown();queue.close()}
    }
    @Test fun concurrencyIsBoundedToFourEndpoints(){
        val started=CountDownLatch(4);val release=CountDownLatch(1);val finished=CountDownLatch(5);val active=AtomicInteger();val maximum=AtomicInteger()
        val queue=MPosPrintQueue<Int>(endpoint={it.toString()},send={active.incrementAndGet().also{n->maximum.updateAndGet{maxOf(it,n)}};started.countDown();check(release.await(5,TimeUnit.SECONDS));active.decrementAndGet();finished.countDown()})
        try{assertTrue(queue.submit((1..5).toList()));assertTrue(started.await(5,TimeUnit.SECONDS));assertEquals(4,active.get());release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));assertEquals(4,maximum.get())}finally{release.countDown();queue.close()}
    }
    @Test fun wholeBatchOverflowAndCloseNeverSendRejectedOrAbandonedJobs(){
        val started=CountDownLatch(1);val release=CountDownLatch(1);val cancelled=Collections.synchronizedList(mutableListOf<Int>());val sent=Collections.synchronizedList(mutableListOf<Int>())
        val queue=MPosPrintQueue<Int>(endpoint={"one"},send={sent.add(it);started.countDown();release.await(5,TimeUnit.SECONDS)},cancelled={cancelled.add(it)},capacity=2)
        try{assertTrue(queue.submit(listOf(1,2)));assertTrue(started.await(5,TimeUnit.SECONDS));assertFalse(queue.submit(listOf(3,4)));queue.close();assertFalse(queue.submit(listOf(5)));assertEquals(listOf(2),cancelled);assertEquals(listOf(1),sent)}finally{release.countDown();queue.close()}
    }
    @Test fun sendFailureCannotPoisonFollowingCopies(){
        val done=CountDownLatch(1);val errors=CountDownLatch(1)
        val queue=MPosPrintQueue<Int>(endpoint={"one"},send={if(it==1)throw IllegalStateException("test failure");done.countDown()},failed={_,_->errors.countDown()})
        try{queue.submit(listOf(1,2));assertTrue(errors.await(5,TimeUnit.SECONDS));assertTrue(done.await(5,TimeUnit.SECONDS))}finally{queue.close()}
    }
}
