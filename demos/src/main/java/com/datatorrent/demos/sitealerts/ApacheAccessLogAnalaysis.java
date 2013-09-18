/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.demos.sitealerts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;

import com.datatorrent.api.DAG;
import com.datatorrent.api.Operator.InputPort;
import com.datatorrent.api.StreamingApplication;
import com.datatorrent.lib.io.ApacheGenRandomLogs;
import com.datatorrent.lib.io.MapMultiConsoleOutputOperator;
import com.datatorrent.lib.logs.ApacheLogParseMapOutputOperator;
import com.datatorrent.lib.logs.DimensionObject;
import com.datatorrent.lib.logs.MultiWindowDimensionAggregation;
import com.datatorrent.lib.logs.MultiWindowDimensionAggregation.AggregateOperation;
import com.datatorrent.lib.util.DimensionTimeBucketOperator;
import com.datatorrent.lib.util.DimensionTimeBucketSumOperator;

/**
 * <p>
 * ApacheAccessLogAnalaysis class.
 * </p>
 * 
 * @since 0.3.2
 */
public class ApacheAccessLogAnalaysis implements StreamingApplication
{
  private InputPort<Map<String, DimensionObject<String>>> consoleOutput(DAG dag, String operatorName)
  {
    MapMultiConsoleOutputOperator<String, DimensionObject<String>> operator = dag.addOperator(operatorName, new MapMultiConsoleOutputOperator<String, DimensionObject<String>>());
    // operator.setSilent(true);
    return operator.input;
  }

  public DimensionTimeBucketSumOperator getPageDimensionTimeBucketSumOperator(String name, DAG dag)
  {
    DimensionTimeBucketSumOperator oper = dag.addOperator(name, DimensionTimeBucketSumOperator.class);
    oper.addDimensionKeyName("ipAddr");
    oper.addDimensionKeyName("url");
    oper.addDimensionKeyName("status");
    oper.addDimensionKeyName("agent");

    oper.addValueKeyName("bytes");
    Set<String> dimensionKey = new HashSet<String>();

    dimensionKey.add("ipAddr");
    dimensionKey.add("url");
    try {
      oper.addCombination(dimensionKey);
    } catch (NoSuchFieldException e) {
    }
    
    Set<String> dimensionKey2 = new HashSet<String>();

    dimensionKey2.add("ipAddr");
    try {
      oper.addCombination(dimensionKey2);
    } catch (NoSuchFieldException e) {
    }
    
    oper.setTimeBucketFlags(DimensionTimeBucketOperator.TIMEBUCKET_MINUTE);
    return oper;
  }

  private MultiWindowDimensionAggregation getAggregationOper(String name, DAG dag)
  {
    MultiWindowDimensionAggregation oper = dag.addOperator("sliding_window", MultiWindowDimensionAggregation.class);
    oper.setWindowSize(3);
    List<int[]> dimensionArrayList = new ArrayList<int[]>();
    int[] dimensionArray = { 0, 1 };
    int[] dimensionArray_2 = { 0 };
    dimensionArrayList.add(dimensionArray_2);
    dimensionArrayList.add(dimensionArray);
    //dimensionArrayList.add(dimensionArray_2);
    oper.setDimensionArray(dimensionArrayList);

    oper.setTimeBucket("m");
    oper.setDimensionKeyVal("0");

   // oper.setOperationType(AggregateOperation.AVERAGE);

    return oper;
  }

  @Override
  public void populateDAG(DAG dag, Configuration conf)
  {
    // set app name
    dag.setAttribute(DAG.APPLICATION_NAME, "SiteOperationsDemoApplication");

    // Generate random apche logs
    ApacheGenRandomLogs rand = dag.addOperator("rand", new ApacheGenRandomLogs());
    // dag.addStream("dimension_out", rand.outport, consoleOutput(dag,
    // "rand_console"));

    // parse log operator
    ApacheLogParseMapOutputOperator parser = dag.addOperator("parser", new ApacheLogParseMapOutputOperator());
    dag.addStream("parserInput", rand.outport, parser.data);
    // dag.addStream("dimension_out", parser.output, consoleOutput(dag,
    // "rand_console"));
    //
    DimensionTimeBucketSumOperator dimensionOperator = getPageDimensionTimeBucketSumOperator("Dimension", dag);
    // dag.getMeta(dimensionOperator).getAttributes().attr(OperatorContext.APPLICATION_WINDOW_COUNT).set(5);
    dag.addStream("dimension_in", parser.output, dimensionOperator.in);
    MultiWindowDimensionAggregation multiWindowAggOpr = getAggregationOper("sliding_window", dag);

    dag.addStream("dimension_out", dimensionOperator.out, multiWindowAggOpr.data);
    dag.addStream("aggregation_output", multiWindowAggOpr.output, consoleOutput(dag, "console"));

  }
}
