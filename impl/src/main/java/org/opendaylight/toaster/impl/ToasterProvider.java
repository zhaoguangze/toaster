/*
 * Copyright (c) 2015 Guangze Zhao, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.*;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ToasterProvider implements BindingAwareProvider,ToasterService, DataChangeListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterProvider.class);

    private ProviderContext providerContext;
    private DataBroker dataService;
    private ListenerRegistration<DataChangeListener> dcReg;
    private BindingAwareBroker.RpcRegistration<ToasterService> rpcReg;

    private NotificationProviderService notificationService ;
    private final ExecutorService executor;
    private final AtomicReference<Future<?>> currentMakeToastTask = new AtomicReference<>();
    private final AtomicLong amountOfBreadInStock = new AtomicLong(100);
    private final AtomicLong toastsMade = new AtomicLong(0);
    private final AtomicLong darknessFactor = new AtomicLong(1000);

    private static final DisplayString TOASTER_MANUFACTURE = new DisplayString("OpenDayLight");
    private static final DisplayString TOASTER_MODEL_NUMBER = new DisplayString("Model 1 - Binding Aware");

    public static final InstanceIdentifier<Toaster> TOASTER_IID = InstanceIdentifier.builder(Toaster.class).build();

    public ToasterProvider() {
        executor = Executors.newFixedThreadPool(3);
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {

        this.providerContext = session;
        this.dataService = session.getSALService(DataBroker.class);
        this.notificationService = session.getSALService(NotificationProviderService.class);

        dcReg = dataService.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                TOASTER_IID,
                this,
                DataChangeScope.SUBTREE);

        rpcReg = session.addRpcImplementation(ToasterService.class, this);

        initToasterOperational();
        initToasterConfiguration();

        LOG.info("ToasterProvider Session Initiated");

    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change){

        DataObject dataObject = change.getUpdatedSubtree();

        if(dataObject instanceof Toaster) {

            Toaster toaster = (Toaster) dataObject;

            LOG.info("onDataChanged - new Toaster config: {}", toaster);

        } else {

            LOG.warn("onDataChanged - not instance of Toaster {}", dataObject);

        }
    }

    @Override
    public void close() throws Exception {

        executor.shutdown();
        WriteTransaction tx = dataService.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, TOASTER_IID);
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("Delete Toaster commit result: {}", result);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Delete of Toaster failed", t);
            }
        });

        dcReg.close();
        rpcReg.close();

        LOG.info("ToasterProvider Closed");

    }

    private void initToasterOperational(){

        Toaster toaster = new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURE)
                .setToasterModelNumber(TOASTER_MODEL_NUMBER)
                .setToasterStatus(Toaster.ToasterStatus.Up)
                .build();

        WriteTransaction tx = dataService.newWriteOnlyTransaction();

        tx.put(LogicalDatastoreType.OPERATIONAL, TOASTER_IID, toaster);

        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("initToasterOperational: transaction succeeded");
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.info("initToasterOperational: transaction failed");
            }
        });

        LOG.info("initToasterOperational: operational status populated: {}", toaster);

    }

    private void initToasterConfiguration(){

        Toaster toaster = new ToasterBuilder().setDarknessFactor((long) 1000)
                .build();

        WriteTransaction tx = dataService.newWriteOnlyTransaction();

        tx.put(LogicalDatastoreType.CONFIGURATION, TOASTER_IID, toaster);

        tx.submit();

        LOG.info("initToasterConfiguration: default config populated: {}", toaster);

    }

    private RpcError makeToasterOutOBreadError() {
        return RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "resouces-denied",
                "Toaster is out of bread", "out-of-stock", null, null);
    }

    private RpcError makeToasterInUseError() {
        return RpcResultBuilder.newWarning(RpcError.ErrorType.APPLICATION, "in-use",
                "Toaster is busy", null, null, null);
    }

    private Toaster buildToaster(final Toaster.ToasterStatus status){
        return new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURE)
                .setToasterModelNumber(TOASTER_MODEL_NUMBER)
                .setToasterStatus(status)
                .build();
    }

    private void setToasterStatusUp(final Function<Boolean, Void> resultCallback) {
        WriteTransaction tx = dataService.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, TOASTER_IID, buildToaster(Toaster.ToasterStatus.Up));
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                notifyCallback(true);
            }

            @Override
            public void onFailure(Throwable throwable) {
                notifyCallback(false);
            }

            void notifyCallback(final boolean result) {
                if( resultCallback != null){
                    resultCallback.apply(result);
                }
            }
        });
    }

    private boolean outOfBread(){
        return amountOfBreadInStock.get() == 0;
    }

    private void checkStatusAndMakeToast(final MakeToastInput input,
                                         final SettableFuture<RpcResult<Void>> futureResult,
                                         final int tries) {
        LOG.info("checkStatusAndMakeToast");
        
    }
    @Override
    public Future<RpcResult<Void>> makeToast(final MakeToastInput input) {
        LOG.info("makeToast: {}", input);
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        checkStatusAndMakeToast(input, futureResult, 2);
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public Future<RpcResult<Void>> restockToaster(final RestockToasterInput input) {
        LOG.info("restockToaster: {}", input);
        amountOfBreadInStock.set(input.getAmountOfBreadToStock());
        if(amountOfBreadInStock.get() > 0) {
            ToasterRestocked reStockedNotification = new ToasterRestockedBuilder()
                    .setAmountOfBread(input.getAmountOfBreadToStock()).build();

            notificationService.publish( reStockedNotification );

        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public Future<RpcResult<Void>> cancelToast() {
        LOG.info("cancelToast");
        Future<?> current = currentMakeToastTask.getAndSet(null);
        if(current != null) {
            current.cancel(true);
        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }


}
