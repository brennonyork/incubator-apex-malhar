/*
 *  Copyright (c) 2012-2015 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.demos.dimensions.sales.generic;

import com.datatorrent.api.Context;
import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.DAG;
import com.datatorrent.api.DAG.Locality;
import com.datatorrent.api.Operator;
import com.datatorrent.api.StreamingApplication;
import com.datatorrent.api.annotation.ApplicationAnnotation;
import com.datatorrent.contrib.dimensions.AppDataSingleSchemaDimensionStoreHDHT;
import com.datatorrent.contrib.hdht.tfile.TFileImpl;
import com.datatorrent.lib.appdata.schemas.SchemaUtils;
import com.datatorrent.lib.counters.BasicCounters;
import com.datatorrent.lib.dimensions.DimensionsComputationFlexibleSingleSchemaMap;
import com.datatorrent.lib.io.PubSubWebSocketAppDataQuery;
import com.datatorrent.lib.io.PubSubWebSocketAppDataResult;
import com.google.common.collect.Maps;
import java.net.URI;
import org.apache.commons.lang.mutable.MutableLong;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ApplicationAnnotation(name=SalesDemo.APP_NAME)
public class SalesDemo implements StreamingApplication
{
  public static final String APP_NAME = "SalesDemoCustomer";
  public static final String PROP_USE_WEBSOCKETS = "dt.application." + APP_NAME + ".useWebSockets";
  public static final String PROP_EMBEDD_QUERY = "dt.application." + APP_NAME + ".embeddQuery";
  public static final String PROP_STORE_PATH = "dt.application." + APP_NAME + ".operator.Store.fileStore.basePathPrefix";

  public static final String EVENT_SCHEMA = "salesGenericEventSchema.json";
  public static final String DIMENSIONAL_SCHEMA = "salesGenericDataSchema.json";

  public SalesDemo()
  {
  }

  @Override
  public void populateDAG(DAG dag, Configuration conf)
  {
    JsonSalesGenerator input = dag.addOperator("InputGenerator", JsonSalesGenerator.class);
    JsonToMapConverter converter = dag.addOperator("Converter", JsonToMapConverter.class);
    DimensionsComputationFlexibleSingleSchemaMap dimensions =
    dag.addOperator("DimensionsComputation", DimensionsComputationFlexibleSingleSchemaMap.class);
    dag.getMeta(dimensions).getAttributes().put(Context.OperatorContext.APPLICATION_WINDOW_COUNT, 4);
    AppDataSingleSchemaDimensionStoreHDHT store = dag.addOperator("Store", AppDataSingleSchemaDimensionStoreHDHT.class);

    String basePath = conf.get(PROP_STORE_PATH);
    TFileImpl hdsFile = new TFileImpl.DTFileImpl();
    basePath += System.currentTimeMillis();
    hdsFile.setBasePath(basePath);

    store.setFileStore(hdsFile);
    dag.setAttribute(store, Context.OperatorContext.COUNTERS_AGGREGATOR, new BasicCounters.LongAggregator< MutableLong >());
    String eventSchema = SchemaUtils.jarResourceFileToString(EVENT_SCHEMA);
    String dimensionalSchema = SchemaUtils.jarResourceFileToString(DIMENSIONAL_SCHEMA);

    dimensions.setConfigurationSchemaJSON(eventSchema);
    Map<String, String> fieldToMapField = Maps.newHashMap();
    fieldToMapField.put("sales", "amount");
    dimensions.setFieldToMapField(fieldToMapField);
    dag.getMeta(dimensions).getMeta(dimensions.output).getUnifierMeta().getAttributes().put(OperatorContext.MEMORY_MB, 8092);

    store.setConfigurationSchemaJSON(eventSchema);
    store.setDimensionalSchemaStubJSON(dimensionalSchema);
    input.setEventSchemaJSON(eventSchema);

    Operator.OutputPort<String> queryPort;
    Operator.InputPort<String> queryResultPort;

    String gatewayAddress = dag.getValue(DAG.GATEWAY_CONNECT_ADDRESS);
    URI uri = URI.create("ws://" + gatewayAddress + "/pubsub");
    PubSubWebSocketAppDataQuery wsIn = new PubSubWebSocketAppDataQuery();
    wsIn.setUri(uri);
    queryPort = wsIn.outputPort;

    dag.addOperator("Query", wsIn);
    dag.addStream("Query", queryPort, store.query).setLocality(Locality.CONTAINER_LOCAL);

    PubSubWebSocketAppDataResult wsOut = dag.addOperator("QueryResult", new PubSubWebSocketAppDataResult());
    wsOut.setUri(uri);
    queryResultPort = wsOut.input;

    dag.addStream("InputStream", input.jsonBytes, converter.input);
    dag.addStream("ConvertStream", converter.outputMap, dimensions.inputEvent);
    dag.addStream("DimensionalData", dimensions.output, store.input);
    dag.addStream("QueryResult", store.queryResult, queryResultPort).setLocality(Locality.CONTAINER_LOCAL);
  }

  private static final Logger LOG = LoggerFactory.getLogger(SalesDemo.class);
}
