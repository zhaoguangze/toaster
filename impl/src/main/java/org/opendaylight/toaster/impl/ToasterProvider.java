/*
 * Copyright (c) 2015 Guangze Zhao, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.*;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToasterProvider implements BindingAwareProvider, DataChangeListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterProvider.class);

    private ProviderContext providerContext;
    private DataBroker dataService;
    private ListenerRegistration<DataChangeListener> dcReg;

    private static final DisplayString TOASTER_MANUFACTURE = new DisplayString("OpenDayLight");
    private static final DisplayString TOASTER_MODEL_NUMBER = new DisplayString("Model 1 - Binding Aware");

    public static final InstanceIdentifier<Toaster> TOASTER_IID = InstanceIdentifier.builder(Toaster.class).build();

    @Override
    public void onSessionInitiated(ProviderContext session) {

        this.providerContext = session;
        this.dataService = session.getSALService(DataBroker.class);

        dcReg = dataService.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                                                        TOASTER_IID,
                                                        this,
                                                        DataChangeScope.SUBTREE);

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

        dcReg.close();

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

        Toaster toaster = new ToasterBuilder().setDarknessFactor((long)1000)
                .build();

        WriteTransaction tx = dataService.newWriteOnlyTransaction();

        tx.put(LogicalDatastoreType.CONFIGURATION, TOASTER_IID, toaster);

        tx.submit();

        LOG.info("initToasterConfiguration: default config populated: {}", toaster);

    }
}
