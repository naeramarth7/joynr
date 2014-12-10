package io.joynr.integration;

/*
 * #%L
 * %%
 * Copyright (C) 2011 - 2013 BMW Car IT GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.joynr.arbitration.ArbitrationStrategy;
import io.joynr.arbitration.DiscoveryQos;
import io.joynr.exceptions.JoynrArbitrationException;
import io.joynr.exceptions.JoynrIllegalStateException;
import io.joynr.integration.util.DummyJoynrApplication;
import io.joynr.integration.util.ServersUtil;
import io.joynr.messaging.ConfigurableMessagingSettings;
import io.joynr.messaging.MessagingPropertyKeys;
import io.joynr.messaging.MessagingQos;
import io.joynr.proxy.ProxyBuilder;
import io.joynr.pubsub.SubscriptionQos;
import io.joynr.runtime.AbstractJoynrApplication;
import io.joynr.runtime.JoynrInjectorFactory;
import io.joynr.runtime.PropertyLoader;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import joynr.OnChangeSubscriptionQos;
import joynr.tests.DefaulttestProvider;
import joynr.tests.testBroadcastInterface;
import joynr.tests.testBroadcastInterface.LocationUpdateBroadcastListener;
import joynr.tests.testBroadcastInterface.LocationUpdateSelectiveBroadcastFilterParameters;
import joynr.tests.testBroadcastInterface.LocationUpdateWithSpeedBroadcastListener;
import joynr.tests.testLocationUpdateSelectiveBroadcastFilter;
import joynr.tests.testProxy;
import joynr.types.GpsFixEnum;
import joynr.types.GpsLocation;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class BroadcastEnd2EndTest {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastEnd2EndTest.class);

    private static final int CONST_DEFAULT_TEST_TIMEOUT = 3000;

    @Rule
    public TestName name = new TestName();

    private static DefaulttestProvider provider;
    private static String domain;
    private static testProxy proxy;

    // private SubscriptionQos subscriptionQos;
    private static DummyJoynrApplication providingApplication;
    private static DummyJoynrApplication consumingApplication;

    private static Server server;

    @BeforeClass
    public static void setupEndpoints() throws Exception {
        server = ServersUtil.startServers();
        domain = "TestDomain" + System.currentTimeMillis();
        setupProvidingApplication();
        setupConsumingApplication();
    }

    @Before
    public void setUp() throws JoynrArbitrationException, InterruptedException, IOException {
        Thread.sleep(200);
        Object methodName = name.getMethodName();
        logger.info("Starting {} ...", methodName);
    }

    @AfterClass
    public static void tearDownEndpoints() throws Exception {
        providingApplication.shutdown();
        providingApplication = null;
        Thread.sleep(200);
        consumingApplication.shutdown();
        consumingApplication = null;
        server.stop();
    }

    private static void setupProvidingApplication() throws InterruptedException {
        Properties factoryPropertiesProvider;

        String channelIdProvider = "JavaTest-" + UUID.randomUUID().getLeastSignificantBits()
                + "-Provider-BroadcastEnd2EndTest";

        factoryPropertiesProvider = PropertyLoader.loadProperties("testMessaging.properties");
        factoryPropertiesProvider.put(MessagingPropertyKeys.CHANNELID, channelIdProvider);
        factoryPropertiesProvider.put(MessagingPropertyKeys.RECEIVERID, UUID.randomUUID().toString());
        factoryPropertiesProvider.put(AbstractJoynrApplication.PROPERTY_JOYNR_DOMAIN_LOCAL, "localdomain-"
                + UUID.randomUUID().toString());
        providingApplication = (DummyJoynrApplication) new JoynrInjectorFactory(factoryPropertiesProvider).createApplication(DummyJoynrApplication.class);

        provider = new DefaulttestProvider();
        providingApplication.getRuntime()
                            .registerCapability(domain, provider, joynr.tests.testSync.class, "BroadcastEnd2End")
                            .waitForFullRegistration(CONST_DEFAULT_TEST_TIMEOUT);
    }

    private static void setupConsumingApplication() throws JoynrArbitrationException, JoynrIllegalStateException,
                                                   InterruptedException {
        String channelIdConsumer = "JavaTest-" + UUID.randomUUID().getLeastSignificantBits()
                + "-Consumer-BroadcastEnd2EndTest";

        Properties factoryPropertiesB = PropertyLoader.loadProperties("testMessaging.properties");
        factoryPropertiesB.put(MessagingPropertyKeys.CHANNELID, channelIdConsumer);
        factoryPropertiesB.put(MessagingPropertyKeys.RECEIVERID, UUID.randomUUID().toString());
        factoryPropertiesB.put(AbstractJoynrApplication.PROPERTY_JOYNR_DOMAIN_LOCAL, "localdomain-"
                + UUID.randomUUID().toString());

        consumingApplication = (DummyJoynrApplication) new JoynrInjectorFactory(factoryPropertiesB).createApplication(DummyJoynrApplication.class);

        MessagingQos messagingQos = new MessagingQos(5000);
        DiscoveryQos discoveryQos = new DiscoveryQos(5000, ArbitrationStrategy.HighestPriority, Long.MAX_VALUE);

        ProxyBuilder<testProxy> proxyBuilder = consumingApplication.getRuntime()
                                                                   .getProxyBuilder(domain, joynr.tests.testProxy.class);
        proxy = proxyBuilder.setMessagingQos(messagingQos).setDiscoveryQos(discoveryQos).build();
        // Wait until all the registrations and lookups are finished, to make
        // sure the timings are correct once the tests start by sending a sync
        // request to the test-proxy
        proxy.getFirstPrime();
        logger.trace("Sync call to proxy finished");

    }

    @Test
    public void testSystemPropertiesUnchanged() {
        assertTrue(System.getProperty(ConfigurableMessagingSettings.PROPERTY_SEND_MSG_RETRY_INTERVAL_MS) == null);
        assertTrue(System.getProperty(ConfigurableMessagingSettings.PROPERTY_DISCOVERY_REQUEST_TIMEOUT) == null);
    }

    @Test(timeout = CONST_DEFAULT_TEST_TIMEOUT)
    public void subscribeToBroadcastOneOutput() throws InterruptedException {

        final Semaphore broadcastReceived = new Semaphore(0);

        long minInterval = 0;
        long ttl = CONST_DEFAULT_TEST_TIMEOUT;
        long expiryDate_ms = System.currentTimeMillis() + CONST_DEFAULT_TEST_TIMEOUT;
        SubscriptionQos subscriptionQos = new OnChangeSubscriptionQos(minInterval, expiryDate_ms, ttl);
        proxy.subscribeToLocationUpdateBroadcast(new LocationUpdateBroadcastListener() {

            @Override
            public void receive(GpsLocation location) {
                assertEquals(location,
                             new GpsLocation(1.0, 2.0, 3.0, GpsFixEnum.MODE2D, 4.0, 5.0, 6.0, 7.0, 8l, 9l, 23));
                broadcastReceived.release();
            }
        }, subscriptionQos);

        Thread.sleep(300);

        provider.locationUpdateEventOccurred(new GpsLocation(1.0,
                                                             2.0,
                                                             3.0,
                                                             GpsFixEnum.MODE2D,
                                                             4.0,
                                                             5.0,
                                                             6.0,
                                                             7.0,
                                                             8l,
                                                             9l,
                                                             23));
        broadcastReceived.acquire();
    }

    @Test(timeout = CONST_DEFAULT_TEST_TIMEOUT)
    public void subscribeToBroadcastMultipleOutputs() throws InterruptedException {

        final Semaphore broadcastReceived = new Semaphore(0);

        long minInterval = 0;
        long ttl = CONST_DEFAULT_TEST_TIMEOUT;
        long expiryDate_ms = System.currentTimeMillis() + CONST_DEFAULT_TEST_TIMEOUT;
        SubscriptionQos subscriptionQos = new OnChangeSubscriptionQos(minInterval, expiryDate_ms, ttl);
        proxy.subscribeToLocationUpdateWithSpeedBroadcast(new LocationUpdateWithSpeedBroadcastListener() {

            @Override
            public void receive(GpsLocation location, Double currentSpeed) {
                assertEquals(location,
                             new GpsLocation(1.0, 2.0, 3.0, GpsFixEnum.MODE2D, 4.0, 5.0, 6.0, 7.0, 8l, 9l, 23));
                assertEquals(currentSpeed, (Double) 100.0);
                broadcastReceived.release();
            }
        }, subscriptionQos);

        Thread.sleep(300);

        provider.locationUpdateWithSpeedEventOccurred(new GpsLocation(1.0,
                                                                      2.0,
                                                                      3.0,
                                                                      GpsFixEnum.MODE2D,
                                                                      4.0,
                                                                      5.0,
                                                                      6.0,
                                                                      7.0,
                                                                      8l,
                                                                      9l,
                                                                      23), 100.0);
        broadcastReceived.acquire();
    }

    @Test(timeout = CONST_DEFAULT_TEST_TIMEOUT)
    public void subscribeToSelectiveBroadcast_FilterTrue() throws InterruptedException {

        final Semaphore broadcastReceived = new Semaphore(0);

        testLocationUpdateSelectiveBroadcastFilter filter1 = new testLocationUpdateSelectiveBroadcastFilter() {

            @Override
            public boolean filter(GpsLocation location,
                    LocationUpdateSelectiveBroadcastFilterParameters filterParameters) {
                return true;
            }
        };
        testLocationUpdateSelectiveBroadcastFilter filter2 = new testLocationUpdateSelectiveBroadcastFilter() {

            @Override
            public boolean filter(GpsLocation location,
                    LocationUpdateSelectiveBroadcastFilterParameters filterParameters) {
                return true;
            }
        };

        provider.addBroadcastFilter(filter1);
        provider.addBroadcastFilter(filter2);

        long minInterval = 0;
        long ttl = CONST_DEFAULT_TEST_TIMEOUT;
        long expiryDate_ms = System.currentTimeMillis() + CONST_DEFAULT_TEST_TIMEOUT;
        SubscriptionQos subscriptionQos = new OnChangeSubscriptionQos(minInterval, expiryDate_ms, ttl);
        proxy.subscribeToLocationUpdateSelectiveBroadcast(new testBroadcastInterface.LocationUpdateSelectiveBroadcastListener() {

                                                              @Override
                                                              public void receive(GpsLocation location) {
                                                                  assertEquals(location,
                                                                               new GpsLocation(1.0,
                                                                                               2.0,
                                                                                               3.0,
                                                                                               GpsFixEnum.MODE2D,
                                                                                               4.0,
                                                                                               5.0,
                                                                                               6.0,
                                                                                               7.0,
                                                                                               8l,
                                                                                               9l,
                                                                                               23));
                                                                  broadcastReceived.release();
                                                              }
                                                          },
                                                          subscriptionQos,
                                                          new testBroadcastInterface.LocationUpdateSelectiveBroadcastFilterParameters());

        Thread.sleep(300);

        provider.locationUpdateSelectiveEventOccurred(new GpsLocation(1.0,
                                                                      2.0,
                                                                      3.0,
                                                                      GpsFixEnum.MODE2D,
                                                                      4.0,
                                                                      5.0,
                                                                      6.0,
                                                                      7.0,
                                                                      8l,
                                                                      9l,
                                                                      23));
        broadcastReceived.acquire();
    }

    @Test(timeout = CONST_DEFAULT_TEST_TIMEOUT)
    public void subscribeToSelectiveBroadcast_FilterFalse() throws InterruptedException {

        final Semaphore broadcastReceived = new Semaphore(0);

        testLocationUpdateSelectiveBroadcastFilter filter1 = new testLocationUpdateSelectiveBroadcastFilter() {

            @Override
            public boolean filter(GpsLocation location,
                    LocationUpdateSelectiveBroadcastFilterParameters filterParameters) {
                return true;
            }
        };
        testLocationUpdateSelectiveBroadcastFilter filter2 = new testLocationUpdateSelectiveBroadcastFilter() {

            @Override
            public boolean filter(GpsLocation location,
                    LocationUpdateSelectiveBroadcastFilterParameters filterParameters) {
                return false;
            }
        };

        provider.addBroadcastFilter(filter1);
        provider.addBroadcastFilter(filter2);

        long minInterval = 0;
        long ttl = CONST_DEFAULT_TEST_TIMEOUT;
        long expiryDate_ms = System.currentTimeMillis() + CONST_DEFAULT_TEST_TIMEOUT;
        SubscriptionQos subscriptionQos = new OnChangeSubscriptionQos(minInterval, expiryDate_ms, ttl);
        proxy.subscribeToLocationUpdateSelectiveBroadcast(new testBroadcastInterface.LocationUpdateSelectiveBroadcastListener() {

                                                              @Override
                                                              public void receive(GpsLocation location) {
                                                                  assertEquals(location,
                                                                               new GpsLocation(1.0,
                                                                                               2.0,
                                                                                               3.0,
                                                                                               GpsFixEnum.MODE2D,
                                                                                               4.0,
                                                                                               5.0,
                                                                                               6.0,
                                                                                               7.0,
                                                                                               8l,
                                                                                               9l,
                                                                                               23));
                                                                  broadcastReceived.release();
                                                              }
                                                          },
                                                          subscriptionQos,
                                                          new testBroadcastInterface.LocationUpdateSelectiveBroadcastFilterParameters());

        Thread.sleep(300);

        provider.locationUpdateSelectiveEventOccurred(new GpsLocation(1.0,
                                                                      2.0,
                                                                      3.0,
                                                                      GpsFixEnum.MODE2D,
                                                                      4.0,
                                                                      5.0,
                                                                      6.0,
                                                                      7.0,
                                                                      8l,
                                                                      9l,
                                                                      23));
        assertFalse(broadcastReceived.tryAcquire(500, TimeUnit.MILLISECONDS));
    }
}
