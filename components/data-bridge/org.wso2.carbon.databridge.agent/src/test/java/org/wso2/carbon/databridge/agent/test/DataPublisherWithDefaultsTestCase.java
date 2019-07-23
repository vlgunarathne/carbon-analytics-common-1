/*
*  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.databridge.agent.test;


import org.apache.log4j.Logger;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.databridge.agent.AgentHolder;
import org.wso2.carbon.databridge.agent.DataPublisher;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAgentConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAuthenticationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointException;
import org.wso2.carbon.databridge.agent.test.binary.BinaryTestServer;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.databridge.commons.exception.TransportException;
import org.wso2.carbon.databridge.commons.utils.DataBridgeCommonsUtils;
import org.wso2.carbon.databridge.core.exception.DataBridgeException;
import org.wso2.carbon.databridge.core.exception.StreamDefinitionStoreException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Datapublisher Test Case with default values.
 */
public class DataPublisherWithDefaultsTestCase {
    private static final Logger log = Logger.getLogger(DataPublisherWithDefaultsTestCase.class);
    private static final String STREAM_NAME = "org.wso2.esb.MediatorStatistics";
    private static final String VERSION = "1.0.0";
    private BinaryTestServer testServer;
    private String agentConfigFileName = "data.agent.config.yaml";

    private static final String STREAM_DEFN = "{" +
            "  'name':'" + STREAM_NAME + "'," +
            "  'version':'" + VERSION + "'," +
            "  'nickName': 'Stock Quote Information'," +
            "  'description': 'Some Desc'," +
            "  'tags':['foo', 'bar']," +
            "  'metaData':[" +
            "          {'name':'ipAdd','type':'STRING'}" +
            "  ]," +
            "  'payloadData':[" +
            "          {'name':'symbol','type':'STRING'}," +
            "          {'name':'price','type':'DOUBLE'}," +
            "          {'name':'volume','type':'INT'}," +
            "          {'name':'max','type':'DOUBLE'}," +
            "          {'name':'min','type':'Double'}" +
            "  ]" +
            "}";

    @BeforeClass
    public static void init() {
        DataPublisherTestUtil.setKeyStoreParams();
        DataPublisherTestUtil.setTrustStoreParams();
    }

    @AfterClass
    public static void stop() throws DataEndpointAuthenticationException, DataEndpointAgentConfigurationException,
            TransportException, DataEndpointException, DataEndpointConfigurationException {
        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://localhost:9687",
                "ssl://localhost:9787", "admin", "admin");
        dataPublisher.shutdownWithAgent();
    }

    private synchronized void startServer(int port, int securePort) throws DataBridgeException,
            StreamDefinitionStoreException, MalformedStreamDefinitionException, IOException {
        testServer = new BinaryTestServer();
        testServer.start(port, securePort);
        testServer.addStreamDefinition(STREAM_DEFN);
    }

    @Test
    public void overLoadPublishWithArbitraryElementsofEvent() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9661, 9761);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9661",
                "ssl://" + hostName + ":9761", "admin", "admin");

        String streamID = DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION);
        Object[] metaData = new Object[]{"127.0.0.1"};
        Object[] correlationData = null;
        Object[] payLoad = new Object[]{"WSO2", 123.4, 2, 12.4, 1.3};
        Map<String, String> arbitrary = new HashMap<String, String>();
        arbitrary.put("test", "testValue");
        arbitrary.put("test1", "test123");

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.publish(streamID, metaData, correlationData, payLoad, arbitrary);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.", e);
        }
        dataPublisher.shutdown();
        AssertJUnit.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void nonBlockingPublishTestOverLoad() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9219, 9319);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9219",
                "ssl://" + hostName + ":9319", "admin", "admin");

        String streamID = DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION);
        Object[] metaData = new Object[]{"127.0.0.1"};
        Object[] correlationData = null;
        Object[] payLoad = new Object[]{"WSO2", 123.4, 2, 12.4, 1.3};
        long timeout = 1000;

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.tryPublish(streamID, metaData, correlationData, payLoad, timeout);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.", e);
        }
        dataPublisher.shutdown();
        AssertJUnit.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void nonBlockingPublishTestOverLoadWithArbitaryElementsOfEvent() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9629, 9729);
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9629",
                "ssl://" + hostName + ":9729", "admin", "admin");

        String streamID = DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION);
        Object[] metaData = new Object[]{"127.0.0.1"};
        Object[] correlationData = null;
        Object[] payLoad = new Object[]{"WSO2", 123.4, 2, 12.4, 1.3};
        Map<String, String> arbitrary = new HashMap<String, String>();
        arbitrary.put("test", "testValue");
        arbitrary.put("test1", "test123");
        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.tryPublish(streamID, metaData, correlationData, payLoad, arbitrary);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.", e);
        }
        dataPublisher.shutdown();
        AssertJUnit.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void nonBlockingPublishTestOverLoadWithTimeStampAndArbitaryElementsOfEvent() throws
            DataEndpointAuthenticationException, DataEndpointAgentConfigurationException,
            TransportException, DataEndpointException, DataEndpointConfigurationException,
            MalformedStreamDefinitionException, DataBridgeException, StreamDefinitionStoreException, IOException {
        startServer(9699, 9799);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9699",
                "ssl://" + hostName + ":9799", "admin", "admin");

        String streamID = DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION);
        Long timeStamp = System.currentTimeMillis();
        Object[] metaData = new Object[]{"127.0.0.1"};
        Object[] correlationData = null;
        Object[] payLoad = new Object[]{"WSO2", 123.4, 2, 12.4, 1.3};
        Map<String, String> arbitrary = new HashMap<String, String>();
        arbitrary.put("test", "testValue");
        arbitrary.put("test1", "test123");

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.tryPublish(streamID, timeStamp, metaData, correlationData, payLoad, arbitrary);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.", e);
        }
        dataPublisher.shutdown();
        AssertJUnit.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test
    public void nonBlockingPublishTestWithTimeOut() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException, MalformedStreamDefinitionException, DataBridgeException,
            StreamDefinitionStoreException, IOException {
        startServer(9129, 9130);
        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary", "tcp://" + hostName + ":9129",
                "ssl://" + hostName + ":9130", "admin", "admin");

        String streamID = DataBridgeCommonsUtils.generateStreamId(STREAM_NAME, VERSION);
        Long timeStamp = System.currentTimeMillis();
        Object[] metaData = new Object[]{"127.0.0.1"};
        Object[] correlationData = null;
        Object[] payLoad = new Object[]{"WSO2", 123.4, 2, 12.4, 1.3};
        long timeout = 1000;

        int numberOfEventsSent = 1000;
        for (int i = 0; i < numberOfEventsSent; i++) {
            dataPublisher.tryPublish(streamID, timeStamp, metaData, correlationData, payLoad, timeout);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.", e);
        }
        dataPublisher.shutdown();
        AssertJUnit.assertEquals(numberOfEventsSent, testServer.getNumberOfEventsReceived());
        testServer.resetReceivedEvents();
        testServer.stop();
    }

    @Test(expectedExceptions = DataEndpointConfigurationException.class)
    public void endpointValidityCheck1() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException {

        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary",
                "{tcp://" + hostName + ":9129|tcp://" + hostName + ":9229,tcp://" + hostName + ":9229}",
                "{ssl://" + hostName + ":9130,ssl://" + hostName + ":9230}", "admin", "admin");

    }

    @Test(expectedExceptions = DataEndpointConfigurationException.class)
    public void endpointValidityCheck2() throws DataEndpointAuthenticationException,
            DataEndpointAgentConfigurationException, TransportException, DataEndpointException,
            DataEndpointConfigurationException {

        AgentHolder.setConfigPath(DataPublisherTestUtil.getDataAgentConfigPath(agentConfigFileName));
        String hostName = DataPublisherTestUtil.LOCAL_HOST;

        DataPublisher dataPublisher = new DataPublisher("Binary",
                "{tcp://" + hostName + ":9129|tcp://" + hostName + ":9229}",
                "{ssl://" + hostName + ":9130}", "admin", "admin");

    }
}
