/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.analysis.manual.process;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS;

@Stream(name = ProcessTraffic.INDEX_NAME, scopeId = PROCESS,
    builder = ProcessTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
        "instanceId",
        "name",
})
public class ProcessTraffic extends Metrics {
    public static final String INDEX_NAME = "process_traffic";
    public static final String SERVICE_ID = "service_id";
    public static final String INSTANCE_ID = "instance_id";
    public static final String NAME = "name";
    public static final String LAYER = "layer";
    public static final String AGENT_ID = "agent_id";
    public static final String PROPERTIES = "properties";
    public static final String LAST_PING_TIME_BUCKET = "last_ping";
    public static final String DETECT_TYPE = "detect_type";

    private static final Gson GSON = new Gson();

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(columnName = INSTANCE_ID, length = 600)
    private String instanceId;

    @Setter
    @Getter
    @Column(columnName = NAME, length = 500)
    private String name;

    @Setter
    @Getter
    @Column(columnName = LAYER)
    private int layer = Layer.UNDEFINED.value();

    @Setter
    @Getter
    @Column(columnName = LAST_PING_TIME_BUCKET)
    private long lastPingTimestamp;

    @Setter
    @Getter
    @Column(columnName = DETECT_TYPE)
    private int detectType = ProcessDetectType.UNDEFINED.value();

    @Setter
    @Getter
    @Column(columnName = AGENT_ID, length = 500)
    private String agentId;

    @Setter
    @Getter
    @Column(columnName = PROPERTIES, storageOnly = true, length = 50000)
    private JsonObject properties;

    @Override
    public boolean combine(Metrics metrics) {
        final ProcessTraffic processTraffic = (ProcessTraffic) metrics;
        this.lastPingTimestamp = processTraffic.getLastPingTimestamp();
        if (StringUtil.isNotBlank(processTraffic.getAgentId())) {
            this.agentId = processTraffic.getAgentId();
        }
        if (processTraffic.getProperties() != null && processTraffic.getProperties().size() > 0) {
            this.properties = processTraffic.getProperties();
        }
        if (processTraffic.getDetectType() > 0) {
            this.detectType = processTraffic.getDetectType();
        }
        return true;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceId(remoteData.getDataStrings(0));
        setInstanceId(remoteData.getDataStrings(1));
        setName(remoteData.getDataStrings(2));
        setLayer(remoteData.getDataIntegers(0));
        setAgentId(remoteData.getDataStrings(3));
        final String propString = remoteData.getDataStrings(4);
        if (StringUtil.isNotEmpty(propString)) {
            setProperties(GSON.fromJson(propString, JsonObject.class));
        }
        setLastPingTimestamp(remoteData.getDataLongs(0));
        setDetectType(remoteData.getDataIntegers(1));
        setTimeBucket(remoteData.getDataLongs(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceId);
        builder.addDataStrings(instanceId);
        builder.addDataStrings(name);
        builder.addDataIntegers(layer);
        builder.addDataStrings(agentId);
        if (properties == null) {
            builder.addDataStrings(Const.EMPTY_STRING);
        } else {
            builder.addDataStrings(GSON.toJson(properties));
        }
        builder.addDataLongs(lastPingTimestamp);
        builder.addDataIntegers(detectType);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    protected String id0() {
        return IDManager.ProcessID.buildId(instanceId, name);
    }

    public static class Builder implements StorageHashMapBuilder<ProcessTraffic> {

        @Override
        public ProcessTraffic storage2Entity(Map<String, Object> dbMap) {
            final ProcessTraffic processTraffic = new ProcessTraffic();
            processTraffic.setServiceId((String) dbMap.get(SERVICE_ID));
            processTraffic.setInstanceId((String) dbMap.get(INSTANCE_ID));
            processTraffic.setName((String) dbMap.get(NAME));
            processTraffic.setLayer(((Number) dbMap.get(LAYER)).intValue());
            processTraffic.setAgentId((String) dbMap.get(AGENT_ID));
            final String propString = (String) dbMap.get(PROPERTIES);
            if (StringUtil.isNotEmpty(propString)) {
                processTraffic.setProperties(GSON.fromJson(propString, JsonObject.class));
            }
            processTraffic.setLastPingTimestamp(((Number) dbMap.get(LAST_PING_TIME_BUCKET)).longValue());
            processTraffic.setDetectType(((Number) dbMap.get(DETECT_TYPE)).intValue());
            processTraffic.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            return processTraffic;
        }

        @Override
        public Map<String, Object> entity2Storage(ProcessTraffic storageData) {
            final HashMap<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(INSTANCE_ID, storageData.getInstanceId());
            map.put(NAME, storageData.getName());
            map.put(LAYER, storageData.getLayer());
            map.put(AGENT_ID, storageData.getAgentId());
            if (storageData.getProperties() != null) {
                map.put(PROPERTIES, GSON.toJson(storageData.getProperties()));
            } else {
                map.put(PROPERTIES, Const.EMPTY_STRING);
            }
            map.put(LAST_PING_TIME_BUCKET, storageData.getLastPingTimestamp());
            map.put(DETECT_TYPE, storageData.getDetectType());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }
    }

    @Override
    public void calculate() {
    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    public static class PropertyUtil {
        public static final String HOST_IP = "host_ip";
        public static final String PID = "pid";
        public static final String COMMAND_LINE = "command_line";
    }
}
