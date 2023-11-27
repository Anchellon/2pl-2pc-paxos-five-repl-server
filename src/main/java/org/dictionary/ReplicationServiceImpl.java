package org.dictionary;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class ReplicationServiceImpl extends TxnServiceGrpc.TxnServiceImplBase implements ReplicationService  {
    String PORT_NUM;
    String txnId;
    CountDownLatch latch = new CountDownLatch(4);
    Map<String, String> permanentDbStore;
    Map<Txn, List<String>> volatileDbStore;
    Logger logger;
//    java.util.
    public ReplicationServiceImpl(Map<String, String> permanentDbStore, Map<Txn, List<String>> volatileDbStore, String txnId, Logger logger, String portNum){
        this.PORT_NUM = portNum;
        this.txnId = txnId;
    }
    @Override
    public Status replicate(List<String> addresses,KeyValue keyValue) throws InterruptedException {
        TxnKV.Builder txnVal = TxnKV.newBuilder();
        Txn txn = Txn.newBuilder().setTxn(txnId).build();
        txnVal.setKey(keyValue.getKey());
        txnVal.setValue(keyValue.getValue());
        txnVal.setTxn(txn);
        txnVal.setAddress(PORT_NUM);

        ExecutorService execService = Executors.newSingleThreadExecutor();
        ListeningExecutorService lExecService = MoreExecutors.listeningDecorator(execService);

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(0))).usePlaintext().build();
        TxnServiceGrpc.TxnServiceFutureStub txnSvc =
                 TxnServiceGrpc.newFutureStub(channel);
        ListenableFuture<Status> vote1 = txnSvc.copy(txnVal.build());
        Futures.addCallback(vote1, new VotePrinter(latch), lExecService);


        channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(1))).usePlaintext().build();
        txnSvc = TxnServiceGrpc.newFutureStub(channel);
        ListenableFuture<Status> vote2 = txnSvc.copy(txnVal.build());
        Futures.addCallback(vote2, new VotePrinter(latch), lExecService);



        channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(2))).usePlaintext().build();
        txnSvc = TxnServiceGrpc.newFutureStub(channel);
        ListenableFuture<Status> vote3 = txnSvc.copy(txnVal.build());
        Futures.addCallback(vote3, new VotePrinter(latch), lExecService);

        channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(3))).usePlaintext().build();
        txnSvc = TxnServiceGrpc.newFutureStub(channel);
        ListenableFuture<Status> vote4 = txnSvc.copy(txnVal.build());
        Futures.addCallback(vote4, new VotePrinter(latch), lExecService);

        ListenableFuture<List<Status>>  voteResult = Futures.allAsList(vote1, vote2, vote3,vote4);
        Status result = Status.newBuilder().build();
        // result is set in the call back

        Futures.addCallback(voteResult, new VoteResultGenerator(result), lExecService);
        latch.await();
        volatileDbStore.put(txn,List.of(keyValue.getKey(), keyValue.getValue()));

//        PHASE 1 Complete
//        Moving to PHASE TWO
//        Proceed to sending Do commit command
        if(result.getStatus() == "SUCCESS"){
            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(1))).usePlaintext().build();
            TxnServiceGrpc.TxnServiceStub txnCommSvc = TxnServiceGrpc.newStub(channel);
            txnCommSvc.doCommit(txn,new CommitCallBack());

            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(2))).usePlaintext().build();
            txnCommSvc = TxnServiceGrpc.newStub(channel);
            txnCommSvc.doCommit(txn,new CommitCallBack());

            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(3))).usePlaintext().build();
            txnCommSvc = TxnServiceGrpc.newStub(channel);
            txnCommSvc.doCommit(txn,new CommitCallBack());

            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(4))).usePlaintext().build();
            txnCommSvc = TxnServiceGrpc.newStub(channel);
            txnCommSvc.doCommit(txn,new CommitCallBack());
            permanentDbStore.put(keyValue.getKey(), keyValue.getValue());


            return result;
        }else {
//            doAbort and return false;
//            Txn.Builder txn = new Txn.newBuilder();
            latch = new CountDownLatch(4);
            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(0))).usePlaintext().build();
            txnSvc = TxnServiceGrpc.newFutureStub(channel);
            ListenableFuture<Status> abort1 = txnSvc.doAbort(txn);

            Futures.addCallback(abort1, new VotePrinter(latch), lExecService);


            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(1))).usePlaintext().build();
            txnSvc = TxnServiceGrpc.newFutureStub(channel);
            ListenableFuture<Status> abort2 = txnSvc.doAbort(txn);
            Futures.addCallback(abort2, new VotePrinter(latch), lExecService);



            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(2))).usePlaintext().build();
            txnSvc = TxnServiceGrpc.newFutureStub(channel);
            ListenableFuture<Status> abort3 = txnSvc.doAbort(txn);
            Futures.addCallback(abort3, new VotePrinter(latch), lExecService);

            channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(addresses.get(3))).usePlaintext().build();
            txnSvc = TxnServiceGrpc.newFutureStub(channel);
            ListenableFuture<Status> abort4 = txnSvc.doAbort(txn);
            Futures.addCallback(abort4, new VotePrinter(latch), lExecService);
            latch.await();
            ListenableFuture<List<Status>>  abortResult = Futures.allAsList(vote1, vote2, vote3,vote4);



            Futures.addCallback(abortResult, new AbortResultGenerator(result), lExecService);
            volatileDbStore.remove(txn);
            if(result.getStatus() == "ABORT_SUCCESS") {
                result = Status.newBuilder()
                        .setKey(keyValue.getKey())
                        .setValue(keyValue.getValue())
                        .setStatus("FAIL")
                        .setMessage("Replication Failed")
                        .build();
                return result;
            }else{
                result = Status.newBuilder()
                        .setKey(keyValue.getKey())
                        .setValue(keyValue.getValue())
                        .setStatus("ABORT_FAIL")
                        .setMessage("DB is now INCONSISTENT")
                        .build();
                return result;
            }
        }




    }

    @Override
    public Status delete(List<String> addresses, KeyValue keyValue, String txn) {
        return null;
    }





    class VotePrinter implements FutureCallback<Status> {
        CountDownLatch latch;
        VotePrinter(CountDownLatch latch){
            this.latch = latch;
        }
        @Override
        public void onSuccess(Status result) {
            latch.countDown();
            System.out.println("Result: " + result);

        }

        @Override
        public void onFailure(Throwable failure) {

            failure.printStackTrace();
        }

    }
    class VoteResultGenerator implements FutureCallback<List<Status>>{
        Status result;
        Status.Builder resultVal = Status.newBuilder();
        VoteResultGenerator(Status result) {
            this.result = result;
        }
        @Override
        public void onSuccess(List<Status> statuses) {

            if(
                    statuses.get(0).getStatus() == "SUCCESS" &&
                    statuses.get(1).getStatus() == "SUCCESS" &&
                    statuses.get(2).getStatus() == "SUCCESS" &&
                    statuses.get(3).getStatus() == "SUCCESS"
            ) {
               resultVal
                        .setKey(statuses.get(0).getKey())
                        .setValue(statuses.get(0).getValue())
                        .setMessage("Successfully added to replicas")
                        .setStatus("SUCCESS");

            }else {
                resultVal
                        .setKey(statuses.get(0).getKey())
                        .setValue(statuses.get(0).getValue())
                        .setMessage("Failed added to replicas")
                        .setStatus("FAIL");
            }
            result = resultVal.build();
        }

        @Override
        public void onFailure(Throwable t) {
            result = resultVal
                    .setMessage("Failed added to replicas")
                    .setStatus("FAIL")
                    .build();
        }
    }
    class Notify implements FutureCallback<Status> {
        CountDownLatch latch;
        Notify(CountDownLatch latch){
            this.latch = latch;
        }
        @Override
        public void onSuccess(Status result) {
            latch.countDown();
            System.out.println("Result: " + result.getStatus());

        }

        @Override
        public void onFailure(Throwable failure) {

            failure.printStackTrace();
        }

    }
    class AbortResultGenerator implements FutureCallback<List<Status>>{
        Status result;
        Status.Builder resultVal = Status.newBuilder();
        AbortResultGenerator(Status result) {
            this.result = result;
        }
        @Override
        public void onSuccess(List<Status> statuses) {

            if(
                    statuses.get(0).getStatus() == "ABORT_SUCCESS" &&
                            statuses.get(1).getStatus() == "ABORT_SUCCESS" &&
                            statuses.get(2).getStatus() == "ABORT_SUCCESS" &&
                            statuses.get(3).getStatus() == "ABORT_SUCCESS"
            ) {
                resultVal
                        .setKey(statuses.get(0).getKey())
                        .setValue(statuses.get(0).getValue())
                        .setMessage("Successfully Aborted Txn")
                        .setStatus("ABORT_SUCCESS");

            }else {
                resultVal
                        .setKey(statuses.get(0).getKey())
                        .setValue(statuses.get(0).getValue())
                        .setMessage("Failed to abort")
                        .setStatus("FAILED_ABORT");
            }
            result = resultVal.build();
        }

        @Override
        public void onFailure(Throwable t) {
            result = resultVal
                    .setMessage("Failed TO ABORT")
                    .setStatus("FAILED_ABORT")
                    .build();
        }
    }

    class CommitCallBack implements io.grpc.stub.StreamObserver<Status> {
        @Override
        public void onNext(Status status) {
            System.out.println("Commit Status:" + status.getStatus());
        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onCompleted() {
            System.out.println("doCommit Sent");
        }
    }
}
